package cn.iocoder.yudao.module.pms.service.dingtalk.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.module.pms.dal.dataobject.dingtalk.PmsDingTalkConfigDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.dingtalk.PmsDingTalkUserDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.notifyrule.PmsNotifyRuleDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.notifylog.PmsNotifyLogDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.project.PmsProjectDO;
import cn.iocoder.yudao.module.pms.dal.mysql.dingtalk.PmsDingTalkUserMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.notifyrule.NotifyRuleMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.notifylog.NotifyLogMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.task.TaskMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.service.dingtalk.DingTalkApiService;
import cn.iocoder.yudao.module.pms.service.dingtalk.DingTalkNotifyService;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 钉钉通知服务实现
 */
@Service
@Slf4j
public class DingTalkNotifyServiceImpl implements DingTalkNotifyService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Resource
    private DingTalkApiService dingTalkApiService;

    @Resource
    private PmsDingTalkUserMapper dingTalkUserMapper;

    @Resource
    private NotifyRuleMapper notifyRuleMapper;

    @Resource
    private NotifyLogMapper notifyLogMapper;

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public boolean sendNotifyByRule(Long ruleId, Map<String, Object> templateVars, List<Long> receiverUserIds) {
        // 1. 获取通知规则
        return sendNotifyByRule(ruleId, templateVars, receiverUserIds, null, null);
    }

    boolean sendNotifyByRule(Long ruleId, Map<String, Object> templateVars, List<Long> receiverUserIds,
                             String businessType, Long businessId) {
        PmsNotifyRuleDO rule = notifyRuleMapper.selectById(ruleId);
        if (rule == null || !"enabled".equals(rule.getStatus())) {
            log.warn("[DingTalkNotify] 通知规则不存在或未启用: ruleId={}", ruleId);
            return false;
        }

        // 2. 检查钉钉配置
        PmsDingTalkConfigDO config = dingTalkApiService.getConfig();
        if (config.getNotifyEnabled() == null || !config.getNotifyEnabled()) {
            log.info("[DingTalkNotify] 钉钉通知未启用");
            return false;
        }

        // 3. 渲染模板
        String title = renderTemplate(rule.getTemplateTitle(), templateVars);
        String content = renderTemplate(rule.getTemplateContent(), templateVars);

        // 4. 获取接收人的钉钉 userid
        List<PmsDingTalkUserDO> dingTalkUsers = dingTalkUserMapper.selectListByUserIds(receiverUserIds);
        // fail closed: 接收人缺少有效钉钉用户映射时记录失败日志
        if (dingTalkUsers == null || dingTalkUsers.size() != receiverUserIds.size()) {
            log.warn("[DingTalkNotify] 接收人缺少有效钉钉用户映射: userIds={}, mapped={}",
                    receiverUserIds, dingTalkUsers != null ? dingTalkUsers.size() : 0);
            saveNotifyLog(rule, title, content, receiverUserIds, "failed",
                    "接收人缺少有效钉钉用户映射", businessType, businessId);
            return false;
        }
        String userIdList = dingTalkUsers.stream()
                .map(PmsDingTalkUserDO::getDingtalkUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(","));

        if (StrUtil.isBlank(userIdList)) {
            log.warn("[DingTalkNotify] 接收人无钉钉用户映射: userIds={}", receiverUserIds);
            saveNotifyLog(rule, title, content, receiverUserIds, "failed",
                    "接收人缺少有效钉钉用户映射", businessType, businessId);
            return false;
        }

        // 5. 发送工作通知
        String taskId = dingTalkApiService.sendWorkNotification(userIdList, title, content);

        // 6. 记录通知日志
        String sendStatus = taskId != null ? "success" : "failed";
        String sendResult = taskId != null ? "task_id=" + taskId : "发送失败";
        saveNotifyLog(rule, title, content, receiverUserIds, sendStatus, sendResult, businessType, businessId);

        return taskId != null;
    }

    @Override
    public boolean sendNotifyDirect(String title, String content, List<Long> receiverUserIds,
                                     String triggerEvent, String businessType, Long businessId) {
        PmsDingTalkConfigDO config = dingTalkApiService.getConfig();
        if (config.getNotifyEnabled() == null || !config.getNotifyEnabled()) {
            return false;
        }

        List<PmsDingTalkUserDO> dingTalkUsers = dingTalkUserMapper.selectListByUserIds(receiverUserIds);
        // fail closed: 接收人缺少有效钉钉用户映射时记录失败日志
        if (dingTalkUsers == null || dingTalkUsers.size() != receiverUserIds.size()) {
            log.warn("[DingTalkNotify] 接收人缺少有效钉钉用户映射: userIds={}, mapped={}",
                    receiverUserIds, dingTalkUsers != null ? dingTalkUsers.size() : 0);
            saveNotifyLog(null, title, content, receiverUserIds, "failed",
                    "接收人缺少有效钉钉用户映射", businessType, businessId);
            return false;
        }
        String userIdList = dingTalkUsers.stream()
                .map(PmsDingTalkUserDO::getDingtalkUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(","));

        if (StrUtil.isBlank(userIdList)) {
            log.warn("[DingTalkNotify] 接收人无钉钉用户映射: userIds={}", receiverUserIds);
            saveNotifyLog(null, title, content, receiverUserIds, "failed",
                    "接收人缺少有效钉钉用户映射", businessType, businessId);
            return false;
        }

        String taskId = dingTalkApiService.sendWorkNotification(userIdList, title, content);
        String sendStatus = taskId != null ? "success" : "failed";
        saveNotifyLog(null, title, content, receiverUserIds, sendStatus,
                taskId != null ? "task_id=" + taskId : "发送失败", businessType, businessId);

        return taskId != null;
    }

    @Override
    public void executeDailyNotifyCheck() {
        log.info("[DingTalkNotify] 开始执行每日通知检查...");

        PmsDingTalkConfigDO config = dingTalkApiService.getConfig();
        if (config.getNotifyEnabled() == null || !config.getNotifyEnabled()) {
            log.info("[DingTalkNotify] 钉钉通知未启用，跳过");
            return;
        }

        // 1. 获取所有启用的通知规则
        List<PmsNotifyRuleDO> rules = notifyRuleMapper.selectList(
                PmsNotifyRuleDO::getStatus, "enabled");
        if (rules == null || rules.isEmpty()) {
            log.info("[DingTalkNotify] 无启用的通知规则");
            return;
        }

        // 2. 获取所有活跃任务
        List<PmsTaskDO> tasks = getActiveTasks();
        log.info("[DingTalkNotify] 待检查任务数: {}", tasks.size());

        LocalDate today = LocalDate.now();

        for (PmsNotifyRuleDO rule : rules) {
            String triggerEvent = rule.getTriggerEvent();
            if ("task_t_minus_3".equals(triggerEvent)) {
                processTMinus3Rule(rule, tasks, today);
            } else if ("task_overdue".equals(triggerEvent)) {
                processOverdueRule(rule, tasks, today);
            }
        }

        log.info("[DingTalkNotify] 每日通知检查完成");
    }

    /**
     * 处理 T-3 提醒规则
     */
    private void processTMinus3Rule(PmsNotifyRuleDO rule, List<PmsTaskDO> tasks, LocalDate today) {
        // 计算 3 个工作日后的日期
        LocalDate targetDate = addWorkDays(today, 3);
        String targetDateStr = targetDate.format(DATE_FMT);

        for (PmsTaskDO task : tasks) {
            if (task.getPlanEndDate() == null) continue;
            String taskEndDateStr = task.getPlanEndDate().format(DATE_FMT);
            if (!taskEndDateStr.equals(targetDateStr)) continue;

            if (hasNotifyAlreadySent(task.getTaskId(), "task_t_minus_3", today)) {
                continue;
            }

            List<Long> receiverIds = getTaskReceivers(task);
            PmsProjectDO project = projectMapper.selectById(task.getProjectId());
            String projectName = project != null ? project.getProjectName() : "";

            Map<String, Object> vars = new HashMap<>();
            vars.put("task_name", task.getTaskName());
            vars.put("plan_end_date", taskEndDateStr);
            vars.put("project_name", projectName);
            vars.put("user_name", "");

            boolean success = sendNotifyByRule(rule.getRuleId(), vars, receiverIds);
            log.info("[DingTalkNotify] T-3 提醒: task={}, receivers={}, success={}",
                    task.getTaskName(), receiverIds, success);
        }
    }

    /**
     * 处理逾期规则（含升级逻辑）
     */
    private void processOverdueRule(PmsNotifyRuleDO rule, List<PmsTaskDO> tasks, LocalDate today) {
        for (PmsTaskDO task : tasks) {
            if (task.getPlanEndDate() == null) continue;
            if (task.getPlanEndDate().isAfter(today)) continue; // 未逾期

            int delayDays = (int) java.time.temporal.ChronoUnit.DAYS.between(task.getPlanEndDate(), today);
            if (delayDays <= 0) continue;

            if (hasNotifyAlreadySent(task.getTaskId(), "task_overdue", today)) {
                continue;
            }
            List<Long> receiverIds = getOverdueReceiverIds(task);
            if (receiverIds.isEmpty()) continue;

            PmsProjectDO project = projectMapper.selectById(task.getProjectId());
            String projectName = project != null ? project.getProjectName() : "";

            Map<String, Object> vars = new HashMap<>();
            vars.put("task_name", task.getTaskName());
            vars.put("delay_days", String.valueOf(delayDays));
            vars.put("project_name", projectName);
            vars.put("plan_end_date", task.getPlanEndDate().format(DATE_FMT));

            boolean success = sendNotifyByRule(rule.getRuleId(), vars, receiverIds, "task", task.getTaskId());
            log.info("[DingTalkNotify] 逾期提醒: task={}, delayDays={}, rule={}, success={}",
                    task.getTaskName(), delayDays, rule.getRuleName(), success);
        }
    }

    /**
     * 增加工作日（跳过周末）
     */
    private LocalDate addWorkDays(LocalDate date, int days) {
        LocalDate result = date;
        int added = 0;
        while (added < days) {
            result = result.plusDays(1);
            if (result.getDayOfWeek().getValue() <= 5) { // 1=Monday, 5=Friday
                added++;
            }
        }
        return result;
    }

    /**
     * 获取升级级别
     */
    private String getEscalationLevel(PmsNotifyRuleDO rule) {
        if (StrUtil.isBlank(rule.getEscalationCondition())) {
            return "L0";
        }
        try {
            JSONObject escalation = JSONUtil.parseObj(rule.getEscalationCondition());
            return escalation.getStr("target_level", "L0");
        } catch (Exception e) {
            return "L0";
        }
    }

    /**
     * 获取所有活跃任务（未完成）
     */
    private List<PmsTaskDO> getActiveTasks() {
        try {
            return jdbcTemplate.query(
                    "SELECT * FROM pms_task WHERE deleted = 0 AND complete_status NOT IN ('done', 'cancelled', 'closed')",
                    new BeanPropertyRowMapper<>(PmsTaskDO.class));
        } catch (Exception e) {
            log.error("[DingTalkNotify] 获取活跃任务失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取任务接收人（负责人 + 协助人）
     * mainOwnerId 是 Long, helperIds 是逗号分隔的 String
     */
    private List<Long> getTaskReceivers(PmsTaskDO task) {
        List<Long> receivers = new ArrayList<>();
        if (task.getMainOwnerId() != null) {
            receivers.add(task.getMainOwnerId());
        }
        // helper_ids 是逗号分隔的字符串
        if (StrUtil.isNotBlank(task.getHelperIds())) {
            String[] parts = task.getHelperIds().split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                if (StrUtil.isNotBlank(trimmed)) {
                    try {
                        receivers.add(Long.parseLong(trimmed));
                    } catch (NumberFormatException e) {
                        log.warn("[DingTalkNotify] 协助人ID格式错误: {}", trimmed);
                    }
                }
            }
        }
        return receivers.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 延期通知只发送项目经理，不包含负责人和协助人。
     */
    List<Long> getOverdueReceiverIds(PmsTaskDO task) {
        return getProjectManagers(task.getProjectId());
    }

    /**
     * 获取项目的项目经理
     */
    private List<Long> getProjectManagers(Long projectId) {
        List<Long> managers = new ArrayList<>();
        try {
            PmsProjectDO project = projectMapper.selectById(projectId);
            if (project != null && project.getProjectManagerId() != null) {
                managers.add(project.getProjectManagerId());
            }
        } catch (Exception e) {
            log.error("[DingTalkNotify] 获取项目经理失败", e);
        }
        return managers;
    }

    /**
     * 检查今日是否已发送过该通知
     */
    private boolean hasNotifyAlreadySent(Long taskId, String triggerEvent, LocalDate date) {
        try {
            String today = date.format(DATE_FMT);
            String sql = "SELECT COUNT(*) FROM pms_notify_log WHERE business_id = ? AND trigger_event = ? " +
                    "AND DATE(send_time) = ? AND send_status = 'success' AND deleted = 0";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, taskId, triggerEvent, today);
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("[DingTalkNotify] 检查通知发送记录失败", e);
            return false;
        }
    }

    /**
     * 保存通知日志
     */
    private void saveNotifyLog(PmsNotifyRuleDO rule, String title, String content,
                               List<Long> receiverUserIds, String sendStatus,
                               String sendResult, String businessType, Long businessId) {
        try {
            PmsNotifyLogDO logDO = new PmsNotifyLogDO();
            logDO.setRuleId(rule != null ? rule.getRuleId() : null);
            logDO.setTriggerEvent(rule != null ? rule.getTriggerEvent() : "manual");
            logDO.setNotifyTarget(receiverUserIds != null ? receiverUserIds.toString() : "");
            logDO.setTargetName("");
            logDO.setChannel("dingtalk");
            logDO.setSendTime(LocalDateTime.now());
            logDO.setSendStatus(sendStatus);
            logDO.setSendResult(sendResult);
            logDO.setTitle(title);
            logDO.setContent(content);
            logDO.setBusinessType(businessType);
            logDO.setBusinessId(businessId);
            // 幂等性与接收人字段
            if ("task".equals(businessType) && businessId != null) {
                logDO.setTaskId(businessId);
            }
            if (receiverUserIds != null && !receiverUserIds.isEmpty()) {
                logDO.setReceiverUserId(receiverUserIds.get(0));
            }
            String evt = rule != null ? rule.getTriggerEvent() : "manual";
            if (businessId != null) {
                logDO.setIdempotencyKey(evt + ":" + businessId + ":" + java.time.LocalDate.now());
            }
            logDO.setRetryCount(0);
            notifyLogMapper.insert(logDO);
        } catch (Exception e) {
            log.error("[DingTalkNotify] 保存通知日志失败", e);
        }
    }

    @Override
    public String renderTemplate(String template, Map<String, Object> vars) {
        if (StrUtil.isBlank(template) || vars == null || vars.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, Object> entry : vars.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }
}
