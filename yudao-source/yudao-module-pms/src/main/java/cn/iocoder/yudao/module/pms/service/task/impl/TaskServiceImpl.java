package cn.iocoder.yudao.module.pms.service.task.impl;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.dal.dataobject.project.PmsProjectDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.tasklog.PmsTaskLogDO;
import cn.iocoder.yudao.module.pms.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.task.TaskMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.tasklog.TaskLogMapper;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.pms.enums.PmsPermKeyEnum;
import cn.iocoder.yudao.module.pms.enums.PmsReviewPolicyEnum;
import cn.iocoder.yudao.module.pms.enums.PmsTaskReviewStatusEnum;
import cn.iocoder.yudao.module.pms.service.dingtalk.DingTalkNotifyService;
import cn.iocoder.yudao.module.pms.service.projectpermission.ProjectPermissionService;
import cn.iocoder.yudao.module.pms.service.task.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import cn.hutool.core.util.StrUtil;

/**
 * 任务 Service 实现
 *
 * ============================ 改造说明 ============================
 * 版本：v2（在线上原文件基础上改造，既有方法全部保留且行为兼容）
 *
 * 【#1 子任务层级】
 *   - createTask   ：自动计算 level、继承 projectId/stageId、解析默认审核人、插入后汇总父进度
 *   - updateTask   ：父任务变更时做循环引用 + 层级上限校验，并级联刷新子树 level
 *   - deleteTask   ：存在子任务时拒绝删除（TASK_HAS_CHILDREN），删除后回算父进度
 *   - 新增 getSubTaskList / getTaskTreeByProject / updateTaskProgress
 *   - 新增 私有方法 refreshProgressUpward / recalcProgressFromChildren（v1 均权平均汇总）
 *
 * 【#3 任务派发审核】
 *   - dispatchTask ：记录 assignerId、重置 reviewStatus、兜底 reviewerId
 *   - 新增 submitReview / approveReview / rejectReview / getMyReviewTaskList
 *   - 审核策略：任务级 review_policy > 项目级 review_policy > need_review
 *   - review_status 流转时同步 complete_status，保证既有列表/看板状态标签不错乱
 *
 * 【权限】
 *   审核权限判定顺序：super_admin > 任务审核人本人 > 项目经理 > 项目级权限点 task_review。
 *   项目级权限服务不可用（#2 未部署 / 存量项目权限矩阵未初始化）时自动降级，
 *   由前三项兜底，不会导致存量项目审核功能不可用。
 *
 * 【踩坑】LambdaQueryWrapperX 的 and()/or() 返回父类 LambdaQueryWrapper，
 *        含 and/or 的查询一律拆成独立语句，见 appendMyTaskCondition。
 * ==================================================================
 */
@Service
public class TaskServiceImpl implements TaskService {

    /**
     * 前端基础 URL（用于拼接任务详情跳转地址，与钉钉通知卡片/待办配合实现"点击直达"）
     * 配置项：pms.notify.frontend-base-url，默认 https://pms.topsun.com
     */
    @org.springframework.beans.factory.annotation.Value("${pms.notify.frontend-base-url:https://pms.topsun.com}")
    private String frontendBaseUrl;

    /**
     * 子任务最大层级（1 顶层 + 2 级子任务）
     */
    private static final int MAX_TASK_LEVEL = 3;

    /**
     * 祖先链遍历保护上限，防止脏数据造成死循环
     */
    private static final int ANCESTOR_GUARD = 32;

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

    @Resource
    private AdminUserMapper adminUserMapper;

    /**
     * 项目级权限服务（#2 权限分级）。
     * 用 @Autowired(required = false) 而非 @Resource，是为了让 #1/#3 可以在 #2 尚未部署时独立启动，
     * 此时项目级权限判定整体降级，由 超管 / 项目经理 / 审核人 / 主责任人 兜底。
     */
    @Autowired(required = false)
    private ProjectPermissionService projectPermissionService;

