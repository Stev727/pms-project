package cn.iocoder.yudao.module.pms.service.overdueremind.impl;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.pms.dal.dataobject.notifyrule.PmsNotifyRuleDO;
import cn.iocoder.yudao.module.pms.dal.mysql.notifyrule.NotifyRuleMapper;
import cn.iocoder.yudao.module.pms.service.overdueremind.OverdueRemindService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 超期提请 Service 实现（迭代14）
 */
@Slf4j
@Service
public class OverdueRemindServiceImpl implements OverdueRemindService {

    /**
     * 超期提请汇总规则的触发事件（与 DingTalkNotifyServiceImpl#processOverdueSummary 约定一致）
     */
    public static final String TRIGGER_EVENT = "overdue_summary";

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private NotifyRuleMapper notifyRuleMapper;

    @Override
    public Map<String, Object> getOverdueTaskPage(Long deptId, Long userId, Long projectId,
                                                  Integer pageNo, Integer pageSize) {
        int no = (pageNo == null || pageNo < 1) ? 1 : pageNo;
        int size = (pageSize == null || pageSize < 1) ? 10 : (Math.min(pageSize, 100));

        StringBuilder where = new StringBuilder(
                " WHERE t.deleted = 0" +
                // 未完成：排除已完成/完成待审核（completed/completion_pending_review）；done/cancelled/closed 兜底
                " AND t.complete_status NOT IN ('done', 'cancelled', 'closed', 'completed', 'completion_pending_review')" +
                " AND t.plan_end_date IS NOT NULL" +
                " AND t.plan_end_date < CURDATE()" +
                // 项目行不存在（日常任务）保留；项目已软删或模板项目排除
                " AND (p.project_id IS NULL OR (p.deleted = 0 AND p.project_type != 'standard_template'))");
        List<Object> params = new ArrayList<>();
        if (userId != null) {
            where.append(" AND t.main_owner_id = ?");
            params.add(userId);
        }
        if (deptId != null) {
            where.append(" AND u.dept_id = ?");
            params.add(deptId);
        }
        if (projectId != null) {
            where.append(" AND t.project_id = ?");
            params.add(projectId);
        }

        String from = " FROM pms_task t" +
                " LEFT JOIN pms_project p ON p.project_id = t.project_id" +
                " LEFT JOIN system_users u ON u.id = t.main_owner_id AND u.deleted = 0" +
                " LEFT JOIN system_dept d ON d.id = u.dept_id AND d.deleted = 0";

        // 1. 总数
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*)" + from + where, Long.class, params.toArray());
        long totalLong = total == null ? 0 : total;

        // 2. 当前页
        String select = "SELECT t.task_id AS taskId, t.task_name AS taskName," +
                " t.project_id AS projectId, p.project_name AS projectName," +
                " t.main_owner_id AS mainOwnerId, u.nickname AS mainOwnerName," +
                " u.dept_id AS deptId, d.name AS deptName," +
                " t.plan_end_date AS planEndDate," +
                " DATEDIFF(CURDATE(), t.plan_end_date) AS overdueDays," +
                " t.complete_status AS completeStatus";
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add((no - 1) * size);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                select + from + where + " ORDER BY overdueDays DESC LIMIT ? OFFSET ?",
                pageParams.toArray());

        // 3. 汇总统计（受筛选影响）
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", totalLong);
        stats.put("ownerCount", jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT t.main_owner_id)" + from + where,
                Long.class, params.toArray()));
        stats.put("projectCount", jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT t.project_id)" + from + where,
                Long.class, params.toArray()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", rows);
        result.put("total", totalLong);
        result.put("stats", stats);
        return result;
    }

    @Override
    public List<Map<String, Object>> getMonitorConfigs() {
        List<PmsNotifyRuleDO> rules = notifyRuleMapper.selectList(
                PmsNotifyRuleDO::getTriggerEvent, TRIGGER_EVENT);
        List<Map<String, Object>> result = new ArrayList<>();
        if (rules == null) {
            return result;
        }
        for (PmsNotifyRuleDO rule : rules) {
            List<Long> notifyUsers = parseUserIds(rule.getNotifyTarget());
            List<Long> ownerIds = parseUserIds(rule.getTriggerCondition());
            if (notifyUsers.isEmpty() || ownerIds.isEmpty()) {
                continue; // 无效配置（含旧版全局规则）不展示
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("notifyUserId", notifyUsers.get(0));
            item.put("ownerIds", ownerIds);
            result.add(item);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMonitorConfigs(List<Map<String, Object>> configs) {
        // 1. 全删旧 overdue_summary 规则（全量替换）
        List<PmsNotifyRuleDO> oldRules = notifyRuleMapper.selectList(
                PmsNotifyRuleDO::getTriggerEvent, TRIGGER_EVENT);
        if (oldRules != null) {
            for (PmsNotifyRuleDO old : oldRules) {
                notifyRuleMapper.deleteById(old.getRuleId());
            }
        }
        // 2. 插入新监控配置：每条配置一条规则（通知人单 + 责任人多）
        if (configs == null) {
            return;
        }
        for (Map<String, Object> config : configs) {
            Long notifyUserId = toLong(config == null ? null : config.get("notifyUserId"));
            List<Long> ownerIds = toLongList(config == null ? null : config.get("ownerIds"));
            if (notifyUserId == null || ownerIds.isEmpty()) {
                log.warn("[OverdueRemind] 跳过无效监控配置: notifyUserId={}, ownerIds={}", notifyUserId, ownerIds);
                continue;
            }
            PmsNotifyRuleDO rule = new PmsNotifyRuleDO();
            rule.setScopeType("global");
            rule.setRuleName("超期监控-" + notifyUserId);
            rule.setTriggerEvent(TRIGGER_EVENT);
            rule.setTriggerCondition(ownerIds.stream().distinct()
                    .map(String::valueOf).collect(Collectors.joining(",")));
            rule.setNotifyTarget(String.valueOf(notifyUserId));
            rule.setNotifyChannel("dingtalk,app");
            rule.setTimeRule("daily_9");
            rule.setTemplateTitle("【PMS】超期提醒");
            rule.setTemplateContent("{user_name}，您好！您监控的责任人有 {overdue_count} 个任务已超期：\n{task_list}\n请督促责任人尽快完成。");
            rule.setStatus("enabled");
            notifyRuleMapper.insert(rule);
        }
        log.info("[OverdueRemind] 监控配置已更新: {} 条", configs.size());
    }

    /** Object 安全转 Long（JSON 数字可能是 Integer/Long/Double） */
    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Object 安全转 List<Long>（ownerIds 数组） */
    private List<Long> toLongList(Object value) {
        List<Long> result = new ArrayList<>();
        if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                Long id = toLong(item);
                if (id != null) {
                    result.add(id);
                }
            }
        }
        return result;
    }

    private List<Long> parseUserIds(String target) {
        List<Long> result = new ArrayList<>();
        if (StrUtil.isBlank(target)) {
            return result;
        }
        for (String token : target.split(",")) {
            String t = token.trim();
            if (t.isEmpty()) continue;
            try {
                result.add(Long.parseLong(t));
            } catch (NumberFormatException ignored) {
            }
        }
        return result.stream().distinct().collect(Collectors.toList());
    }
}
