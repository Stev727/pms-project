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
import cn.iocoder.yudao.module.pms.service.dingtalk.DingTalkTodoService;
import cn.iocoder.yudao.module.pms.service.message.PmsMessageService;
import cn.iocoder.yudao.module.pms.service.notification.TaskNotificationPolicy;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 *
 * ============================ 改造说明（v2）============================
 * 版本：v2（在线上原文件基础上改造，原方法签名/行为全部保留兼容）
 *
 * 【#4 消息提醒增强】
 *   - {@link #sendNotifyDirect(String, String, List, String, String, Long)}
 *     在原"发钉钉工作通知"基础上，同步落"站内消息"（pms_message）
 *     并根据 triggerEvent 自动管理"钉钉待办"（创建/完成）。
 *
 *   - 站内消息：对每个 receiverUser 调 {@link PmsMessageService#sendMessage}，
 *     落库失败仅记日志，不影响主流程。
 *
 *   - 钉钉待办触发规则：
 *       task_dispatched / completion_submitted → 给每个 receiver 创建待办
 *       task_review_approved / completion_approved / task_review_auto_passed
 *         → 把该 taskId 下所有 pending 待办标记完成（业务完成即完结待办）
 *
 *   - 失败降级：站内消息 / 钉钉待办失败均不抛异常，不阻塞调用方。
 *
 *   - 错误码：{@link cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants#DINGTALK_TODO_FAILED}
 *     仅作为"返回值标记"，本类不主动抛出该异常（保持 sendNotifyDirect 兼容）。
 *
 * 【通知规则打通】
 *   - {@link TaskNotificationPolicy#isSupported} 白名单已扩展，新增事件：
 *     task_review_submitted / task_review_approved / task_review_rejected /
 *     task_review_auto_passed。运营可在通知规则页（pms_notify_rule）配置。
 *
 * 【与任务模块对接】
 *   TaskServiceImpl 通过 5 处调用 sendNotifyDirect 触发通知：
 *     1) dispatchTask        → triggerEvent=task_dispatched
 *     2) reviewCompletion    → completion_approved / completion_rejected
 *     3) submitReview(skip) → task_review_auto_passed
 *     4) submitReview       → task_review_submitted
 *     5) approveReview/rejectReview → task_review_approved / task_review_rejected
 *   本服务方法签名保持原样，无需任务模块改动。
 * =====================================================================
 */
