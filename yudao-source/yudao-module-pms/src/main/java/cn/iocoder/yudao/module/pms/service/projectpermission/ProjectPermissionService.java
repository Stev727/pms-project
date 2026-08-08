package cn.iocoder.yudao.module.pms.service.projectpermission;

import cn.iocoder.yudao.module.pms.dal.dataobject.projectpermission.PmsProjectPermissionDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.projectrole.PmsProjectRoleDO;

import java.util.List;
import java.util.Set;

/**
 * 项目级权限 Service 接口（#2 权限分级的核心）
 *
 * 与菜单级权限（@PreAuthorize("@ss.hasPermission('pms:xxx:create')")）正交叠加：
 * 菜单级决定「能不能进功能」，本 Service 决定「在某个项目里能干什么」。
 */
public interface ProjectPermissionService {

    // ==================== 权限判定 ====================

    /**
     * 判定某用户在某项目是否拥有某权限点
     *
     * 放行顺序：超管 > 项目经理 > 角色矩阵命中
     *
     * @param userId    用户ID
     * @param projectId 项目ID
     * @param permKey   权限点，取值见 PmsPermKeyEnum
     * @return true 允许
     */
    boolean can(Long userId, Long projectId, String permKey);

    /**
     * 判定当前登录用户在某项目是否拥有某权限点
     */
    boolean canCurrentUser(Long projectId, String permKey);

    /**
     * 校验当前登录用户权限，无权限直接抛 ServiceException
     */
    void checkPermission(Long projectId, String permKey);

    /**
     * 批量取某用户在某项目的全部允许权限点（前端一次性拉取，避免 N 次请求）
     */
    Set<String> allowedKeys(Long userId, Long projectId);

    /**
     * 取某用户在某项目的角色ID列表。
     * 供 #7 文档权限（pms_document_permission 按 roleId 授权）复用。
     */
    List<Long> getMemberRoleIds(Long userId, Long projectId);

    // ==================== 角色管理 ====================

    /**
     * 查询某项目的角色列表
     */
    List<PmsProjectRoleDO> getRoleList(Long projectId);

    /**
     * 创建项目角色
     */
    Long createRole(PmsProjectRoleDO entity);

    /**
     * 更新项目角色（仅可改名称/排序/备注，roleCode 不允许变更）
     */
    void updateRole(PmsProjectRoleDO entity);

    /**
     * 删除项目角色。内置角色 / 仍有成员占用的角色不允许删除
     */
    void deleteRole(Long roleId);

    // ==================== 权限矩阵 ====================

    /**
     * 查询某项目完整权限矩阵
     */
    List<PmsProjectPermissionDO> getPermissionList(Long projectId);

    /**
     * 整体覆盖保存某项目的权限矩阵（先删后插，保证与前端所见一致）
     */
    void savePermissions(Long projectId, List<PmsProjectPermissionDO> permissions);

    /**
     * 按默认模板初始化某项目的角色 + 权限矩阵。
     * 幂等：已存在角色的项目不会重复初始化。
     * 供项目创建流程（ProjectServiceImpl.createProjectBundle）调用。
     */
    void initProjectPermission(Long projectId);

}

