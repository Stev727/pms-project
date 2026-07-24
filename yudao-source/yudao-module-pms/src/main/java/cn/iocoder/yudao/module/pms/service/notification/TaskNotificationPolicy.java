package cn.iocoder.yudao.module.pms.service.notification;

import java.util.Set;

/**
 * 任务通知白名单。未列出的事件一律不发送。
 */
public final class TaskNotificationPolicy {

    private static final Set<String> SUPPORTED_EVENTS = Set.of(
            "task_overdue", "task_dispatched",
            "change_submitted", "change_approved", "change_rejected",
            "completion_submitted", "completion_approved", "completion_rejected");

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
