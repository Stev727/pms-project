package cn.iocoder.yudao.module.pms.service.approvalrecord;

import cn.iocoder.yudao.module.pms.dal.dataobject.approvalrecord.PmsApprovalRecordDO;
import java.util.List;

/**
 * 审批记录 Service 接口
 */
public interface ApprovalRecordService {

    Long createApprovalRecord(PmsApprovalRecordDO entity);

    void updateApprovalRecord(PmsApprovalRecordDO entity);

    void deleteApprovalRecord(Long id);

    PmsApprovalRecordDO getApprovalRecord(Long id);

    List<PmsApprovalRecordDO> getApprovalRecordList();

}