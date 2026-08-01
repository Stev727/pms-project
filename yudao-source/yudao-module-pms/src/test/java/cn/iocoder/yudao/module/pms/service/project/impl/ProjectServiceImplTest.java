package cn.iocoder.yudao.module.pms.service.project.impl;

import cn.iocoder.yudao.module.pms.dal.dataobject.projectmember.PmsProjectMemberDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectServiceImplTest {

    @Test
    void involvedProjectsIncludeTaskAssignmentsAndActiveMemberships() {
        PmsTaskDO ownedTask = new PmsTaskDO();
        ownedTask.setProjectId(10L);

        PmsProjectMemberDO activeMember = new PmsProjectMemberDO();
        activeMember.setProjectId(20L);
        activeMember.setStatus("active");

        PmsProjectMemberDO exitedMember = new PmsProjectMemberDO();
        exitedMember.setProjectId(30L);
        exitedMember.setStatus("inactive");

        assertEquals(Set.of(10L, 20L), ProjectServiceImpl.collectInvolvedProjectIds(
                List.of(ownedTask), List.of(activeMember, exitedMember)));
    }

    @Test
    void templateTaskCopyKeepsOnlyStructureAndTaskName() {
        PmsTaskDO template = new PmsTaskDO();
        template.setTaskName("结构设计");
        template.setStageId(20L);
        template.setPlanStartDate(java.time.LocalDate.of(2026, 8, 1));
        template.setPlanEndDate(java.time.LocalDate.of(2026, 8, 8));
        template.setMainOwnerId(1353L);
        template.setHelperIds("1354");
        template.setProgress(70);

        PmsTaskDO copied = ProjectServiceImpl.newTemplateTask(template, 99L, 88L);

        assertEquals("结构设计", copied.getTaskName());
        assertEquals(99L, copied.getProjectId());
        assertEquals(88L, copied.getStageId());
        assertEquals(null, copied.getPlanStartDate());
        assertEquals(null, copied.getPlanEndDate());
        assertEquals(null, copied.getMainOwnerId());
        assertEquals(null, copied.getHelperIds());
        assertEquals(0, copied.getProgress());
        assertEquals("not_started", copied.getCompleteStatus());
    }
}
