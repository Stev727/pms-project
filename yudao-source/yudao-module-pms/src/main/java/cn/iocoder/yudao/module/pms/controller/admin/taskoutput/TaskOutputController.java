package cn.iocoder.yudao.module.pms.controller.admin.taskoutput;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.dal.dataobject.taskoutput.PmsTaskOutputDO;
import cn.iocoder.yudao.module.pms.service.taskoutput.TaskOutputService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 任务输出物")
@RestController
@RequestMapping("/pms/task-output")
@Validated
public class TaskOutputController {

    @Resource
    private TaskOutputService taskOutputService;

    @PostMapping("/create")
    @Operation(summary = "创建任务输出物")
    @PreAuthorize("@ss.hasPermission('pms:task_output:create')")
    public CommonResult<Long> create(@RequestBody PmsTaskOutputDO entity) {
        return success(taskOutputService.createTaskOutput(entity));
    }

    @PutMapping("/update")
    @Operation(summary = "更新任务输出物")
    @PreAuthorize("@ss.hasPermission('pms:task_output:update')")
    public CommonResult<Boolean> update(@RequestBody PmsTaskOutputDO entity) {
        taskOutputService.updateTaskOutput(entity);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除任务输出物")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:task_output:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        taskOutputService.deleteTaskOutput(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取任务输出物")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:task_output:query')")
    public CommonResult<PmsTaskOutputDO> get(@RequestParam("id") Long id) {
        return success(taskOutputService.getTaskOutput(id));
    }

    @GetMapping("/list")
    @Operation(summary = "获取任务输出物列表")
    @PreAuthorize("@ss.hasPermission('pms:task_output:query')")
    public CommonResult<List<PmsTaskOutputDO>> list() {
        return success(taskOutputService.getTaskOutputList());
    }

}