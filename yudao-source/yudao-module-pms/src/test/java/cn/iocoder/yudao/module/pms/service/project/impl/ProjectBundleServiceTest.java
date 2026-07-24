package cn.iocoder.yudao.module.pms.service.project.impl;

import cn.iocoder.yudao.module.pms.controller.admin.project.vo.ProjectCreateBundleReqVO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.dal.dataobject.project.PmsProjectDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.projectmember.PmsProjectMemberDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.notifyrule.PmsNotifyRuleDO;
import cn.iocoder.yudao.module.pms.dal.mysql.notifyrule.NotifyRuleMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.projectmember.ProjectMemberMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.task.TaskMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProjectBundleServiceTest {

    @Test
    void createsMembersBeforeTasksAndAssignsProjectId() {
        ProjectServiceImpl service = new ProjectServiceImpl();
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        ProjectMemberMapper memberMapper = mock(ProjectMemberMapper.class);
        TaskMapper taskMapper = mock(TaskMapper.class);
        ReflectionTestUtils.setField(service, "projectMapper", projectMapper);
        ReflectionTestUtils.setField(service, "projectMemberMapper", memberMapper);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        doAnswer(invocation -> {
            ((PmsProjectDO) invocation.getArgument(0)).setProjectId(10L);
            return 1;
        }).when(projectMapper).insert(any(PmsProjectDO.class));

        PmsProjectDO project = new PmsProjectDO();
        project.setProjectCode("PRJ-TEST-001");
        PmsProjectMemberDO member = new PmsProjectMemberDO();
        member.setUserId(1353L);
        PmsTaskDO task = new PmsTaskDO();
        task.setMainOwnerId(1353L);
        ProjectCreateBundleReqVO request = new ProjectCreateBundleReqVO();
        request.setProject(project);
        request.setMembers(List.of(member));
        request.setTasks(List.of(task));

        assertEquals(10L, service.createProjectBundle(request));
        assertEquals(10L, member.getProjectId());
        assertEquals(10L, task.getProjectId());
        var order = inOrder(projectMapper, memberMapper, taskMapper);
        order.verify(projectMapper).insert(project);
        order.verify(memberMapper).insert(member);
        order.verify(taskMapper).insert(task);
    }

    @Test
    void rejectsTaskOwnerWhoIsNotProjectMember() {
        ProjectServiceImpl service = new ProjectServiceImpl();
        PmsProjectDO project = new PmsProjectDO();
        PmsProjectMemberDO member = new PmsProjectMemberDO();
        member.setUserId(1353L);
        PmsTaskDO task = new PmsTaskDO();
        task.setMainOwnerId(2001L);
        ProjectCreateBundleReqVO request = new ProjectCreateBundleReqVO();
        request.setProject(project);
        request.setMembers(List.of(member));
        request.setTasks(List.of(task));

        assertThrows(ServiceException.class, () -> service.createProjectBundle(request));
    }


    @Test
    void copiesSelectedNotificationModeRulesAsProjectSnapshot() {
        ProjectServiceImpl service = new ProjectServiceImpl();
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        ProjectMemberMapper memberMapper = mock(ProjectMemberMapper.class);
        TaskMapper taskMapper = mock(TaskMapper.class);
        NotifyRuleMapper notifyRuleMapper = mock(NotifyRuleMapper.class);
        ReflectionTestUtils.setField(service, "projectMapper", projectMapper);
        ReflectionTestUtils.setField(service, "projectMemberMapper", memberMapper);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "notifyRuleMapper", notifyRuleMapper);
        doAnswer(invocation -> {
            ((PmsProjectDO) invocation.getArgument(0)).setProjectId(10L);
            return 1;
        }).when(projectMapper).insert(any(PmsProjectDO.class));
        PmsNotifyRuleDO templateRule = new PmsNotifyRuleDO();
        templateRule.setRuleId(99L);
        templateRule.setModeId(7L);
        templateRule.setScopeType("mode");
        templateRule.setRuleName("任务延期通知项目经理");
        templateRule.setTriggerEvent("task_overdue");
        templateRule.setStatus("enabled");
        doReturn(List.of(templateRule)).when(notifyRuleMapper).selectList(any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class), eq(7L));
        ProjectCreateBundleReqVO request = new ProjectCreateBundleReqVO();
        PmsProjectDO project = new PmsProjectDO();
        project.setProjectCode("PRJ-MODE-001");
        request.setProject(project);
        request.setNotifyModeId(7L);
        assertEquals(10L, service.createProjectBundle(request));
        var captor = org.mockito.ArgumentCaptor.forClass(PmsNotifyRuleDO.class);
        verify(notifyRuleMapper).insert(captor.capture());
        PmsNotifyRuleDO snapshot = captor.getValue();
        assertEquals(10L, snapshot.getProjectId());
        assertEquals("project", snapshot.getScopeType());
        assertEquals(7L, snapshot.getSourceModeId());
        assertEquals(null, snapshot.getRuleId());
    }
}
