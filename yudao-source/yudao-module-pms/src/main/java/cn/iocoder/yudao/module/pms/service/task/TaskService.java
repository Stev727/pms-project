package cn.iocoder.yudao.module.pms.service.task;

import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskBoardVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 任务 Service 接口
 *
 * ============================ 改造说明 ============================
 * 版本：v2（在线上原文件基础上改造，原有 10 个方法签名一个未改，仅追加新方法）
 * 改造内容：
 *   【#1 子任务层级】
 *     + getSubTaskList(parentTaskId)      查直接子任务
 *     + getTaskTreeByProject(projectId)   查项目任务（含层级字段，前端自行组树）
 *     + updateTaskProgress(taskId, progress) 进度填报并自底向上汇总父任务进度
 *   【#3 任务派发审核】
 *     + submitReview(taskId)                       提交审核
 *     + approveReview(taskId, reviewComment)       审核通过
 *     + rejectReview(taskId, reviewComment)        审核驳回（原因必填）
 *     + getMyReviewTaskList(projectId, reviewStatus) 待我审核的任务
 * ==================================================================
 */
public interface TaskService {

    // ==================== 既有方法（签名保持不变） ====================

    Long createTask(PmsTaskDO entity);

    void simulateDingtalkConfirm(Long taskId);

    /**
     * 接收任务（待接收 → 进行中）
     * 任务负责人确认接收派发的任务；具备项目任务编辑权限者也可代接收。
     *
     * @param taskId 任务编号
     */
    void acceptTask(Long taskId);

    /**
     * 公开接口版本：跳过身份校验（由签名保障安全性）
     */
    void acceptTaskPublic(Long taskId);

    void dispatchTask(Long taskId);

    void submitCompletion(Long taskId, String actualCompleteDate, String completionNote);

    void reviewCompletion(Long taskId, boolean approved, String reviewOpinion, Long operatorId);

    void updateTask(PmsTaskDO entity);

    void deleteTask(Long id);

    PmsTaskDO getTask(Long id);

    /**
     * 获取任务列表（含权限过滤）
     * 非管理员只能看到自己作为主责任人、协助人或审核人的任务
     */
    List<PmsTaskDO> getTaskList();

    /**
     * 获取任务列表（含权限过滤）
     * @param mainOwnerId 主责任人ID（可选）
     * @param projectId 项目ID（可选）
     * @param projectType 项目类型（可选，传 standard_template 时不过滤）
     */
    List<PmsTaskDO> getTaskList(Long mainOwnerId, Long projectId, String projectType);

    // ==================== #1 子任务层级（新增） ====================

    /**
     * 查询某任务的直接子任务列表
     *
     * @param parentTaskId 父任务ID
     * @return 子任务列表，按 sortOrder 升序
     */
    List<PmsTaskDO> getSubTaskList(Long parentTaskId);

    /**
     * 查询某项目的全部任务（含 level / parentTaskId 字段，由前端组装成树）。
     * 与 getTaskList(null, projectId, null) 的区别：本方法不做「只看自己的任务」过滤，
     * 保证子任务分派给他人时树形结构不会断层；仅在调用方已确认有项目查看权时使用。
     *
     * @param projectId 项目ID
     */
    List<PmsTaskDO> getTaskTreeByProject(Long projectId);

    /**
     * 进度填报。更新自身进度后，自底向上重算所有祖先任务的进度（v1 均权平均）。
     *
     * @param taskId   任务ID
     * @param progress 进度 0-100
     */
    void updateTaskProgress(Long taskId, Integer progress);

    // ==================== #3 任务派发审核（新增） ====================

    /**
     * 提交审核：review_status none/rejected -> submitted。
     * 若审核策略为 self_review / skip，则直接置为 completed。
     *
     * @param taskId 任务ID
     */
    void submitReview(Long taskId);

    /**
     * 审核通过：review_status submitted -> completed，同时 complete_status -> completed、进度置 100。
     *
     * @param taskId        任务ID
     * @param reviewComment 审核意见，可为空
     */
    void approveReview(Long taskId, String reviewComment);

    /**
     * 审核通过（公开接口，跳过身份校验）：用于钉钉「一键通过」免登录直连。
     * 复用 approveReview 的状态流转与通知逻辑，但 operatorId 用任务审核人兜底，不校验登录态。
     *
     * @param taskId 任务ID
     */
    void approveReviewPublic(Long taskId);

    /**
     * 查询某用户的直属领导 ID（日常任务的审核人兜底）。
     * 用于新建日常任务弹窗预校验：返回 null 表示未抽取到直属领导，需提示用户检查部门领导设置。
     *
     * @param userId 用户ID
     * @return 直属领导ID，若未抽取到返回 null
     */
    Long resolveReviewerOf(Long userId);

    /**
     * 审核驳回：review_status submitted -> rejected，同时 complete_status 回到 in_progress。
     *
     * @param taskId        任务ID
     * @param reviewComment 驳回原因，必填
     */
    void rejectReview(Long taskId, String reviewComment);

    /**
     * 查询「待我审核」的任务列表
     *
     * @param projectId    项目ID，可为空表示全部项目
     * @param reviewStatus 审核状态，可为空默认 submitted
     */
    List<PmsTaskDO> getMyReviewTaskList(Long projectId, String reviewStatus);

    // ==================== 日常任务 / 我的任务看板 ====================

    /**
     * 我的任务看板聚合查询。
     * 返回三块数据：历史遗留（范围起点之前未完成）、时间段内项目任务（按项目分组）、时间段内日常任务。
     *
     * @param userIds            人员ID列表，为空默认本人
     * @param dateFrom           范围起点 yyyy-MM-dd（含）
     * @param dateTo             范围终点 yyyy-MM-dd（含）
     * @param includeSubordinates 是否递归包含下属，默认 true
     */
    TaskBoardVO boardQuery(List<Long> userIds, LocalDate dateFrom, LocalDate dateTo, boolean includeSubordinates);

    /**
     * 部门审核中心：查询待当前用户（责任人直属领导）审核的日常任务列表
     */
    List<PmsTaskDO> getDeptReviewTaskList();

}

