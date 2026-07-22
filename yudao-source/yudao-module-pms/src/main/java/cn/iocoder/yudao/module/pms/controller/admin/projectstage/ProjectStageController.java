package cn.iocoder.yudao.module.pms.controller.admin.projectstage;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.dal.dataobject.projectstage.PmsProjectStageDO;
import cn.iocoder.yudao.module.pms.service.projectstage.ProjectStageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 项目阶段")
@RestController
@RequestMapping("/pms/project-stage")
@Validated
public class ProjectStageController {

    @Resource
    private ProjectStageService projectStageService;

    @PostMapping("/create")
    @Operation(summary = "创建项目阶段")
    @PreAuthorize("@ss.hasPermission('pms:project_stage:create')")
    public CommonResult<Long> create(@RequestBody PmsProjectStageDO entity) {
        return success(projectStageService.createProjectStage(entity));
    }

    @PutMapping("/update")
    @Operation(summary = "更新项目阶段")
    @PreAuthorize("@ss.hasPermission('pms:project_stage:update')")
    public CommonResult<Boolean> update(@RequestBody PmsProjectStageDO entity) {
        projectStageService.updateProjectStage(entity);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除项目阶段")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project_stage:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        projectStageService.deleteProjectStage(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取项目阶段")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project_stage:query')")
    public CommonResult<PmsProjectStageDO> get(@RequestParam("id") Long id) {
        return success(projectStageService.getProjectStage(id));
    }

    @GetMapping("/list")
    @Operation(summary = "获取项目阶段列表")
    @PreAuthorize("@ss.hasPermission('pms:project_stage:query')")
    public CommonResult<List<PmsProjectStageDO>> list() {
        return success(projectStageService.getProjectStageList());
    }

}