package cn.iocoder.yudao.module.pms.service.changerecord.impl;

import cn.iocoder.yudao.module.pms.dal.dataobject.changerecord.PmsChangeRecordDO;
import cn.iocoder.yudao.module.pms.dal.mysql.changerecord.ChangeRecordMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChangeRecordReviewTest {

    @Test
    void approveThenProjectManagerExecutesChange() {
        ChangeRecordMapper mapper = mock(ChangeRecordMapper.class);
        ChangeRecordServiceImpl service = new ChangeRecordServiceImpl();
        ReflectionTestUtils.setField(service, "changeRecordMapper", mapper);
        PmsChangeRecordDO record = new PmsChangeRecordDO();
        record.setChangeId(1L);
        record.setApprovalStatus("pending");
        record.setChangeStatus("pending_review");
        when(mapper.selectById(1L)).thenReturn(record);

        service.reviewChange(1L, true, 1353L);
        assertEquals("approved", record.getApprovalStatus());
        assertEquals("approved", record.getChangeStatus());
        assertEquals(1353L, record.getApproverId());

        service.executeApprovedChange(1L);
        assertEquals("executed", record.getChangeStatus());
        verify(mapper, times(2)).updateById(any(PmsChangeRecordDO.class));
    }

    @Test
    void rejectedChangeCannotBeExecuted() {
        ChangeRecordMapper mapper = mock(ChangeRecordMapper.class);
        ChangeRecordServiceImpl service = new ChangeRecordServiceImpl();
        ReflectionTestUtils.setField(service, "changeRecordMapper", mapper);
        PmsChangeRecordDO record = new PmsChangeRecordDO();
        record.setChangeId(1L);
        record.setApprovalStatus("rejected");
        when(mapper.selectById(1L)).thenReturn(record);
        assertThrows(IllegalStateException.class, () -> service.executeApprovedChange(1L));
    }
}
