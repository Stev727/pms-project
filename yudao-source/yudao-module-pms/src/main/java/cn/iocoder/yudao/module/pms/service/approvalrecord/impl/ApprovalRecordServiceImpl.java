package cn.iocoder.yudao.module.pms.service.approvalrecord.impl;

import cn.iocoder.yudao.module.pms.dal.dataobject.approvalrecord.PmsApprovalRecordDO;
import cn.iocoder.yudao.module.pms.dal.mysql.approvalrecord.ApprovalRecordMapper;
import cn.iocoder.yudao.module.pms.service.approvalrecord.ApprovalRecordService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

@Service
public class ApprovalRecordServiceImpl implements ApprovalRecordService {

    @Resource
    private ApprovalRecordMapper approvalRecordMapper;

    @Override
    public Long createApprovalRecord(PmsApprovalRecordDO entity) {
        approvalRecordMapper.insert(entity);
        return entity.getApprovalId();
    }

    @Override
    public void updateApprovalRecord(PmsApprovalRecordDO entity) {
        approvalRecordMapper.updateById(entity);
    }

    @Override
    public void deleteApprovalRecord(Long id) {
        approvalRecordMapper.deleteById(id);
    }

    @Override
    public PmsApprovalRecordDO getApprovalRecord(Long id) {
        return approvalRecordMapper.selectById(id);
    }

    @Override
    public List<PmsApprovalRecordDO> getApprovalRecordList() {
        return approvalRecordMapper.selectList(null);
    }

}