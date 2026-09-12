package cn.iocoder.yudao.module.pms.service.task.impl;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.dal.dataobject.project.PmsProjectDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.tasklog.PmsTaskLogDO;
import org.springframework.jdbc.core.JdbcTemplate;
import cn.iocoder.yudao.module.pms.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.task.TaskMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.tasklog.TaskLogMapper;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.pms.enums.PmsPermKeyEnum;
import cn.iocoder.yudao.module.pms.enums.PmsReviewPolicyEnum;
import cn.iocoder.yudao.module.pms.enums.PmsTaskReviewStatusEnum;
import cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskBoardVO;
import cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskBoardScopeVO;
import cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskExportExcel;
import cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskWeeklyReportVO;
import cn.iocoder.yudao.module.pms.dal.dataobject.projectstage.PmsProjectStageDO;
import cn.iocoder.yudao.module.pms.service.projectstage.ProjectStageService;
import cn.iocoder.yudao.module.pms.service.dingtalk.DingTalkNotifyService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.pms.service.projectpermission.ProjectPermissionService;
import cn.iocoder.yudao.module.pms.service.task.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.function.Consumer;
import java.util.HashMap;
import java.util.Collections;
import java.util.HashSet;
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
@lombok.extern.slf4j.Slf4j
public class TaskServiceImpl implements TaskService {

    /**
     * 前端基础 URL（用于拼接任务详情跳转地址，与钉钉通知卡片/待办配合实现"点击直达"）
     * 配置项：pms.notify.frontend-base-url，默认 https://pms.topsunpower.cc
     */
    @org.springframework.beans.factory.annotation.Value("${pms.notify.frontend-base-url:https://pms.topsunpower.cc}")
    private String frontendBaseUrl;

    /**
     * 子任务最大层级（1 顶层 + 2 级子任务）
     */
    private static final int MAX_TASK_LEVEL = 3;

    /**
     * 祖先链遍历保护上限，防止脏数据造成死循环
     */
    private static final int ANCESTOR_GUARD = 32;

    /**
     * 任务类型 → 中文标签（与前端 pms-utils taskTypeOptions 保持一致）
     */
    private static final Map<String, String> TASK_TYPE_LABEL = new HashMap<>();
    static {
        TASK_TYPE_LABEL.put("design", "设计任务");
        TASK_TYPE_LABEL.put("review", "评审任务");
        TASK_TYPE_LABEL.put("testing", "测试任务");
        TASK_TYPE_LABEL.put("procurement", "采购任务");
        TASK_TYPE_LABEL.put("prototyping", "试制任务");
        TASK_TYPE_LABEL.put("documentation", "文档任务");
        TASK_TYPE_LABEL.put("approval", "审批任务");
        TASK_TYPE_LABEL.put("supplier_synergy", "供应商协同");
        TASK_TYPE_LABEL.put("other", "其他");
        TASK_TYPE_LABEL.put("standard", "标准任务"); // 兼容历史数据占位值
    }

    /**
     * 优先级 → 中文标签（与前端 pms-utils priorityOptions 保持一致）
     */
    private static final Map<String, String> PRIORITY_LABEL = new HashMap<>();
    static {
        PRIORITY_LABEL.put("urgent", "紧急");
        PRIORITY_LABEL.put("high", "高");
        PRIORITY_LABEL.put("medium", "中");
        PRIORITY_LABEL.put("normal", "普通");
        PRIORITY_LABEL.put("low", "低");
    }

    /**
     * 完成状态 → 中文标签（与前端 pms-utils taskStatusMap 保持一致）
     */
    private static final Map<String, String> COMPLETE_STATUS_LABEL = new HashMap<>();
    static {
        COMPLETE_STATUS_LABEL.put("not_started", "未开始");
        COMPLETE_STATUS_LABEL.put("pending_accept", "待接收");
        COMPLETE_STATUS_LABEL.put("in_progress", "进行中");
        COMPLETE_STATUS_LABEL.put("pending_review", "待审核");
        COMPLETE_STATUS_LABEL.put("completion_pending_review", "待审核");
        COMPLETE_STATUS_LABEL.put("completed", "已完成");
        COMPLETE_STATUS_LABEL.put("delayed", "已延期");
        COMPLETE_STATUS_LABEL.put("rejected", "已退回");
        COMPLETE_STATUS_LABEL.put("paused", "已暂停");
        COMPLETE_STATUS_LABEL.put("cancelled", "已取消");
    }

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Resource
    private TaskMapper taskMapper;
    @Resource
    private ProjectMapper projectMapper;
    @org.springframework.beans.factory.annotation.Autowired
    private JdbcTemplate jdbcTemplate;

    @Resource
    private DingTalkNotifyService dingTalkNotifyService;

    @Resource
    private TaskLogMapper taskLogMapper;

    @Resource
    private SecurityFrameworkService securityFrameworkService;

    @Resource
    private AdminUserMapper adminUserMapper;

    /**
     * 部门 API（#日常任务）：解析责任人直属部门领导，作为日常任务的审核人
     */
    @Resource
    private DeptApi deptApi;

    /**
     * 用户 API（#日常任务）：读取责任人的部门，用于解析直属领导
     */
    @Resource
    private AdminUserApi adminUserApi;

    /**
     * 项目阶段 Service（#任务导出）：解析任务所属阶段名称
     */
    @Resource
    private ProjectStageService projectStageService;

