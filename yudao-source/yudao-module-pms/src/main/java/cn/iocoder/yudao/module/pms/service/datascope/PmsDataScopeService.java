package cn.iocoder.yudao.module.pms.service.datascope;

import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;

import java.util.List;

/**
 * PMS 数据范围 Service（#9 BI 看板按部门数据权限）
 *
 * <p>设计原则：
 * <ul>
 *   <li>超管 / 拥有全局权限的角色 → 全部项目（返回 null 表示"不限制"，避免巨大 IN 列表）</li>
 *   <li>部门负责人 → 本部门 + 下级部门（递归取子部门）的项目</li>
 *   <li>普通用户 → 仅自己参与的项目（pms_project_member 里有自己的）</li>
 * </ul>
 *
 * <p>关于 yudao 数据权限框架（{@code DeptDataPermissionRule}）：
 * 它基于 SQL 拦截器（jsqlparser）在单表上拼 WHERE 条件。PMS 看板需求里：
 * <ol>
 *   <li>普通用户可见项目来自 {@code pms_project_member} 关联表，不是 {@code pms_project.dept_id}</li>
 *   <li>看板是前端聚合实现（前端调 list 接口全量拉取后渲染图表），没有"统计 SQL"可拦截</li>
 *   <li>需要暴露"可见部门树"给前端筛选器，yudao 框架不直接提供</li>
 * </ol>
 * 因此选择 <b>不复用 yudao DeptDataPermissionRule</b>，自写显式过滤服务，更贴合 PMS 现状。
 *
 * <p>注：复用 yudao {@link cn.iocoder.yudao.module.system.api.dept.DeptApi} 取部门数据。
 */
public interface PmsDataScopeService {

    /**
     * 获取当前登录用户可见的项目ID列表。
     *
     * <p>返回值语义：
     * <ul>
     *   <li>{@code null} → 表示不限制（超管 / 全局权限角色），调用方不应追加 IN 条件</li>
     *   <li>非空 List → 调用方按 IN 过滤；空 List 表示无任何可见项目</li>
     * </ul>
     *
     * @param userId 系统用户ID
     * @return 可见项目ID列表，null 表示不限制
     */
    List<Long> getVisibleProjectIds(Long userId);

    /**
     * 获取当前登录用户可见的部门ID数组。
     *
     * <p>用于 BI 看板的"按部门聚合统计"场景：统计接口拿到可见部门ID数组后，
     * 只统计这些部门下的项目/任务。
     *
     * <p>返回值语义同 {@link #getVisibleProjectIds}：
     * {@code null} 表示不限制，空数组表示无可见部门。
     *
     * @param userId 系统用户ID
     * @return 可见部门ID数组，null 表示不限制
     */
    Long[] getVisibleDeptIds(Long userId);

    /**
     * 获取前端部门筛选器用的部门树（只含自己有权看的部门）。
     *
     * <p>规则：
     * <ul>
     *   <li>超管 / 全局权限角色 → 返回完整部门树</li>
     *   <li>部门负责人 → 返回自己所在部门 + 全部下级部门的子树</li>
     *   <li>普通用户 → 返回自己所在部门（含下级），用于筛选器默认选中</li>
     * </ul>
     *
     * @param userId 系统用户ID
     * @return 部门列表（已拍平，前端可自行组树）
     */
    List<DeptRespDTO> getVisibleDeptTree(Long userId);

    /**
     * 判断当前用户是否拥有"全局数据权限"。
     * 即：超管 或 拥有 pms:dashboard:all 菜单权限的角色。
     *
     * @param userId 系统用户ID
     * @return true 表示可以看全部
     */
    boolean hasGlobalDataScope(Long userId);

}

