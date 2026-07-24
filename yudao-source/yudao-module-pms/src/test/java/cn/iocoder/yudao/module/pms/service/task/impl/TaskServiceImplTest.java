package cn.iocoder.yudao.module.pms.service.task.impl;

import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import cn.iocoder.yudao.module.pms.dal.mysql.task.TaskMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TaskServiceImplTest {

    @Test
    void createAndStartTaskDoNotSendNotification() {
        TaskServiceImpl service = new TaskServiceImpl();
        TaskMapper taskMapper = mock(TaskMapper.class);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        PmsTaskDO task = new PmsTaskDO();
        task.setTaskId(20L);
        task.setProjectId(10L);
        task.setCompleteStatus("not_started");
        service.createTask(task);

        PmsTaskDO oldTask = new PmsTaskDO();
        oldTask.setTaskId(20L);
        oldTask.setCompleteStatus("not_started");
        when(taskMapper.selectById(20L)).thenReturn(oldTask);
        task.setCompleteStatus("in_progress");
        service.updateTask(task);

        assertTrue(java.util.Arrays.stream(TaskServiceImpl.class.getDeclaredFields())
                .noneMatch(field -> field.getType().getSimpleName().equals("NotifyRuleMapper")));
        assertTrue(java.util.Arrays.stream(TaskServiceImpl.class.getDeclaredFields())
                .noneMatch(field -> field.getType().getSimpleName().equals("NotifyLogMapper")));
    }
    @Test
    void completionRequiresProjectManagerApproval() {
        TaskServiceImpl service = new TaskServiceImpl();
        TaskMapper mapper = mock(TaskMapper.class);
        ReflectionTestUtils.setField(service, "taskMapper", mapper);
        PmsTaskDO task = new PmsTaskDO();
        task.setTaskId(20L);
        task.setCompleteStatus("in_progress");
        when(mapper.selectById(20L)).thenReturn(task);
        service.submitCompletion(20L);
        assertEquals("completion_pending_review", task.getCompleteStatus());
        service.reviewCompletion(20L, true);
        assertEquals("completed", task.getCompleteStatus());
        assertEquals(100, task.getProgress());
    }

    @Test
    void rejectedCompletionReturnsTaskToInProgress() {
        TaskServiceImpl service = new TaskServiceImpl();
        TaskMapper mapper = mock(TaskMapper.class);
        ReflectionTestUtils.setField(service, "taskMapper", mapper);
        PmsTaskDO task = new PmsTaskDO();
        task.setTaskId(20L);
        task.setCompleteStatus("completion_pending_review");
        when(mapper.selectById(20L)).thenReturn(task);
        service.reviewCompletion(20L, false);
        assertEquals("in_progress", task.getCompleteStatus());
    }
}
