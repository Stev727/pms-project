package cn.iocoder.yudao.module.pms.controller.admin.task;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
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

    @PostMapping("/completion/submit")
    @Operation(summary = "提交任务完成审核")
    @PreAuthorize("@ss.hasPermission('pms:task:update')")
    public CommonResult<Boolean> submitCompletion(@RequestParam("taskId") Long taskId) {
        taskService.submitCompletion(taskId);
        return success(true);
    }

    @PutMapping("/completion/review")
    @Operation(summary = "项目经理审核任务完成")
    @PreAuthorize("@ss.hasPermission('pms:task:update')")
    public CommonResult<Boolean> reviewCompletion(@RequestParam("taskId") Long taskId,
                                                   @RequestParam("approved") boolean approved) {
        taskService.reviewCompletion(taskId, approved, cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId());
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
    @Operation(summary = "获取任务列表")
    @PreAuthorize("@ss.hasPermission('pms:task:query')")
    public CommonResult<List<PmsTaskDO>> list(
            @RequestParam(value = "mainOwnerId", required = false) Long mainOwnerId,
            @RequestParam(value = "projectId", required = false) Long projectId) {
        List<PmsTaskDO> list = taskService.getTaskList(mainOwnerId, projectId);
        return success(list);
    }

}