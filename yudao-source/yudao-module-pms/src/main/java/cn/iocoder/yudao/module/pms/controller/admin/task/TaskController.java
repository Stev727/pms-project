package cn.iocoder.yudao.module.pms.controller.admin.task;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskBoardVO;
import cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskBoardScopeVO;
import cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskExportExcel;
import cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskWeeklyReportVO;
import cn.iocoder.yudao.module.pms.service.task.TaskService;
import cn.iocoder.yudao.module.pms.enums.PmsPermKeyEnum;
import cn.iocoder.yudao.module.pms.service.projectpermission.ProjectPermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 任务 Controller
 *
 * ============================ 改造说明 ============================
 * 版本：v2（在线上原文件基础上改造，原有 9 个端点路径 / 参数 / 权限一个未改）
 * 新增端点：
 *   【#1 子任务层级】
 *     GET  /pms/task/children?parentTaskId=   查直接子任务
 *     GET  /pms/task/tree?projectId=          查项目全部任务（前端组树，不做「只看我的」过滤）
 *     PUT  /pms/task/progress?taskId=&progress= 进度填报，自动汇总父任务进度
 *   【#3 任务派发审核】
 *     POST /pms/task/submit-review?taskId=
 *     POST /pms/task/approve-review?taskId=&reviewComment=
 *     POST /pms/task/reject-review?taskId=&reviewComment=（原因必填）
 *     GET  /pms/task/my-review-list?projectId=&reviewStatus=
 *
 * 权限点复用既有 pms:task:query / pms:task:update，无需新增菜单 SQL。
 * ==================================================================
 */
@Tag(name = "管理后台 - 任务")
@RestController
@RequestMapping("/pms/task")
@Validated
public class TaskController {

    @Resource
    private TaskService taskService;

    @Autowired(required = false)
    private ProjectPermissionService projectPermissionService;

    /**
     * 项目级权限校验兜底（与 QualityIssueController 一致）：
     * 日常任务（projectId=null）跳过，仅菜单级 @PreAuthorize 把关。
     */
    private void requireProjectPerm(Long projectId, String permKey) {
        if (projectPermissionService == null || projectId == null) {
            return;
        }
        projectPermissionService.checkPermission(projectId, permKey);
    }

    private Long getTaskProjectId(Long taskId) {
        if (taskId == null) return null;
        PmsTaskDO task = taskService.getTask(taskId);
        return task == null ? null : task.getProjectId();
    }

    @PostMapping("/create")
    @Operation(summary = "创建任务")
    @PreAuthorize("@ss.hasPermission('pms:task:create')")
    public CommonResult<Long> create(@RequestBody PmsTaskDO entity) {
        requireProjectPerm(entity.getProjectId(), PmsPermKeyEnum.TASK_CREATE.getKey());
        return success(taskService.createTask(entity));
    }

    @GetMapping("/reviewer-of")
    @Operation(summary = "查询某用户的直属领导ID（日常任务审核人预校验）")
    @Parameter(name = "userId", description = "用户ID", required = true)
    public CommonResult<Long> reviewerOf(@RequestParam("userId") Long userId) {
        return success(taskService.resolveReviewerOf(userId));
    }

    @PostMapping("/dispatch")
    @Operation(summary = "派发任务并通知负责人")
    @PreAuthorize("@ss.hasPermission('pms:task:update')")
    public CommonResult<Boolean> dispatch(@RequestParam("taskId") Long taskId) {
        requireProjectPerm(getTaskProjectId(taskId), PmsPermKeyEnum.TASK_ASSIGN.getKey());
        taskService.dispatchTask(taskId);
        return success(true);
    }

    @PostMapping("/submit-completion")
    @Operation(summary = "提交任务完成（进入待审核）")
    @Parameter(name = "taskId", description = "任务编号", required = true)
    @Parameter(name = "actualCompleteDate", description = "实际完成日期(yyyy-MM-dd)")
    @Parameter(name = "completionNote", description = "完成说明")
    @PreAuthorize("@ss.hasPermission('pms:task:update')")
    public CommonResult<Boolean> submitCompletion(
            @RequestParam("taskId") Long taskId,
            @RequestParam(value = "actualCompleteDate", required = false) String actualCompleteDate,
            @RequestParam(value = "completionNote", required = false) String completionNote) {
        taskService.submitCompletion(taskId, actualCompleteDate, completionNote);
        return success(true);
    }