    /** 项目阶段 Mapper（批量导入：直接插入新阶段，绕过 createProjectStage 的 PM 校验——导入端点已做 TASK_CREATE 权限校验） */
    @Resource
    private cn.iocoder.yudao.module.pms.dal.mysql.projectstage.ProjectStageMapper projectStageMapper;

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
        // 【日常任务】project_id 为 NULL 表示非项目任务：自动置为进行中，并解析直属领导为审核人
        if (entity.getProjectId() == null) {
            if (entity.getCompleteStatus() == null || entity.getCompleteStatus().isEmpty()
                    || "not_started".equals(entity.getCompleteStatus())) {
                entity.setCompleteStatus("in_progress");
            }
            if (entity.getReviewerId() == null && entity.getMainOwnerId() != null) {
                entity.setReviewerId(resolveDailyTaskReviewer(entity.getMainOwnerId()));
            }
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
        doDispatchTransition(task);
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
        String oldStatus = task.getCompleteStatus();
        if (task == null) {
            throw new ServiceException(ErrorCodeConstants.TASK_NOT_EXISTS);
        }

        PmsTaskDO update = new PmsTaskDO();
        update.setTaskId(taskId);
        update.setCompleteStatus("in_progress");
        taskMapper.updateById(update);

        // 记录任务日志
        writeTaskLog(taskId, "dingtalk_confirm", "钉钉确认模拟：任务状态变更为进行中");
        logStatusChange(taskId, oldStatus, "in_progress");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acceptTask(Long taskId) {
        PmsTaskDO task = requireTask(taskId);
        String oldStatus = task.getCompleteStatus();
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
        logStatusChange(taskId, oldStatus, "in_progress");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acceptTaskPublic(Long taskId) {
        PmsTaskDO task = requireTask(taskId);
        String oldStatus = task.getCompleteStatus();
        if (!"pending_accept".equals(task.getCompleteStatus())) {
            throw new ServiceException(ErrorCodeConstants.TASK_STATUS_INVALID);
        }
        // 公开接口：签名已验证合法性，跳过身份校验，直接执行状态变更
        PmsTaskDO update = new PmsTaskDO();
        update.setTaskId(taskId);
        update.setCompleteStatus("in_progress");
        taskMapper.updateById(update);
        writeTaskLog(taskId, "task_accept_public", "通过钉钉一键接收，状态变更为进行中");
        logStatusChange(taskId, oldStatus, "in_progress");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitCompletion(Long taskId, String actualCompleteDate, String completionNote) {
        PmsTaskDO task = requireTask(taskId);
        String oldStatus = task.getCompleteStatus();
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

        // 通知审核人（与 submitReview 保持一致的通知逻辑）
        PmsProjectDO project727 = projectMapper.selectById(task.getProjectId());
        String projectName727 = project727 == null || project727.getProjectName() == null ? "" : project727.getProjectName();
        Long reviewerId727 = task.getReviewerId();
        if (reviewerId727 != null) {
            String detailUrl727 = frontendBaseUrl + "/pms/project-detail/" + task.getProjectId() + "?taskId=" + taskId;
            sendNotifyQuietly("【PMS】任务待您审核",
                    "项目「" + projectName727 + "」任务「" + task.getTaskName() + "」已提交完成，请及时审核。",
                    List.of(reviewerId727), "task_review_submitted", taskId, detailUrl727);
        }

        // 【日常任务】提交完成时才触发领导审核通知（不在创建时通知）
        // 日常任务流程：创建 → 责任人执行 → 提交完成 → 领导审核
        if (task.getProjectId() == null) {
            String dailyDetailUrl = frontendBaseUrl + "/pms/my-task-board?taskId=" + taskId;
            Long dailyReviewerId = task.getReviewerId();
            if (dailyReviewerId != null) {
                String ownerName = "";
                if (task.getMainOwnerId() != null) {
                    AdminUserDO owner = adminUserMapper.selectById(task.getMainOwnerId());
                    if (owner != null && owner.getNickname() != null) {
                        ownerName = owner.getNickname();
                    }
                }
                sendNotifyQuietly("【PMS】日常任务待您审核",
                        "您的下属「" + ownerName + "」的日常任务「" + task.getTaskName() + "」已提交完成，请审批（通过/驳回）。",
                        List.of(dailyReviewerId), "task_review_submitted", taskId, dailyDetailUrl);
            }
        }

        writeTaskLog(taskId, "submit_completion", "提交完成，审核人[" + reviewerId727 + "]");
        logStatusChange(taskId, oldStatus, "completion_pending_review");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewCompletion(Long taskId, boolean approved, String reviewOpinion, Long operatorId) {
        PmsTaskDO task = requireTask(taskId);
        String oldStatus = task.getCompleteStatus();
        Integer oldProgress = task.getProgress();
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
        logStatusChange(taskId, oldStatus, task.getCompleteStatus());
        logProgressChange(taskId, oldProgress, task.getProgress());

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
        if (entity.getCompleteStatus() != null && !entity.getCompleteStatus().equals(old.getCompleteStatus())) {
            logStatusChange(entity.getTaskId(), old.getCompleteStatus(), entity.getCompleteStatus());
        }
        if (entity.getProgress() != null && !entity.getProgress().equals(old.getProgress())) {
            logProgressChange(entity.getTaskId(), old.getProgress(), entity.getProgress());
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
                // 模板项目不校验项目经理/主责任人/项目级权限，由菜单权限控制
                if (project == null || !"standard_template".equals(project.getProjectType())) {
                    boolean isPM = project != null && Objects.equals(project.getProjectManagerId(), userId);
                    boolean isOwner = Objects.equals(task.getMainOwnerId(), userId);
                    boolean hasPerm = hasProjectPerm(task.getProjectId(), PmsPermKeyEnum.TASK_DELETE.getKey());
                    if (!isPM && !isOwner && !hasPerm) {
                        throw new ServiceException(ErrorCodeConstants.PROJECT_MANAGER_REQUIRED);
                    }
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

            // 批2：非PM项目中自己负责/协助/待审核的任务（含日常任务 project_id IS NULL）
            LambdaQueryWrapperX<PmsTaskDO> wrapper2 = new LambdaQueryWrapperX<>();
            // 【修复】日常任务 project_id 为 NULL；MySQL 下 NULL NOT IN (...) 返回 UNKNOWN 会剔除该行，
            // 故显式 OR project_id IS NULL，保证 PM 自建/负责的日常任务在「任务管理」可见
            wrapper2.and(w -> w.isNull(PmsTaskDO::getProjectId).or().notIn(PmsTaskDO::getProjectId, pmProjectIds));
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
        String oldStatus = task.getCompleteStatus();
        Integer oldProgress = task.getProgress();
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
        logProgressChange(taskId, oldProgress, value);
        if ("not_started".equals(oldStatus) && value > 0) {
            logStatusChange(taskId, "not_started", "in_progress");
        }
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
        String oldStatus = task.getCompleteStatus();
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
            logStatusChange(taskId, oldStatus, "completed");
            String detailUrl689 = frontendBaseUrl + "/pms/project-detail/" + task.getProjectId() + "?taskId=" + taskId;
            sendNotifyQuietly("【PMS】任务已完成",
                    "项目「" + projectName + "」任务「" + task.getTaskName() + "」已按" + policyLabel(policy) + "策略直接完成。",
                    buildReviewReceivers(task, project), "task_review_auto_passed", taskId, detailUrl689);
            return;
        }

        // 策略为 need_review：必须有审核人
        Long reviewerId = task.getReviewerId();
        if (reviewerId == null) {
            // 日常任务（无项目）：审核人 = 责任人直属部门领导
            reviewerId = task.getProjectId() == null
                    ? resolveDailyTaskReviewer(task.getMainOwnerId())
                    : resolveDefaultReviewer(loadParent(task), task.getProjectId());
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
        logStatusChange(taskId, oldStatus, "completion_pending_review");
        String detailUrl714 = frontendBaseUrl + "/pms/project-detail/" + task.getProjectId() + "?taskId=" + taskId;
        sendNotifyQuietly("【PMS】任务待您审核",
                "项目「" + projectName + "」任务「" + task.getTaskName() + "」已提交完成，请及时审核。",
                List.of(reviewerId), "task_review_submitted", taskId, detailUrl714);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveReview(Long taskId, String reviewComment) {
        PmsTaskDO task = requireTask(taskId);
        String oldStatus = task.getCompleteStatus();
        Integer oldProgress = task.getProgress();
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
        logStatusChange(taskId, oldStatus, "completed");
        logProgressChange(taskId, oldProgress, 100);
        String projectName = project == null || project.getProjectName() == null ? "" : project.getProjectName();
        String detailUrl747 = frontendBaseUrl + "/pms/project-detail/" + task.getProjectId() + "?taskId=" + taskId;
        sendNotifyQuietly("【PMS】任务审核通过",
                "项目「" + projectName + "」任务「" + task.getTaskName() + "」审核已通过。"
                        + (reviewComment == null || reviewComment.isBlank() ? "" : "意见：" + reviewComment),
                buildReviewReceivers(task, project), "task_review_approved", taskId, detailUrl747);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveReviewPublic(Long taskId) {
        // 公开接口：签名已验证合法性，跳过身份校验与权限校验，直接执行状态流转
        PmsTaskDO task = requireTask(taskId);
        String oldStatus = task.getCompleteStatus();
        Integer oldProgress = task.getProgress();
        // 【日常任务】project_id 为 NULL 表示非项目任务：创建即进入「领导审批」流程，
        // 允许 in_progress+reviewStatus=none 直接走快速审批（无需先 submitReview）。
        boolean isDaily = task.getProjectId() == null;
        if (!isDaily && !PmsTaskReviewStatusEnum.canReview(task.getReviewStatus())) {
            throw new ServiceException(ErrorCodeConstants.TASK_REVIEW_STATUS_INVALID);
        }
        PmsProjectDO project = projectMapper.selectById(task.getProjectId());

        PmsTaskDO update = new PmsTaskDO();
        update.setTaskId(taskId);
        update.setReviewStatus(PmsTaskReviewStatusEnum.COMPLETED.getStatus());
        update.setCompleteStatus("completed");
        update.setProgress(100);
        update.setActualCompleteDate(task.getActualCompleteDate() == null ? LocalDate.now() : task.getActualCompleteDate());
        String reviewComment = isDaily ? "钉钉一键审批通过（日常任务）" : "钉钉一键通过";
        update.setReviewComment(reviewComment);
        update.setReviewOpinion(reviewComment);
        taskMapper.updateById(update);

        // #1：子任务通过后，父任务进度自动汇总
        refreshProgressUpward(task.getParentTaskId());

        writeTaskLog(taskId, "approve_review_public", "通过钉钉一键" + (isDaily ? "审批通过（日常任务）" : "通过") + "，状态变更为已完成");
        logStatusChange(taskId, oldStatus, "completed");
        logProgressChange(taskId, oldProgress, 100);
        String detailUrlPublic = isDaily
                ? frontendBaseUrl + "/pms/my-task-board"
                : frontendBaseUrl + "/pms/project-detail/" + task.getProjectId() + "?taskId=" + taskId;
        if (isDaily) {
            // 日常任务：仅通知责任人（直属领导已审批完成，无需重复通知）
            if (task.getMainOwnerId() != null) {
                sendNotifyQuietly("【PMS】您的日常任务已审批通过",
                        "您的日常任务「" + task.getTaskName() + "」已由您的直属领导审批通过。",
                        List.of(task.getMainOwnerId()), "task_review_approved", taskId, detailUrlPublic);
            }
        } else {
            String projectName = project == null || project.getProjectName() == null ? "" : project.getProjectName();
            sendNotifyQuietly("【PMS】任务审核通过",
                    "项目「" + projectName + "」任务「" + task.getTaskName() + "」审核已通过（钉钉一键）。",
                    buildReviewReceivers(task, project), "task_review_approved", taskId, detailUrlPublic);
        }
    }

    @Override
    public Long resolveReviewerOf(Long userId) {
        // 暴露给前端做预校验：返回 null 表示未抽取到直属领导
        try {
            return resolveDailyTaskReviewer(userId);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectReview(Long taskId, String reviewComment) {
        if (reviewComment == null || reviewComment.isBlank()) {
            throw new ServiceException(ErrorCodeConstants.TASK_REVIEW_COMMENT_REQUIRED);
        }
        PmsTaskDO task = requireTask(taskId);
        String oldStatus = task.getCompleteStatus();
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
        logStatusChange(taskId, oldStatus, "in_progress");
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

    // ==================================================================
    // 日常任务 / 我的任务看板
    // ==================================================================

    @Override
    public TaskBoardVO boardQuery(String userIds, LocalDate dateFrom, LocalDate dateTo,
                                  boolean includeSubordinates) {
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();

        // 1) 计算当前用户可查看的人员范围（安全兜底）
        BoardScope scope = resolveBoardScope(loginUserId);
        boolean isAdmin = scope.allowedUserIds == null;

        // 2) 解析前端传入的人员种子（逗号分隔）；未传则按角色默认范围
        Set<Long> owners = new LinkedHashSet<>();
        boolean queryAll = false;
        if (userIds == null || userIds.trim().isEmpty()) {
            if (isAdmin) {
                queryAll = true; // 管理员未选人 → 查看全部
            } else {
                owners.add(loginUserId);
            }
        } else {
            List<Long> seeds = parseUserIds(userIds);
            // 安全门禁：种子必须在允许范围内（管理员 allowedUserIds=null 表示全部放行）
            if (scope.allowedUserIds != null) {
                List<Long> filtered = new ArrayList<>();
                for (Long id : seeds) {
                    if (scope.allowedUserIds.contains(id)) filtered.add(id);
                }
                seeds = filtered;
                if (seeds.isEmpty()) seeds.add(loginUserId);
            }
            owners.addAll(seeds);
            // 3) 含下属开关：递归展开每个种子用户的下属
            if (includeSubordinates) {
                for (Long uid : seeds) {
                    expandSubordinates(uid, owners);
                }
            }
            // 4) 最终门禁：展开后的集合仍须落在允许范围内
            if (scope.allowedUserIds != null) {
                owners.retainAll(scope.allowedUserIds);
            }
        }
        if (!queryAll && owners.isEmpty()) {
            owners.add(loginUserId);
        }

        // 未完成状态集合（看板只关心未完结的任务）
        List<String> unfinished = List.of("not_started", "pending_accept", "in_progress",
                "completion_pending_review", "pending_review", "delayed", "rejected", "paused");

        // 【口径对齐】我的任务看板 = 项目管理-任务列表(项目任务) + 日常任务 的汇总；
        // 与任务列表页面一致，排除 standard_template 模板项目的任务（模板任务不进看板）
        List<Long> templateProjectIds = projectMapper.selectList(new LambdaQueryWrapperX<PmsProjectDO>()
                .select(PmsProjectDO::getProjectId)
                .eq(PmsProjectDO::getProjectType, "standard_template"))
                .stream().map(PmsProjectDO::getProjectId).filter(Objects::nonNull).collect(Collectors.toList());
        Set<Long> templateProjectIdSet = new HashSet<>(templateProjectIds);

        List<PmsTaskDO> legacy;
        List<PmsTaskDO> inRange;
        List<PmsTaskDO> dailyTasks;
        if (queryAll) {
            // 管理员查看全部：不加 main_owner_id 过滤
            legacy = taskMapper.selectList(new LambdaQueryWrapperX<PmsTaskDO>()
                    .lt(PmsTaskDO::getPlanStartDate, dateFrom)
                    .in(PmsTaskDO::getCompleteStatus, unfinished)
                    .orderByAsc(PmsTaskDO::getPlanStartDate));
            inRange = taskMapper.selectList(new LambdaQueryWrapperX<PmsTaskDO>()
                    .between(PmsTaskDO::getPlanStartDate, dateFrom, dateTo)
                    .in(PmsTaskDO::getCompleteStatus, unfinished)
                    .orderByAsc(PmsTaskDO::getPlanStartDate));
            // 【PMS】日常任务（project_id 为空）：放宽日期——计划日期为空或落在范围内，均纳入看板，
            // 避免「我的看板」因无计划日期而丢失日常任务（与任务看板语义对齐）。
            // 【F3-fix】日常任务看板展示全部状态（含已完成），方便回顾；不再限制未完成
            dailyTasks = taskMapper.selectList(new LambdaQueryWrapperX<PmsTaskDO>()
                    .isNull(PmsTaskDO::getProjectId)
                    .and(w -> w.isNull(PmsTaskDO::getPlanStartDate)
                            .or().between(PmsTaskDO::getPlanStartDate, dateFrom, dateTo))
                    .orderByAsc(PmsTaskDO::getPlanStartDate));
        } else {
            List<Long> ownerList = new ArrayList<>(owners);
            legacy = taskMapper.selectList(new LambdaQueryWrapperX<PmsTaskDO>()
                    .in(PmsTaskDO::getMainOwnerId, ownerList)
                    .lt(PmsTaskDO::getPlanStartDate, dateFrom)
                    .in(PmsTaskDO::getCompleteStatus, unfinished)
                    .orderByAsc(PmsTaskDO::getPlanStartDate));
            inRange = taskMapper.selectList(new LambdaQueryWrapperX<PmsTaskDO>()
                    .in(PmsTaskDO::getMainOwnerId, ownerList)
                    .between(PmsTaskDO::getPlanStartDate, dateFrom, dateTo)
                    .in(PmsTaskDO::getCompleteStatus, unfinished)
                    .orderByAsc(PmsTaskDO::getPlanStartDate));
            // 【F3-fix】日常任务看板展示全部状态（含已完成），方便回顾
            dailyTasks = taskMapper.selectList(new LambdaQueryWrapperX<PmsTaskDO>()
                    .isNull(PmsTaskDO::getProjectId)
                    .in(PmsTaskDO::getMainOwnerId, ownerList)
                    .and(w -> w.isNull(PmsTaskDO::getPlanStartDate)
                            .or().between(PmsTaskDO::getPlanStartDate, dateFrom, dateTo))
                    .orderByAsc(PmsTaskDO::getPlanStartDate));
        }

        // 排除模板项目的任务（legacy/inRange 查询口径不含模板；dailyTasks 均无项目不受影响）
        if (!templateProjectIdSet.isEmpty()) {
            legacy = legacy.stream()
                    .filter(t -> t.getProjectId() == null || !templateProjectIdSet.contains(t.getProjectId()))
                    .collect(Collectors.toList());
            inRange = inRange.stream()
                    .filter(t -> t.getProjectId() == null || !templateProjectIdSet.contains(t.getProjectId()))
                    .collect(Collectors.toList());
        }

        Map<Long, List<PmsTaskDO>> projectMap = new LinkedHashMap<>();
        for (PmsTaskDO t : inRange) {
            // 日常任务已由独立的 dailyTasks 查询处理，这里仅收口项目任务，避免重复计数
            if (t.getProjectId() != null) {
                projectMap.computeIfAbsent(t.getProjectId(), k -> new ArrayList<>()).add(t);
            }
        }

        // 组装项目分组（带项目名称；jdbc 直查含已逻辑删除项目，避免显示「未知项目」/「项目 ID」）
        List<TaskBoardVO.ProjectTaskGroup> projectGroups = new ArrayList<>();
        for (Map.Entry<Long, List<PmsTaskDO>> entry : projectMap.entrySet()) {
            TaskBoardVO.ProjectTaskGroup group = new TaskBoardVO.ProjectTaskGroup();
            group.setProjectId(entry.getKey());
            group.setProjectName(resolveProjectName(entry.getKey()));
            fillProjectName(entry.getValue());
            group.setTasks(entry.getValue());
            projectGroups.add(group);
        }

        // 历史遗留任务填充项目名（jdbc 直查，含已逻辑删除项目）
        fillProjectName(legacy);

        TaskBoardVO vo = new TaskBoardVO();
        vo.setLegacyTasks(legacy);
        vo.setProjectGroups(projectGroups);
        vo.setDailyTasks(dailyTasks);
        return vo;
    }

    @Override
    public TaskBoardScopeVO getBoardScope() {
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        BoardScope scope = resolveBoardScope(loginUserId);
        TaskBoardScopeVO vo = new TaskBoardScopeVO();
        vo.setIsAdmin(scope.isAdmin);
        vo.setIsLeader(scope.isLeader);
        vo.setLoginUserId(loginUserId);
        vo.setAllowedUserIds(scope.allowedUserIds);
        return vo;
    }

    /**
     * 看板人员范围判定（三级权限模型，安全兜底的唯一来源）：
     *   - 管理员（拥有 pms:board:admin 权限点）：allowedUserIds=null 表示全部
     *   - 领导（有下属）：allowedUserIds=[本人, 下属...]
     *   - 非领导：allowedUserIds=[本人]
     */
    private BoardScope resolveBoardScope(Long loginUserId) {
        BoardScope scope = new BoardScope();
        scope.isAdmin = securityFrameworkService.hasAnyPermissions("pms:board:admin");
        if (scope.isAdmin) {
            scope.allowedUserIds = null; // 全部
            scope.isLeader = false;
            return scope;
        }
        List<AdminUserRespDTO> subs = safeGetSubordinates(loginUserId);
        scope.isLeader = subs != null && !subs.isEmpty();
        Set<Long> allowed = new LinkedHashSet<>();
        allowed.add(loginUserId);
        if (subs != null) {
            for (AdminUserRespDTO sub : subs) {
                if (sub.getId() != null) allowed.add(sub.getId());
            }
        }
        scope.allowedUserIds = new ArrayList<>(allowed);
        return scope;
    }

    /**
     * 解析逗号分隔的人员ID字符串为 Long 列表（非法值跳过）
     */
    private List<Long> parseUserIds(String userIds) {
        List<Long> result = new ArrayList<>();
        if (userIds == null || userIds.trim().isEmpty()) return result;
        for (String s : userIds.split(",")) {
            s = s.trim();
            if (s.isEmpty()) continue;
            try {
                result.add(Long.parseLong(s));
            } catch (NumberFormatException ignored) {
                // 跳过非法ID
            }
        }
        return result;
    }

    /**
     * 递归展开某用户的下属，并入 owners 集合
     */
    private void expandSubordinates(Long uid, Set<Long> owners) {
        try {
            List<AdminUserRespDTO> subs = adminUserApi.getUserListBySubordinate(uid);
            if (subs != null) {
                for (AdminUserRespDTO sub : subs) {
                    if (sub.getId() != null) owners.add(sub.getId());
                }
            }
        } catch (Exception ignored) {
            // 下属解析失败不影响主流程
        }
    }

    /**
     * 安全获取某用户的下属（异常降级为 null）
     */
    private List<AdminUserRespDTO> safeGetSubordinates(Long uid) {
        try {
            return adminUserApi.getUserListBySubordinate(uid);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 看板范围判定中间结构
     */
    private static class BoardScope {
        boolean isAdmin;
        boolean isLeader;
        List<Long> allowedUserIds; // null = 全部
    }

    @Override
    public List<PmsTaskDO> getDeptReviewTaskList() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        String status = PmsTaskReviewStatusEnum.SUBMITTED.getStatus();
        // 直属领导（审核人）查看待审日常任务；超管可见全部日常待审任务
        Long reviewerId = securityFrameworkService.hasAnyRoles("super_admin") ? null : userId;
        return taskMapper.selectDeptReviewTasks(reviewerId, status);
    }
    // ==================================================================
    // 批量派发 + 批量导入任务（2026-09-12 新增）
    // ==================================================================

    /** 任务类型中文 → 字典值（批量导入用，含简称别名；未命中透传原值兼容历史数据） */
    private static final Map<String, String> IMPORT_TASK_TYPE_ALIAS;
    /** 优先级中文 → 字典值 */
    private static final Map<String, String> IMPORT_PRIORITY_ALIAS;

    static {
        Map<String, String> t = new HashMap<>();
        t.put("设计任务", "design"); t.put("设计", "design"); t.put("design", "design");
        t.put("评审任务", "review"); t.put("评审", "review"); t.put("review", "review");
        t.put("测试任务", "testing"); t.put("测试", "testing"); t.put("testing", "testing");
        t.put("采购任务", "procurement"); t.put("采购", "procurement"); t.put("procurement", "procurement");
        t.put("试制任务", "prototyping"); t.put("试制", "prototyping"); t.put("打样", "prototyping"); t.put("prototyping", "prototyping");
        t.put("文档任务", "documentation"); t.put("文档", "documentation"); t.put("documentation", "documentation");
        t.put("审批任务", "approval"); t.put("审批", "approval"); t.put("approval", "approval");
        t.put("供应商协同", "supplier_synergy"); t.put("供方协同", "supplier_synergy"); t.put("supplier_synergy", "supplier_synergy");
        t.put("其他", "other"); t.put("other", "other");
        IMPORT_TASK_TYPE_ALIAS = Collections.unmodifiableMap(t);
        Map<String, String> p = new HashMap<>();
        p.put("紧急", "urgent"); p.put("urgent", "urgent");
        p.put("高", "high"); p.put("high", "high");
        p.put("中", "medium"); p.put("medium", "medium");
        p.put("普通", "normal"); p.put("一般", "normal"); p.put("normal", "normal");
        p.put("低", "low"); p.put("low", "low");
        IMPORT_PRIORITY_ALIAS = Collections.unmodifiableMap(p);
    }

    @Override
    public cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskBatchDispatchRespVO batchDispatch(
            List<Long> taskIds, Long defaultOwnerId) {
        if (taskIds == null || taskIds.isEmpty()) {
            throw new ServiceException(ErrorCodeConstants.TASK_BATCH_EMPTY);
        }
        cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskBatchDispatchRespVO resp =
                new cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskBatchDispatchRespVO();
        List<cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskBatchDispatchRespVO.Item> items = new ArrayList<>();
        // 按负责人聚合待通知任务（同一负责人只发一条汇总通知 + 一条待办）
        Map<Long, List<PmsTaskDO>> notifyGroups = new LinkedHashMap<>();
        // taskId → 派发后任务（用于回填负责人姓名）
        Map<Long, PmsTaskDO> touched = new LinkedHashMap<>();

        for (Long taskId : taskIds) {
            cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskBatchDispatchRespVO.Item item =
                    new cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskBatchDispatchRespVO.Item();
            item.setTaskId(taskId);
            item.setNotified(false);
            try {
                PmsTaskDO task = taskMapper.selectById(taskId);
                if (task == null) {
                    throw new ServiceException(ErrorCodeConstants.TASK_NOT_EXISTS);
                }
                item.setTaskName(task.getTaskName());
                touched.put(taskId, task);
                String reason = validateDispatchable(task);
                if (reason == null && task.getMainOwnerId() == null) {
                    if (defaultOwnerId == null) {
                        reason = "未设置负责人，无法派发";
                    } else {
                        // 统一设置负责人：补到未设置的任务上（已设的不覆盖）
                        task.setMainOwnerId(defaultOwnerId);
                        taskMapper.updateById(task);
                        item.setOwnerPatched(true);
                    }
                }
                if (reason != null) {
                    item.setSuccess(false);
                    item.setReason(reason);
                    items.add(item);
                    continue;
                }
                doDispatchTransition(task);
                item.setSuccess(true);
                notifyGroups.computeIfAbsent(task.getMainOwnerId(), k -> new ArrayList<>()).add(task);
            } catch (Exception e) {
                item.setSuccess(false);
                item.setReason(e.getMessage() != null ? e.getMessage() : "派发失败");
            }
            items.add(item);
        }

        // 聚合通知：按负责人分组发一条汇总通知（含任务清单 + 计划周期）
        Map<Long, String> ownerNameMap = new HashMap<>();
        if (!notifyGroups.isEmpty()) {
            for (AdminUserDO u : adminUserMapper.selectBatchIds(new HashSet<>(notifyGroups.keySet()))) {
                ownerNameMap.put(u.getId(), u.getNickname());
            }
        }
        for (Map.Entry<Long, List<PmsTaskDO>> entry : notifyGroups.entrySet()) {
            List<PmsTaskDO> group = entry.getValue();
            PmsTaskDO first = group.get(0);
            PmsProjectDO project = projectMapper.selectById(first.getProjectId());
            String projectName = project != null && project.getProjectName() != null ? project.getProjectName() : "";
            StringBuilder sb = new StringBuilder();
            sb.append("项目「").append(projectName).append("」向您派发 ").append(group.size()).append(" 项任务：");
            int idx = 1;
            for (PmsTaskDO t : group) {
                sb.append("\n").append(idx++).append(". ").append(t.getTaskName());
                if (t.getPlanStartDate() != null || t.getPlanEndDate() != null) {
                    sb.append("（").append(t.getPlanStartDate() != null ? t.getPlanStartDate().toString() : "待定")
                            .append(" ~ ").append(t.getPlanEndDate() != null ? t.getPlanEndDate().toString() : "待定").append("）");
                }
            }
            sb.append("\n\n请及时查收并处理。");
            String detailUrl = frontendBaseUrl + "/pms/project-detail/" + first.getProjectId();
            boolean sent = dingTalkNotifyService.sendNotifyDirect("【PMS】任务批量派发通知", sb.toString(),
                    List.of(entry.getKey()), "task_dispatched", "task", first.getTaskId(), detailUrl);
            for (PmsTaskDO t : group) {
                for (cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskBatchDispatchRespVO.Item item : items) {
                    if (item.getTaskId() != null && item.getTaskId().equals(t.getTaskId())
                            && Boolean.TRUE.equals(item.getSuccess())) {
                        item.setNotified(sent);
                        if (!sent) {
                            item.setReason("派发成功，但钉钉通知发送失败");
                        }
                    }
                }
            }
        }
        // 回填负责人姓名
        for (cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskBatchDispatchRespVO.Item item : items) {
            PmsTaskDO t = touched.get(item.getTaskId());
            if (t != null && t.getMainOwnerId() != null) {
                item.setOwnerName(ownerNameMap.get(t.getMainOwnerId()));
            }
        }
        resp.setItems(items);
        resp.setSuccessCount((int) items.stream().filter(i -> Boolean.TRUE.equals(i.getSuccess())).count());
        resp.setFailureCount(items.size() - resp.getSuccessCount());
        log.info("[batchDispatch][共 {} 项，成功 {} 项，失败 {} 项]",
                items.size(), resp.getSuccessCount(), resp.getFailureCount());
        return resp;
    }

    /** 校验任务当前状态是否可派发；可派发返回 null，否则返回中文原因 */
    private String validateDispatchable(PmsTaskDO task) {
        String s = task.getCompleteStatus();
        if (!"not_started".equals(s) && !"rejected".equals(s)) {
            String label = COMPLETE_STATUS_LABEL.containsKey(s) ? COMPLETE_STATUS_LABEL.get(s) : s;
            return "当前状态为「" + label + "」，仅未开始/被拒绝的任务可派发";
        }
        return null;
    }

    /** 派发状态流转（不含通知）：供单条 dispatchTask 与批量 batchDispatch 复用 */
    private void doDispatchTransition(PmsTaskDO task) {
        String oldStatus = task.getCompleteStatus();
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
        writeTaskLog(task.getTaskId(), "dispatch", "派发任务给用户[" + task.getMainOwnerId() + "]");
        logStatusChange(task.getTaskId(), oldStatus, "pending_accept");
    }

    @Override
    public List<cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskImportExcel> getTaskImportTemplateRows(Long projectId) {
        List<cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskImportExcel> rows = new ArrayList<>();
        List<PmsProjectStageDO> stages = projectStageMapper.selectList(
                new LambdaQueryWrapperX<PmsProjectStageDO>().eq(PmsProjectStageDO::getProjectId, projectId));
        stages.sort(java.util.Comparator.comparing(s -> s.getSortOrder() == null ? Integer.MAX_VALUE : s.getSortOrder()));
        for (PmsProjectStageDO s : stages) {
            cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskImportExcel row =
                    new cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskImportExcel();
            row.setStageNo(s.getSortOrder());
            row.setStageName(s.getStageName());
            // 纯阶段参考行：任务列留空；导入时已有阶段直接跳过（仅作名称参考），新阶段按序号创建
            rows.add(row);
        }
        return rows;
    }

    /** 批量导入解析后的任务行（内部结构） */
    private static class ImportRow {
        cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskImportExcel row;
        int excelRowNo;
        String stageName;
        boolean stageExists;
        String stageKey;
        Long dbStageId;
        String taskName;
        boolean isSub;
        String parentName;
        Long dbParentId;
        ImportRow batchParent;
        String taskType;
        String priority;
        Long ownerId;
        String helperIds;
        LocalDate planStart;
        LocalDate planEnd;
        boolean excluded;
        Long createdTaskId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskImportRespVO importTask(
            Long projectId, List<cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskImportExcel> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new ServiceException(ErrorCodeConstants.TEMPLATE_IMPORT_FILE_EMPTY);
        }
        PmsProjectDO project = projectMapper.selectById(projectId);
        if (project == null || "standard_template".equals(project.getProjectType())) {
            throw new ServiceException(ErrorCodeConstants.TASK_IMPORT_PROJECT_INVALID);
        }

        // ---------- 已有数据索引 ----------
        List<PmsProjectStageDO> existingStages = projectStageMapper.selectList(
                new LambdaQueryWrapperX<PmsProjectStageDO>().eq(PmsProjectStageDO::getProjectId, projectId));
        // 序号 → 已有阶段（任务行按「阶段序号」关联阶段）
        Map<Integer, PmsProjectStageDO> stageByNo = new HashMap<>();
        // 序号冲突的已有阶段（同序号多阶段 → 该序号不可引用）
        Set<Integer> ambiguousStageNos = new HashSet<>();
        // 阶段名 → id（新阶段重名检查）
        Map<String, Long> stageIdByName = new HashMap<>();
        for (PmsProjectStageDO s : existingStages) {
            if (s.getStageName() != null) {
                stageIdByName.put(s.getStageName().trim(), s.getStageId());
            }
            if (s.getSortOrder() != null) {
                PmsProjectStageDO prev = stageByNo.put(s.getSortOrder(), s);
                if (prev != null && !prev.getStageId().equals(s.getStageId())) {
                    ambiguousStageNos.add(s.getSortOrder());
                }
            }
        }
        List<PmsTaskDO> existingTasks = taskMapper.selectList(
                new LambdaQueryWrapperX<PmsTaskDO>().eq(PmsTaskDO::getProjectId, projectId));
        // stageId → 阶段内全部任务名（同名查重）
        Map<Long, Set<String>> namesByStage = new HashMap<>();
        // stageId → 阶段内顶层任务名 → taskId（父任务解析）
        Map<Long, Map<String, Long>> topIdByStage = new HashMap<>();
        for (PmsTaskDO t : existingTasks) {
            if (t.getStageId() == null || t.getTaskName() == null) {
                continue;
            }
            String name = t.getTaskName().trim();
            namesByStage.computeIfAbsent(t.getStageId(), k -> new HashSet<>()).add(name);
            if (t.getParentTaskId() == null) {
                topIdByStage.computeIfAbsent(t.getStageId(), k -> new HashMap<>()).put(name, t.getTaskId());
            }
        }

        List<cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskImportErrorExcel> failureRows = new ArrayList<>();
        // 新阶段声明：序号 → 名称（阶段定义行 或 任务行内联声明）
        Map<Integer, String> declaredStages = new LinkedHashMap<>();
        List<ImportRow> parsed = new ArrayList<>();

        // ---------- 第一遍：阶段行（任务名称为空的行）----------
        int excelRowNo = 1;
        for (cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskImportExcel row : rows) {
            excelRowNo++;
            String taskName = row.getTaskName() == null ? "" : row.getTaskName().trim();
            if (!taskName.isEmpty()) {
                continue;
            }
            String stageName = row.getStageName() == null ? "" : row.getStageName().trim();
            Integer stageNo = row.getStageNo();
            if (stageName.isEmpty() && stageNo == null) {
                continue; // 完全空行
            }
            List<String> errors = new ArrayList<>();
            String prefix = "第" + excelRowNo + "行：";
            if (stageNo == null || stageNo < 1) {
                errors.add("阶段行需填写阶段序号（1、2、3…，新阶段将按此序号创建）");
            } else if (stageName.isEmpty()) {
                errors.add("新阶段需填写阶段名称");
            } else {
                PmsProjectStageDO exist = stageByNo.get(stageNo);
                if (exist != null) {
                    if (!exist.getStageName().trim().equals(stageName)) {
                        errors.add("阶段序号 " + stageNo + " 已对应阶段「" + exist.getStageName().trim()
                                + "」，与填写名称「" + stageName + "」不一致");
                    }
                    // 名称一致 → 模板预填参考行，跳过
                } else if (stageIdByName.containsKey(stageName)) {
                    errors.add("阶段「" + stageName + "」已存在，请改用该阶段的原有序号");
                } else {
                    String prev = declaredStages.get(stageNo);
                    if (prev == null) {
                        declaredStages.put(stageNo, stageName);
                    } else if (!prev.equals(stageName)) {
                        errors.add("阶段序号 " + stageNo + " 声明的名称前后不一致（" + prev + " / " + stageName + "）");
                    }
                }
            }
            if (!errors.isEmpty()) {
                failureRows.add(cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskImportErrorExcel.of(
                        row, prefix + String.join("；", errors)));
            }
        }

        // ---------- 第二遍：任务行校验 ----------
        excelRowNo = 1;
        for (cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskImportExcel row : rows) {
            excelRowNo++;
            String taskName = row.getTaskName() == null ? "" : row.getTaskName().trim();
            if (taskName.isEmpty()) {
                continue; // 阶段行已在第一遍处理
            }
            List<String> errors = new ArrayList<>();
            String prefix = "第" + excelRowNo + "行：";
            String stageName = row.getStageName() == null ? "" : row.getStageName().trim();
            Integer stageNo = row.getStageNo();

            // 阶段解析：序号必填 → 已有阶段 / 批次声明阶段 / 内联声明新阶段
            String stageKey = null;
            boolean stageExists = false;
            Long dbStageId = null;
            String resolvedStageName = null;
            if (stageNo == null || stageNo < 1) {
                errors.add("阶段序号必填（参照模板预填的序号，任务将挂到该阶段下）");
            } else if (ambiguousStageNos.contains(stageNo)) {
                errors.add("阶段序号 " + stageNo + " 在项目内对应多个阶段，无法引用");
            } else {
                PmsProjectStageDO exist = stageByNo.get(stageNo);
                if (exist != null) {
                    if (!stageName.isEmpty() && !exist.getStageName().trim().equals(stageName)) {
                        errors.add("阶段序号 " + stageNo + " 对应阶段「" + exist.getStageName().trim()
                                + "」，与填写名称「" + stageName + "」不一致");
                    }
                    stageKey = "S" + exist.getStageId();
                    stageExists = true;
                    dbStageId = exist.getStageId();
                    resolvedStageName = exist.getStageName().trim();
                } else {
                    String declared = declaredStages.get(stageNo);
                    if (declared == null && !stageName.isEmpty()) {
                        // 内联声明：任务行直接填新阶段序号+名称
                        if (stageIdByName.containsKey(stageName)) {
                            errors.add("阶段序号 " + stageNo + " 不存在，且名称「" + stageName
                                    + "」与已有阶段重名（请改用该阶段的原有序号）");
                        } else {
                            declaredStages.put(stageNo, stageName);
                            declared = stageName;
                        }
                    }
                    if (declared == null) {
                        errors.add("阶段序号 " + stageNo + " 不存在；若要新建阶段，请同时填写「阶段名称」");
                    } else {
                        if (!stageName.isEmpty() && !declared.equals(stageName)) {
                            errors.add("阶段序号 " + stageNo + " 已声明为阶段「" + declared
                                    + "」，与填写名称「" + stageName + "」不一致");
                        }
                        stageKey = "N" + stageNo;
                        resolvedStageName = declared;
                    }
                }
            }

            // 任务序号：选填（留空系统自动编号）；填写须为正整数、阶段内唯一（结构校验阶段检查唯一性）
            if (row.getTaskNo() != null && row.getTaskNo() < 1) {
                errors.add("任务序号须为正整数（留空则系统自动编号）");
            }
            String parentName = row.getParentTaskName() == null ? "" : row.getParentTaskName().trim();
            String taskType = "other";
            if (row.getTaskType() != null && !row.getTaskType().trim().isEmpty()) {
                String mapped = IMPORT_TASK_TYPE_ALIAS.get(row.getTaskType().trim());
                // 字典未识别：透传原值（兼容历史数据如 standard）
                taskType = mapped != null ? mapped : row.getTaskType().trim();
            }
            String priority = null;
            if (row.getPriority() != null && !row.getPriority().trim().isEmpty()) {
                priority = IMPORT_PRIORITY_ALIAS.get(row.getPriority().trim());
                if (priority == null) {
                    errors.add("优先级「" + row.getPriority().trim() + "」无法识别（可选：紧急/高/中/普通/低）");
                }
            }
            Long ownerId = null;
            if (row.getOwnerName() != null && !row.getOwnerName().trim().isEmpty()) {
                ownerId = resolveImportUser(row.getOwnerName().trim(), errors, "负责人");
            }
            String helperIds = null;
            if (row.getHelperNames() != null && !row.getHelperNames().trim().isEmpty()) {
                StringBuilder hs = new StringBuilder();
                for (String h : row.getHelperNames().split("[,，、]")) {
                    String ht = h.trim();
                    if (ht.isEmpty()) {
                        continue;
                    }
                    Long hid = resolveImportUser(ht, errors, "协助人「" + ht + "」");
                    if (hid != null) {
                        if (hs.length() > 0) {
                            hs.append(",");
                        }
                        hs.append(hid);
                    }
                }
                helperIds = hs.length() > 0 ? hs.toString() : null;
            }
            LocalDate planStart = parseImportDate(row.getPlanStartDate(), errors, "计划开始日期");
            LocalDate planEnd = parseImportDate(row.getPlanEndDate(), errors, "计划结束日期");
            if ((planStart != null) != (planEnd != null)) {
                errors.add("计划开始/结束日期须同时填写");
            }
            if (planStart != null && planEnd != null && planEnd.isBefore(planStart)) {
                errors.add("计划结束日期不能早于计划开始日期");
            }

            if (!errors.isEmpty()) {
                failureRows.add(cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskImportErrorExcel.of(
                        row, prefix + String.join("；", errors)));
                continue;
            }
            ImportRow pr = new ImportRow();
            pr.row = row;
            pr.excelRowNo = excelRowNo;
            pr.stageKey = stageKey;
            pr.stageName = resolvedStageName;
            pr.stageExists = stageExists;
            pr.dbStageId = dbStageId;
            pr.taskName = taskName;
            pr.parentName = parentName;
            pr.isSub = !parentName.isEmpty();
            pr.taskType = taskType;
            pr.priority = priority;
            pr.ownerId = ownerId;
            pr.helperIds = helperIds;
            pr.planStart = planStart;
            pr.planEnd = planEnd;
            parsed.add(pr);
        }

        // ---------- 结构性校验（stageKey 维度，父任务解析与行序无关） ----------
        // 批次内顶层任务：stageKey → 任务名 → 行
        Map<String, Map<String, ImportRow>> batchTop = new HashMap<>();
        for (ImportRow pr : parsed) {
            if (pr.isSub) {
                continue;
            }
            Map<String, ImportRow> tops = batchTop.computeIfAbsent(pr.stageKey, k -> new HashMap<>());
            if (tops.containsKey(pr.taskName)) {
                failureRows.add(cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskImportErrorExcel.of(
                        pr.row, "第" + pr.excelRowNo + "行：阶段「" + pr.stageName + "」内任务名称「" + pr.taskName + "」重复"));
                pr.excluded = true;
            } else {
                tops.put(pr.taskName, pr);
            }
        }
        // 批次内任务名唯一 + 显式任务序号阶段内唯一 + 与已有任务同名查重
        Map<String, Set<String>> batchNamesByStage = new HashMap<>();
        Map<String, Set<Integer>> batchNoByStage = new HashMap<>();
        for (ImportRow pr : parsed) {
            if (pr.excluded) {
                continue;
            }
            String prefix = "第" + pr.excelRowNo + "行：";
            if (!batchNamesByStage.computeIfAbsent(pr.stageKey, k -> new HashSet<>()).add(pr.taskName)) {
                failureRows.add(cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskImportErrorExcel.of(
                        pr.row, prefix + "阶段「" + pr.stageName + "」内任务名称「" + pr.taskName + "」重复"));
                pr.excluded = true;
                continue;
            }
            if (pr.row.getTaskNo() != null
                    && !batchNoByStage.computeIfAbsent(pr.stageKey, k -> new HashSet<>()).add(pr.row.getTaskNo())) {
                failureRows.add(cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskImportErrorExcel.of(
                        pr.row, prefix + "阶段「" + pr.stageName + "」内任务序号 " + pr.row.getTaskNo() + " 重复"));
                pr.excluded = true;
                continue;
            }
            if (pr.stageExists) {
                Set<String> names = namesByStage.get(pr.dbStageId);
                if (names != null && names.contains(pr.taskName)) {
                    failureRows.add(cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskImportErrorExcel.of(
                            pr.row, prefix + "阶段「" + pr.stageName + "」已存在同名任务「" + pr.taskName
                                    + "」（追加式导入不覆盖已有任务）"));
                    pr.excluded = true;
                }
            }
        }
        // 父任务解析（顺序无关）：父任务须为该阶段已有顶层任务或本批次顶层任务，最多两级
        Map<String, Map<String, ImportRow>> batchAll = new HashMap<>();
        for (ImportRow pr : parsed) {
            batchAll.computeIfAbsent(pr.stageKey, k -> new HashMap<>()).put(pr.taskName, pr);
        }
        for (ImportRow pr : parsed) {
            if (pr.excluded || !pr.isSub) {
                continue;
            }
            String prefix = "第" + pr.excelRowNo + "行：";
            if (pr.parentName.equals(pr.taskName)) {
                failureRows.add(cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskImportErrorExcel.of(
                        pr.row, prefix + "父任务名称不能与任务名称相同"));
                pr.excluded = true;
                continue;
            }
            Long dbParent = null;
            ImportRow batchParent = null;
            if (pr.stageExists) {
                Map<String, Long> tops = topIdByStage.get(pr.dbStageId);
                dbParent = tops != null ? tops.get(pr.parentName) : null;
            }
            if (dbParent == null) {
                Map<String, ImportRow> all = batchAll.get(pr.stageKey);
                ImportRow cand = all != null ? all.get(pr.parentName) : null;
                if (cand != null && cand.excluded) {
                    cand = null;
                }
                if (cand != null && cand.isSub) {
                    failureRows.add(cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskImportErrorExcel.of(
                            pr.row, prefix + "父任务「" + pr.parentName + "」本身是子任务，最多支持两级"));
                    pr.excluded = true;
                    continue;
                }
                batchParent = cand;
            }
            if (dbParent == null && batchParent == null) {
                failureRows.add(cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskImportErrorExcel.of(
                        pr.row, prefix + "父任务「" + pr.parentName + "」不存在于阶段「" + pr.stageName
                                + "」（需为该阶段已有顶层任务或本批次顶层任务）"));
                pr.excluded = true;
                continue;
            }
            pr.dbParentId = dbParent;
            pr.batchParent = batchParent;
        }

        // 任一行失败 → 整批不落库
        if (!failureRows.isEmpty()) {
            log.warn("[importTask][projectId={} 共 {} 行校验失败，整批不落库]", projectId, failureRows.size());
            return cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskImportRespVO.builder()
                    .success(false)
                    .stageCount(0)
                    .taskCount(0)
                    .failureRows(failureRows)
                    .build();
        }

        // ---------- 落库（同一事务） ----------
        int stageCount = 0;
        Map<String, Long> finalStageId = new HashMap<>();
        for (PmsProjectStageDO s : stageByNo.values()) {
            finalStageId.put("S" + s.getStageId(), s.getStageId());
        }
        List<Map.Entry<Integer, String>> newStages = new ArrayList<>(declaredStages.entrySet());
        newStages.sort(Map.Entry.comparingByKey());
        for (Map.Entry<Integer, String> e : newStages) {
            PmsProjectStageDO s = new PmsProjectStageDO();
            s.setProjectId(projectId);
            s.setStageName(e.getValue());
            s.setSortOrder(e.getKey());
            s.setStatus("not_started");
            projectStageMapper.insert(s);
            finalStageId.put("N" + e.getKey(), s.getStageId());
            stageCount++;
        }

        // 任务序号自动编号：留空 → 阶段内按文件顺序顺延（已有任务之后，避开批次显式序号）
        Map<String, Integer> autoNext = new HashMap<>();
        Map<String, Set<Integer>> autoUsed = new HashMap<>();
        for (ImportRow pr : parsed) {
            if (!autoUsed.containsKey(pr.stageKey)) {
                Set<Integer> u = new HashSet<>();
                int start = 1;
                if (pr.stageExists) {
                    for (PmsTaskDO t : existingTasks) {
                        if (t.getStageId() != null && t.getStageId().equals(pr.dbStageId)) {
                            if (t.getSortOrder() != null) {
                                u.add(t.getSortOrder());
                                if (t.getSortOrder() >= start) {
                                    start = t.getSortOrder() + 1;
                                }
                            }
                        }
                    }
                }
                autoUsed.put(pr.stageKey, u);
                autoNext.put(pr.stageKey, start);
            }
        }
        for (ImportRow pr : parsed) {
            if (pr.row.getTaskNo() != null) {
                autoUsed.get(pr.stageKey).add(pr.row.getTaskNo());
            }
        }
        for (ImportRow pr : parsed) {
            if (pr.row.getTaskNo() != null) {
                continue;
            }
            int next = autoNext.get(pr.stageKey);
            while (autoUsed.get(pr.stageKey).contains(next)) {
                next++;
            }
            pr.row.setTaskNo(next);
            autoUsed.get(pr.stageKey).add(next);
            autoNext.put(pr.stageKey, next + 1);
        }

        int taskCount = 0;
        // 顶层任务先建（父任务先于子任务落库；createTask 自动算 level/审核人兜底/进度汇总）
        for (ImportRow pr : parsed) {
            if (pr.excluded || pr.isSub) {
                continue;
            }
            PmsTaskDO entity = buildImportTask(projectId, finalStageId.get(pr.stageKey), pr);
            createTask(entity);
            pr.createdTaskId = entity.getTaskId();
            taskCount++;
        }
        // 子任务后建（父任务可能是本批次新建的顶层任务）
        for (ImportRow pr : parsed) {
            if (pr.excluded || !pr.isSub) {
                continue;
            }
            Long parentId = pr.dbParentId != null ? pr.dbParentId
                    : (pr.batchParent != null ? pr.batchParent.createdTaskId : null);
            if (parentId == null) {
                // 理论不可达（校验阶段已保证），兜底抛错回滚
                throw new ServiceException(ErrorCodeConstants.TASK_PARENT_NOT_EXISTS);
            }
            PmsTaskDO entity = buildImportTask(projectId, finalStageId.get(pr.stageKey), pr);
            entity.setParentTaskId(parentId);
            createTask(entity);
            pr.createdTaskId = entity.getTaskId();
            taskCount++;
        }
        log.info("[importTask][projectId={} 追加导入完成：新阶段 {} 个 / 新任务 {} 条]",
                projectId, stageCount, taskCount);
        return cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskImportRespVO.builder()
                .success(true)
                .stageCount(stageCount)
                .taskCount(taskCount)
                .build();
    }

    /** 组装导入任务的 DO（completeStatus 固定为未开始） */
    private PmsTaskDO buildImportTask(Long projectId, Long stageId, ImportRow pr) {
        PmsTaskDO entity = new PmsTaskDO();
        entity.setProjectId(projectId);
        entity.setStageId(stageId);
        entity.setTaskName(pr.taskName);
        entity.setTaskType(pr.taskType);
        if (pr.priority != null) {
            entity.setPriority(pr.priority);
        }
        entity.setMainOwnerId(pr.ownerId);
        entity.setHelperIds(pr.helperIds);
        entity.setPlanStartDate(pr.planStart);
        entity.setPlanEndDate(pr.planEnd);
        entity.setCompleteStatus("not_started");
        entity.setSortOrder(pr.row.getTaskNo());
        if (pr.row.getRemark() != null && !pr.row.getRemark().trim().isEmpty()) {
            entity.setDescription(pr.row.getRemark().trim());
        }
        return entity;
    }

    /** 解析导入的用户（工号优先，其次姓名精确匹配）；命中唯一返回ID，否则写入错误信息返回 null */
    private Long resolveImportUser(String key, List<String> errors, String label) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM system_users WHERE deleted = 0 AND status = 0 AND tenant_id = 1 "
                        + "AND (employee_no = ? OR nickname = ?)",
                Long.class, key, key);
        if (ids.isEmpty()) {
            errors.add(label + "「" + key + "」未找到（需工号或姓名精确匹配）");
            return null;
        }
        if (ids.size() > 1) {
            errors.add(label + "「" + key + "」匹配到多个用户，请改用工号");
            return null;
        }
        return ids.get(0);
    }

    /** 解析导入日期（yyyy-MM-dd）；空返回 null，格式非法写入错误信息 */
    private LocalDate parseImportDate(String value, List<String> errors, String label) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), DATE_FMT);
        } catch (Exception e) {
            errors.add(label + "格式须为 yyyy-MM-dd（当前值「" + value.trim() + "」）");
            return null;
        }
    }

    // ==================================================================
    // 周报看板
    // ==================================================================

    @Override
    public TaskWeeklyReportVO getWeeklyReport(Long userId, LocalDate date) {
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        BoardScope scope = resolveBoardScope(loginUserId);
        List<Long> owners = resolveReportOwners(userId, scope);
        boolean isAll = (owners == null); // null 表示管理员查看全部

        // 计算自然周（周一~周日）
        LocalDate d = (date == null) ? LocalDate.now() : date;
        LocalDate weekStart = d.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);
        LocalDate lastWeekStart = weekStart.minusDays(7);
        LocalDate lastWeekEnd = weekStart.minusDays(1);
        LocalDate today = LocalDate.now();

        TaskWeeklyReportVO vo = new TaskWeeklyReportVO();
        vo.setWeekStart(weekStart);
        vo.setWeekEnd(weekEnd);
        vo.setLastWeekStart(lastWeekStart);
        vo.setLastWeekEnd(lastWeekEnd);
        vo.setTargetUserId(userId != null ? userId : loginUserId);
        vo.setIsAdmin(scope.isAdmin);
        vo.setIsLeader(scope.isLeader);

        // A 上周完成
        List<PmsTaskDO> completed = queryOwned(owners, isAll, w -> w
                .eq(PmsTaskDO::getCompleteStatus, "completed")
                .between(PmsTaskDO::getActualCompleteDate, lastWeekStart, lastWeekEnd));
        vo.setLastWeekCompleted(completed);

        // B 本周计划（未完成 + 计划窗口与本周重叠，且必须有计划开始日期）
        List<String> notCompleted = List.of("not_started", "pending_accept", "in_progress",
                "completion_pending_review", "pending_review", "delayed", "rejected", "paused");
        List<PmsTaskDO> plan = queryOwned(owners, isAll, w -> w
                .in(PmsTaskDO::getCompleteStatus, notCompleted)
                .isNotNull(PmsTaskDO::getPlanStartDate)
                .le(PmsTaskDO::getPlanStartDate, weekEnd)
                .and(ww -> ww.isNull(PmsTaskDO::getPlanEndDate).or().ge(PmsTaskDO::getPlanEndDate, weekStart)));
        vo.setThisWeekPlan(plan);

        // E 未来计划（未完成 + 计划开始日期在本周之后，按计划开始日期升序）
        List<PmsTaskDO> future = queryOwned(owners, isAll, w -> w
                .in(PmsTaskDO::getCompleteStatus, notCompleted)
                .isNotNull(PmsTaskDO::getPlanStartDate)
                .gt(PmsTaskDO::getPlanStartDate, weekEnd)
                .orderByAsc(PmsTaskDO::getPlanStartDate));
        vo.setFuturePlans(future);

        // C 上周延期（已启动/流转过但未完成且逾期到上周末）
        List<String> started = List.of("pending_accept", "in_progress", "completion_pending_review",
                "pending_review", "delayed", "paused", "rejected");
        List<PmsTaskDO> delayed = queryOwned(owners, isAll, w -> w
                .in(PmsTaskDO::getCompleteStatus, started)
                .le(PmsTaskDO::getPlanEndDate, lastWeekEnd));
        List<TaskWeeklyReportVO.DelayedTaskVO> delayedVO = new ArrayList<>();
        for (PmsTaskDO t : delayed) {
            TaskWeeklyReportVO.DelayedTaskVO dv = new TaskWeeklyReportVO.DelayedTaskVO();
            dv.setTask(t);
            long od = t.getPlanEndDate() == null ? 0 : ChronoUnit.DAYS.between(t.getPlanEndDate(), today);
            dv.setOverdueDays(od);
            delayedVO.add(dv);
        }
        vo.setLastWeekDelayed(delayedVO);

        // D 上周动态（方案2：状态/进度变更日志，精确前后值）
        List<TaskWeeklyReportVO.TaskChangeLogVO> changes = buildChangeLogs(owners, isAll, lastWeekStart, lastWeekEnd);
        vo.setLastWeekChanges(changes);

        // 注入项目名称（周报看板任务卡展示；日常任务 projectId 为空自动跳过）
        fillProjectName(completed);
        fillProjectName(plan);
        fillProjectName(future);
        fillProjectName(delayed);

        return vo;
    }

    /** 单项目名解析（jdbc 直查含已逻辑删除项目；查不到回落「未知项目」） */
    private String resolveProjectName(Long projectId) {
        if (projectId == null) {
            return "未知项目";
        }
        if (jdbcTemplate != null) {
            try {
                List<String> names = jdbcTemplate.queryForList(
                        "SELECT project_name FROM pms_project WHERE project_id = ?", String.class, projectId);
                if (!names.isEmpty() && names.get(0) != null) {
                    return names.get(0);
                }
            } catch (Exception ignore) {
                // 失败走 mapper 兜底（仅未删项目）
            }
        }
        PmsProjectDO p = projectMapper.selectById(projectId);
        return p != null && p.getProjectName() != null ? p.getProjectName() : "未知项目";
    }

    /** 批量填充任务所属项目名称（@TableField(exist=false) 展示字段） */
    private void fillProjectName(List<PmsTaskDO> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        Set<Long> projectIds = tasks.stream().map(PmsTaskDO::getProjectId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (projectIds.isEmpty()) {
            return;
        }
        // 用 jdbcTemplate 直接查，绕开 mybatis 拦截器链（含 @TableLogic/@Tenant），返回包含已逻辑删除的项目名
        final Map<Long, String> nameMap = new java.util.HashMap<>();
        if (jdbcTemplate != null) {
            // 拼 IN 子句（jdbcTemplate 不会自动展开 Collection）
            StringBuilder sb = new StringBuilder("SELECT project_id, project_name FROM pms_project WHERE project_id IN (");
            List<Long> idList = new java.util.ArrayList<>(projectIds);
            for (int i = 0; i < idList.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("?");
            }
            sb.append(")");
            try {
                jdbcTemplate.query(sb.toString(), rs -> {
                    try {
                        long pid = rs.getLong("project_id");
                        String pname = rs.getString("project_name");
                        if (pname != null) nameMap.put(pid, pname);
                    } catch (Exception ignore) {}
                }, idList.toArray());
            } catch (Exception ignore) {
                // 极端兜底：jdbc 失败时退回原 mapper selectBatchIds（仅未删项目）
                projectMapper.selectBatchIds(projectIds).forEach(p -> {
                    if (p.getProjectName() != null) nameMap.put(p.getProjectId(), p.getProjectName());
                });
            }
        } else {
            // 没有 JdbcTemplate 注入（理论不会）走原 mapper
            projectMapper.selectBatchIds(projectIds).forEach(p -> {
                if (p.getProjectName() != null) nameMap.put(p.getProjectId(), p.getProjectName());
            });
        }
        tasks.forEach(t -> {
            if (t.getProjectId() != null) {
                t.setProjectName(nameMap.get(t.getProjectId()));
            }
        });
    }

    private List<Long> resolveReportOwners(Long userId, BoardScope scope) {
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        if (userId != null && userId == 0L) {
            return scope.isAdmin ? null : List.of(loginUserId);
        }
        if (userId == null) {
            return List.of(loginUserId);
        }
        if (scope.allowedUserIds != null && !scope.allowedUserIds.contains(userId)) {
            return List.of(loginUserId);
        }
        return List.of(userId);
    }

    private List<PmsTaskDO> queryOwned(List<Long> owners, boolean isAll,
                                       Consumer<LambdaQueryWrapperX<PmsTaskDO>> extra) {
        LambdaQueryWrapperX<PmsTaskDO> w = new LambdaQueryWrapperX<>();
        if (!isAll && owners != null) {
            w.in(PmsTaskDO::getMainOwnerId, owners);
        }
        extra.accept(w);
        w.orderByDesc(PmsTaskDO::getUpdateTime);
        return taskMapper.selectList(w);
    }

    private List<TaskWeeklyReportVO.TaskChangeLogVO> buildChangeLogs(List<Long> owners, boolean isAll,
                                                                     LocalDate lastWeekStart, LocalDate lastWeekEnd) {
        LambdaQueryWrapperX<PmsTaskDO> tw = new LambdaQueryWrapperX<>();
        tw.select(PmsTaskDO::getTaskId, PmsTaskDO::getTaskName, PmsTaskDO::getProjectId,
                PmsTaskDO::getMainOwnerId, PmsTaskDO::getPlanEndDate);
        if (!isAll && owners != null) {
            tw.in(PmsTaskDO::getMainOwnerId, owners);
        }
        List<PmsTaskDO> ownedTasks = taskMapper.selectList(tw);
        if (ownedTasks.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> taskIds = ownedTasks.stream().map(PmsTaskDO::getTaskId)
                .collect(Collectors.toList());
        Map<Long, PmsTaskDO> taskMap = ownedTasks.stream()
                .collect(Collectors.toMap(PmsTaskDO::getTaskId, t -> t, (a, b) -> a));

        List<PmsTaskLogDO> logs = taskLogMapper.selectList(new LambdaQueryWrapperX<PmsTaskLogDO>()
                .in(PmsTaskLogDO::getTaskId, taskIds)
                .in(PmsTaskLogDO::getOperationType, List.of("status_change", "progress_update"))
                .between(PmsTaskLogDO::getOperationTime, lastWeekStart.atStartOfDay(), lastWeekEnd.atTime(23, 59, 59))
                .orderByAsc(PmsTaskLogDO::getOperationTime));

        Map<Long, List<PmsTaskLogDO>> byTask = new LinkedHashMap<>();
        for (PmsTaskLogDO lg : logs) {
            byTask.computeIfAbsent(lg.getTaskId(), k -> new ArrayList<>()).add(lg);
        }
        List<TaskWeeklyReportVO.TaskChangeLogVO> result = new ArrayList<>();
        for (Map.Entry<Long, List<PmsTaskLogDO>> entry : byTask.entrySet()) {
            PmsTaskDO t = taskMap.get(entry.getKey());
            if (t == null) continue;
            TaskWeeklyReportVO.TaskChangeLogVO cv = new TaskWeeklyReportVO.TaskChangeLogVO();
            cv.setTaskId(t.getTaskId());
            cv.setTaskName(t.getTaskName());
            if (t.getProjectId() != null) {
                cv.setProjectName(resolveProjectName(t.getProjectId()));
            }
            List<TaskWeeklyReportVO.ChangeItemVO> items = new ArrayList<>();
            for (PmsTaskLogDO lg : entry.getValue()) {
                TaskWeeklyReportVO.ChangeItemVO it = new TaskWeeklyReportVO.ChangeItemVO();
                it.setOperationType(lg.getOperationType());
                it.setBeforeValue(lg.getBeforeValue());
                it.setAfterValue(lg.getAfterValue());
                it.setOperationTime(lg.getOperationTime());
                it.setOperatorName(lg.getOperatorName());
                items.add(it);
            }
            cv.setChanges(items);
            result.add(cv);
        }
        return result;
    }


    /**
     * 解析日常任务（无项目）的审核人：责任人的直属部门领导。
     * 若责任人无部门或部门无领导，则回退为责任人本人（自检）。
     */
    private Long resolveDailyTaskReviewer(Long mainOwnerId) {
        if (mainOwnerId == null) return null;
        try {
            AdminUserRespDTO user = adminUserApi.getUser(mainOwnerId);
            if (user != null && user.getDeptId() != null) {
                DeptRespDTO dept = deptApi.getDept(user.getDeptId());
                if (dept != null && dept.getLeaderUserId() != null) {
                    return dept.getLeaderUserId();
                }
            }
        } catch (Exception e) {
            // 降级：解析失败不影响创建，回退为责任人本人
        }
        return mainOwnerId;
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

    // ===== 周报看板：变更日志埋点（方案2 精确前后值）=====
    private void writeTaskChange(Long taskId, String operationType, String beforeValue, String afterValue) {
        try {
            PmsTaskLogDO log = new PmsTaskLogDO();
            log.setTaskId(taskId);
            log.setOperationType(operationType);
            log.setOperatorId(SecurityFrameworkUtils.getLoginUserId());
            log.setOperationTime(LocalDateTime.now());
            String content;
            if ("status_change".equals(operationType)) {
                content = "状态: " + (beforeValue == null ? "无" : beforeValue) + " → " + (afterValue == null ? "无" : afterValue);
            } else {
                content = "进度: " + (beforeValue == null ? "0" : beforeValue) + "% → " + (afterValue == null ? "0" : afterValue) + "%";
            }
            log.setOperationContent(content);
            log.setBeforeValue(beforeValue);
            log.setAfterValue(afterValue);
            taskLogMapper.insert(log);
        } catch (Exception e) {
            // 日志失败不影响主流程
        }
    }

    private void logStatusChange(Long taskId, String oldStatus, String newStatus) {
        if (oldStatus == null ? newStatus == null : oldStatus.equals(newStatus)) {
            return;
        }
        writeTaskChange(taskId, "status_change", oldStatus, newStatus);
    }

    private void logProgressChange(Long taskId, Integer oldProgress, Integer newProgress) {
        if (Objects.equals(oldProgress, newProgress)) {
            return;
        }
        writeTaskChange(taskId, "progress_update",
                oldProgress == null ? null : oldProgress.toString(),
                newProgress == null ? null : newProgress.toString());
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

    // ==================== 任务导出（新增） ====================

    @Override
    public List<TaskExportExcel> exportTaskByProject(Long projectId) {
        // 导出该项目全部任务（忽略页面筛选），与 getTaskTreeByProject 行为一致
        List<PmsTaskDO> tasks = getTaskTreeByProject(projectId);
        if (tasks == null || tasks.isEmpty()) {
            return new ArrayList<>();
        }

        // 收集需要解析的用户 / 部门 / 阶段
        Set<Long> userIds = new HashSet<>();
        Set<Long> deptIds = new HashSet<>();
        for (PmsTaskDO t : tasks) {
            if (t.getMainOwnerId() != null) {
                userIds.add(t.getMainOwnerId());
            }
            if (t.getDeptId() != null) {
                deptIds.add(t.getDeptId());
            }
            if (StrUtil.isNotBlank(t.getHelperIds())) {
                for (String s : t.getHelperIds().split(",")) {
                    String id = s.trim();
                    if (id.isEmpty()) {
                        continue;
                    }
                    try {
                        userIds.add(Long.parseLong(id));
                    } catch (NumberFormatException ignored) {
                        // 忽略非法 ID
                    }
                }
            }
        }

        Map<Long, AdminUserRespDTO> userMap = userIds.isEmpty()
                ? Collections.emptyMap() : adminUserApi.getUserMap(userIds);
        Map<Long, DeptRespDTO> deptMap = deptIds.isEmpty()
                ? Collections.emptyMap() : deptApi.getDeptMap(deptIds);

        // 阶段名称映射（按项目过滤）
        Map<Long, String> stageNameMap = new HashMap<>();
        for (PmsProjectStageDO stage : projectStageService.getProjectStageList()) {
            if (projectId.equals(stage.getProjectId())) {
                stageNameMap.put(stage.getStageId(), stage.getStageName());
            }
        }

        List<TaskExportExcel> rows = new ArrayList<>(tasks.size());
        for (PmsTaskDO t : tasks) {
            rows.add(TaskExportExcel.builder()
                    .taskCode(t.getTaskCode())
                    .taskName(t.getTaskName())
                    .stageName(safe(stageNameMap.get(t.getStageId())))
                    .mainOwnerName(nicknameOf(userMap, t.getMainOwnerId()))
                    .helperNames(helperNames(userMap, t.getHelperIds()))
                    .deptName(safe(deptNameOf(deptMap, t.getDeptId())))
                    .taskTypeLabel(labelOf(TASK_TYPE_LABEL, t.getTaskType()))
                    .priorityLabel(labelOf(PRIORITY_LABEL, t.getPriority()))
                    .levelLabel((t.getLevel() == null ? 1 : t.getLevel()) + "级")
                    .completeStatusLabel(labelOf(COMPLETE_STATUS_LABEL, t.getCompleteStatus()))
                    .reviewStatusLabel(PmsTaskReviewStatusEnum.labelOf(t.getReviewStatus()))
                    .progress(t.getProgress())
                    .planStartDate(formatDate(t.getPlanStartDate()))
                    .planEndDate(formatDate(t.getPlanEndDate()))
                    .actualCompleteDate(formatDate(t.getActualCompleteDate()))
                    .isMilestoneLabel(boolLabel(t.getIsMilestone()))
                    .isCriticalPathLabel(boolLabel(t.getIsCriticalPath()))
                    .build());
        }
        return rows;
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }

    private static String nicknameOf(Map<Long, AdminUserRespDTO> userMap, Long userId) {
        if (userId == null) {
            return "";
        }
        AdminUserRespDTO u = userMap.get(userId);
        return u != null && u.getNickname() != null ? u.getNickname() : "";
    }

    private static String deptNameOf(Map<Long, DeptRespDTO> deptMap, Long deptId) {
        if (deptId == null) {
            return null;
        }
        DeptRespDTO d = deptMap.get(deptId);
        return d != null ? d.getName() : null;
    }

    private static String helperNames(Map<Long, AdminUserRespDTO> userMap, String helperIds) {
        if (StrUtil.isBlank(helperIds)) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (String s : helperIds.split(",")) {
            String id = s.trim();
            if (id.isEmpty()) {
                continue;
            }
            try {
                AdminUserRespDTO u = userMap.get(Long.parseLong(id));
                if (u != null && u.getNickname() != null) {
                    names.add(u.getNickname());
                }
            } catch (NumberFormatException ignored) {
                // 忽略非法 ID
            }
        }
        return String.join("、", names);
    }

    private static String labelOf(Map<String, String> map, String value) {
        if (value == null) {
            return "";
        }
        String label = map.get(value);
        return label != null ? label : value;
    }

    private static String formatDate(LocalDate date) {
        return date != null ? DATE_FMT.format(date) : "";
    }

    private static String boolLabel(Boolean b) {
        return Boolean.TRUE.equals(b) ? "是" : "否";
    }

}

