package cn.iocoder.yudao.module.pms.controller.admin.taskdependency;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.dal.dataobject.taskdependency.PmsTaskDependencyDO;
import cn.iocoder.yudao.module.pms.service.taskdependency.TaskDependencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 任务依赖")
@RestController
@RequestMapping("/pms/task-dependency")
@Validated
public class TaskDependencyController {

    @Resource
    private TaskDependencyService taskDependencyService;

    @PostMapping("/create")
    @Operation(summary = "创建任务依赖")
    @PreAuthorize("@ss.hasPermission('pms:task_dependency:create')")
    public CommonResult<Long> create(@RequestBody PmsTaskDependencyDO entity) {
        return success(taskDependencyService.createTaskDependency(entity));
    }

    @PutMapping("/update")
    @Operation(summary = "更新任务依赖")
    @PreAuthorize("@ss.hasPermission('pms:task_dependency:update')")
    public CommonResult<Boolean> update(@RequestBody PmsTaskDependencyDO entity) {
        taskDependencyService.updateTaskDependency(entity);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除任务依赖")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:task_dependency:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        taskDependencyService.deleteTaskDependency(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取任务依赖")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:task_dependency:query')")
    public CommonResult<PmsTaskDependencyDO> get(@RequestParam("id") Long id) {
        return success(taskDependencyService.getTaskDependency(id));
    }

    @GetMapping("/list")
    @Operation(summary = "获取任务依赖列表")
    @PreAuthorize("@ss.hasPermission('pms:task_dependency:query')")
    public CommonResult<List<PmsTaskDependencyDO>> list() {
        return success(taskDependencyService.getTaskDependencyList());
    }

}