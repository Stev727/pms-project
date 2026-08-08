package cn.iocoder.yudao.module.pms.service.notification;

import java.util.Set;

/**
 * 任务通知白名单。未列出的事件一律不发送。
 *
 * ============================ 改造说明（v2）============================
 * 版本：v2（#4 通知规则打通：把任务审核相关新事件接入规则引擎）
 *
 * 新增事件：
 *   - task_review_submitted    任务提交审核（待审核人处理）
 *   - task_review_approved     任务审核通过
 *   - task_review_rejected     任务审核驳回
 *   - task_review_auto_passed  任务按 self_review/skip 策略自动通过
 *
 * 这些事件由 TaskServiceImpl 的 submitReview/approveReview/rejectReview 触发，
 * 通过 DingTalkNotifyService.sendNotifyDirect 落到 pms_notify_log + pms_message +
 * pms_dingtalk_todo。运营可在「通知规则」页（pms_notify_rule）针对这些事件
 * 配置模板与升级规则。
 * =====================================================================
 */
public final class TaskNotificationPolicy {

    private static final Set<String> SUPPORTED_EVENTS = Set.of(
            "task_overdue", "task_dispatched",
            "change_submitted", "change_approved", "change_rejected",
            "completion_submitted", "completion_approved", "completion_rejected",
            // #4 任务派发审核事件
            "task_review_submitted", "task_review_approved",
            "task_review_rejected", "task_review_auto_passed",
            // #4 T-3 提醒（与 executeDailyNotifyCheck 对齐）
            "task_t_minus_3");

    private TaskNotificationPolicy() {
    }

    public static Set<String> supportedEvents() {
        return SUPPORTED_EVENTS;
    }

    public static boolean isSupported(String event) {
        return SUPPORTED_EVENTS.contains(event);
    }

    public static String idempotencyKey(String event, Long businessId, String occurrence) {
        return event + ":" + businessId + ":" + occurrence;
    }
}

