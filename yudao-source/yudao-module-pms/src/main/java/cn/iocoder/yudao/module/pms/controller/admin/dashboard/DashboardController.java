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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import cn.iocoder.yudao.module.pms.controller.admin.dashboard.vo.DeptStatVO;
import cn.iocoder.yudao.module.pms.dal.dataobject.projectmember.PmsProjectMemberDO;
import cn.iocoder.yudao.module.pms.dal.mysql.projectmember.ProjectMemberMapper;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;

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
    @Resource
    private ProjectMemberMapper projectMemberMapper;
    @Resource
    private AdminUserApi adminUserApi;

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

    /**
     * 部门协作分析：按部门聚合成员参与 + 任务完成率/延期占比。
     * <p>数据口径：
     * <ul>
     *   <li>参与部门/成员数：项目成员(pms_project_member, active) → 成员 system_users.dept_id</li>
     *   <li>任务完成/延期：任务 main_owner_id → 负责人 system_users.dept_id</li>
     * </ul>
     * deptId 传入时仅返回该部门及其子部门的行。
     */
    @GetMapping("/dept-stats")
    @Operation(summary = "部门协作分析：按部门聚合任务完成率/延期占比")
    @Parameter(name = "deptId", description = "部门ID（含下级，只返回该部门及其子部门的数据，可空）")
    @Parameter(name = "projectName", description = "项目名称模糊过滤，可空")
    @PreAuthorize("@ss.hasPermission('pms:dept-analysis:query')")
    public CommonResult<List<DeptStatVO>> getDeptStats(
            @RequestParam(value = "deptId", required = false) Long deptId,
            @RequestParam(value = "projectName", required = false) String projectName) {
        Long userId = cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId();
        List<Long> visibleProjectIds = pmsDataScopeService.getVisibleProjectIds(userId);

        // 1. 可见项目（数据范围 + 排除模板 + 项目名称模糊）
        // 注意：LambdaQueryWrapperX 的 .ne() 返回父类 LambdaQueryWrapper，不能链式赋值，
        //       必须拆成独立语句调用（wrapper 内部可变，方法返回值可丢弃）。
        LambdaQueryWrapperX<PmsProjectDO> pw = new LambdaQueryWrapperX<>();
        pw.ne(PmsProjectDO::getProjectType, "standard_template");
        if (visibleProjectIds != null) {
            if (visibleProjectIds.isEmpty()) {
                return success(Collections.emptyList());
            }
            pw.in(PmsProjectDO::getProjectId, visibleProjectIds);
        }
        if (projectName != null && !projectName.trim().isEmpty()) {
            pw.like(PmsProjectDO::getProjectName, projectName.trim());
        }
        List<PmsProjectDO> projects = projectMapper.selectList(pw);
        if (projects.isEmpty()) {
            return success(Collections.emptyList());
        }
        List<Long> projectIds = projects.stream().map(PmsProjectDO::getProjectId).collect(Collectors.toList());

        // 2. 项目成员（active）→ 部门参与
        List<PmsProjectMemberDO> members = projectMemberMapper.selectList(
                new LambdaQueryWrapperX<PmsProjectMemberDO>().in(PmsProjectMemberDO::getProjectId, projectIds));
        members = members.stream()
                .filter(m -> m.getStatus() == null || "active".equals(m.getStatus()))
                .collect(Collectors.toList());

        // 3. 任务 → 负责人 → 部门（完成/延期）
        List<PmsTaskDO> tasks = taskMapper.selectList(
                new LambdaQueryWrapperX<PmsTaskDO>().in(PmsTaskDO::getProjectId, projectIds));

        // 4. 批量解析 user → deptId
        Set<Long> userIds = new HashSet<>();
        for (PmsProjectMemberDO m : members) {
            if (m.getUserId() != null) userIds.add(m.getUserId());
        }
        for (PmsTaskDO t : tasks) {
            if (t.getMainOwnerId() != null) userIds.add(t.getMainOwnerId());
        }
        Map<Long, Long> userDeptMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<AdminUserRespDTO> users = adminUserApi.getUserList(userIds);
            for (AdminUserRespDTO u : users) {
                userDeptMap.put(u.getId(), u.getDeptId());
            }
        }
        // 部门名
        Set<Long> deptIds = new HashSet<>();
        for (Long d : userDeptMap.values()) {
            if (d != null) deptIds.add(d);
        }
        Map<Long, DeptRespDTO> deptMap = deptIds.isEmpty()
                ? Collections.emptyMap() : deptApi.getDeptMap(deptIds);

        // 5. 聚合：部门 → 参与项目集合 / 成员集合 / 任务[总,完成,延期]
        Map<Long, Set<Long>> deptProjects = new HashMap<>();
        Map<Long, Set<Long>> deptMembers = new HashMap<>();
        for (PmsProjectMemberDO m : members) {
            Long did = userDeptMap.get(m.getUserId());
            if (did == null) did = 0L;
            deptProjects.computeIfAbsent(did, k -> new HashSet<>()).add(m.getProjectId());
            deptMembers.computeIfAbsent(did, k -> new HashSet<>()).add(m.getUserId());
        }
        Map<Long, int[]> deptTask = new HashMap<>();
        java.time.LocalDate today = java.time.LocalDate.now();
        for (PmsTaskDO t : tasks) {
            Long did = userDeptMap.get(t.getMainOwnerId());
            if (did == null) did = 0L;
            int[] arr = deptTask.computeIfAbsent(did, k -> new int[3]);
            arr[0]++;
            if ("completed".equals(t.getCompleteStatus())) arr[1]++;
            if (t.getPlanEndDate() != null && t.getPlanEndDate().isBefore(today)
                    && !"completed".equals(t.getCompleteStatus())
                    && (t.getProgress() == null || t.getProgress() < 100)) {
                arr[2]++;
            }
        }
        int totalDelayed = 0;
        for (int[] a : deptTask.values()) totalDelayed += a[2];

        // 6. 组装 VO（deptId 过滤：只保留选中部门及其子部门）
        Set<Long> allowedDepts = null;
        if (deptId != null) {
            allowedDepts = new HashSet<>();
            allowedDepts.add(deptId);
            collectChildDeptIds(deptId, allowedDepts);
        }
        Set<Long> allDepts = new HashSet<>();
        allDepts.addAll(deptProjects.keySet());
        allDepts.addAll(deptTask.keySet());
        List<DeptStatVO> result = new ArrayList<>();
        for (Long did : allDepts) {
            if (allowedDepts != null && !allowedDepts.contains(did)) continue;
            DeptStatVO vo = new DeptStatVO();
            vo.setDeptId(did);
            DeptRespDTO dept = deptMap.get(did);
            vo.setDeptName(did == 0L ? "未分配部门" : (dept == null ? ("部门#" + did) : dept.getName()));
            vo.setProjectCount(deptProjects.getOrDefault(did, Collections.emptySet()).size());
            vo.setMemberCount(deptMembers.getOrDefault(did, Collections.emptySet()).size());
            int[] arr = deptTask.getOrDefault(did, new int[3]);
            vo.setTaskTotal(arr[0]);
            vo.setTaskCompleted(arr[1]);
            vo.setTaskDelayed(arr[2]);
            vo.setCompletionRate(arr[0] == 0 ? 0.0 : Math.round(arr[1] * 1000.0 / arr[0]) / 10.0);
            vo.setDelayRate(totalDelayed == 0 ? 0.0 : Math.round(arr[2] * 1000.0 / totalDelayed) / 10.0);
            result.add(vo);
        }
        result.sort((a, b) -> Integer.compare(b.getTaskTotal(), a.getTaskTotal()));
        return success(result);
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

