package cn.iocoder.yudao.module.pms.service.notification;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskNotificationPolicyTest {

    @Test
    void dispatchReviewAndOverdueEventsAreAllowed() {
        assertEquals(Set.of(
                "task_overdue", "task_dispatched",
                "change_submitted", "change_approved", "change_rejected",
                "completion_submitted", "completion_approved", "completion_rejected"
        ), TaskNotificationPolicy.supportedEvents());
        assertFalse(TaskNotificationPolicy.isSupported("task_created"));
        assertTrue(TaskNotificationPolicy.isSupported("task_dispatched"));
        assertFalse(TaskNotificationPolicy.isSupported("task_started"));
        assertTrue(TaskNotificationPolicy.isSupported("task_overdue"));
    }

    @Test
    void overdueIdempotencyKeyIsStablePerTaskAndDate() {
        assertEquals("task_overdue:20:2026-07-24",
                TaskNotificationPolicy.idempotencyKey("task_overdue", 20L, "2026-07-24"));
    }
}
