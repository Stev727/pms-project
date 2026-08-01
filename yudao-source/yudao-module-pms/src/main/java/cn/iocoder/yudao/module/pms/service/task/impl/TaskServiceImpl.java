package cn.iocoder.yudao.module.pms.service.task.impl;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import cn.iocoder.yudao.module.pms.dal.mysql.task.TaskMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.dal.dataobject.project.PmsProjectDO;
import cn.iocoder.yudao.module.pms.dal.mysql.tasklog.TaskLogMapper;
import cn.iocoder.yudao.module.pms.dal.dataobject.tasklog.PmsTaskLogDO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import java.time.LocalDateTime;
import cn.iocoder.yudao.module.pms.service.task.TaskService;
import cn.iocoder.yudao.module.pms.service.dingtalk.DingTalkNotifyService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.time.temporal.ChronoUnit;

@Service
public class TaskServiceImpl implements TaskService {

    @Resource
    private TaskMapper taskMapper;
    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private DingTalkNotifyService dingTalkNotifyService;

    @Resource
    private TaskLogMapper taskLogMapper;

    @Resource
    private SecurityFrameworkService securityFrameworkService;

    @Override
    public Long createTask(PmsTaskDO entity) {
        normalizeSchedule(entity);
        taskMapper.insert(entity);

        return entity.getTaskId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dispatchTask(Long taskId) {
        PmsTaskDO task = requireTask(taskId);
        if (!("not_started".equals(task.getCompleteStatus()) || "rejected".equals(task.getCompleteStatus()))) {
            throw new ServiceException(cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.TASK_STATUS_INVALID);
        }
        if (task.getMainOwnerId() == null) {
            throw new ServiceException(cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.TASK_OWNER_NOT_MEMBER);
        }
        PmsProjectDO project = projectMapper.selectById(task.getProjectId());
        String projectName = project != null && project.getProjectName() != null ? project.getProjectName() : "";
        task.setCompleteStatus("pending_accept");
        task.setIsDispatched(true);
        task.setDispatchTime(LocalDateTime.now());
        taskMapper.updateById(task);
        String title = "【PMS】任务派发通知";
        String content = "项目「" + projectName + "」向您派发任务「" + task.getTaskName() + "」，请及时接收并处理。";
        boolean sent = dingTalkNotifyService.sendNotifyDirect(title, content, List.of(task.getMainOwnerId()),
                "task_dispatched", "task", taskId);
        if (!sent) {
            throw new ServiceException(cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.DINGTALK_NOTIFY_FAILED);
        }
    }

    @Override
    public void simulateDingtalkConfirm(Long taskId) {
        PmsTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ServiceException(cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.TASK_NOT_EXISTS);
        }

        PmsTaskDO update = new PmsTaskDO();
        update.setTaskId(taskId);
        update.setCompleteStatus("in_progress");
        taskMapper.updateById(update);

        // 记录任务日志
        try {
            PmsTaskLogDO log = new PmsTaskLogDO();
            log.setTaskId(taskId);
            log.setOperationType("dingtalk_confirm");
            log.setOperationTime(LocalDateTime.now());
            log.setOperationContent("钉钉确认模拟：任务状态变更为进行中");
            taskLogMapper.insert(log);
        } catch (Exception e) {
            // 日志失败不影响主流程
        }
    }