@Service
@Slf4j
public class DingTalkNotifyServiceImpl implements DingTalkNotifyService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 前端基础 URL（用于拼接任务详情跳转地址）
     * 配置项：pms.notify.frontend-base-url，默认 https://pms.topsun.com
     */
    @Value("${pms.notify.frontend-base-url:https://pms.topsun.com}")
    private String frontendBaseUrl;

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

    /**
     * #4 钉钉待办服务。失败降级，业务继续走。
     */
    @Resource
    private DingTalkTodoService dingTalkTodoService;

    /**
     * #4 站内消息服务。落库失败仅记日志，不阻塞主流程。
     */
    @Resource
    private PmsMessageService pmsMessageService;

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

        // 5. 发送通知（规则触发暂不支持详情跳转 URL；有 robotCode 时走机器人通道）
        String taskId;
        if (StrUtil.isNotBlank(config.getRobotCode())) {
            List<String> userIdArr = Arrays.asList(userIdList.split(","));
            taskId = dingTalkApiService.sendRobotMessage(userIdArr, title, content, null);
        } else {
            taskId = dingTalkApiService.sendWorkNotification(userIdList, title, content, null);
        }

        // 6. 记录通知日志
        String sendStatus = taskId != null ? "success" : "failed";
        String sendResult = taskId != null ? "task_id=" + taskId : "发送失败";
        saveNotifyLog(rule, title, content, receiverUserIds, sendStatus, sendResult, businessType, businessId);

        // 7. #4 站内消息（同步落库，失败仅记日志）
        sendInAppMessageSafely(receiverUserIds, title, content, businessType, businessId, rule.getTriggerEvent());

        // 8. #4 钉钉待办（按 triggerEvent 决定创建/完成，规则触发路径暂不支持详情 URL）
        handleDingTalkTodoSafely(rule.getTriggerEvent(), businessType, businessId,
                receiverUserIds, title, content, null);

        return taskId != null;
    }

    @Override
    public boolean sendNotifyDirect(String title, String content, List<Long> receiverUserIds,
                                     String triggerEvent, String businessType, Long businessId,
                                     String detailUrl) {
        PmsDingTalkConfigDO config = dingTalkApiService.getConfig();
        if (config.getNotifyEnabled() == null || !config.getNotifyEnabled()) {
            // #4：钉钉通知未启用，仍要落站内消息
            sendInAppMessageSafely(receiverUserIds, title, content, businessType, businessId, triggerEvent);
            handleDingTalkTodoSafely(triggerEvent, businessType, businessId,
                    receiverUserIds, title, content, detailUrl);
            return false;
        }

        List<PmsDingTalkUserDO> dingTalkUsers = dingTalkUserMapper.selectListByUserIds(receiverUserIds);
        // fail closed: 接收人缺少有效钉钉用户映射时记录失败日志
        if (dingTalkUsers == null || dingTalkUsers.size() != receiverUserIds.size()) {
            log.warn("[DingTalkNotify] 接收人缺少有效钉钉用户映射: userIds={}, mapped={}",
                    receiverUserIds, dingTalkUsers != null ? dingTalkUsers.size() : 0);
            saveNotifyLog(null, title, content, receiverUserIds, "failed",
                    "接收人缺少有效钉钉用户映射", businessType, businessId);
            // #4：钉钉发不出，仍要落站内消息 + 待办
            sendInAppMessageSafely(receiverUserIds, title, content, businessType, businessId, triggerEvent);
            handleDingTalkTodoSafely(triggerEvent, businessType, businessId,
                    receiverUserIds, title, content, detailUrl);
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
            sendInAppMessageSafely(receiverUserIds, title, content, businessType, businessId, triggerEvent);
            handleDingTalkTodoSafely(triggerEvent, businessType, businessId,
                    receiverUserIds, title, content, detailUrl);
            return false;
        }

        // #机器人通道：有 robotCode 时以独立机器人身份发送（显示为「项目管理」对话）
        // 否则降级为传统工作通知（显示为「工作通知:浙江中坚科技股份有限公司」）
        String taskId;
        if (StrUtil.isNotBlank(config.getRobotCode())) {
            List<String> userIdArr = Arrays.asList(userIdList.split(","));
            // 根据触发事件推导卡片类型：审核类事件用 review 卡片（通过+驳回），其余用 dispatch 卡片（一键接收+详情）
            String cardType = "task_review_submitted".equals(triggerEvent) ? "review" : "dispatch";
            taskId = dingTalkApiService.sendRobotMessage(userIdArr, title, content, detailUrl, cardType);
            log.info("[DingTalkNotify] 使用机器人通道发送 robotCode={} users={} cardType={}", config.getRobotCode(), userIdArr.size(), cardType);
        } else {
            taskId = dingTalkApiService.sendWorkNotification(userIdList, title, content, detailUrl);
        }
        String sendStatus = taskId != null ? "success" : "failed";
        saveNotifyLog(null, title, content, receiverUserIds, sendStatus,
                taskId != null ? "task_id=" + taskId : "发送失败", businessType, businessId);

        // #4：同步落站内消息 + 待办管理（不论钉钉工作通知成功与否，都要做）
        sendInAppMessageSafely(receiverUserIds, title, content, businessType, businessId, triggerEvent);
        handleDingTalkTodoSafely(triggerEvent, businessType, businessId,
                receiverUserIds, title, content, detailUrl);

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
            processRule(rule, tasks, today);
        }

        log.info("[DingTalkNotify] 每日通知检查完成");
    }

    /**
     * 按规则统一分发处理（支持 T-N 提前提醒、逾期每日、逾期满 N 天、项目级作用域）
     */
    private void processRule(PmsNotifyRuleDO rule, List<PmsTaskDO> tasks, LocalDate today) {
        String triggerEvent = rule.getTriggerEvent();
        if (StrUtil.isBlank(triggerEvent)) {
            return;
        }

        // 项目级规则：仅作用于指定项目；全局/模式级规则作用于全部活跃任务
        List<PmsTaskDO> scopedTasks = tasks;
        if ("project".equals(rule.getScopeType()) && rule.getProjectId() != null) {
            final Long pid = rule.getProjectId();
            scopedTasks = tasks.stream()
                    .filter(t -> pid.equals(t.getProjectId()))
                    .collect(Collectors.toList());
            if (scopedTasks.isEmpty()) {
                return;
            }
        }

        if (triggerEvent.startsWith("task_t_minus_")) {
            int days = parseSuffixDays(triggerEvent, "task_t_minus_", 3);
            processAdvanceRule(rule, scopedTasks, today, days);
        } else if ("task_overdue".equals(triggerEvent)) {
            processOverdueRule(rule, scopedTasks, today, 1);
        } else if (triggerEvent.startsWith("task_overdue_")) {
            int days = parseSuffixDays(triggerEvent, "task_overdue_", 1);
            processOverdueRule(rule, scopedTasks, today, days);
        } else {
            log.info("[DingTalkNotify] 跳过非定时扫描事件: triggerEvent={}", triggerEvent);
        }
    }

    /**
     * 解析 triggerEvent 后缀数字（如 task_overdue_3 -> 3）
     */
    private int parseSuffixDays(String triggerEvent, String prefix, int defaultDays) {
        try {
            String num = triggerEvent.substring(prefix.length());
            return Integer.parseInt(num.trim());
        } catch (Exception e) {
            return defaultDays;
        }
    }

    /**
     * 处理提前提醒规则（计划结束前 N 个工作日）
     */
    private void processAdvanceRule(PmsNotifyRuleDO rule, List<PmsTaskDO> tasks, LocalDate today, int daysBefore) {
        LocalDate targetDate = addWorkDays(today, daysBefore);
        String targetDateStr = targetDate.format(DATE_FMT);

        for (PmsTaskDO task : tasks) {
            if (task.getPlanEndDate() == null) continue;
            String taskEndDateStr = task.getPlanEndDate().format(DATE_FMT);
            if (!taskEndDateStr.equals(targetDateStr)) continue;

            if (hasNotifyAlreadySent(task.getTaskId(), rule.getTriggerEvent(), today)) {
                continue;
            }

            List<Long> receiverIds = computeReceivers(rule, task);
            if (receiverIds.isEmpty()) continue;

            PmsProjectDO project = projectMapper.selectById(task.getProjectId());
            String projectName = project != null ? project.getProjectName() : "";

            Map<String, Object> vars = new HashMap<>();
            vars.put("task_name", task.getTaskName());
            vars.put("plan_end_date", taskEndDateStr);
            vars.put("project_name", projectName);
            vars.put("user_name", "");

            boolean success = sendNotifyByRule(rule.getRuleId(), vars, receiverIds);
            log.info("[DingTalkNotify] 提前{}天提醒: task={}, receivers={}, success={}",
                    daysBefore, task.getTaskName(), receiverIds, success);
        }
    }

    /**
     * 处理逾期规则（延期满 minDelayDays 天触发；minDelayDays=1 即每日逾期提醒）
     */
    private void processOverdueRule(PmsNotifyRuleDO rule, List<PmsTaskDO> tasks, LocalDate today, int minDelayDays) {
        for (PmsTaskDO task : tasks) {
            if (task.getPlanEndDate() == null) continue;
            if (task.getPlanEndDate().isAfter(today)) continue; // 未逾期

            int delayDays = (int) java.time.temporal.ChronoUnit.DAYS.between(task.getPlanEndDate(), today);
            if (delayDays < minDelayDays) continue;

            if (hasNotifyAlreadySent(task.getTaskId(), rule.getTriggerEvent(), today)) {
                continue;
            }
            List<Long> receiverIds = computeReceivers(rule, task);
            if (receiverIds.isEmpty()) continue;

            PmsProjectDO project = projectMapper.selectById(task.getProjectId());
            String projectName = project != null ? project.getProjectName() : "";

            Map<String, Object> vars = new HashMap<>();
            vars.put("task_name", task.getTaskName());
            vars.put("delay_days", String.valueOf(delayDays));
            vars.put("project_name", projectName);
            vars.put("plan_end_date", task.getPlanEndDate().format(DATE_FMT));

            boolean success = sendNotifyByRule(rule.getRuleId(), vars, receiverIds, "task", task.getTaskId());
            log.info("[DingTalkNotify] 逾期提醒(满{}天): task={}, delayDays={}, rule={}, success={}",
                    minDelayDays, task.getTaskName(), delayDays, rule.getRuleName(), success);
        }
    }

    /**
     * 根据规则 notifyTarget 解析接收人；为空时回退到默认接收人。
     * 支持：main_owner(主责任人) / helper(协助人) / pm(项目经理) / dept_head(部门负责人)
     */
    private List<Long> computeReceivers(PmsNotifyRuleDO rule, PmsTaskDO task) {
        String target = rule.getNotifyTarget();
        boolean isAdvance = rule.getTriggerEvent() != null && rule.getTriggerEvent().startsWith("task_t_minus_");
        if (StrUtil.isBlank(target)) {
            // 回退默认：提前提醒->责任人+协助人；逾期->项目经理
            return isAdvance ? getTaskReceivers(task) : getProjectManagers(task.getProjectId());
        }
        List<Long> result = new ArrayList<>();
        for (String token : target.split(",")) {
            token = token.trim();
            if (token.isEmpty()) continue;
            switch (token) {
                case "main_owner":
                    if (task.getMainOwnerId() != null) result.add(task.getMainOwnerId());
                    break;
                case "helper":
                    if (StrUtil.isNotBlank(task.getHelperIds())) {
                        for (String h : task.getHelperIds().split(",")) {
                            String ht = h.trim();
                            if (StrUtil.isNotBlank(ht)) {
                                try { result.add(Long.parseLong(ht)); } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                    break;
                case "pm": {
                    PmsProjectDO p = projectMapper.selectById(task.getProjectId());
                    if (p != null && p.getProjectManagerId() != null) result.add(p.getProjectManagerId());
                    break;
                }
                case "dept_head": {
                    Long leader = resolveDeptLeader(task.getProjectId());
                    if (leader != null) result.add(leader);
                    break;
                }
                default:
                    log.warn("[DingTalkNotify] 不支持的通知对象 token: {}", token);
            }
        }
        result = result.stream().distinct().collect(Collectors.toList());
        if (result.isEmpty()) {
            return isAdvance ? getTaskReceivers(task) : getProjectManagers(task.getProjectId());
        }
        return result;
    }

    /**
     * 解析项目所属部门的负责人用户ID
     */
    private Long resolveDeptLeader(Long projectId) {
        try {
            Long deptId = jdbcTemplate.queryForObject(
                    "SELECT dept_id FROM pms_project WHERE id = ? AND deleted = 0", Long.class, projectId);
            if (deptId == null) return null;
            return jdbcTemplate.queryForObject(
                    "SELECT leader_user_id FROM system_dept WHERE id = ?", Long.class, deptId);
        } catch (Exception e) {
            log.warn("[DingTalkNotify] 解析部门负责人失败: projectId={}", projectId, e);
            return null;
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

    // ==================================================================
    // #4 新增：站内消息 + 钉钉待办（私有方法，失败降级）
    // ==================================================================

    /**
     * #4 站内消息：给每个 receiverUser 落一条站内消息。
     * 落库失败仅记日志，不影响主流程。
     */
    private void sendInAppMessageSafely(List<Long> receiverUserIds, String title, String content,
                                       String businessType, Long businessId, String triggerEvent) {
        if (receiverUserIds == null || receiverUserIds.isEmpty()) {
            return;
        }
        for (Long receiverId : receiverUserIds) {
            try {
                pmsMessageService.sendMessage(receiverId, title, content, businessType, businessId, triggerEvent);
            } catch (Exception e) {
                log.error("[DingTalkNotify] 站内消息落库失败: receiverId={}, bizType={}, bizId={}",
                        receiverId, businessType, businessId, e);
            }
        }
    }

    /**
     * #4 钉钉待办：根据 triggerEvent 决定创建或完成待办。
     *
     * <p>规则：
     * <ul>
     *   <li>task_dispatched / completion_submitted → 给每个 receiver 创建待办</li>
     *   <li>task_review_approved / completion_approved / task_review_auto_passed
     *         → 把该 taskId 下所有 pending 待办标记完成</li>
     *   <li>其它事件（如 task_review_rejected / task_overdue）→ 不动待办</li>
     * </ul>
     *
     * <p>注意：businessId 在本服务的语义是 taskId（调用方 TaskServiceImpl 全部传 taskId），
     * 用作 dingTalkTodoService 的 bizTaskId 参数。
     */
    private void handleDingTalkTodoSafely(String triggerEvent, String businessType, Long businessId,
                                         List<Long> receiverUserIds, String title, String content,
                                         String detailUrl) {
        if (!"task".equals(businessType) || businessId == null) {
            return;
        }
        try {
            if ("task_dispatched".equals(triggerEvent) || "completion_submitted".equals(triggerEvent)) {
                // 派发 / 提交完成 → 为每个接收人创建待办
                if (receiverUserIds == null || receiverUserIds.isEmpty()) {
                    return;
                }
                for (Long receiverId : receiverUserIds) {
                    try {
                        dingTalkTodoService.createTodoForTask(businessId, receiverId, title, content, detailUrl);
                    } catch (Exception e) {
                        log.error("[DingTalkNotify] 创建钉钉待办失败: bizTaskId={}, userId={}",
                                businessId, receiverId, e);
                    }
                }
            } else if ("task_review_approved".equals(triggerEvent)
                    || "completion_approved".equals(triggerEvent)
                    || "task_review_auto_passed".equals(triggerEvent)) {
                // 任务完成（审核通过 / 自动通过）→ 标记该 task 全部 pending 待办完成
                dingTalkTodoService.completeTodoByTask(businessId);
            }
            // 其它事件：不动待办
        } catch (Exception e) {
            log.error("[DingTalkNotify] 钉钉待办管理异常: triggerEvent={}, bizTaskId={}",
                    triggerEvent, businessId, e);
        }
    }

}

