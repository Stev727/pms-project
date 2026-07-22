package cn.iocoder.yudao.module.pms.service.notifyrule.impl;

import cn.iocoder.yudao.module.pms.dal.dataobject.notifyrule.PmsNotifyRuleDO;
import cn.iocoder.yudao.module.pms.dal.mysql.notifyrule.NotifyRuleMapper;
import cn.iocoder.yudao.module.pms.service.notifyrule.NotifyRuleService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

@Service
public class NotifyRuleServiceImpl implements NotifyRuleService {

    @Resource
    private NotifyRuleMapper notifyRuleMapper;

    @Override
    public Long createNotifyRule(PmsNotifyRuleDO entity) {
        notifyRuleMapper.insert(entity);
        return entity.getRuleId();
    }

    @Override
    public void updateNotifyRule(PmsNotifyRuleDO entity) {
        notifyRuleMapper.updateById(entity);
    }

    @Override
    public void deleteNotifyRule(Long id) {
        notifyRuleMapper.deleteById(id);
    }

    @Override
    public PmsNotifyRuleDO getNotifyRule(Long id) {
        return notifyRuleMapper.selectById(id);
    }

    @Override
    public List<PmsNotifyRuleDO> getNotifyRuleList() {
        return notifyRuleMapper.selectList(null);
    }

}