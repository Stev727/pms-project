package cn.iocoder.yudao.module.pms.controller.admin.tasklog;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.dal.dataobject.tasklog.PmsTaskLogDO;
import cn.iocoder.yudao.module.pms.service.tasklog.TaskLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 任务操作日志")
@RestController
@RequestMapping("/pms/task-log")
@Validated
public class TaskLogController {

    @Resource
    private TaskLogService taskLogService;

    @PostMapping("/create")
    @Operation(summary = "创建任务操作日志")
    @PreAuthorize("@ss.hasPermission('pms:task_log:create')")
    public CommonResult<Long> create(@RequestBody PmsTaskLogDO entity) {
        return success(taskLogService.createTaskLog(entity));
    }

    @PutMapping("/update")
    @Operation(summary = "更新任务操作日志")
    @PreAuthorize("@ss.hasPermission('pms:task_log:update')")
    public CommonResult<Boolean> update(@RequestBody PmsTaskLogDO entity) {
        taskLogService.updateTaskLog(entity);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除任务操作日志")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:task_log:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        taskLogService.deleteTaskLog(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取任务操作日志")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:task_log:query')")
    public CommonResult<PmsTaskLogDO> get(@RequestParam("id") Long id) {
        return success(taskLogService.getTaskLog(id));
    }

    @GetMapping("/list")
    @Operation(summary = "获取任务操作日志列表")
    @PreAuthorize("@ss.hasPermission('pms:task_log:query')")
    public CommonResult<List<PmsTaskLogDO>> list() {
        return success(taskLogService.getTaskLogList());
    }

}