package cn.iocoder.yudao.module.pms.service.report.impl;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.controller.admin.report.vo.*;
import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.tasklog.PmsTaskLogDO;
import cn.iocoder.yudao.module.pms.dal.mysql.task.TaskMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.tasklog.TaskLogMapper;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.pms.service.report.WeeklyReportService;
import cn.iocoder.yudao.module.pms.service.datascope.PmsDataScopeService;
import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 周报报表 Service 实现
 *
 * 与现有 TaskService 完全独立，自持有 5 区块查询逻辑、resolveBoardScope、resolveReportOwners 等。
 * 扁平化 5 区块为 List<WeeklyReportRowVO>，供前端表格 + Excel 导出共用。
 */
@Service
public class WeeklyReportServiceImpl implements WeeklyReportService {

    @Resource
    private TaskMapper taskMapper;
    @Resource
    private TaskLogMapper taskLogMapper;
    @Resource
    private DeptApi deptApi;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private PmsDataScopeService pmsDataScopeService;
    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 完成状态 → 中文标签 */
    private static final Map<String, String> COMPLETE_STATUS_LABEL = new HashMap<>();
    static {
        COMPLETE_STATUS_LABEL.put("not_started", "未开始");
        COMPLETE_STATUS_LABEL.put("pending_accept", "待接收");
        COMPLETE_STATUS_LABEL.put("in_progress", "进行中");
        COMPLETE_STATUS_LABEL.put("pending_review", "待审核");
        COMPLETE_STATUS_LABEL.put("completion_pending_review", "待审核");
        COMPLETE_STATUS_LABEL.put("completed", "已完成");
        COMPLETE_STATUS_LABEL.put("delayed", "已延期");
        COMPLETE_STATUS_LABEL.put("rejected", "已退回");
        COMPLETE_STATUS_LABEL.put("paused", "已暂停");
    }

    /** 操作类型 → 中文标签 */
    private static final Map<String, String> CHANGE_OP_LABEL = new HashMap<>();
    static {
        CHANGE_OP_LABEL.put("status_change", "状态变更");
        CHANGE_OP_LABEL.put("progress_update", "进度更新");
    }

    /** 区块分类常量 */
    private static final String CAT_LAST_WEEK_DUE = "📌上周应完成";
    private static final String CAT_THIS_WEEK_PLAN = "📋本周计划";
    private static final String CAT_FUTURE_PLAN = "🗓️未来计划";
    private static final String CAT_DELAYED = "⏰历史延期";
    private static final String CAT_CHANGES = "🔄上周动态";

    // ==================== 公共接口 ====================

    @Override
    public List<WeeklyReportRowVO> getWeeklyReportPage(WeeklyReportPageReqVO req) {
        LocalDate d = (req.getDate() == null) ? LocalDate.now() : req.getDate();
        List<WeeklyReportRowVO> all = buildAllRows(d, req.getDeptId(), req.getUserId());

        // 内存分页（数据量较小；如有需要可改为 SQL 分页）
        int pageNo = Math.max(1, req.getPageNo() == null ? 1 : req.getPageNo());
        int pageSize = Math.max(1, Math.min(500, req.getPageSize() == null ? 20 : req.getPageSize()));
        int from = Math.min((pageNo - 1) * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());
        return all.subList(from, to);
    }

    @Override
    public WeeklyReportDeptRespVO getVisibleDepts() {
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();

        // 从数据库取登录人真实部门（不依赖登录态 deptId——token 中可能缺失导致返回 null）
        Long myDeptId = null;
        try {
            AdminUserRespDTO u = loginUserId == null ? null : adminUserApi.getUser(loginUserId);
            if (u != null) {
                myDeptId = u.getDeptId();
            }
        } catch (Exception ignore) {
        }

        List<DeptRespDTO> depts = new ArrayList<>();
        boolean globalScope = false;
        try {
            globalScope = pmsDataScopeService != null
                    && pmsDataScopeService.hasGlobalDataScope(loginUserId);
        } catch (Exception ignore) {
        }
        if (globalScope) {
            // 超管 / 全局数据权限 → 全部启用部门
            depts.addAll(loadAllEnabledDepts());
        } else if (myDeptId != null) {
            // 本部门 + 所有下级部门
            Set<Long> ids = new LinkedHashSet<>();
            ids.add(myDeptId);
            try {
                List<DeptRespDTO> children = deptApi.getChildDeptList(myDeptId);
                if (children != null) {
                    for (DeptRespDTO d : children) {
                        if (d != null && d.getId() != null) {
                            ids.add(d.getId());
                        }
                    }
                }
            } catch (Exception ignore) {
            }
            List<DeptRespDTO> list = deptApi.getDeptList(ids);
            if (list != null) {
                depts.addAll(list);
            }
        } else {
            // 登录人无部门 → 回退为全部启用部门（避免下拉永远为空）
            depts.addAll(loadAllEnabledDepts());
        }

        // 过滤停用部门（CommonStatusEnum: 0=开启）
        depts = depts.stream()
                .filter(d -> d != null && d.getId() != null
                        && (d.getStatus() == null || d.getStatus() == 0))
                .collect(Collectors.toList());

        return WeeklyReportDeptRespVO.builder()
                .depts(depts)
                .myDeptId(myDeptId)
                .build();
    }

