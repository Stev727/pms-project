package cn.iocoder.yudao.module.pms.service.datascope.impl;

import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.dal.dataobject.project.PmsProjectDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.projectmember.PmsProjectMemberDO;
import cn.iocoder.yudao.module.pms.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.projectmember.ProjectMemberMapper;
import cn.iocoder.yudao.module.pms.service.datascope.PmsDataScopeService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * PMS 数据范围 Service 实现（#9 BI 看板按部门数据权限）
 *
 * <p>实现要点：
 * <ul>
 *   <li>判断"部门负责人"用 {@link DeptRespDTO#getLeaderUserId()} 字段</li>
 *   <li>取子部门用 {@link DeptApi#getChildDeptList(Long)} 仅返回直接子部门，需自己写递归</li>
 *   <li>普通用户可见项目走 {@code pms_project_member} 关联表查</li>
 *   <li>用 SecurityFrameworkService 判定超管与全局权限角色</li>
 * </ul>
 *
 * <p>性能考虑：deptApi 单次 RPC 拉全部门列表后内存过滤组树，避免递归 RPC 风暴；
 * 可见项目ID列表上限不会太大（一个用户参与的项目数量有限），直接 IN 查询。
 */
@Service
public class PmsDataScopeServiceImpl implements PmsDataScopeService {

    /**
     * 全局数据权限角色编码。运营可给指定角色挂这个菜单权限点，让其看全部 BI 数据。
     */
    private static final String GLOBAL_DASHBOARD_PERMISSION = "pms:dashboard:all";

    /**
     * 部门树遍历保护上限，防止脏数据造成死循环。
     */
    private static final int DEPT_TREE_GUARD = 1000;

    @Resource
    private DeptApi deptApi;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private ProjectMemberMapper projectMemberMapper;

    @Autowired(required = false)
    private SecurityFrameworkService securityFrameworkService;

    // ==================================================================
    // 可见项目
    // ==================================================================

    @Override
    public List<Long> getVisibleProjectIds(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        // 超管 / 全局权限角色 → null（不限制）
        if (hasGlobalDataScope(userId)) {
            return null;
        }

        // 部门负责人 → 本部门 + 下级部门的所有项目
        Set<Long> deptIds = collectManagedDeptIds(userId);
        if (!deptIds.isEmpty()) {
            List<PmsProjectDO> projects = projectMapper.selectList(new LambdaQueryWrapperX<PmsProjectDO>()
                    .in(PmsProjectDO::getDeptId, deptIds));
            if (projects == null || projects.isEmpty()) {
                return Collections.emptyList();
            }
            return projects.stream()
                    .map(PmsProjectDO::getProjectId)
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());
        }

        // 普通用户 → 仅自己参与的项目（pms_project_member）
        List<PmsProjectMemberDO> memberships = projectMemberMapper.selectList(
                new LambdaQueryWrapperX<PmsProjectMemberDO>()
                        .eq(PmsProjectMemberDO::getUserId, userId));
        if (memberships == null || memberships.isEmpty()) {
            return Collections.emptyList();
        }
        return memberships.stream()
                .map(PmsProjectMemberDO::getProjectId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }

    // ==================================================================
    // 可见部门
    // ==================================================================

    @Override
    public Long[] getVisibleDeptIds(Long userId) {
        if (userId == null) {
            return new Long[0];
        }
        // 超管 / 全局权限角色 → null（不限制）
        if (hasGlobalDataScope(userId)) {
            return null;
        }
        // 部门负责人 → 本部门 + 下级部门
        Set<Long> managed = collectManagedDeptIds(userId);
        if (!managed.isEmpty()) {
            return managed.toArray(new Long[0]);
        }
        // 普通用户 → 自己所在部门 + 下级部门（用于筛选器候选）
        Long userDeptId = SecurityFrameworkUtils.getLoginUserDeptId();
        if (userDeptId == null) {
            return new Long[0];
        }
        Set<Long> withChildren = new LinkedHashSet<>();
        withChildren.add(userDeptId);
        collectAllChildDeptIds(userDeptId, withChildren);
        return withChildren.toArray(new Long[0]);
    }

    @Override
    public List<DeptRespDTO> getVisibleDeptTree(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        // 超管 / 全局权限角色 → 全部部门树
        if (hasGlobalDataScope(userId)) {
            return loadAllDeptList();
        }
        // 部门负责人 → 本部门 + 下级部门
        Set<Long> managed = collectManagedDeptIds(userId);
        if (!managed.isEmpty()) {
            Set<Long> withChildren = new LinkedHashSet<>();
            for (Long id : managed) {
                withChildren.add(id);
                collectAllChildDeptIds(id, withChildren);
            }
            return deptApi.getDeptList(withChildren);
        }
        // 普通用户 → 自己所在部门 + 下级部门
        Long userDeptId = SecurityFrameworkUtils.getLoginUserDeptId();
        if (userDeptId == null) {
            return Collections.emptyList();
        }
        Set<Long> withChildren = new LinkedHashSet<>();
        withChildren.add(userDeptId);
        collectAllChildDeptIds(userDeptId, withChildren);
        return deptApi.getDeptList(withChildren);
    }

    @Override
    public boolean hasGlobalDataScope(Long userId) {
        if (userId == null || securityFrameworkService == null) {
            return false;
        }
        // 超管
        if (securityFrameworkService.hasAnyRoles("super_admin")) {
            return true;
        }
        // 拥有 pms:dashboard:all 菜单权限的角色 → 全局
        return securityFrameworkService.hasPermission(GLOBAL_DASHBOARD_PERMISSION);
    }

    // ==================================================================
    // 私有方法
    // ==================================================================

    /**
     * 取当前用户作为负责人的所有部门ID（不含下级，下级单独递归取）。
     *
     * <p>实现：拉全部部门列表，过滤 leaderUserId = userId。
     * yudao 部门数量通常不会太多（几十到几百），全量拉取后内存过滤即可。
     */
    private Set<Long> collectManagedDeptIds(Long userId) {
        List<DeptRespDTO> allDepts = loadAllDeptList();
        if (allDepts == null || allDepts.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Set<Long> managed = new LinkedHashSet<>();
        for (DeptRespDTO dept : allDepts) {
            if (dept.getLeaderUserId() != null && Objects.equals(dept.getLeaderUserId(), userId)) {
                managed.add(dept.getId());
            }
        }
        return managed;
    }

    /**
     * 递归取某部门的所有后代部门ID（不含自身）。
     *
     * <p>实现思路：拉全部部门，按 parentId 在内存组树，BFS 遍历。
     * 这样可以一次性加载、避免 N 次 RPC（{@link DeptApi#getChildDeptList} 只返回直接子部门）。
     */
    private void collectAllChildDeptIds(Long rootDeptId, Set<Long> collector) {
        if (rootDeptId == null) {
            return;
        }
        List<DeptRespDTO> allDepts = loadAllDeptList();
        if (allDepts == null || allDepts.isEmpty()) {
            return;
        }
        // 按 parentId 分组（内存）
        java.util.Map<Long, List<Long>> childrenMap = new java.util.HashMap<>();
        for (DeptRespDTO dept : allDepts) {
            if (dept.getParentId() == null) {
                continue;
            }
            childrenMap.computeIfAbsent(dept.getParentId(), k -> new ArrayList<>()).add(dept.getId());
        }
        // BFS 遍历收集所有后代
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootDeptId);
        int guard = 0;
        while (!queue.isEmpty() && guard++ < DEPT_TREE_GUARD) {
            Long current = queue.poll();
            List<Long> children = childrenMap.get(current);
            if (children == null || children.isEmpty()) {
                continue;
            }
            for (Long childId : children) {
                if (collector.add(childId)) {
                    queue.add(childId);
                }
            }
        }
    }

    /**
     * 拉取系统所有部门列表（用于组树 / 找负责人）。
     * 这里没有全量接口，使用空 IDs 列表 + 各级子部门递归拉取的兜底方式。
     *
     * <p>实际 yudao 后台部门通常会有几百个以内，分批拉取后内存合并即可。
     * 若部署规模较大，可改为 SQL 直查 system_dept 表，避免 RPC 风暴。
     */
    private List<DeptRespDTO> loadAllDeptList() {
        // 先取根部门（id=1）的子部门，再递归取下级
        List<DeptRespDTO> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(1L); // yudao 根部门固定为 1
        int guard = 0;
        while (!queue.isEmpty() && guard++ < DEPT_TREE_GUARD) {
            Long current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            List<DeptRespDTO> children = deptApi.getChildDeptList(current);
            if (children == null || children.isEmpty()) {
                continue;
            }
            for (DeptRespDTO child : children) {
                result.add(child);
                queue.add(child.getId());
            }
        }
        return result;
    }

}