    @PostMapping("/review-completion")
    @Operation(summary = "审核任务完成（通过/驳回）")
    @Parameter(name = "taskId", description = "任务编号", required = true)
    @Parameter(name = "approved", description = "是否通过", required = true)
    @Parameter(name = "reviewOpinion", description = "审核意见")
    @PreAuthorize("@ss.hasPermission('pms:task:update')")
    public CommonResult<Boolean> reviewCompletion(
            @RequestParam("taskId") Long taskId,
            @RequestParam("approved") Boolean approved,
            @RequestParam(value = "reviewOpinion", required = false) String reviewOpinion) {
        requireProjectPerm(getTaskProjectId(taskId), PmsPermKeyEnum.TASK_REVIEW.getKey());
        taskService.reviewCompletion(taskId, approved, reviewOpinion,
                SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

    @PutMapping("/update")
    @Operation(summary = "更新任务")
    @PreAuthorize("@ss.hasPermission('pms:task:update')")
    public CommonResult<Boolean> update(@RequestBody PmsTaskDO entity) {
        requireProjectPerm(entity.getProjectId(), PmsPermKeyEnum.TASK_EDIT.getKey());
        taskService.updateTask(entity);
        return success(true);
    }

    @PostMapping("/simulate-dingtalk-confirm")
    @Operation(summary = "模拟钉钉确认")
    @Parameter(name = "taskId", description = "任务编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:task:update')")
    public CommonResult<Boolean> simulateDingtalkConfirm(@RequestParam("taskId") Long taskId) {
        taskService.simulateDingtalkConfirm(taskId);
        return success(true);
    }

    @PostMapping("/accept")
    @Operation(summary = "接收任务（待接收→进行中）")
    @Parameter(name = "taskId", description = "任务编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:task:accept')")
    public CommonResult<Boolean> accept(@RequestParam("taskId") Long taskId) {
        taskService.acceptTask(taskId);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除任务")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:task:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        requireProjectPerm(getTaskProjectId(id), PmsPermKeyEnum.TASK_DELETE.getKey());
        taskService.deleteTask(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取任务")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:task:query')")
    public CommonResult<PmsTaskDO> get(@RequestParam("id") Long id) {
        return success(taskService.getTask(id));
    }

    @GetMapping("/weekly-report")
    @Operation(summary = "周报看板聚合查询（上周完成/本周计划/上周延期/上周动态）")
    @Parameter(name = "date", description = "基准日期 yyyy-MM-dd，默认今天；按自然周（周一~周日）计算")
    @Parameter(name = "userId", description = "目标人员ID；为空=本人；0=全部（仅管理员）")
    @PreAuthorize("@ss.hasPermission('pms:task:query')")
    public CommonResult<TaskWeeklyReportVO> weeklyReport(
            @RequestParam(value = "date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestParam(value = "userId", required = false) Long userId) {
        return success(taskService.getWeeklyReport(userId, date));
    }

    @GetMapping("/list")
    @Operation(summary = "获取任务列表（含权限过滤）")
    @PreAuthorize("@ss.hasPermission('pms:task:query')")
    public CommonResult<List<PmsTaskDO>> list(
            @RequestParam(value = "mainOwnerId", required = false) Long mainOwnerId,
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "projectType", required = false) String projectType) {
        List<PmsTaskDO> list = taskService.getTaskList(mainOwnerId, projectId, projectType);
        return success(list);
    }

    // ==================== #1 子任务层级（新增） ====================

    @GetMapping("/children")
    @Operation(summary = "获取直接子任务列表")
    @Parameter(name = "parentTaskId", description = "父任务编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:task:query')")
    public CommonResult<List<PmsTaskDO>> children(@RequestParam("parentTaskId") Long parentTaskId) {
        return success(taskService.getSubTaskList(parentTaskId));
    }

    @GetMapping("/tree")
    @Operation(summary = "获取项目全部任务（含层级字段，由前端组装成树）")
    @Parameter(name = "projectId", description = "项目编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:task:query')")
    public CommonResult<List<PmsTaskDO>> tree(@RequestParam("projectId") Long projectId) {
        return success(taskService.getTaskTreeByProject(projectId));
    }

    @PutMapping("/progress")
    @Operation(summary = "进度填报（自动汇总父任务进度）")
    @Parameter(name = "taskId", description = "任务编号", required = true)
    @Parameter(name = "progress", description = "进度 0-100", required = true)
    @PreAuthorize("@ss.hasPermission('pms:task:update')")
    public CommonResult<Boolean> progress(@RequestParam("taskId") Long taskId,
                                          @RequestParam("progress") Integer progress) {
        requireProjectPerm(getTaskProjectId(taskId), PmsPermKeyEnum.TASK_EDIT.getKey());
        taskService.updateTaskProgress(taskId, progress);
        return success(true);
    }

    // ==================== #3 任务派发审核（新增） ====================

    @PostMapping("/submit-review")
    @Operation(summary = "提交审核（in_progress -> submitted）")
    @Parameter(name = "taskId", description = "任务编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:task:update')")
    public CommonResult<Boolean> submitReview(@RequestParam("taskId") Long taskId) {
        taskService.submitReview(taskId);
        return success(true);
    }

    @PostMapping("/approve-review")
    @Operation(summary = "审核通过（submitted -> completed）")
    @Parameter(name = "taskId", description = "任务编号", required = true)
    @Parameter(name = "reviewComment", description = "审核意见")
    @PreAuthorize("@ss.hasPermission('pms:task:update')")
    public CommonResult<Boolean> approveReview(
            @RequestParam("taskId") Long taskId,
            @RequestParam(value = "reviewComment", required = false) String reviewComment) {
        requireProjectPerm(getTaskProjectId(taskId), PmsPermKeyEnum.TASK_REVIEW.getKey());
        taskService.approveReview(taskId, reviewComment);
        return success(true);
    }

    @PostMapping("/reject-review")
    @Operation(summary = "审核驳回（submitted -> rejected，原因必填）")
    @Parameter(name = "taskId", description = "任务编号", required = true)
    @Parameter(name = "reviewComment", description = "驳回原因", required = true)
    @PreAuthorize("@ss.hasPermission('pms:task:update')")
    public CommonResult<Boolean> rejectReview(
            @RequestParam("taskId") Long taskId,
            @RequestParam("reviewComment") String reviewComment) {
        requireProjectPerm(getTaskProjectId(taskId), PmsPermKeyEnum.TASK_REVIEW.getKey());
        taskService.rejectReview(taskId, reviewComment);
        return success(true);
    }

    @GetMapping("/my-review-list")
    @Operation(summary = "待我审核的任务列表")
    @Parameter(name = "projectId", description = "项目编号，不传表示全部项目")
    @Parameter(name = "reviewStatus", description = "审核状态，默认 submitted")
    @PreAuthorize("@ss.hasPermission('pms:task:query')")
    public CommonResult<List<PmsTaskDO>> myReviewList(
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "reviewStatus", required = false) String reviewStatus) {
        return success(taskService.getMyReviewTaskList(projectId, reviewStatus));
    }

    // ==================== 日常任务 / 我的任务看板（新增） ====================

    @GetMapping("/board")
    @Operation(summary = "我的任务看板聚合查询（历史遗留/项目任务/日常任务）")
    @Parameter(name = "userIds", description = "人员ID列表（逗号分隔字符串），不传按当前用户权限范围默认（管理员=全部 / 其他=本人）")
    @Parameter(name = "dateFrom", description = "范围起点 yyyy-MM-dd", required = true)
    @Parameter(name = "dateTo", description = "范围终点 yyyy-MM-dd", required = true)
    @Parameter(name = "includeSubordinates", description = "是否递归包含下属，默认 true")
    @PreAuthorize("@ss.hasPermission('pms:board:query')")
    public CommonResult<TaskBoardVO> board(
            @RequestParam(value = "userIds", required = false) String userIds,
            @RequestParam("dateFrom") String dateFrom,
            @RequestParam("dateTo") String dateTo,
            @RequestParam(value = "includeSubordinates", defaultValue = "true") boolean includeSubordinates) {
        return success(taskService.boardQuery(userIds, LocalDate.parse(dateFrom), LocalDate.parse(dateTo), includeSubordinates));
    }

    @GetMapping("/board-scope")
    @Operation(summary = "我的任务看板：当前用户可查看的人员范围（权限判定）")
    @PreAuthorize("@ss.hasPermission('pms:board:query')")
    public CommonResult<TaskBoardScopeVO> boardScope() {
        return success(taskService.getBoardScope());
    }

    @GetMapping("/dept-review-list")
    @Operation(summary = "部门审核中心：待我审核的日常任务列表")
    @PreAuthorize("@ss.hasPermission('pms:daily-task:review')")
    public CommonResult<List<PmsTaskDO>> deptReviewList() {
        return success(taskService.getDeptReviewTaskList());
    }

    // ==================== 任务导出（新增） ====================

    @GetMapping("/export")
    @Operation(summary = "导出项目全部任务（Excel，忽略页面筛选）")
    @Parameter(name = "projectId", description = "项目编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:task:query')")
    public void export(HttpServletResponse response,
                       @RequestParam("projectId") Long projectId) throws IOException {
        List<TaskExportExcel> list = taskService.exportTaskByProject(projectId);
        ExcelUtils.write(response, "项目任务.xlsx", "任务", TaskExportExcel.class, list);
    }

}

