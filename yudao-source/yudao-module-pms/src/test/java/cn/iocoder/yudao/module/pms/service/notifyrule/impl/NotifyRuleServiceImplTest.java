package cn.iocoder.yudao.module.pms.service.notifyrule.impl;

import cn.iocoder.yudao.module.pms.dal.dataobject.notifyrule.PmsNotifyRuleDO;
import cn.iocoder.yudao.module.pms.dal.mysql.notifyrule.NotifyRuleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotifyRuleServiceImplTest {
    private NotifyRuleServiceImpl service;
    private NotifyRuleMapper mapper;

    @BeforeEach
    void setUp() {
        service = new NotifyRuleServiceImpl();
        mapper = mock(NotifyRuleMapper.class);
        ReflectionTestUtils.setField(service, "notifyRuleMapper", mapper);
    }

    @Test
    void taskRuleOverridesProjectRuleForSameEvent() {
        PmsNotifyRuleDO projectRule = rule(1L, 10L, null, "task_overdue");
        PmsNotifyRuleDO taskRule = rule(2L, 10L, 20L, "task_overdue");
        when(mapper.selectList(null)).thenReturn(List.of(projectRule, taskRule));
        assertEquals(List.of(taskRule), service.getEffectiveRules(10L, 20L, "task_overdue"));
    }

    @Test
    void projectRuleIsInheritedWhenTaskHasNoOverride() {
        PmsNotifyRuleDO projectRule = rule(1L, 10L, null, "change_approved");
        when(mapper.selectList(null)).thenReturn(List.of(projectRule));
        assertEquals(List.of(projectRule), service.getEffectiveRules(10L, 20L, "change_approved"));
    }

    @Test
    void noMatchingRuleFailsClosed() {
        when(mapper.selectList(null)).thenReturn(List.of());
        assertTrue(service.getEffectiveRules(10L, 20L, "task_created").isEmpty());
    }

    private static PmsNotifyRuleDO rule(Long id, Long projectId, Long taskId, String event) {
        PmsNotifyRuleDO rule = new PmsNotifyRuleDO();
        rule.setRuleId(id);
        rule.setProjectId(projectId);
        rule.setTaskId(taskId);
        rule.setScopeType(taskId == null ? "project" : "task");
        rule.setTriggerEvent(event);
        rule.setStatus("enabled");
        return rule;
    }
}
