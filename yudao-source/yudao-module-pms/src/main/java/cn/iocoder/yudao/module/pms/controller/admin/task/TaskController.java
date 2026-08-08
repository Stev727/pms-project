package cn.iocoder.yudao.module.pms.controller.admin.task;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import cn.iocoder.yudao.module.pms.service.task.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
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

    @PostMapping("/create")
    @Operation(summary = "创建任务")
    @PreAuthorize("@ss.hasPermission('pms:task:create')")
    public CommonResult<Long> create(@RequestBody PmsTaskDO entity) {
        return success(taskService.createTask(entity));
    }

    @PostMapping("/dispatch")
    @Operation(summary = "派发任务并通知负责人")
    @PreAuthorize("@ss.hasPermission('pms:task:update')")
    public CommonResult<Boolean> dispatch(@RequestParam("taskId") Long taskId) {
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
        taskService.reviewCompletion(taskId, approved, reviewOpinion,
                SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

    @PutMapping("/update")
    @Operation(summary = "更新任务")
    @PreAuthorize("@ss.hasPermission('pms:task:update')")
    public CommonResult<Boolean> update(@RequestBody PmsTaskDO entity) {
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

    @DeleteMapping("/delete")
    @Operation(summary = "删除任务")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:task:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
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

}

