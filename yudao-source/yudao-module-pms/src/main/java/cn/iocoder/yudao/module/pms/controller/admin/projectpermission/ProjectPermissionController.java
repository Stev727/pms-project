package cn.iocoder.yudao.module.pms.controller.admin.projectpermission;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.controller.admin.projectpermission.vo.ProjectPermMatrixRespVO;
import cn.iocoder.yudao.module.pms.controller.admin.projectpermission.vo.ProjectPermSaveReqVO;
import cn.iocoder.yudao.module.pms.dal.dataobject.projectmember.PmsProjectMemberDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.projectpermission.PmsProjectPermissionDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.projectrole.PmsProjectRoleDO;
import cn.iocoder.yudao.module.pms.dal.mysql.projectmember.ProjectMemberMapper;
import cn.iocoder.yudao.module.pms.enums.PmsPermKeyEnum;
import cn.iocoder.yudao.module.pms.service.projectpermission.ProjectPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 项目权限")
@RestController
@RequestMapping("/pms/project-permission")
@Validated
public class ProjectPermissionController {

    @Resource
    private ProjectPermissionService projectPermissionService;
    @Resource
    private ProjectMemberMapper projectMemberMapper;

    // ==================== 前端权限拉取 ====================

    /**
     * 前端进入项目详情时调用一次，拿到当前用户在该项目的全部权限点，
     * 各 Tab 按钮用 can(permKey) 判断，避免逐个按钮请求后端。
     */
    @GetMapping("/my-permissions")
    @Operation(summary = "获取当前用户在指定项目的权限点集合")
    @Parameter(name = "projectId", description = "项目ID", required = true)
    public CommonResult<Set<String>> myPermissions(@RequestParam("projectId") Long projectId) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return success(projectPermissionService.allowedKeys(userId, projectId));
    }

    // ==================== 权限矩阵 ====================

    @GetMapping("/matrix")
    @Operation(summary = "获取项目权限矩阵")
    @Parameter(name = "projectId", description = "项目ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<ProjectPermMatrixRespVO> matrix(@RequestParam("projectId") Long projectId) {
        ProjectPermMatrixRespVO resp = new ProjectPermMatrixRespVO();
        resp.setProjectId(projectId);

        // 1. 角色列（附带成员数，便于 PM 判断能否删除）
        List<PmsProjectMemberDO> members = projectMemberMapper.selectActiveListByProjectId(projectId);
        List<ProjectPermMatrixRespVO.RoleItem> roles = new ArrayList<>();
        for (PmsProjectRoleDO role : projectPermissionService.getRoleList(projectId)) {
            ProjectPermMatrixRespVO.RoleItem item = new ProjectPermMatrixRespVO.RoleItem();
            item.setRoleId(role.getRoleId());
            item.setRoleName(role.getRoleName());
            item.setRoleCode(role.getRoleCode());
            item.setIsSystem(role.getIsSystem());
            item.setSortOrder(role.getSortOrder());
            final String roleCode = role.getRoleCode();
            item.setMemberCount((int) members.stream()
                    .filter(m -> Objects.equals(m.getRoleCode(), roleCode))
                    .count());
            roles.add(item);
        }
        resp.setRoles(roles);

        // 2. 权限点行（按分组）
        List<ProjectPermMatrixRespVO.PermGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<PmsPermKeyEnum>> entry : PmsPermKeyEnum.groupedAll().entrySet()) {
            ProjectPermMatrixRespVO.PermGroup group = new ProjectPermMatrixRespVO.PermGroup();
            group.setGroup(entry.getKey());
            group.setItems(entry.getValue().stream().map(e -> {
                ProjectPermMatrixRespVO.PermItem pi = new ProjectPermMatrixRespVO.PermItem();
                pi.setPermKey(e.getKey());
                pi.setLabel(e.getLabel());
                return pi;
            }).collect(Collectors.toList()));
            groups.add(group);
        }
        resp.setPermGroups(groups);

        // 3. 已授权项
        resp.setGrantedPairs(projectPermissionService.getPermissionList(projectId).stream()
                .filter(p -> Boolean.TRUE.equals(p.getAllowed()))
                .map(p -> p.getRoleId() + ":" + p.getPermKey())
                .collect(Collectors.toList()));

        // 4. 当前用户能否编辑
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        resp.setEditable(projectPermissionService.can(userId, projectId, PmsPermKeyEnum.PERMISSION_MANAGE.getKey()));
        return success(resp);
    }

    @PostMapping("/save-matrix")
    @Operation(summary = "保存项目权限矩阵（整体覆盖）")
    @PreAuthorize("@ss.hasPermission('pms:project:update')")
    public CommonResult<Boolean> saveMatrix(@Valid @RequestBody ProjectPermSaveReqVO reqVO) {
        List<PmsProjectPermissionDO> list = new ArrayList<>();
        if (reqVO.getGrantedPairs() != null) {
            for (String pair : reqVO.getGrantedPairs()) {
                if (pair == null) {
                    continue;
                }
                int idx = pair.indexOf(':');
                if (idx <= 0 || idx == pair.length() - 1) {
                    continue;
                }
                PmsProjectPermissionDO item = new PmsProjectPermissionDO();
                item.setProjectId(reqVO.getProjectId());
                item.setRoleId(Long.valueOf(pair.substring(0, idx)));
                item.setPermKey(pair.substring(idx + 1));
                item.setAllowed(true);
                list.add(item);
            }
        }
        projectPermissionService.savePermissions(reqVO.getProjectId(), list);
        return success(true);
    }

    // ==================== 角色管理 ====================

    @GetMapping("/role/list")
    @Operation(summary = "获取项目角色列表")
    @Parameter(name = "projectId", description = "项目ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<List<PmsProjectRoleDO>> roleList(@RequestParam("projectId") Long projectId) {
        return success(projectPermissionService.getRoleList(projectId));
    }

    @PostMapping("/role/create")
    @Operation(summary = "创建项目角色")
    @PreAuthorize("@ss.hasPermission('pms:project:update')")
    public CommonResult<Long> roleCreate(@RequestBody PmsProjectRoleDO entity) {
        return success(projectPermissionService.createRole(entity));
    }

    @PutMapping("/role/update")
    @Operation(summary = "更新项目角色")
    @PreAuthorize("@ss.hasPermission('pms:project:update')")
    public CommonResult<Boolean> roleUpdate(@RequestBody PmsProjectRoleDO entity) {
        projectPermissionService.updateRole(entity);
        return success(true);
    }

    @DeleteMapping("/role/delete")
    @Operation(summary = "删除项目角色")
    @Parameter(name = "roleId", description = "角色ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project:update')")
    public CommonResult<Boolean> roleDelete(@RequestParam("roleId") Long roleId) {
        projectPermissionService.deleteRole(roleId);
        return success(true);
    }

    @PostMapping("/init")
    @Operation(summary = "按默认模板初始化项目权限（存量项目补数据用，幂等）")
    @Parameter(name = "projectId", description = "项目ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project:update')")
    public CommonResult<Boolean> init(@RequestParam("projectId") Long projectId) {
        projectPermissionService.initProjectPermission(projectId);
        return success(true);
    }

}

