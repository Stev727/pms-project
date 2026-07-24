package cn.iocoder.yudao.module.pms.service.task.impl;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
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

    @Override
    public Long createTask(PmsTaskDO entity) {
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
    public void submitCompletion(Long taskId) {
        PmsTaskDO task = requireTask(taskId);
        if (!"in_progress".equals(task.getCompleteStatus())) {
            throw new ServiceException(cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.TASK_STATUS_INVALID);
        }
        task.setCompleteStatus("completion_pending_review");
        taskMapper.updateById(task);
    }

    @Override
    public void reviewCompletion(Long taskId, boolean approved, Long operatorId) {
        PmsTaskDO task = requireTask(taskId);
        requireProjectManager(task, operatorId);
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
        taskMapper.updateById(task);
    }

    private void requireProjectManager(PmsTaskDO task, Long operatorId) {
        PmsProjectDO project = projectMapper.selectById(task.getProjectId());
        if (project == null || !java.util.Objects.equals(project.getProjectManagerId(), operatorId)) {
            throw new ServiceException(cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.PROJECT_MANAGER_REQUIRED);
        }
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
        taskMapper.updateById(entity);
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
        return taskMapper.selectList(null);
    }

    @Override
    public List<PmsTaskDO> getTaskList(Long mainOwnerId, Long projectId) {
        return taskMapper.selectList(new LambdaQueryWrapperX<PmsTaskDO>()
            .eqIfPresent(PmsTaskDO::getMainOwnerId, mainOwnerId)
            .eqIfPresent(PmsTaskDO::getProjectId, projectId)
            .orderByAsc(PmsTaskDO::getSortOrder));
    }

}