    // ==================================================================
    // 既有能力
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTask(PmsTaskDO entity) {
        normalizeSchedule(entity);
        // #1：解析层级 / 继承父任务属性 / 解析默认审核人
        resolveHierarchyOnCreate(entity);
        // #3：审核状态初始化
        if (entity.getReviewStatus() == null || entity.getReviewStatus().isEmpty()) {
            entity.setReviewStatus(PmsTaskReviewStatusEnum.NONE.getStatus());
        }
        if (entity.getProgress() == null) {
            entity.setProgress(0);
        }
        taskMapper.insert(entity);

        // #1：新增子任务后，父任务进度需要重算
        if (entity.getParentTaskId() != null) {
            refreshProgressUpward(entity.getParentTaskId());
        }
        return entity.getTaskId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dispatchTask(Long taskId) {
        PmsTaskDO task = requireTask(taskId);
        if (!("not_started".equals(task.getCompleteStatus()) || "rejected".equals(task.getCompleteStatus()))) {
            throw new ServiceException(ErrorCodeConstants.TASK_STATUS_INVALID);
        }
        if (task.getMainOwnerId() == null) {
            throw new ServiceException(ErrorCodeConstants.TASK_OWNER_NOT_MEMBER);
        }
        PmsProjectDO project = projectMapper.selectById(task.getProjectId());
        String projectName = project != null && project.getProjectName() != null ? project.getProjectName() : "";
        task.setCompleteStatus("pending_accept");
        task.setIsDispatched(true);
        task.setDispatchTime(LocalDateTime.now());
        // #3：记录派发人，并把审核状态复位（重新派发时清掉上一轮的驳回态）
        task.setAssignerId(SecurityFrameworkUtils.getLoginUserId());
        task.setReviewStatus(PmsTaskReviewStatusEnum.NONE.getStatus());
        task.setReviewComment("");
        // #1/#3：审核人兜底，保证后续「提交审核」不会因无审核人而卡死
        if (task.getReviewerId() == null) {
            task.setReviewerId(resolveDefaultReviewer(loadParent(task), task.getProjectId()));
        }
        taskMapper.updateById(task);
        writeTaskLog(taskId, "dispatch", "派发任务给用户[" + task.getMainOwnerId() + "]");
        String title = "【PMS】任务派发通知";
        // 构建富文本通知内容（项目名、任务名、计划周期、协助人）
        StringBuilder sb = new StringBuilder();
        sb.append("项目「").append(projectName).append("」向您派发任务「").append(task.getTaskName()).append("」，请及时接收并处理。");
        // 计划周期
        if (task.getPlanStartDate() != null || task.getPlanEndDate() != null) {
            String start = task.getPlanStartDate() != null ? task.getPlanStartDate().toString() : "待定";
            String end = task.getPlanEndDate() != null ? task.getPlanEndDate().toString() : "待定";
            sb.append("\n\n📅 计划周期: ").append(start).append(" ~ ").append(end);
        }
        // 协助人
        if (StrUtil.isNotBlank(task.getHelperIds())) {
            List<Long> hIds = java.util.Arrays.stream(task.getHelperIds().split(","))
                    .map(String::trim).filter(StrUtil::isNotBlank)
                    .map(Long::parseLong).collect(java.util.stream.Collectors.toList());
            if (!hIds.isEmpty()) {
                List<AdminUserDO> helpers = adminUserMapper.selectBatchIds(hIds);
                String hNames = helpers.stream().map(AdminUserDO::getNickname)
                        .collect(java.util.stream.Collectors.joining(", "));
                if (StrUtil.isNotBlank(hNames)) {
                    sb.append("\n👥 协助人: ").append(hNames);
                }
            }
        }
        String content = sb.toString();
        // #4 增强：详情跳转 URL（钉钉卡片/待办点击直达 PMS 任务抽屉）
        String detailUrl = frontendBaseUrl + "/pms/project-detail/" + task.getProjectId() + "?taskId=" + task.getTaskId();
        boolean sent = dingTalkNotifyService.sendNotifyDirect(title, content, List.of(task.getMainOwnerId()),
                "task_dispatched", "task", taskId, detailUrl);
        if (!sent) {
            throw new ServiceException(ErrorCodeConstants.DINGTALK_NOTIFY_FAILED);
        }
    }

    @Override
    public void simulateDingtalkConfirm(Long taskId) {
        PmsTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ServiceException(ErrorCodeConstants.TASK_NOT_EXISTS);
        }

        PmsTaskDO update = new PmsTaskDO();
        update.setTaskId(taskId);
        update.setCompleteStatus("in_progress");
        taskMapper.updateById(update);

        // 记录任务日志
        writeTaskLog(taskId, "dingtalk_confirm", "钉钉确认模拟：任务状态变更为进行中");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acceptTask(Long taskId) {
        PmsTaskDO task = requireTask(taskId);
        // 仅「待接收」状态可接收
        if (!"pending_accept".equals(task.getCompleteStatus())) {
            throw new ServiceException(ErrorCodeConstants.TASK_STATUS_INVALID);
        }
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        boolean isOwner = loginUserId != null && loginUserId.equals(task.getMainOwnerId());
        boolean canManage = hasProjectPerm(task.getProjectId(), PmsPermKeyEnum.TASK_EDIT.getKey());
        if (!isOwner && !canManage) {
            throw new ServiceException(ErrorCodeConstants.TASK_ACCEPT_PERMISSION_DENIED);
        }
        PmsTaskDO update = new PmsTaskDO();
        update.setTaskId(taskId);
        update.setCompleteStatus("in_progress");
        taskMapper.updateById(update);
        writeTaskLog(taskId, "task_accept", "任务负责人确认接收，状态变更为进行中");
    }

    @Override
    public void submitCompletion(Long taskId, String actualCompleteDate, String completionNote) {
        PmsTaskDO task = requireTask(taskId);
        if (!"in_progress".equals(task.getCompleteStatus())) {
            throw new ServiceException(ErrorCodeConstants.TASK_STATUS_INVALID);
        }
        task.setCompleteStatus("completion_pending_review");
        // 设置实际完成日期：优先使用用户填写的日期，否则默认今天
        if (actualCompleteDate != null && !actualCompleteDate.isEmpty()) {
            task.setActualCompleteDate(LocalDate.parse(actualCompleteDate));
        } else {
            task.setActualCompleteDate(LocalDate.now());
        }
        task.setCompletionNote(completionNote);
        // #3：与新审核状态机对齐，避免两套状态打架
        task.setReviewStatus(PmsTaskReviewStatusEnum.SUBMITTED.getStatus());
        if (task.getReviewerId() == null) {
            task.setReviewerId(resolveDefaultReviewer(loadParent(task), task.getProjectId()));
        }
        taskMapper.updateById(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewCompletion(Long taskId, boolean approved, String reviewOpinion, Long operatorId) {
        PmsTaskDO task = requireTask(taskId);
        PmsProjectDO project = requireProjectManager(task, operatorId);
        if (!"completion_pending_review".equals(task.getCompleteStatus())) {
            throw new ServiceException(ErrorCodeConstants.TASK_STATUS_INVALID);
        }
        if (approved) {
            task.setCompleteStatus("completed");
            task.setProgress(100);
            task.setActualCompleteDate(LocalDate.now());
            task.setReviewStatus(PmsTaskReviewStatusEnum.COMPLETED.getStatus());
        } else {
            task.setCompleteStatus("in_progress");
            task.setReviewStatus(PmsTaskReviewStatusEnum.REJECTED.getStatus());
        }
        task.setReviewOpinion(reviewOpinion);
        // #3：同步到新字段，两个入口的审核意见展示保持一致
        task.setReviewComment(reviewOpinion == null ? "" : reviewOpinion);
        taskMapper.updateById(task);

        // #1：完成状态变化会影响父任务进度
        refreshProgressUpward(task.getParentTaskId());

        LinkedHashSet<Long> receiverIds = new LinkedHashSet<>();
        if (task.getMainOwnerId() != null) {
            receiverIds.add(task.getMainOwnerId());
        }
        if (project.getProjectManagerId() != null) {
            receiverIds.add(project.getProjectManagerId());
        }
        if (!receiverIds.isEmpty()) {
            String result = approved ? "通过" : "驳回";
            // #4 增强：详情跳转 URL（钉钉卡片点击直达 PMS 任务抽屉）
            String detailUrl = frontendBaseUrl + "/pms/project-detail/" + task.getProjectId() + "?taskId=" + task.getTaskId();
            dingTalkNotifyService.sendNotifyDirect(
                    "【PMS】任务完成审核" + result,
                    "项目「" + (project.getProjectName() == null ? "" : project.getProjectName())
                            + "」任务「" + task.getTaskName() + "」完成审核已" + result
                            + (reviewOpinion == null || reviewOpinion.isBlank() ? "" : "，意见：" + reviewOpinion),
                    new ArrayList<>(receiverIds),
                    approved ? "completion_approved" : "completion_rejected", "task", taskId, detailUrl);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTask(PmsTaskDO entity) {
        normalizeSchedule(entity);
        if ("completed".equals(entity.getCompleteStatus())) {
            entity.setProgress(100);
        }
        PmsTaskDO old = requireTask(entity.getTaskId());

        // #1：父任务变更（挂到别的任务下 / 从顶层变子任务）需要重新校验层级
        boolean parentChanged = entity.getParentTaskId() != null
                && !Objects.equals(entity.getParentTaskId(), old.getParentTaskId());
        if (parentChanged) {
            applyParentChange(old, entity);
        } else {
            // 不允许前端直接改 level，避免层级被写坏
            entity.setLevel(null);
        }

        taskMapper.updateById(entity);

        if (parentChanged) {
            // 子树层级级联下推
            cascadeLevel(entity.getTaskId(), entity.getLevel());
            // 旧父链的子任务集合发生变化，需要回算
            refreshProgressUpward(old.getParentTaskId());
        }
        // 进度 / 完成状态变化都会影响父任务进度
        if (entity.getProgress() != null || entity.getCompleteStatus() != null || parentChanged) {
            PmsTaskDO current = taskMapper.selectById(entity.getTaskId());
            if (current != null) {
                refreshProgressUpward(current.getParentTaskId());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(Long id) {
        PmsTaskDO task = taskMapper.selectById(id);
        // 权限校验：PM、super_admin、任务主责任人，或拥有项目级 task_delete 权限者可删除
        if (!securityFrameworkService.hasAnyRoles("super_admin")) {
            Long userId = SecurityFrameworkUtils.getLoginUserId();
            if (task != null) {
                PmsProjectDO project = projectMapper.selectById(task.getProjectId());
                boolean isPM = project != null && Objects.equals(project.getProjectManagerId(), userId);
                boolean isOwner = Objects.equals(task.getMainOwnerId(), userId);
                boolean hasPerm = hasProjectPerm(task.getProjectId(), PmsPermKeyEnum.TASK_DELETE.getKey());
                if (!isPM && !isOwner && !hasPerm) {
                    throw new ServiceException(ErrorCodeConstants.PROJECT_MANAGER_REQUIRED);
                }
            }
        }
        // #1：存在子任务时禁止删除，避免产生孤儿节点
        Long childCount = taskMapper.selectCountByParentTaskId(id);
        if (childCount != null && childCount > 0) {
            throw new ServiceException(ErrorCodeConstants.TASK_HAS_CHILDREN);
        }
        Long parentTaskId = task == null ? null : task.getParentTaskId();
        taskMapper.deleteById(id);
        // #1：删除子任务后父任务进度需要重算
        refreshProgressUpward(parentTaskId);
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
            // 非项目经理：只返回当前用户负责/协助/待审核的任务
            LambdaQueryWrapperX<PmsTaskDO> wrapper = new LambdaQueryWrapperX<>();
            wrapper.eqIfPresent(PmsTaskDO::getProjectId, projectId);
            wrapper.orderByAsc(PmsTaskDO::getSortOrder);
            appendMyTaskCondition(wrapper, userId);
            // #1：补齐祖先任务，避免子任务在树里断层显示
            return supplementAncestors(taskMapper.selectList(wrapper));
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

            // 批2：非PM项目中自己负责/协助/待审核的任务
            LambdaQueryWrapperX<PmsTaskDO> wrapper2 = new LambdaQueryWrapperX<>();
            wrapper2.notIn(PmsTaskDO::getProjectId, pmProjectIds);
            wrapper2.eqIfPresent(PmsTaskDO::getMainOwnerId, mainOwnerId);
            wrapper2.orderByAsc(PmsTaskDO::getSortOrder);
            appendMyTaskCondition(wrapper2, userId);
            result.addAll(taskMapper.selectList(wrapper2));
            return result;
        }

        // 没有作为PM的项目：只返回自己负责/协助/待审核的任务
        LambdaQueryWrapperX<PmsTaskDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.eqIfPresent(PmsTaskDO::getMainOwnerId, mainOwnerId);
        wrapper.orderByAsc(PmsTaskDO::getSortOrder);
        appendMyTaskCondition(wrapper, userId);
        return taskMapper.selectList(wrapper);
    }

    // ==================================================================
    // #1 子任务层级
    // ==================================================================

    @Override
    public List<PmsTaskDO> getSubTaskList(Long parentTaskId) {
        if (parentTaskId == null) {
            return new ArrayList<>();
        }
        return taskMapper.selectListByParentTaskId(parentTaskId);
    }

    @Override
    public List<PmsTaskDO> getTaskTreeByProject(Long projectId) {
        if (projectId == null) {
            return new ArrayList<>();
        }
        return taskMapper.selectListByProjectId(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTaskProgress(Long taskId, Integer progress) {
        PmsTaskDO task = requireTask(taskId);
        // 父任务进度由子任务汇总得到，不允许手工填报
        Long childCount = taskMapper.selectCountByParentTaskId(taskId);
        if (childCount != null && childCount > 0) {
            throw new ServiceException(ErrorCodeConstants.TASK_STATUS_INVALID);
        }
        int value = progress == null ? 0 : progress;
        if (value < 0) {
            value = 0;
        }
        if (value > 100) {
            value = 100;
        }
        PmsTaskDO update = new PmsTaskDO();
        update.setTaskId(taskId);
        update.setProgress(value);
        if (value > 0 && "not_started".equals(task.getCompleteStatus())) {
            update.setCompleteStatus("in_progress");
        }
        taskMapper.updateById(update);
        writeTaskLog(taskId, "progress_report", "进度更新为 " + value + "%");
        refreshProgressUpward(task.getParentTaskId());
    }

    /**
     * 创建任务时解析层级信息：
     * - 顶层任务 level = 1，审核人默认取项目经理
     * - 子任务 level = 父任务 level + 1，超过 3 级报错；projectId/stageId 缺省继承父任务；
     *   审核人默认取父任务主责任人
     */
    private void resolveHierarchyOnCreate(PmsTaskDO entity) {
        Long parentTaskId = entity.getParentTaskId();
        if (parentTaskId == null) {
            entity.setLevel(1);
            if (entity.getReviewerId() == null) {
                entity.setReviewerId(resolveDefaultReviewer(null, entity.getProjectId()));
            }
            return;
        }
        PmsTaskDO parent = taskMapper.selectById(parentTaskId);
        if (parent == null) {
            throw new ServiceException(ErrorCodeConstants.TASK_PARENT_NOT_EXISTS);
        }
        int parentLevel = parent.getLevel() == null ? 1 : parent.getLevel();
        int level = parentLevel + 1;
        if (level > MAX_TASK_LEVEL) {
            throw new ServiceException(ErrorCodeConstants.TASK_LEVEL_EXCEED);
        }
        entity.setLevel(level);
        if (entity.getProjectId() == null) {
            entity.setProjectId(parent.getProjectId());
        }
        if (entity.getStageId() == null) {
            entity.setStageId(parent.getStageId());
        }
        if (entity.getReviewerId() == null) {
            entity.setReviewerId(resolveDefaultReviewer(parent, entity.getProjectId()));
        }
    }

    /**
     * 更新任务时处理父任务变更：循环引用校验 + 层级上限校验 + 审核人兜底
     */
    private void applyParentChange(PmsTaskDO old, PmsTaskDO entity) {
        Long newParentId = entity.getParentTaskId();
        validateNotCircular(old.getTaskId(), newParentId);
        PmsTaskDO parent = taskMapper.selectById(newParentId);
        if (parent == null) {
            throw new ServiceException(ErrorCodeConstants.TASK_PARENT_NOT_EXISTS);
        }
        int newLevel = (parent.getLevel() == null ? 1 : parent.getLevel()) + 1;
        if (newLevel > MAX_TASK_LEVEL) {
            throw new ServiceException(ErrorCodeConstants.TASK_LEVEL_EXCEED);
        }
        // 连同自身子树一起搬迁，搬迁后最深的一层也不能越界
        int subTreeDepth = calcSubTreeDepth(old.getTaskId(), 1);
        if (newLevel + subTreeDepth - 1 > MAX_TASK_LEVEL) {
            throw new ServiceException(ErrorCodeConstants.TASK_LEVEL_EXCEED);
        }
        entity.setLevel(newLevel);
        if (entity.getReviewerId() == null && parent.getMainOwnerId() != null) {
            entity.setReviewerId(parent.getMainOwnerId());
        }
    }

    /**
     * 防循环引用：沿新父任务向上回溯，若遇到自己则说明成环
     */
    private void validateNotCircular(Long taskId, Long newParentId) {
        if (newParentId == null) {
            return;
        }
        if (Objects.equals(taskId, newParentId)) {
            throw new ServiceException(ErrorCodeConstants.TASK_PARENT_CIRCULAR);
        }
        Long cursor = newParentId;
        int guard = 0;
        while (cursor != null && guard++ < ANCESTOR_GUARD) {
            if (Objects.equals(cursor, taskId)) {
                throw new ServiceException(ErrorCodeConstants.TASK_PARENT_CIRCULAR);
            }
            PmsTaskDO parent = taskMapper.selectById(cursor);
            if (parent == null) {
                break;
            }
            cursor = parent.getParentTaskId();
        }
    }

    /**
     * 计算以 taskId 为根的子树深度（自身算 1 层）
     */
    private int calcSubTreeDepth(Long taskId, int depth) {
        if (depth >= MAX_TASK_LEVEL) {
            return depth;
        }
        List<PmsTaskDO> children = taskMapper.selectListByParentTaskId(taskId);
        int max = depth;
        for (PmsTaskDO child : children) {
            int childDepth = calcSubTreeDepth(child.getTaskId(), depth + 1);
            if (childDepth > max) {
                max = childDepth;
            }
        }
        return max;
    }

    /**
     * 父任务层级变化后，把子树的 level 逐层下推
     */
    private void cascadeLevel(Long taskId, Integer level) {
        if (level == null) {
            return;
        }
        List<PmsTaskDO> children = taskMapper.selectListByParentTaskId(taskId);
        for (PmsTaskDO child : children) {
            int childLevel = level + 1;
            if (childLevel > MAX_TASK_LEVEL) {
                throw new ServiceException(ErrorCodeConstants.TASK_LEVEL_EXCEED);
            }
            PmsTaskDO update = new PmsTaskDO();
            update.setTaskId(child.getTaskId());
            update.setLevel(childLevel);
            taskMapper.updateById(update);
            cascadeLevel(child.getTaskId(), childLevel);
        }
    }

    /**
     * 自底向上重算祖先任务进度。
     * v1 汇总规则：父任务进度 = 直接子任务进度的算术平均（均权），四舍五入取整。
     *
     * @param parentTaskId 起始父任务ID，为 null 表示当前任务是顶层任务，无需汇总
     */
    private void refreshProgressUpward(Long parentTaskId) {
        Long cursor = parentTaskId;
        int guard = 0;
        while (cursor != null && guard++ < ANCESTOR_GUARD) {
            PmsTaskDO parent = taskMapper.selectById(cursor);
            if (parent == null) {
                break;
            }
            recalcProgressFromChildren(parent);
            cursor = parent.getParentTaskId();
        }
    }

    /**
     * 用直接子任务的进度重算某个父任务的进度
     */
    private void recalcProgressFromChildren(PmsTaskDO parent) {
        List<PmsTaskDO> children = taskMapper.selectListByParentTaskId(parent.getTaskId());
        if (children.isEmpty()) {
            // 已经没有子任务了，保留其自身进度，不做覆盖
            return;
        }
        int sum = 0;
        for (PmsTaskDO child : children) {
            sum += child.getProgress() == null ? 0 : child.getProgress();
        }
        int avg = (int) Math.round((double) sum / children.size());
        if (avg < 0) {
            avg = 0;
        }
        if (avg > 100) {
            avg = 100;
        }
        if (Objects.equals(parent.getProgress(), avg)) {
            return;
        }
        PmsTaskDO update = new PmsTaskDO();
        update.setTaskId(parent.getTaskId());
        update.setProgress(avg);
        taskMapper.updateById(update);
    }

    /**
     * 把结果集中缺失的祖先任务补齐，保证前端能组出完整的树。
     * 场景：子任务分给了 A，父任务属于 B，A 登录时只查到子任务会导致树断层。
     */
    private List<PmsTaskDO> supplementAncestors(List<PmsTaskDO> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return tasks;
        }
        Map<Long, PmsTaskDO> merged = new LinkedHashMap<>();
        Deque<Long> queue = new ArrayDeque<>();
        for (PmsTaskDO task : tasks) {
            merged.put(task.getTaskId(), task);
            if (task.getParentTaskId() != null) {
                queue.add(task.getParentTaskId());
            }
        }
        int guard = 0;
        while (!queue.isEmpty() && guard++ < 1000) {
            Long parentId = queue.poll();
            if (parentId == null || merged.containsKey(parentId)) {
                continue;
            }
            PmsTaskDO parent = taskMapper.selectById(parentId);
            if (parent == null) {
                continue;
            }
            merged.put(parentId, parent);
            if (parent.getParentTaskId() != null) {
                queue.add(parent.getParentTaskId());
            }
        }
        return new ArrayList<>(merged.values());
    }

    // ==================================================================
    // #3 任务派发审核
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitReview(Long taskId) {
        PmsTaskDO task = requireTask(taskId);
        // 只有进行中 / 已延期的任务可以提交审核
        if (!("in_progress".equals(task.getCompleteStatus()) || "delayed".equals(task.getCompleteStatus()))) {
            throw new ServiceException(ErrorCodeConstants.TASK_STATUS_INVALID);
        }
        if (!PmsTaskReviewStatusEnum.canSubmit(task.getReviewStatus())) {
            throw new ServiceException(ErrorCodeConstants.TASK_REVIEW_STATUS_INVALID);
        }
        PmsProjectDO project = projectMapper.selectById(task.getProjectId());
        String policy = resolveReviewPolicy(task, project);
        String projectName = project == null || project.getProjectName() == null ? "" : project.getProjectName();

        PmsTaskDO update = new PmsTaskDO();
        update.setTaskId(taskId);
        if (task.getActualCompleteDate() == null) {
            update.setActualCompleteDate(LocalDate.now());
        }

        // 策略为 self_review / skip：提交即视为通过，不进审核队列
        if (!PmsReviewPolicyEnum.needReview(policy)) {
            update.setReviewStatus(PmsTaskReviewStatusEnum.COMPLETED.getStatus());
            update.setCompleteStatus("completed");
            update.setProgress(100);
            update.setReviewComment("审核策略[" + policyLabel(policy) + "]，提交即通过");
            taskMapper.updateById(update);
            refreshProgressUpward(task.getParentTaskId());
            writeTaskLog(taskId, "submit_review", "提交审核，策略[" + policyLabel(policy) + "]自动通过");
            String detailUrl689 = frontendBaseUrl + "/pms/project-detail/" + task.getProjectId() + "?taskId=" + taskId;
            sendNotifyQuietly("【PMS】任务已完成",
                    "项目「" + projectName + "」任务「" + task.getTaskName() + "」已按" + policyLabel(policy) + "策略直接完成。",
                    buildReviewReceivers(task, project), "task_review_auto_passed", taskId, detailUrl689);
            return;
        }

        // 策略为 need_review：必须有审核人
        Long reviewerId = task.getReviewerId();
        if (reviewerId == null) {
            reviewerId = resolveDefaultReviewer(loadParent(task), task.getProjectId());
        }
        if (reviewerId == null) {
            throw new ServiceException(ErrorCodeConstants.TASK_REVIEWER_REQUIRED);
        }
        update.setReviewerId(reviewerId);
        update.setReviewStatus(PmsTaskReviewStatusEnum.SUBMITTED.getStatus());
        // 同步既有完成状态，保证列表/看板标签显示为「待审核」
        update.setCompleteStatus("completion_pending_review");
        // 清掉上一轮驳回原因（用空串而非 null，MyBatis Plus 的 updateById 会忽略 null 字段）
        update.setReviewComment("");
        taskMapper.updateById(update);

        writeTaskLog(taskId, "submit_review", "提交审核，审核人[" + reviewerId + "]");
        String detailUrl714 = frontendBaseUrl + "/pms/project-detail/" + task.getProjectId() + "?taskId=" + taskId;
        sendNotifyQuietly("【PMS】任务待您审核",
                "项目「" + projectName + "」任务「" + task.getTaskName() + "」已提交完成，请及时审核。",
                List.of(reviewerId), "task_review_submitted", taskId, detailUrl714);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveReview(Long taskId, String reviewComment) {
        PmsTaskDO task = requireTask(taskId);
        if (!PmsTaskReviewStatusEnum.canReview(task.getReviewStatus())) {
            throw new ServiceException(ErrorCodeConstants.TASK_REVIEW_STATUS_INVALID);
        }
        PmsProjectDO project = projectMapper.selectById(task.getProjectId());
        requireReviewPermission(task, project, SecurityFrameworkUtils.getLoginUserId());

        PmsTaskDO update = new PmsTaskDO();
        update.setTaskId(taskId);
        update.setReviewStatus(PmsTaskReviewStatusEnum.COMPLETED.getStatus());
        update.setCompleteStatus("completed");
        update.setProgress(100);
        update.setActualCompleteDate(task.getActualCompleteDate() == null ? LocalDate.now() : task.getActualCompleteDate());
        update.setReviewComment(reviewComment == null ? "" : reviewComment);
        if (reviewComment != null && !reviewComment.isBlank()) {
            // 兼容既有「完成确认意见」展示
            update.setReviewOpinion(reviewComment);
        }
        taskMapper.updateById(update);

        // #1：子任务通过后，父任务进度自动汇总
        refreshProgressUpward(task.getParentTaskId());

        writeTaskLog(taskId, "approve_review", "审核通过" + (reviewComment == null || reviewComment.isBlank() ? "" : "：" + reviewComment));
        String projectName = project == null || project.getProjectName() == null ? "" : project.getProjectName();
        String detailUrl747 = frontendBaseUrl + "/pms/project-detail/" + task.getProjectId() + "?taskId=" + taskId;
        sendNotifyQuietly("【PMS】任务审核通过",
                "项目「" + projectName + "」任务「" + task.getTaskName() + "」审核已通过。"
                        + (reviewComment == null || reviewComment.isBlank() ? "" : "意见：" + reviewComment),
                buildReviewReceivers(task, project), "task_review_approved", taskId, detailUrl747);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectReview(Long taskId, String reviewComment) {
        if (reviewComment == null || reviewComment.isBlank()) {
            throw new ServiceException(ErrorCodeConstants.TASK_REVIEW_COMMENT_REQUIRED);
        }
        PmsTaskDO task = requireTask(taskId);
        if (!PmsTaskReviewStatusEnum.canReview(task.getReviewStatus())) {
            throw new ServiceException(ErrorCodeConstants.TASK_REVIEW_STATUS_INVALID);
        }
        PmsProjectDO project = projectMapper.selectById(task.getProjectId());
        requireReviewPermission(task, project, SecurityFrameworkUtils.getLoginUserId());

        PmsTaskDO update = new PmsTaskDO();
        update.setTaskId(taskId);
        update.setReviewStatus(PmsTaskReviewStatusEnum.REJECTED.getStatus());
        // 驳回后回到进行中，负责人整改后可再次提交
        update.setCompleteStatus("in_progress");
        update.setReviewComment(reviewComment);
        update.setReviewOpinion(reviewComment);
        taskMapper.updateById(update);

        writeTaskLog(taskId, "reject_review", "审核驳回：" + reviewComment);
        String projectName = project == null || project.getProjectName() == null ? "" : project.getProjectName();
        String detailUrl778 = frontendBaseUrl + "/pms/project-detail/" + task.getProjectId() + "?taskId=" + taskId;
        sendNotifyQuietly("【PMS】任务审核被驳回",
                "项目「" + projectName + "」任务「" + task.getTaskName() + "」审核被驳回，原因：" + reviewComment,
                buildReviewReceivers(task, project), "task_review_rejected", taskId, detailUrl778);
    }

    @Override
    public List<PmsTaskDO> getMyReviewTaskList(Long projectId, String reviewStatus) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        String status = reviewStatus == null || reviewStatus.isEmpty()
                ? PmsTaskReviewStatusEnum.SUBMITTED.getStatus() : reviewStatus;

        Map<Long, PmsTaskDO> merged = new LinkedHashMap<>();
        for (PmsTaskDO task : taskMapper.selectListByReviewer(userId, projectId, status)) {
            merged.put(task.getTaskId(), task);
        }

        // 超管：所有该状态的任务都能审
        if (securityFrameworkService.hasAnyRoles("super_admin")) {
            for (PmsTaskDO task : taskMapper.selectListByProjectAndReviewStatus(projectId, status)) {
                merged.putIfAbsent(task.getTaskId(), task);
            }
            return new ArrayList<>(merged.values());
        }

        // 项目经理：自己管的项目内该状态任务都能审（兜住审核人未设置的历史数据）
        List<PmsProjectDO> pmProjects = projectMapper.selectList(new LambdaQueryWrapperX<PmsProjectDO>()
                .eq(PmsProjectDO::getProjectManagerId, userId)
                .eqIfPresent(PmsProjectDO::getProjectId, projectId));
        if (pmProjects != null && !pmProjects.isEmpty()) {
            List<Long> pmProjectIds = pmProjects.stream()
                    .map(PmsProjectDO::getProjectId)
                    .collect(Collectors.toList());
            List<PmsTaskDO> pmTasks = taskMapper.selectList(new LambdaQueryWrapperX<PmsTaskDO>()
                    .in(PmsTaskDO::getProjectId, pmProjectIds)
                    .eq(PmsTaskDO::getReviewStatus, status)
                    .orderByDesc(PmsTaskDO::getUpdateTime));
            for (PmsTaskDO task : pmTasks) {
                merged.putIfAbsent(task.getTaskId(), task);
            }
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 解析生效的审核策略：任务级 > 项目级 > 默认 need_review
     */
    private String resolveReviewPolicy(PmsTaskDO task, PmsProjectDO project) {
        if (task != null && PmsReviewPolicyEnum.isValid(task.getReviewPolicy())) {
            return task.getReviewPolicy();
        }
        if (project != null && PmsReviewPolicyEnum.isValid(project.getReviewPolicy())) {
            return project.getReviewPolicy();
        }
        return PmsReviewPolicyEnum.NEED_REVIEW.getPolicy();
    }

    private String policyLabel(String policy) {
        return PmsReviewPolicyEnum.SKIP.getPolicy().equals(policy)
                ? PmsReviewPolicyEnum.SKIP.getLabel() : PmsReviewPolicyEnum.SELF_REVIEW.getLabel();
    }

    /**
     * 审核权限：super_admin > 审核人本人 > 项目经理 > 项目级权限点 task_review
     */
    private void requireReviewPermission(PmsTaskDO task, PmsProjectDO project, Long userId) {
        if (securityFrameworkService.hasAnyRoles("super_admin")) {
            return;
        }
        if (task.getReviewerId() != null && Objects.equals(task.getReviewerId(), userId)) {
            return;
        }
        if (project != null && Objects.equals(project.getProjectManagerId(), userId)) {
            return;
        }
        if (hasProjectPerm(task.getProjectId(), PmsPermKeyEnum.TASK_REVIEW.getKey())) {
            return;
        }
        throw new ServiceException(ErrorCodeConstants.PROJECT_MANAGER_REQUIRED);
    }

    /**
     * 审核结果通知接收人：主责任人 + 派发人 + 项目经理
     */
    private LinkedHashSet<Long> buildReviewReceivers(PmsTaskDO task, PmsProjectDO project) {
        LinkedHashSet<Long> receivers = new LinkedHashSet<>();
        if (task.getMainOwnerId() != null) {
            receivers.add(task.getMainOwnerId());
        }
        if (task.getAssignerId() != null) {
            receivers.add(task.getAssignerId());
        }
        if (project != null && project.getProjectManagerId() != null) {
            receivers.add(project.getProjectManagerId());
        }
        return receivers;
    }

    // ==================================================================
    // 通用私有方法
    // ==================================================================

    /**
     * 「我参与的任务」过滤条件：主责任人 / 审核人 / 协助人。
     *
     * 【踩坑】LambdaQueryWrapperX 的 and()/or() 返回父类 LambdaQueryWrapper，
     * 不能继续链式调用 X 扩展方法，因此 lambda 内必须写成独立语句。
     */
    private void appendMyTaskCondition(LambdaQueryWrapperX<PmsTaskDO> wrapper, Long userId) {
        final Long uid = userId;
        wrapper.and(w -> {
            w.eq(PmsTaskDO::getMainOwnerId, uid);
            w.or();
            w.eq(PmsTaskDO::getReviewerId, uid);
            w.or();
            w.apply("FIND_IN_SET({0}, helper_ids) > 0", uid);
        });
    }

    /**
     * 解析默认审核人：有父任务取父任务主责任人，否则取项目经理
     */
    private Long resolveDefaultReviewer(PmsTaskDO parent, Long projectId) {
        if (parent != null && parent.getMainOwnerId() != null) {
            return parent.getMainOwnerId();
        }
        if (projectId == null) {
            return null;
        }
        PmsProjectDO project = projectMapper.selectById(projectId);
        return project == null ? null : project.getProjectManagerId();
    }

    private PmsTaskDO loadParent(PmsTaskDO task) {
        if (task == null || task.getParentTaskId() == null) {
            return null;
        }
        return taskMapper.selectById(task.getParentTaskId());
    }

    /**
     * 项目级权限判定。#2 未部署或权限矩阵未初始化时返回 false（降级），
     * 由 超管 / 项目经理 / 审核人 / 主责任人 兜底放行，避免存量项目功能不可用。
     */
    private boolean hasProjectPerm(Long projectId, String permKey) {
        if (projectPermissionService == null || projectId == null) {
            return false;
        }
        try {
            return projectPermissionService.can(SecurityFrameworkUtils.getLoginUserId(), projectId, permKey);
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * 写任务操作日志，失败不影响主流程
     */
    private void writeTaskLog(Long taskId, String operationType, String content) {
        try {
            PmsTaskLogDO log = new PmsTaskLogDO();
            log.setTaskId(taskId);
            log.setOperationType(operationType);
            log.setOperatorId(SecurityFrameworkUtils.getLoginUserId());
            log.setOperationTime(LocalDateTime.now());
            log.setOperationContent(content);
            taskLogMapper.insert(log);
        } catch (Exception e) {
            // 日志失败不影响主流程
        }
    }

    /**
     * 发送钉钉通知，失败不阻断审核主流程。
     * 注意：与 dispatchTask 不同 —— 派发通知失败必须回滚，审核通知失败不回滚。
     */
    private void sendNotifyQuietly(String title, String content, Collection<Long> receiverIds,
                                   String triggerEvent, Long taskId, String detailUrl) {
        if (receiverIds == null || receiverIds.isEmpty()) {
            return;
        }
        try {
            dingTalkNotifyService.sendNotifyDirect(title, content, new ArrayList<>(receiverIds),
                    triggerEvent, "task", taskId, detailUrl);
        } catch (Exception e) {
            // 通知失败不影响审核结果落库
        }
    }

    private PmsProjectDO requireProjectManager(PmsTaskDO task, Long operatorId) {
        PmsProjectDO project = projectMapper.selectById(task.getProjectId());
        if (project == null) {
            throw new ServiceException(ErrorCodeConstants.TASK_NOT_EXISTS);
        }
        // 超管(super_admin)可以审核所有任务/变更
        if (securityFrameworkService.hasAnyRoles("super_admin")) {
            return project;
        }
        // 项目经理可以审核
        if (Objects.equals(project.getProjectManagerId(), operatorId)) {
            return project;
        }
        // 任务审核人可以审核
        if (task.getReviewerId() != null && Objects.equals(task.getReviewerId(), operatorId)) {
            return project;
        }
        // 拥有项目级 task_review 权限者可以审核
        if (hasProjectPerm(task.getProjectId(), PmsPermKeyEnum.TASK_REVIEW.getKey())) {
            return project;
        }
        throw new ServiceException(ErrorCodeConstants.PROJECT_MANAGER_REQUIRED);
    }

    private PmsTaskDO requireTask(Long taskId) {
        PmsTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ServiceException(ErrorCodeConstants.TASK_NOT_EXISTS);
        }
        return task;
    }

    private void normalizeSchedule(PmsTaskDO task) {
        boolean hasStart = task.getPlanStartDate() != null;
        boolean hasEnd = task.getPlanEndDate() != null;
        if (hasStart != hasEnd || (hasStart && task.getPlanEndDate().isBefore(task.getPlanStartDate()))) {
            throw new ServiceException(ErrorCodeConstants.TASK_DATE_INVALID);
        }
        if (hasStart) {
            task.setCycle((int) ChronoUnit.DAYS.between(task.getPlanStartDate(), task.getPlanEndDate()) + 1);
        }
    }

}