    @Override
    public void submitCompletion(Long taskId, String actualCompleteDate, String completionNote) {
        PmsTaskDO task = requireTask(taskId);
        if (!"in_progress".equals(task.getCompleteStatus())) {
            throw new ServiceException(cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.TASK_STATUS_INVALID);
        }
        task.setCompleteStatus("completion_pending_review");
        // 设置实际完成日期：优先使用用户填写的日期，否则默认今天
        if (actualCompleteDate != null && !actualCompleteDate.isEmpty()) {
            task.setActualCompleteDate(java.time.LocalDate.parse(actualCompleteDate));
        } else {
            task.setActualCompleteDate(java.time.LocalDate.now());
        }
        task.setCompletionNote(completionNote);
        taskMapper.updateById(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewCompletion(Long taskId, boolean approved, String reviewOpinion, Long operatorId) {
        PmsTaskDO task = requireTask(taskId);
        PmsProjectDO project = requireProjectManager(task, operatorId);
        if (!"completion_pending_review".equals(task.getCompleteStatus())) {
            throw new ServiceException(cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.TASK_STATUS_INVALID);
        }
        if (approved) {
            task.setCompleteStatus("completed");
            task.setProgress(100);
            task.setActualCompleteDate(java.time.LocalDate.now());
        } else {
            task.setCompleteStatus("in_progress");
        }
        task.setReviewOpinion(reviewOpinion);
        taskMapper.updateById(task);

        java.util.LinkedHashSet<Long> receiverIds = new java.util.LinkedHashSet<>();
        if (task.getMainOwnerId() != null) {
            receiverIds.add(task.getMainOwnerId());
        }
        if (project.getProjectManagerId() != null) {
            receiverIds.add(project.getProjectManagerId());
        }
        if (!receiverIds.isEmpty()) {
            String result = approved ? "通过" : "驳回";
            dingTalkNotifyService.sendNotifyDirect(
                    "【PMS】任务完成审核" + result,
                    "项目「" + (project.getProjectName() == null ? "" : project.getProjectName())
                            + "」任务「" + task.getTaskName() + "」完成审核已" + result
                            + (reviewOpinion == null || reviewOpinion.isBlank() ? "" : "，意见：" + reviewOpinion),
                    new java.util.ArrayList<>(receiverIds),
                    approved ? "completion_approved" : "completion_rejected", "task", taskId);
        }
    }

    private PmsProjectDO requireProjectManager(PmsTaskDO task, Long operatorId) {
        PmsProjectDO project = projectMapper.selectById(task.getProjectId());
        if (project == null) {
            throw new ServiceException(cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.TASK_NOT_EXISTS);
        }
        // 超管(super_admin)可以审核所有任务/变更
        if (securityFrameworkService.hasAnyRoles("super_admin")) {
            return project;
        }
        // 项目经理可以审核
        if (java.util.Objects.equals(project.getProjectManagerId(), operatorId)) {
            return project;
        }
        throw new ServiceException(cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.PROJECT_MANAGER_REQUIRED);
    }

    private PmsTaskDO requireTask(Long taskId) {
        PmsTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ServiceException(cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.TASK_NOT_EXISTS);
        }
        return task;
    }

    @Override
    public void updateTask(PmsTaskDO entity) {
        normalizeSchedule(entity);
        if ("completed".equals(entity.getCompleteStatus())) {
            entity.setProgress(100);
        }
        taskMapper.updateById(entity);
    }

    private void normalizeSchedule(PmsTaskDO task) {
        boolean hasStart = task.getPlanStartDate() != null;
        boolean hasEnd = task.getPlanEndDate() != null;
        if (hasStart != hasEnd || (hasStart && task.getPlanEndDate().isBefore(task.getPlanStartDate()))) {
            throw new ServiceException(cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.TASK_DATE_INVALID);
        }
        if (hasStart) {
            task.setCycle((int) ChronoUnit.DAYS.between(task.getPlanStartDate(), task.getPlanEndDate()) + 1);
        }
    }

    @Override
    public void deleteTask(Long id) {
        taskMapper.deleteById(id);
    }

    @Override
    public PmsTaskDO getTask(Long id) {
        return taskMapper.selectById(id);
    }

    @Override
    public List<PmsTaskDO> getTaskList() {
        return getTaskList(null, null, null);
    }

    @Override
    public List<PmsTaskDO> getTaskList(Long mainOwnerId, Long projectId, String projectType) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        boolean isAdmin = securityFrameworkService.hasAnyRoles("super_admin");

        // 管理员或模板查询：不过滤用户
        if (isAdmin || "standard_template".equals(projectType)) {
            return taskMapper.selectList(new LambdaQueryWrapperX<PmsTaskDO>()
                .eqIfPresent(PmsTaskDO::getMainOwnerId, mainOwnerId)
                .eqIfPresent(PmsTaskDO::getProjectId, projectId)
                .orderByAsc(PmsTaskDO::getSortOrder));
        }

        // 指定项目查询：判断是否为项目经理
        if (projectId != null) {
            PmsProjectDO project = projectMapper.selectById(projectId);
            if (project != null) {
                // 模板项目不过滤
                if ("standard_template".equals(project.getProjectType())) {
                    return taskMapper.selectList(new LambdaQueryWrapperX<PmsTaskDO>()
                        .eqIfPresent(PmsTaskDO::getMainOwnerId, mainOwnerId)
                        .eqIfPresent(PmsTaskDO::getProjectId, projectId)
                        .orderByAsc(PmsTaskDO::getSortOrder));
                }
                // 项目经理看全部任务
                if (project.getProjectManagerId() != null && project.getProjectManagerId().equals(userId)) {
                    return taskMapper.selectList(new LambdaQueryWrapperX<PmsTaskDO>()
                        .eqIfPresent(PmsTaskDO::getMainOwnerId, mainOwnerId)
                        .eqIfPresent(PmsTaskDO::getProjectId, projectId)
                        .orderByAsc(PmsTaskDO::getSortOrder));
                }
            }
            // 非项目经理：只返回当前用户负责/协助的任务
            LambdaQueryWrapperX<PmsTaskDO> wrapper = new LambdaQueryWrapperX<>();
            wrapper.eqIfPresent(PmsTaskDO::getProjectId, projectId);
            wrapper.orderByAsc(PmsTaskDO::getSortOrder);
            wrapper.and(w -> w.eq(PmsTaskDO::getMainOwnerId, userId)
                .or().apply("FIND_IN_SET({0}, helper_ids) > 0", userId));
            return taskMapper.selectList(wrapper);
        }

        // 全局查询（projectId == null）：项目经理看自己负责项目的全部任务 + 其他项目自己负责/协助的任务
        List<PmsProjectDO> pmProjects = projectMapper.selectList(new LambdaQueryWrapperX<PmsProjectDO>()
            .eq(PmsProjectDO::getProjectManagerId, userId)
            .ne(PmsProjectDO::getProjectType, "standard_template"));

        if (pmProjects != null && !pmProjects.isEmpty()) {
            List<Long> pmProjectIds = pmProjects.stream()
                .map(PmsProjectDO::getProjectId)
                .collect(Collectors.toList());

            // 批1：PM项目的全部任务
            List<PmsTaskDO> result = new ArrayList<>(taskMapper.selectList(new LambdaQueryWrapperX<PmsTaskDO>()
                .in(PmsTaskDO::getProjectId, pmProjectIds)
                .eqIfPresent(PmsTaskDO::getMainOwnerId, mainOwnerId)
                .orderByAsc(PmsTaskDO::getSortOrder)));

            // 批2：非PM项目中自己负责/协助的任务
            LambdaQueryWrapperX<PmsTaskDO> wrapper2 = new LambdaQueryWrapperX<>();
            wrapper2.notIn(PmsTaskDO::getProjectId, pmProjectIds);
            wrapper2.eqIfPresent(PmsTaskDO::getMainOwnerId, mainOwnerId);
            wrapper2.orderByAsc(PmsTaskDO::getSortOrder);
            wrapper2.and(w -> w.eq(PmsTaskDO::getMainOwnerId, userId)
                .or().apply("FIND_IN_SET({0}, helper_ids) > 0", userId));
            result.addAll(taskMapper.selectList(wrapper2));
            return result;
        }

        // 没有作为PM的项目：只返回自己负责/协助的任务
        LambdaQueryWrapperX<PmsTaskDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.eqIfPresent(PmsTaskDO::getMainOwnerId, mainOwnerId);
        wrapper.orderByAsc(PmsTaskDO::getSortOrder);
        wrapper.and(w -> w.eq(PmsTaskDO::getMainOwnerId, userId)
            .or().apply("FIND_IN_SET({0}, helper_ids) > 0", userId));
        return taskMapper.selectList(wrapper);
    }

}
