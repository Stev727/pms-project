package cn.iocoder.yudao.module.pms.service.changerecord.impl;

import cn.iocoder.yudao.module.pms.dal.dataobject.changerecord.PmsChangeRecordDO;
import cn.iocoder.yudao.module.pms.dal.mysql.changerecord.ChangeRecordMapper;
import cn.iocoder.yudao.module.pms.service.changerecord.ChangeRecordService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

@Service
public class ChangeRecordServiceImpl implements ChangeRecordService {

    @Resource
    private ChangeRecordMapper changeRecordMapper;

    @Override
    public Long createChangeRecord(PmsChangeRecordDO entity) {
        changeRecordMapper.insert(entity);
        return entity.getChangeId();
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