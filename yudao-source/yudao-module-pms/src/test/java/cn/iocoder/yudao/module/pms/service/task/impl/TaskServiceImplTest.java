package cn.iocoder.yudao.module.pms.service.task.impl;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.dal.dataobject.project.PmsProjectDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import cn.iocoder.yudao.module.pms.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.task.TaskMapper;
import cn.iocoder.yudao.module.pms.service.dingtalk.DingTalkNotifyService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;

class TaskServiceImplTest {
    @Test
    void createAndStartTaskDoNotSendNotification() {
        TaskServiceImpl service = new TaskServiceImpl();
        TaskMapper mapper = mock(TaskMapper.class);
        ReflectionTestUtils.setField(service, "taskMapper", mapper);
        PmsTaskDO task = new PmsTaskDO();
        task.setTaskId(20L);
        service.createTask(task);
        assertTrue(java.util.Arrays.stream(TaskServiceImpl.class.getDeclaredFields())
                .noneMatch(field -> field.getType().getSimpleName().startsWith("Notify")));
    }

    @Test
    void dispatchSendsDingTalkToMainOwner() {
        TaskServiceImpl service = new TaskServiceImpl();
        TaskMapper mapper = mock(TaskMapper.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        DingTalkNotifyService notifyService = mock(DingTalkNotifyService.class);
        ReflectionTestUtils.setField(service, "taskMapper", mapper);
        ReflectionTestUtils.setField(service, "projectMapper", projectMapper);
        ReflectionTestUtils.setField(service, "dingTalkNotifyService", notifyService);
        PmsTaskDO task = task("not_started");
        task.setTaskName("孙文远派发测试");
        task.setMainOwnerId(1353L);
        PmsProjectDO project = new PmsProjectDO();
        project.setProjectName("PMS推送测试");
        when(mapper.selectById(20L)).thenReturn(task);
        when(projectMapper.selectById(10L)).thenReturn(project);
        when(notifyService.sendNotifyDirect(anyString(), anyString(), eq(List.of(1353L)),
                eq("task_dispatched"), eq("task"), eq(20L))).thenReturn(true);

        service.dispatchTask(20L);

        assertEquals("pending_accept", task.getCompleteStatus());
        assertTrue(task.getIsDispatched());
        assertNotNull(task.getDispatchTime());
        verify(notifyService).sendNotifyDirect(contains("任务派发"), contains("孙文远派发测试"),
                eq(List.of(1353L)), eq("task_dispatched"), eq("task"), eq(20L));
    }

    @Test
    void projectManagerCanApproveCompletion() {
        TaskServiceImpl service = serviceWithProjectManager(1353L);
        TaskMapper mapper = (TaskMapper) ReflectionTestUtils.getField(service, "taskMapper");
        PmsTaskDO task = task("completion_pending_review");
        when(mapper.selectById(20L)).thenReturn(task);
        service.reviewCompletion(20L, true, 1353L);
        assertEquals("completed", task.getCompleteStatus());
        assertEquals(100, task.getProgress());
    }

    @Test
    void nonProjectManagerCannotApproveCompletion() {
        TaskServiceImpl service = serviceWithProjectManager(1353L);
        TaskMapper mapper = (TaskMapper) ReflectionTestUtils.getField(service, "taskMapper");
        when(mapper.selectById(20L)).thenReturn(task("completion_pending_review"));
        assertThrows(ServiceException.class, () -> service.reviewCompletion(20L, true, 1L));
    }

    @Test
    void missingTaskReturnsServiceError() {
        TaskServiceImpl service = new TaskServiceImpl();
        TaskMapper mapper = mock(TaskMapper.class);
        ReflectionTestUtils.setField(service, "taskMapper", mapper);
        assertThrows(ServiceException.class, () -> service.submitCompletion(404L));
    }

    private static TaskServiceImpl serviceWithProjectManager(Long managerId) {
        TaskServiceImpl service = new TaskServiceImpl();
        TaskMapper taskMapper = mock(TaskMapper.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "projectMapper", projectMapper);
        PmsProjectDO project = new PmsProjectDO();
        project.setProjectId(10L);
        project.setProjectManagerId(managerId);
        when(projectMapper.selectById(10L)).thenReturn(project);
        return service;
    }

    private static PmsTaskDO task(String status) {
        PmsTaskDO task = new PmsTaskDO();
        task.setTaskId(20L);
        task.setProjectId(10L);
        task.setCompleteStatus(status);
        return task;
    }
}
