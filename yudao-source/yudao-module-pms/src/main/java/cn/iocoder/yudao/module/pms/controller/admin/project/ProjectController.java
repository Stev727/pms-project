package cn.iocoder.yudao.module.pms.controller.admin.project;

import cn.iocoder.yudao.module.pms.controller.admin.project.vo.ProjectCreateBundleReqVO;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.dal.dataobject.project.PmsProjectDO;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;

import cn.iocoder.yudao.module.pms.service.project.ProjectService;
import cn.iocoder.yudao.module.pms.enums.PmsPermKeyEnum;
import cn.iocoder.yudao.module.pms.service.projectpermission.ProjectPermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 项目")
@RestController
@RequestMapping("/pms/project")
@Validated
public class ProjectController {

    @Resource
    private ProjectService projectService;

    @Autowired(required = false)
    private ProjectPermissionService projectPermissionService;

    /**
     * 项目级权限校验兜底：删除项目需 project_edit 权限点（默认仅 pm/admin/dept_head）。
     */
    private void requireProjectPerm(Long projectId, String permKey) {
        if (projectPermissionService == null || projectId == null) {
            return;
        }
        projectPermissionService.checkPermission(projectId, permKey);
    }

    @PostMapping("/create")
    @Operation(summary = "创建项目")
    @PreAuthorize("@ss.hasPermission('pms:project:create')")
    public CommonResult<Long> create(@RequestBody PmsProjectDO entity) {
        return success(projectService.createProject(entity));
    }

    @PostMapping("/create-bundle")
    @Operation(summary = "事务创建项目、成员、任务和默认通知规则")
    @PreAuthorize("@ss.hasPermission('pms:project:create')")
    public CommonResult<Long> createBundle(@RequestBody ProjectCreateBundleReqVO request) {
        return success(projectService.createProjectBundle(request));
    }

    @PutMapping("/update")
    @Operation(summary = "更新项目")
    @PreAuthorize("@ss.hasPermission('pms:project:update')")
    public CommonResult<Boolean> update(@RequestBody PmsProjectDO entity) {
        // P0: 限制只有项目经理或超管可以修改项目基本信息
        Long currentUserId = SecurityFrameworkUtils.getLoginUserId();
        boolean isSuperAdmin = SecurityFrameworkUtils.getLoginUser() != null
                && SecurityFrameworkUtils.getLoginUser().getScopes() != null
                && SecurityFrameworkUtils.getLoginUser().getScopes().contains("super_admin");
        if (!isSuperAdmin) {
            // 从数据库读取真实项目信息判断是否PM
            PmsProjectDO existing = projectService.getProject(entity.getProjectId());
            if (existing != null && existing.getProjectManagerId() != null
                    && !existing.getProjectManagerId().equals(currentUserId)) {
                throw new cn.iocoder.yudao.framework.common.exception.ServiceException(
                    cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.PROJECT_MANAGER_REQUIRED);
            }
        }
        projectService.updateProject(entity);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除项目")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        requireProjectPerm(id, PmsPermKeyEnum.PROJECT_EDIT.getKey());
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
    @Operation(summary = "获取项目列表（含权限过滤）")
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<List<PmsProjectDO>> list(
            @RequestParam(value = "projectType", required = false) String projectType) {
        return success(projectService.getProjectList(projectType));
    }

    @GetMapping("/count-by-template")
    @Operation(summary = "获取各模板的使用项目数量（绕过权限过滤）")
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<Map<Long, Long>> countByTemplate() {
        return success(projectService.countByTemplate());
    }

}
