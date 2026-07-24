package cn.iocoder.yudao.module.pms.service.changerecord.impl;

import cn.iocoder.yudao.module.pms.dal.dataobject.changerecord.PmsChangeRecordDO;
import cn.iocoder.yudao.module.pms.dal.mysql.changerecord.ChangeRecordMapper;
import cn.iocoder.yudao.module.pms.service.changerecord.ChangeRecordService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChangeRecordServiceImpl implements ChangeRecordService {

    @Resource
    private ChangeRecordMapper changeRecordMapper;

    @Override
    public Long createChangeRecord(PmsChangeRecordDO entity) {
        entity.setApprovalStatus("pending");
        entity.setChangeStatus("pending_review");
        changeRecordMapper.insert(entity);
        return entity.getChangeId();
    }

    @Override
    public void reviewChange(Long id, boolean approved, Long approverId) {
        PmsChangeRecordDO record = requireRecord(id);
        if (!"pending".equals(record.getApprovalStatus())) {
            throw new IllegalStateException("只有待审核变更可以审核");
        }
        record.setApproverId(approverId);
        record.setApprovalStatus(approved ? "approved" : "rejected");
        record.setChangeStatus(approved ? "approved" : "rejected");
        changeRecordMapper.updateById(record);
    }

    @Override
    public void executeApprovedChange(Long id) {
        PmsChangeRecordDO record = requireRecord(id);
        if (!"approved".equals(record.getApprovalStatus()) || !"approved".equals(record.getChangeStatus())) {
            throw new IllegalStateException("只有审核通过的变更可以执行");
        }
        record.setChangeStatus("executed");
        record.setExecuteTime(LocalDateTime.now());
        changeRecordMapper.updateById(record);
    }

    private PmsChangeRecordDO requireRecord(Long id) {
        PmsChangeRecordDO record = changeRecordMapper.selectById(id);
        if (record == null) {
            throw new IllegalArgumentException("变更记录不存在");
        }
        return record;
    }

    @Override
    public void updateChangeRecord(PmsChangeRecordDO entity) {
        changeRecordMapper.updateById(entity);
    }

    @Override
    public void deleteChangeRecord(Long id) {
        changeRecordMapper.deleteById(id);
    }

    @Override
    public PmsChangeRecordDO getChangeRecord(Long id) {
        return changeRecordMapper.selectById(id);
    }

    @Override
    public List<PmsChangeRecordDO> getChangeRecordList() {
        return changeRecordMapper.selectList(null);
    }

}