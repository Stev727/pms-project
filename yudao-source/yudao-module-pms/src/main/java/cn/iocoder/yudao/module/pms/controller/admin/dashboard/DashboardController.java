package cn.iocoder.yudao.module.pms.controller.admin.dashboard;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.dal.dataobject.project.PmsProjectDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.projectstage.PmsProjectStageDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import cn.iocoder.yudao.module.pms.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.projectstage.ProjectStageMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.task.TaskMapper;
import cn.iocoder.yudao.module.pms.service.datascope.PmsDataScopeService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * PMS BI 看板 Controller（#9 BI 看板按部门数据权限）
 *
 * <p>现状：PMS Dashboard 原本是纯前端实现，直接调 {@code getProjectList/getTaskList/getStageList}
 * 全量拉取后渲染图表。这些接口本身已做"自己参与"过滤，部门负责人看不到本部门其他同事的项目，
 * 满足不了 #9 需求。
 *
 * <p>本 Controller 新增 4 个 BI 专用端点，绕开既有 list 接口的过滤逻辑，直接走 Mapper 查，
 * 由 {@link PmsDataScopeService} 统一控制可见范围：
 * <ul>
 *   <li>{@code GET /pms/dashboard/depts}     → 当前用户可见部门树（前端筛选器用）</li>
 *   <li>{@code GET /pms/dashboard/projects}  → 按数据范围 + 部门筛选过滤后的项目</li>
 *   <li>{@code GET /pms/dashboard/tasks}     → 同上，过滤后的任务</li>
 *   <li>{@code GET /pms/dashboard/stages}    → 同上，过滤后的阶段</li>
 * </ul>
 *
 * <p>过滤策略：{@link PmsDataScopeService#getVisibleProjectIds(Long)} 返回 null 表示不限制，
 * 返回 List 表示按 ID IN 过滤；前端再传 deptId 做二次筛选。
 *
 * <p>注意：本 Controller 直接查 Mapper，progress 字段不填充（前端 dashboard 已能从任务聚合计算）。
 */
@Tag(name = "管理后台 - PMS BI 看板")
@RestController
@RequestMapping("/pms/dashboard")
@Validated
public class DashboardController {

    @Resource
    private PmsDataScopeService pmsDataScopeService;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private ProjectStageMapper projectStageMapper;

    @Resource
    private DeptApi deptApi;

    /**
     * 获取当前用户可见的部门树（前端筛选器用）。
     */
    @GetMapping("/depts")
    @Operation(summary = "获取当前用户可见部门树（BI 筛选器）")
    @PreAuthorize("@ss.hasPermission('pms:dashboard:query')")
    public CommonResult<List<DeptRespDTO>> getVisibleDepts() {
        Long userId = cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId();
        return success(pmsDataScopeService.getVisibleDeptTree(userId));
    }

    /**
     * 获取按数据范围过滤后的项目列表。
     *
     * @param deptId 可选，部门筛选。传值后只返回该部门及其下级部门的项目；
     *               不传则按当前用户整体可见范围返回。
     */
    @GetMapping("/projects")
    @Operation(summary = "获取 BI 看板可见项目列表")
    @Parameter(name = "deptId", description = "部门ID（含下级，可空）")
    @PreAuthorize("@ss.hasPermission('pms:dashboard:query')")
    public CommonResult<List<PmsProjectDO>> getDashboardProjects(
            @RequestParam(value = "deptId", required = false) Long deptId) {
        Long userId = cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId();
        List<Long> visibleProjectIds = pmsDataScopeService.getVisibleProjectIds(userId);

        // null 表示不限制（超管 / 全局权限）
        if (visibleProjectIds == null) {
            List<PmsProjectDO> projects = projectMapper.selectList(new LambdaQueryWrapperX<PmsProjectDO>()
                    .ne(PmsProjectDO::getProjectType, "standard_template"));
            return success(applyProjectDeptFilter(projects, deptId));
        }
        if (visibleProjectIds.isEmpty()) {
            return success(Collections.emptyList());
        }
        List<PmsProjectDO> projects = projectMapper.selectList(new LambdaQueryWrapperX<PmsProjectDO>()
                .in(PmsProjectDO::getProjectId, visibleProjectIds));
        return success(applyProjectDeptFilter(projects, deptId));
    }

    /**
     * 获取按数据范围过滤后的任务列表。
     */
    @GetMapping("/tasks")
    @Operation(summary = "获取 BI 看板可见任务列表")
    @Parameter(name = "deptId", description = "部门ID（含下级，可空）")
    @PreAuthorize("@ss.hasPermission('pms:dashboard:query')")
    public CommonResult<List<PmsTaskDO>> getDashboardTasks(
            @RequestParam(value = "deptId", required = false) Long deptId) {
        Long userId = cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId();
        List<Long> visibleProjectIds = pmsDataScopeService.getVisibleProjectIds(userId);

        if (visibleProjectIds == null) {
            List<PmsTaskDO> tasks = taskMapper.selectList(new LambdaQueryWrapperX<PmsTaskDO>()
                    .orderByAsc(PmsTaskDO::getSortOrder));
            return success(applyTaskDeptFilter(tasks, deptId));
        }
        if (visibleProjectIds.isEmpty()) {
            return success(Collections.emptyList());
        }
        List<PmsTaskDO> tasks = taskMapper.selectList(new LambdaQueryWrapperX<PmsTaskDO>()
                .in(PmsTaskDO::getProjectId, visibleProjectIds)
                .orderByAsc(PmsTaskDO::getSortOrder));
        return success(applyTaskDeptFilter(tasks, deptId));
    }

    /**
     * 获取按数据范围过滤后的阶段列表。
     */
    @GetMapping("/stages")
    @Operation(summary = "获取 BI 看板可见阶段列表")
    @Parameter(name = "deptId", description = "部门ID（含下级，可空）")
    @PreAuthorize("@ss.hasPermission('pms:dashboard:query')")
    public CommonResult<List<PmsProjectStageDO>> getDashboardStages(
            @RequestParam(value = "deptId", required = false) Long deptId) {
        Long userId = cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId();
        List<Long> visibleProjectIds = pmsDataScopeService.getVisibleProjectIds(userId);

        if (visibleProjectIds == null) {
            return success(projectStageMapper.selectList(new LambdaQueryWrapperX<PmsProjectStageDO>()));
        }
        if (visibleProjectIds.isEmpty()) {
            return success(Collections.emptyList());
        }
        List<PmsProjectStageDO> stages = projectStageMapper.selectList(
                new LambdaQueryWrapperX<PmsProjectStageDO>()
                        .in(PmsProjectStageDO::getProjectId, visibleProjectIds));
        return success(stages);
    }

    // ==================================================================
    // 私有：部门筛选二次过滤
    // ==================================================================

    /**
     * 项目按部门筛选：deptId 传入后，把该部门及其下级部门的项目筛选出来。
     */
    private List<PmsProjectDO> applyProjectDeptFilter(List<PmsProjectDO> projects, Long deptId) {
        if (deptId == null) {
            return projects == null ? Collections.emptyList() : projects;
        }
        if (projects == null || projects.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> deptIdSet = new HashSet<>();
        deptIdSet.add(deptId);
        collectChildDeptIds(deptId, deptIdSet);
        return projects.stream()
                .filter(p -> p.getDeptId() != null && deptIdSet.contains(p.getDeptId()))
                .collect(Collectors.toList());
    }

    /**
     * 任务按部门筛选：任务表 pms_task.dept_id 是责任部门，按它筛。
     */
    private List<PmsTaskDO> applyTaskDeptFilter(List<PmsTaskDO> tasks, Long deptId) {
        if (deptId == null) {
            return tasks == null ? Collections.emptyList() : tasks;
        }
        if (tasks == null || tasks.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> deptIdSet = new HashSet<>();
        deptIdSet.add(deptId);
        collectChildDeptIds(deptId, deptIdSet);
        return tasks.stream()
                .filter(t -> t.getDeptId() != null && deptIdSet.contains(t.getDeptId()))
                .collect(Collectors.toList());
    }

    /**
     * 递归取某部门的所有后代部门ID（含自身）。
     * 复用 DeptApi.getChildDeptList 单层查询，做 BFS 递归。
     */
    private void collectChildDeptIds(Long rootDeptId, Set<Long> collector) {
        if (rootDeptId == null) {
            return;
        }
        List<DeptRespDTO> children = deptApi.getChildDeptList(rootDeptId);
        if (children == null || children.isEmpty()) {
            return;
        }
        for (DeptRespDTO child : children) {
            if (collector.add(child.getId())) {
                collectChildDeptIds(child.getId(), collector);
            }
        }
    }

}