    /** 全部启用部门（JDBC 直查本租户；超管或无部门用户的兜底） */
    private List<DeptRespDTO> loadAllEnabledDepts() {
        if (jdbcTemplate == null) {
            return new ArrayList<>();
        }
        Long tenantId = null;
        try {
            tenantId = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId();
        } catch (Exception ignore) {
        }
        if (tenantId == null) {
            tenantId = 1L;
        }
        try {
            return jdbcTemplate.query(
                    "SELECT id, name, parent_id, leader_user_id, status FROM system_dept "
                            + "WHERE deleted = 0 AND status = 0 AND tenant_id = ?",
                    (rs, i) -> {
                        DeptRespDTO d = new DeptRespDTO();
                        d.setId(rs.getLong("id"));
                        d.setName(rs.getString("name"));
                        d.setParentId(rs.getLong("parent_id"));
                        long leader = rs.getLong("leader_user_id");
                        d.setLeaderUserId(rs.wasNull() ? null : leader);
                        d.setStatus(rs.getInt("status"));
                        return d;
                    },
                    tenantId);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Override
    public WeeklyReportExportResult exportWeeklyReport(LocalDate date, Long deptId, Long userId) {
        LocalDate d = (date == null) ? LocalDate.now() : date;
        List<WeeklyReportRowVO> all = buildAllRows(d, deptId, userId);

        List<WeeklyReportExportVO> rows = new ArrayList<>();
        for (WeeklyReportRowVO r : all) {
            rows.add(WeeklyReportExportVO.builder()
                    .category(r.getCategory())
                    .projectName(r.getProjectName())
                    .taskName(r.getTaskName())
                    .mainOwnerName(r.getMainOwnerName())
                    .planStartDate(formatDate(r.getPlanStartDate()))
                    .planEndDate(formatDate(r.getPlanEndDate()))
                    .actualCompleteDate(formatDate(r.getActualCompleteDate()))
                    .completeStatusLabel(r.getCompleteStatusLabel())
                    .progress(r.getProgress())
                    .overdueDays(r.getOverdueDays())
                    .operationTypeLabel(r.getOperationTypeLabel())
                    .beforeValue(r.getBeforeValue())
                    .afterValue(r.getAfterValue())
                    .operationTime(r.getOperationTime() == null ? "" : r.getOperationTime().format(DT_FMT))
                    .operatorName(r.getOperatorName())
                    .build());
        }

        String deptLabel = resolveDeptLabel(deptId);
        String fileName = "周报报表_" + deptLabel + "_" + d.format(DATE_FMT) + ".xlsx";

        return WeeklyReportExportResult.builder()
                .fileName(fileName)
                .data(rows)
                .build();
    }

    // ==================== 核心：构建所有行 ====================

    private List<WeeklyReportRowVO> buildAllRows(LocalDate d, Long deptId, Long userId) {
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        BoardScope scope = resolveBoardScope(loginUserId);
        List<Long> owners = resolveOwners(userId, deptId, scope);
        boolean isAll = (owners == null);

        LocalDate weekStart = d.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);
        LocalDate lastWeekStart = weekStart.minusDays(7);
        LocalDate lastWeekEnd = weekStart.minusDays(1);
        LocalDate today = LocalDate.now();

        // 5 区块原始数据
        // A 上周应完成
        List<PmsTaskDO> due = queryOwned(owners, isAll, w -> w
                .isNotNull(PmsTaskDO::getPlanEndDate)
                .between(PmsTaskDO::getPlanEndDate, lastWeekStart, lastWeekEnd));
        due.sort((a, b) -> {
            boolean ca = "completed".equals(a.getCompleteStatus());
            boolean cb = "completed".equals(b.getCompleteStatus());
            if (ca != cb) return ca ? -1 : 1;
            if (ca) {
                LocalDate da = a.getActualCompleteDate() == null ? LocalDate.MIN : a.getActualCompleteDate();
                LocalDate db = b.getActualCompleteDate() == null ? LocalDate.MIN : b.getActualCompleteDate();
                return db.compareTo(da);
            }
            LocalDate pa = a.getPlanEndDate() == null ? LocalDate.MAX : a.getPlanEndDate();
            LocalDate pb = b.getPlanEndDate() == null ? LocalDate.MAX : b.getPlanEndDate();
            return pa.compareTo(pb);
        });

        // B 本周计划
        List<String> notCompleted = List.of("not_started", "pending_accept", "in_progress",
                "completion_pending_review", "pending_review", "delayed", "rejected", "paused");
        List<PmsTaskDO> plan = queryOwned(owners, isAll, w -> w
                .in(PmsTaskDO::getCompleteStatus, notCompleted)
                .isNotNull(PmsTaskDO::getPlanStartDate)
                .le(PmsTaskDO::getPlanStartDate, weekEnd)
                .and(ww -> ww.isNull(PmsTaskDO::getPlanEndDate).or().ge(PmsTaskDO::getPlanEndDate, weekStart)));

        // E 未来计划
        List<PmsTaskDO> future = queryOwned(owners, isAll, w -> w
                .in(PmsTaskDO::getCompleteStatus, notCompleted)
                .isNotNull(PmsTaskDO::getPlanStartDate)
                .gt(PmsTaskDO::getPlanStartDate, weekEnd)
                .orderByAsc(PmsTaskDO::getPlanStartDate));

        // C 历史延期
        List<PmsTaskDO> delayed = queryOwned(owners, isAll, w -> w
                .in(PmsTaskDO::getCompleteStatus, notCompleted)
                .le(PmsTaskDO::getPlanEndDate, lastWeekEnd));
        List<DelayedPair> delayedPairs = new ArrayList<>();
        for (PmsTaskDO t : delayed) {
            long od = t.getPlanEndDate() == null ? 0 : ChronoUnit.DAYS.between(t.getPlanEndDate(), today);
            delayedPairs.add(new DelayedPair(t, od));
        }
        delayedPairs.sort((a, b) -> Long.compare(b.overdueDays, a.overdueDays));

        // D 上周动态
        List<ChangeTriple> changes = buildChangeLogRows(owners, isAll, lastWeekStart, lastWeekEnd);

        // 注入项目名
        fillProjectName(due);
        fillProjectName(plan);
        fillProjectName(future);
        delayedPairs.forEach(p -> fillProjectName(Collections.singletonList(p.task)));
        changes.forEach(c -> {
            if (c.taskProjectId != null) c.taskProjectName = resolveProjectName(c.taskProjectId);
        });

        // 收集 ownerId 用于昵称解析
        Set<Long> ownerIds = new HashSet<>();
        collectOwnerIds(ownerIds, due);
        collectOwnerIds(ownerIds, plan);
        collectOwnerIds(ownerIds, future);
        for (DelayedPair p : delayedPairs) {
            if (p.task != null && p.task.getMainOwnerId() != null) ownerIds.add(p.task.getMainOwnerId());
        }
        Map<Long, AdminUserRespDTO> userMap = ownerIds.isEmpty()
                ? Collections.emptyMap()
                : adminUserApi.getUserList(ownerIds).stream()
                    .collect(Collectors.toMap(AdminUserRespDTO::getId, u -> u, (a, b) -> a));

        // 扁平化为行 VO
        List<WeeklyReportRowVO> rows = new ArrayList<>();
        for (PmsTaskDO t : due) {
            rows.add(toTaskRow(CAT_LAST_WEEK_DUE, t, null, userMap));
        }
        for (PmsTaskDO t : plan) {
            rows.add(toTaskRow(CAT_THIS_WEEK_PLAN, t, null, userMap));
        }
        for (PmsTaskDO t : future) {
            rows.add(toTaskRow(CAT_FUTURE_PLAN, t, null, userMap));
        }
        for (DelayedPair p : delayedPairs) {
            rows.add(toTaskRow(CAT_DELAYED, p.task, p.overdueDays, userMap));
        }
        for (ChangeTriple c : changes) {
            rows.add(toChangeRow(c));
        }
        return rows;
    }

    // ==================== 数据查询辅助 ====================

    private List<PmsTaskDO> queryOwned(List<Long> owners, boolean isAll,
                                       Consumer<LambdaQueryWrapperX<PmsTaskDO>> extra) {
        LambdaQueryWrapperX<PmsTaskDO> w = new LambdaQueryWrapperX<>();
        if (!isAll && owners != null) {
            w.in(PmsTaskDO::getMainOwnerId, owners);
        }
        extra.accept(w);
        w.orderByDesc(PmsTaskDO::getUpdateTime);
        return taskMapper.selectList(w);
    }

    private void collectOwnerIds(Set<Long> out, List<PmsTaskDO> tasks) {
        if (tasks == null) return;
        for (PmsTaskDO t : tasks) {
            if (t != null && t.getMainOwnerId() != null) out.add(t.getMainOwnerId());
        }
    }

    private void fillProjectName(List<PmsTaskDO> tasks) {
        if (tasks == null) return;
        for (PmsTaskDO t : tasks) {
            if (t != null && t.getProjectId() != null) {
                t.setProjectName(resolveProjectName(t.getProjectId()));
            }
        }
    }

    private String resolveProjectName(Long projectId) {
        if (projectId == null) return "未知项目";
        if (jdbcTemplate != null) {
            try {
                List<String> names = jdbcTemplate.queryForList(
                        "SELECT project_name FROM pms_project WHERE project_id = ?", String.class, projectId);
                if (names != null && !names.isEmpty()) {
                    return names.get(0);
                }
            } catch (Exception ignore) {
            }
        }
        return "未知项目";
    }

    private WeeklyReportRowVO toTaskRow(String category, PmsTaskDO t, Long overdueDays,
                                         Map<Long, AdminUserRespDTO> userMap) {
        return WeeklyReportRowVO.builder()
                .category(category)
                .projectName(t.getProjectName())
                .taskName(t.getTaskName())
                .mainOwnerName(nicknameOf(userMap, t.getMainOwnerId()))
                .planStartDate(t.getPlanStartDate())
                .planEndDate(t.getPlanEndDate())
                .actualCompleteDate(t.getActualCompleteDate())
                .completeStatus(t.getCompleteStatus())
                .completeStatusLabel(labelOf(COMPLETE_STATUS_LABEL, t.getCompleteStatus()))
                .progress(t.getProgress())
                .overdueDays(overdueDays)
                .taskId(t.getTaskId())
                .build();
    }

    private WeeklyReportRowVO toChangeRow(ChangeTriple c) {
        return WeeklyReportRowVO.builder()
                .category(CAT_CHANGES)
                .projectName(c.taskProjectName)
                .taskName(c.taskName)
                .operationType(c.operationType)
                .operationTypeLabel(labelOf(CHANGE_OP_LABEL, c.operationType))
                .beforeValue(c.beforeValue)
                .afterValue(c.afterValue)
                .operationTime(c.operationTime)
                .operatorName(c.operatorName)
                .taskId(c.taskId)
                .build();
    }

    // ==================== 上周动态构建 ====================

    private List<ChangeTriple> buildChangeLogRows(List<Long> owners, boolean isAll,
                                                  LocalDate lastWeekStart, LocalDate lastWeekEnd) {
        LambdaQueryWrapperX<PmsTaskDO> tw = new LambdaQueryWrapperX<>();
        tw.select(PmsTaskDO::getTaskId, PmsTaskDO::getTaskName, PmsTaskDO::getProjectId,
                PmsTaskDO::getMainOwnerId, PmsTaskDO::getPlanEndDate);
        if (!isAll && owners != null) {
            tw.in(PmsTaskDO::getMainOwnerId, owners);
        }
        List<PmsTaskDO> ownedTasks = taskMapper.selectList(tw);
        if (ownedTasks.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, PmsTaskDO> taskMap = ownedTasks.stream()
                .collect(Collectors.toMap(PmsTaskDO::getTaskId, t -> t, (a, b) -> a));
        List<Long> taskIds = new ArrayList<>(taskMap.keySet());

        List<PmsTaskLogDO> logs = taskLogMapper.selectList(new LambdaQueryWrapperX<PmsTaskLogDO>()
                .in(PmsTaskLogDO::getTaskId, taskIds)
                .in(PmsTaskLogDO::getOperationType, List.of("status_change", "progress_update"))
                .between(PmsTaskLogDO::getOperationTime, lastWeekStart.atStartOfDay(), lastWeekEnd.atTime(23, 59, 59))
                .orderByAsc(PmsTaskLogDO::getOperationTime));

        List<ChangeTriple> result = new ArrayList<>();
        for (PmsTaskLogDO lg : logs) {
            PmsTaskDO t = taskMap.get(lg.getTaskId());
            if (t == null) continue;
            ChangeTriple ct = new ChangeTriple();
            ct.taskId = t.getTaskId();
            ct.taskName = t.getTaskName();
            ct.taskProjectId = t.getProjectId();
            ct.operationType = lg.getOperationType();
            ct.beforeValue = lg.getBeforeValue();
            ct.afterValue = lg.getAfterValue();
            ct.operationTime = lg.getOperationTime();
            ct.operatorName = lg.getOperatorName();
            result.add(ct);
        }
        return result;
    }

    private static class ChangeTriple {
        Long taskId;
        String taskName;
        Long taskProjectId;
        String taskProjectName;
        String operationType;
        String beforeValue;
        String afterValue;
        LocalDateTime operationTime;
        String operatorName;
    }

    private static class DelayedPair {
        PmsTaskDO task;
        long overdueDays;
        DelayedPair(PmsTaskDO task, long overdueDays) {
            this.task = task;
            this.overdueDays = overdueDays;
        }
    }

    // ==================== 人员/部门维度解析 ====================

    private List<Long> resolveOwners(Long userId, Long deptId, BoardScope scope) {
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        // 部门维度优先
        if (deptId != null) {
            List<Long> allDeptIds = new ArrayList<>();
            allDeptIds.add(deptId);
            try {
                List<DeptRespDTO> childDepts = deptApi.getChildDeptList(deptId);
                if (childDepts != null) {
                    for (DeptRespDTO d : childDepts) {
                        if (d != null && d.getId() != null && !allDeptIds.contains(d.getId())) {
                            allDeptIds.add(d.getId());
                        }
                    }
                }
            } catch (Exception ignore) {
            }
            List<AdminUserRespDTO> users = adminUserApi.getUserListByDeptIds(allDeptIds);
            if (users == null || users.isEmpty()) {
                return List.of(-1L);
            }
            return users.stream().map(AdminUserRespDTO::getId).collect(Collectors.toList());
        }
        // userId=0 视为"全公司"——权限由 Controller @PreAuthorize 控制
        if (userId != null && userId == 0L) {
            return null;
        }
        if (userId == null) {
            return List.of(loginUserId);
        }
        if (scope.allowedUserIds != null && !scope.allowedUserIds.contains(userId)) {
            return List.of(loginUserId);
        }
        return List.of(userId);
    }

    private BoardScope resolveBoardScope(Long loginUserId) {
        BoardScope scope = new BoardScope();
        if (loginUserId == null) return scope;
        scope.allowedUserIds = null;
        return scope;
    }

    private static class BoardScope {
        boolean isAdmin;
        boolean isLeader;
        List<Long> allowedUserIds;
    }

    // ==================== 部门名解析（用于文件名） ====================

    private String resolveDeptLabel(Long deptId) {
        if (deptId != null) {
            try {
                DeptRespDTO d = deptApi.getDept(deptId);
                if (d != null && StrUtil.isNotBlank(d.getName())) {
                    return d.getName();
                }
            } catch (Exception ignore) {
            }
        }
        try {
            Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
            if (loginUserId != null) {
                AdminUserRespDTO u = adminUserApi.getUser(loginUserId);
                if (u != null && u.getDeptId() != null) {
                    DeptRespDTO d = deptApi.getDept(u.getDeptId());
                    if (d != null && StrUtil.isNotBlank(d.getName())) {
                        return d.getName() + "-个人";
                    }
                }
            }
        } catch (Exception ignore) {
        }
        return "个人";
    }

    // ==================== 工具方法 ====================

    private static String nicknameOf(Map<Long, AdminUserRespDTO> userMap, Long userId) {
        if (userId == null) return "";
        AdminUserRespDTO u = userMap.get(userId);
        return u != null && u.getNickname() != null ? u.getNickname() : "";
    }

    private static String labelOf(Map<String, String> map, String value) {
        if (value == null) return "";
        String label = map.get(value);
        return label != null ? label : value;
    }

    private static String formatDate(LocalDate date) {
        return date != null ? DATE_FMT.format(date) : "";
    }
}
