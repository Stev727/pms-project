package cn.iocoder.yudao.module.pms.controller.admin.project;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.dal.dataobject.project.PmsProjectDO;
import cn.iocoder.yudao.module.pms.service.project.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 项目")
@RestController
@RequestMapping("/pms/project")
@Validated
public class ProjectController {

    @Resource
    private ProjectService projectService;

    @PostMapping("/create")
    @Operation(summary = "创建项目")
    @PreAuthorize("@ss.hasPermission('pms:project:create')")
    public CommonResult<Long> create(@RequestBody PmsProjectDO entity) {
        return success(projectService.createProject(entity));
    }

    @PutMapping("/update")
    @Operation(summary = "更新项目")
    @PreAuthorize("@ss.hasPermission('pms:project:update')")
    public CommonResult<Boolean> update(@RequestBody PmsProjectDO entity) {
        projectService.updateProject(entity);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除项目")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        projectService.deleteProject(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取项目")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<PmsProjectDO> get(@RequestParam("id") Long id) {
        return success(projectService.getProject(id));
    }

    @GetMapping("/list")
    @Operation(summary = "获取项目列表")
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<List<PmsProjectDO>> list() {
        return success(projectService.getProjectList());
    }

}