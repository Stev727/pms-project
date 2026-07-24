package cn.iocoder.yudao.module.pms.service.dingtalk.impl;

import cn.iocoder.yudao.module.pms.dal.dataobject.dingtalk.PmsDingTalkConfigDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.dingtalk.PmsDingTalkUserDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.notifylog.PmsNotifyLogDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.notifyrule.PmsNotifyRuleDO;
import cn.iocoder.yudao.module.pms.dal.mysql.dingtalk.PmsDingTalkUserMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.notifylog.NotifyLogMapper;
import cn.iocoder.yudao.module.pms.dal.dataobject.project.PmsProjectDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import cn.iocoder.yudao.module.pms.dal.mysql.project.ProjectMapper;
import org.junit.jupiter.api.Test;
import cn.iocoder.yudao.module.pms.dal.mysql.notifyrule.NotifyRuleMapper;
import cn.iocoder.yudao.module.pms.service.dingtalk.DingTalkApiService;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DingTalkNotifyServiceImplTest {

    @Test
    void overdueReceiversContainOnlyProjectManager() {
        DingTalkNotifyServiceImpl service = new DingTalkNotifyServiceImpl();
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        ReflectionTestUtils.setField(service, "projectMapper", projectMapper);
        PmsProjectDO project = new PmsProjectDO();
        project.setProjectId(10L);
        project.setProjectManagerId(1353L);
        when(projectMapper.selectById(10L)).thenReturn(project);

        PmsTaskDO task = new PmsTaskDO();
        task.setProjectId(10L);
        task.setMainOwnerId(2001L);
        task.setHelperIds("2002,2003");

        assertEquals(List.of(1353L), service.getOverdueReceiverIds(task));
    }

    @Test
    void ruleNotificationLogKeepsTaskBusinessId() {
        DingTalkNotifyServiceImpl service = new DingTalkNotifyServiceImpl();
        NotifyRuleMapper ruleMapper = mock(NotifyRuleMapper.class);
        DingTalkApiService apiService = mock(DingTalkApiService.class);
        PmsDingTalkUserMapper userMapper = mock(PmsDingTalkUserMapper.class);
        NotifyLogMapper logMapper = mock(NotifyLogMapper.class);
        ReflectionTestUtils.setField(service, "notifyRuleMapper", ruleMapper);
        ReflectionTestUtils.setField(service, "dingTalkApiService", apiService);
        ReflectionTestUtils.setField(service, "dingTalkUserMapper", userMapper);
        ReflectionTestUtils.setField(service, "notifyLogMapper", logMapper);

        PmsNotifyRuleDO rule = new PmsNotifyRuleDO();
        rule.setRuleId(1L);
        rule.setStatus("enabled");
        rule.setTriggerEvent("task_overdue");
        when(ruleMapper.selectById(1L)).thenReturn(rule);
        PmsDingTalkConfigDO config = new PmsDingTalkConfigDO();
        config.setNotifyEnabled(true);
        when(apiService.getConfig()).thenReturn(config);
        PmsDingTalkUserDO user = new PmsDingTalkUserDO();
        user.setDingtalkUserId("1353");
        when(userMapper.selectListByUserIds(List.of(1353L))).thenReturn(List.of(user));
        when(apiService.sendWorkNotification(any(), any(), any())).thenReturn("ding-task-1");

        service.sendNotifyByRule(1L, Map.of(), List.of(1353L), "task", 20L);

        org.mockito.ArgumentCaptor<PmsNotifyLogDO> captor =
                org.mockito.ArgumentCaptor.forClass(PmsNotifyLogDO.class);
        verify(logMapper).insert(captor.capture());
        assertEquals(20L, captor.getValue().getBusinessId());
        assertEquals("task", captor.getValue().getBusinessType());
        assertEquals("task_overdue", captor.getValue().getTriggerEvent());
    }
}
