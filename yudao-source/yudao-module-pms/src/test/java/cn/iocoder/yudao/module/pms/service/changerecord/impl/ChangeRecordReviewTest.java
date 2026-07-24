package cn.iocoder.yudao.module.pms.service.changerecord.impl;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.dal.dataobject.changerecord.PmsChangeRecordDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.project.PmsProjectDO;
import cn.iocoder.yudao.module.pms.dal.mysql.changerecord.ChangeRecordMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.project.ProjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChangeRecordReviewTest {
    @Test
    void projectManagerApprovesAndExecutesChange() {
        ChangeRecordServiceImpl service = serviceWithManager(1353L);
        ChangeRecordMapper mapper = (ChangeRecordMapper) ReflectionTestUtils.getField(service, "changeRecordMapper");
        PmsChangeRecordDO record = record("pending", "pending_review");
        when(mapper.selectById(1L)).thenReturn(record);
        service.reviewChange(1L, true, 1353L);
        service.executeApprovedChange(1L, 1353L);
        assertEquals("executed", record.getChangeStatus());
        verify(mapper, times(2)).updateById(any(PmsChangeRecordDO.class));
    }

    @Test
    void nonProjectManagerCannotReviewOrExecute() {
        ChangeRecordServiceImpl service = serviceWithManager(1353L);
        ChangeRecordMapper mapper = (ChangeRecordMapper) ReflectionTestUtils.getField(service, "changeRecordMapper");
        when(mapper.selectById(1L)).thenReturn(record("pending", "pending_review"));
        assertThrows(ServiceException.class, () -> service.reviewChange(1L, true, 1L));
        when(mapper.selectById(1L)).thenReturn(record("approved", "approved"));
        assertThrows(ServiceException.class, () -> service.executeApprovedChange(1L, 1L));
    }

    @Test
    void missingChangeReturnsServiceError() {
        ChangeRecordServiceImpl service = serviceWithManager(1353L);
        assertThrows(ServiceException.class, () -> service.reviewChange(404L, true, 1353L));
    }

    private static ChangeRecordServiceImpl serviceWithManager(Long managerId) {
        ChangeRecordServiceImpl service = new ChangeRecordServiceImpl();
        ChangeRecordMapper mapper = mock(ChangeRecordMapper.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        ReflectionTestUtils.setField(service, "changeRecordMapper", mapper);
        ReflectionTestUtils.setField(service, "projectMapper", projectMapper);
        PmsProjectDO project = new PmsProjectDO();
        project.setProjectId(10L);
        project.setProjectManagerId(managerId);
        when(projectMapper.selectById(10L)).thenReturn(project);
        return service;
    }

    private static PmsChangeRecordDO record(String approval, String status) {
        PmsChangeRecordDO record = new PmsChangeRecordDO();
        record.setChangeId(1L);
        record.setProjectId(10L);
        record.setApprovalStatus(approval);
        record.setChangeStatus(status);
        return record;
    }
}
