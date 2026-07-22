package cn.iocoder.yudao.module.pms.service.notifylog.impl;

import cn.iocoder.yudao.module.pms.dal.dataobject.notifylog.PmsNotifyLogDO;
import cn.iocoder.yudao.module.pms.dal.mysql.notifylog.NotifyLogMapper;
import cn.iocoder.yudao.module.pms.service.notifylog.NotifyLogService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

@Service
public class NotifyLogServiceImpl implements NotifyLogService {

    @Resource
    private NotifyLogMapper notifyLogMapper;

    @Override
    public Long createNotifyLog(PmsNotifyLogDO entity) {
        notifyLogMapper.insert(entity);
        return entity.getLogId();
    }

    @Override
    public void updateNotifyLog(PmsNotifyLogDO entity) {
        notifyLogMapper.updateById(entity);
    }

    @Override
    public void deleteNotifyLog(Long id) {
        notifyLogMapper.deleteById(id);
    }

    @Override
    public PmsNotifyLogDO getNotifyLog(Long id) {
        return notifyLogMapper.selectById(id);
    }

    @Override
    public List<PmsNotifyLogDO> getNotifyLogList() {
        return notifyLogMapper.selectList(null);
    }

}