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
}
