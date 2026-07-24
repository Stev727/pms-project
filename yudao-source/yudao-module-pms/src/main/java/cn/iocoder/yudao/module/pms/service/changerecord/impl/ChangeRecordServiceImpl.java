package cn.iocoder.yudao.module.pms.service.changerecord.impl;

import cn.iocoder.yudao.module.pms.dal.dataobject.changerecord.PmsChangeRecordDO;
import cn.iocoder.yudao.module.pms.dal.mysql.changerecord.ChangeRecordMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.dal.dataobject.project.PmsProjectDO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.service.changerecord.ChangeRecordService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChangeRecordServiceImpl implements ChangeRecordService {

    @Resource
    private ChangeRecordMapper changeRecordMapper;
    @Resource
    private ProjectMapper projectMapper;

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
        requireProjectManager(record, approverId);
        if (!"pending".equals(record.getApprovalStatus())) {
            throw new ServiceException(cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.CHANGE_STATUS_INVALID);
        }
        record.setApproverId(approverId);
        record.setApprovalStatus(approved ? "approved" : "rejected");
        record.setChangeStatus(approved ? "approved" : "rejected");
        changeRecordMapper.updateById(record);
    }

    @Override
    public void executeApprovedChange(Long id, Long operatorId) {
        PmsChangeRecordDO record = requireRecord(id);
        requireProjectManager(record, operatorId);
        if (!"approved".equals(record.getApprovalStatus()) || !"approved".equals(record.getChangeStatus())) {
            throw new ServiceException(cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.CHANGE_STATUS_INVALID);
        }
        record.setChangeStatus("executed");
        record.setExecuteTime(LocalDateTime.now());
        changeRecordMapper.updateById(record);
    }

    private void requireProjectManager(PmsChangeRecordDO record, Long operatorId) {
        PmsProjectDO project = projectMapper.selectById(record.getProjectId());
        if (project == null || !java.util.Objects.equals(project.getProjectManagerId(), operatorId)) {
            throw new ServiceException(cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.PROJECT_MANAGER_REQUIRED);
        }
    }

    private PmsChangeRecordDO requireRecord(Long id) {
        PmsChangeRecordDO record = changeRecordMapper.selectById(id);
        if (record == null) {
            throw new ServiceException(cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.CHANGE_NOT_EXISTS);
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