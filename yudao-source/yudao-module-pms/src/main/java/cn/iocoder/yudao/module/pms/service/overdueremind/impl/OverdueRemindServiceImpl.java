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
                " AND t.complete_status NOT IN ('done', 'cancelled', 'closed')" +
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
    public List<Long> getReceivers() {
        PmsNotifyRuleDO rule = findOrCreateSummaryRule();
        return parseUserIds(rule.getNotifyTarget());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateReceivers(List<Long> userIds) {
        PmsNotifyRuleDO rule = findOrCreateSummaryRule();
        String target = (userIds == null) ? "" : userIds.stream()
                .distinct()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        PmsNotifyRuleDO update = new PmsNotifyRuleDO();
        update.setRuleId(rule.getRuleId());
        update.setNotifyTarget(target);
        notifyRuleMapper.updateById(update);
        log.info("[OverdueRemind] 超期提请接收人已更新: ruleId={}, target={}", rule.getRuleId(), target);
    }

    /**
     * 查找超期提请汇总规则；不存在则创建（默认停用状态，配置接收人后由用户启用或保持 enabled）
     */
    private PmsNotifyRuleDO findOrCreateSummaryRule() {
        List<PmsNotifyRuleDO> rules = notifyRuleMapper.selectList(
                PmsNotifyRuleDO::getTriggerEvent, TRIGGER_EVENT);
        if (rules != null && !rules.isEmpty()) {
            return rules.get(0);
        }
        // 预置创建（enabled：每日 09:00 job 直接消费）
        PmsNotifyRuleDO rule = new PmsNotifyRuleDO();
        rule.setScopeType("global");
        rule.setRuleName("超期提请");
        rule.setTriggerEvent(TRIGGER_EVENT);
        rule.setTriggerCondition("");
        rule.setNotifyTarget("");
        rule.setNotifyChannel("dingtalk,app");
        rule.setTimeRule("daily_9");
        rule.setTemplateTitle("【PMS】超期提醒");
        rule.setTemplateContent("{user_name}，您好！您监控的任务有 {overdue_count} 个已超期：\n{task_list}\n请督促责任人尽快完成。");
        rule.setStatus("enabled");
        notifyRuleMapper.insert(rule);
        log.info("[OverdueRemind] 已创建超期提请汇总规则: ruleId={}", rule.getRuleId());
        return rule;
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
