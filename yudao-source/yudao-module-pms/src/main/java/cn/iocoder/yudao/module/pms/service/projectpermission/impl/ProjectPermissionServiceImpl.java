package cn.iocoder.yudao.module.pms.service.projectpermission.impl;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.dal.dataobject.project.PmsProjectDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.projectmember.PmsProjectMemberDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.projectpermission.PmsProjectPermissionDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.projectpermission.PmsProjectPermissionTemplateDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.projectrole.PmsProjectRoleDO;
import cn.iocoder.yudao.module.pms.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.projectmember.ProjectMemberMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.projectpermission.ProjectPermissionMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.projectpermission.ProjectPermissionTemplateMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.projectrole.ProjectRoleMapper;
import cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.pms.enums.PmsPermKeyEnum;
import cn.iocoder.yudao.module.pms.service.projectpermission.ProjectPermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProjectPermissionServiceImpl implements ProjectPermissionService {

    /**
     * 超级管理员角色编码，与 ProjectMemberServiceImpl 现有写法保持一致
     */
    private static final String ROLE_SUPER_ADMIN = "super_admin";

    /**
     * PMO / 高管全量角色，对所有项目拥有全部项目级权限
     */
    private static final String ROLE_PMO = "pmo";

    @Resource
    private ProjectRoleMapper projectRoleMapper;
    @Resource
    private ProjectPermissionMapper projectPermissionMapper;
    @Resource
    private ProjectPermissionTemplateMapper projectPermissionTemplateMapper;
    @Resource
    private ProjectMemberMapper projectMemberMapper;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private SecurityFrameworkService securityFrameworkService;

    // ==================== 权限判定 ====================

    @Override
    public boolean can(Long userId, Long projectId, String permKey) {
        if (userId == null || projectId == null || permKey == null) {
            return false;
        }
        // 1. 超管 / PMO 全量放行
        if (hasGlobalRole()) {
            return true;
        }
        // 2. 项目经理放行（项目内最高权限，不受矩阵限制）
        if (isProjectManager(userId, projectId)) {
            return true;
        }
        // 3. 取用户在该项目的角色编码
        List<String> roleCodes = projectMemberMapper.selectRoleCodes(projectId, userId);
        if (roleCodes.isEmpty()) {
            return false;
        }
        // 4. roleCode -> roleId
        List<Long> roleIds = projectRoleMapper.selectListByProjectIdAndRoleCodes(projectId, roleCodes).stream()
                .map(PmsProjectRoleDO::getRoleId)
                .collect(Collectors.toList());
        if (roleIds.isEmpty()) {
            return false;
        }
        // 5. 任一角色 allowed=1 即放行（多角色取并集，即最大权限）
        return projectPermissionMapper.existsAllowed(projectId, roleIds, permKey);
    }

    @Override
    public boolean canCurrentUser(Long projectId, String permKey) {
        return can(SecurityFrameworkUtils.getLoginUserId(), projectId, permKey);
    }

    @Override
    public void checkPermission(Long projectId, String permKey) {
        if (!canCurrentUser(projectId, permKey)) {
            throw new ServiceException(ErrorCodeConstants.PROJECT_PERMISSION_DENIED);
        }
    }

    @Override
    public Set<String> allowedKeys(Long userId, Long projectId) {
        if (userId == null || projectId == null) {
            return Collections.emptySet();
        }
        // 超管 / PMO / 项目经理 —— 全部权限点
        if (hasGlobalRole() || isProjectManager(userId, projectId)) {
            return new HashSet<>(PmsPermKeyEnum.allKeys());
        }
        List<String> roleCodes = projectMemberMapper.selectRoleCodes(projectId, userId);
        if (roleCodes.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> roleIds = projectRoleMapper.selectListByProjectIdAndRoleCodes(projectId, roleCodes).stream()
                .map(PmsProjectRoleDO::getRoleId)
                .collect(Collectors.toList());
        if (roleIds.isEmpty()) {
            return Collections.emptySet();
        }
        return projectPermissionMapper.selectAllowedByRoleIds(projectId, roleIds).stream()
                .map(PmsProjectPermissionDO::getPermKey)
                .collect(Collectors.toSet());
    }

    // ==================== 角色管理 ====================

    @Override
    public List<PmsProjectRoleDO> getRoleList(Long projectId) {
        if (projectId == null) {
            return Collections.emptyList();
        }
        return projectRoleMapper.selectListByProjectId(projectId);
    }

    @Override
    public Long createRole(PmsProjectRoleDO entity) {
        requirePermissionManage(entity.getProjectId());
        validateRoleCode(entity.getRoleCode());
        // 项目内角色编码唯一
        if (projectRoleMapper.selectByProjectIdAndRoleCode(entity.getProjectId(), entity.getRoleCode()) != null) {
            throw new ServiceException(ErrorCodeConstants.PROJECT_ROLE_CODE_DUPLICATE);
        }
        if (entity.getIsSystem() == null) {
            entity.setIsSystem(false);
        }
        if (entity.getSortOrder() == null) {
            entity.setSortOrder(projectRoleMapper.selectListByProjectId(entity.getProjectId()).size() + 1);
        }
        projectRoleMapper.insert(entity);
        return entity.getRoleId();
    }

    @Override
    public void updateRole(PmsProjectRoleDO entity) {
        PmsProjectRoleDO exists = projectRoleMapper.selectById(entity.getRoleId());
        if (exists == null) {
            throw new ServiceException(ErrorCodeConstants.PROJECT_ROLE_NOT_EXISTS);
        }
        requirePermissionManage(exists.getProjectId());
        // roleCode 与成员表强关联，不允许变更；projectId 同理
        entity.setRoleCode(exists.getRoleCode());
        entity.setProjectId(exists.getProjectId());
        entity.setIsSystem(exists.getIsSystem());
        projectRoleMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long roleId) {
        PmsProjectRoleDO exists = projectRoleMapper.selectById(roleId);
        if (exists == null) {
            throw new ServiceException(ErrorCodeConstants.PROJECT_ROLE_NOT_EXISTS);
        }
        requirePermissionManage(exists.getProjectId());
        // 内置角色不允许删除
        if (Boolean.TRUE.equals(exists.getIsSystem())) {
            throw new ServiceException(ErrorCodeConstants.PROJECT_ROLE_SYSTEM_UNDELETABLE);
        }
        // 仍有成员使用该角色 -> 拒绝删除，避免成员失去角色变成"裸成员"
        final String roleCode = exists.getRoleCode();
        boolean inUse = projectMemberMapper.selectActiveListByProjectId(exists.getProjectId()).stream()
                .anyMatch(m -> Objects.equals(m.getRoleCode(), roleCode));
        if (inUse) {
            throw new ServiceException(ErrorCodeConstants.PROJECT_ROLE_IN_USE);
        }
        projectPermissionMapper.deleteByRoleId(exists.getProjectId(), roleId);
        projectRoleMapper.deleteById(roleId);
    }

    // ==================== 权限矩阵 ====================

    @Override
    public List<PmsProjectPermissionDO> getPermissionList(Long projectId) {
        if (projectId == null) {
            return Collections.emptyList();
        }
        return projectPermissionMapper.selectListByProjectId(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void savePermissions(Long projectId, List<PmsProjectPermissionDO> permissions) {
        requirePermissionManage(projectId);
        // 整体覆盖：先清空再插入，保证落库结果与前端矩阵完全一致
        projectPermissionMapper.deleteByProjectId(projectId);
        if (permissions == null || permissions.isEmpty()) {
            return;
        }
        Set<Long> validRoleIds = projectRoleMapper.selectListByProjectId(projectId).stream()
                .map(PmsProjectRoleDO::getRoleId)
                .collect(Collectors.toSet());
        for (PmsProjectPermissionDO item : permissions) {
            // 只落「允许」的记录，禁止项不落库（无记录即禁止），减少数据量
            if (!Boolean.TRUE.equals(item.getAllowed())) {
                continue;
            }
            if (!validRoleIds.contains(item.getRoleId())) {
                log.warn("[savePermissions] 跳过非法 roleId({}) projectId({})", item.getRoleId(), projectId);
                continue;
            }
            if (!PmsPermKeyEnum.isValidKey(item.getPermKey())) {
                log.warn("[savePermissions] 跳过非法 permKey({}) projectId({})", item.getPermKey(), projectId);
                continue;
            }
            PmsProjectPermissionDO insert = new PmsProjectPermissionDO();
            insert.setProjectId(projectId);
            insert.setRoleId(item.getRoleId());
            insert.setPermKey(item.getPermKey());
            insert.setAllowed(true);
            projectPermissionMapper.insert(insert);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initProjectPermission(Long projectId) {
        if (projectId == null) {
            return;
        }
        // 幂等：已初始化过就不再重复
        if (!projectRoleMapper.selectListByProjectId(projectId).isEmpty()) {
            return;
        }
        // 默认模板由平台统一维护在 tenant_id=0；读取时必须忽略租户过滤，
        // 否则任意业务租户调用初始化都会“成功但不生成角色”。
        List<PmsProjectPermissionTemplateDO> templates =
                TenantUtils.executeIgnore(projectPermissionTemplateMapper::selectAllOrdered);
        if (templates.isEmpty()) {
            log.warn("[initProjectPermission] 权限模板为空，项目({})未初始化角色", projectId);
            return;
        }
        // 1. 按 roleCode 建角色
        Map<String, Long> roleCodeToId = new LinkedHashMap<>();
        int sort = 1;
        for (PmsProjectPermissionTemplateDO tpl : templates) {
            if (roleCodeToId.containsKey(tpl.getRoleCode())) {
                continue;
            }
            PmsProjectRoleDO role = new PmsProjectRoleDO();
            role.setProjectId(projectId);
            role.setRoleCode(tpl.getRoleCode());
            role.setRoleName(tpl.getRoleName() != null ? tpl.getRoleName() : tpl.getRoleCode());
            role.setIsSystem(true);
            role.setSortOrder(sort++);
            projectRoleMapper.insert(role);
            roleCodeToId.put(tpl.getRoleCode(), role.getRoleId());
        }
        // 2. 按模板落权限矩阵
        for (PmsProjectPermissionTemplateDO tpl : templates) {
            if (!Boolean.TRUE.equals(tpl.getAllowed())) {
                continue;
            }
            Long roleId = roleCodeToId.get(tpl.getRoleCode());
            if (roleId == null) {
                continue;
            }
            PmsProjectPermissionDO perm = new PmsProjectPermissionDO();
            perm.setProjectId(projectId);
            perm.setRoleId(roleId);
            perm.setPermKey(tpl.getPermKey());
            perm.setAllowed(true);
            projectPermissionMapper.insert(perm);
        }
        log.info("[initProjectPermission] 项目({})初始化 {} 个角色", projectId, roleCodeToId.size());
    }

    // ==================== 内部方法 ====================

    /**
     * 是否拥有全局角色（超管 / PMO）
     */
    private boolean hasGlobalRole() {
        try {
            return securityFrameworkService.hasAnyRoles(ROLE_SUPER_ADMIN, ROLE_PMO);
        } catch (Exception e) {
            // 定时任务等无登录上下文的场景，hasAnyRoles 会抛异常，此时按无全局角色处理
            return false;
        }
    }

    /**
     * 是否为该项目的项目经理
     */
    private boolean isProjectManager(Long userId, Long projectId) {
        PmsProjectDO project = projectMapper.selectById(projectId);
        return project != null && Objects.equals(project.getProjectManagerId(), userId);
    }

    /**
     * 权限配置操作前置校验：仅超管 / PMO / 项目经理 / 拥有 permission_manage 的角色
     */
    private void requirePermissionManage(Long projectId) {
        if (projectId == null) {
            throw new ServiceException(ErrorCodeConstants.PROJECT_REQUIRED);
        }
        if (hasGlobalRole()) {
            return;
        }
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (isProjectManager(userId, projectId)) {
            return;
        }
        if (!can(userId, projectId, PmsPermKeyEnum.PERMISSION_MANAGE.getKey())) {
            throw new ServiceException(ErrorCodeConstants.PROJECT_PERMISSION_DENIED);
        }
    }

    /**
     * 角色编码格式校验：小写字母 + 数字 + 下划线，2-64 位
     */
    private void validateRoleCode(String roleCode) {
        if (roleCode == null || !roleCode.matches("^[a-z][a-z0-9_]{1,63}$")) {
            throw new ServiceException(ErrorCodeConstants.PROJECT_ROLE_CODE_INVALID);
        }
    }

    /**
     * 供其它模块复用：把成员的 roleCode 列表转成 roleId 列表（#7 文档权限会用）
     */
    @Override
    public List<Long> getMemberRoleIds(Long userId, Long projectId) {
        List<String> roleCodes = projectMemberMapper.selectRoleCodes(projectId, userId);
        if (roleCodes.isEmpty()) {
            return new ArrayList<>();
        }
        return projectRoleMapper.selectListByProjectIdAndRoleCodes(projectId, roleCodes).stream()
                .map(PmsProjectRoleDO::getRoleId)
                .collect(Collectors.toList());
    }

}
