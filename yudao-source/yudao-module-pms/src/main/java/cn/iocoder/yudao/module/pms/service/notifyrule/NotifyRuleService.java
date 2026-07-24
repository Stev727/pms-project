package cn.iocoder.yudao.module.pms.service.notifyrule;

import cn.iocoder.yudao.module.pms.dal.dataobject.notifyrule.PmsNotifyRuleDO;
import java.util.List;

/**
 * 通知规则 Service 接口
 */
public interface NotifyRuleService {

    Long createNotifyRule(PmsNotifyRuleDO entity);

    void updateNotifyRule(PmsNotifyRuleDO entity);

    void deleteNotifyRule(Long id);

    PmsNotifyRuleDO getNotifyRule(Long id);

    List<PmsNotifyRuleDO> getNotifyRuleList();

    List<PmsNotifyRuleDO> getEffectiveRules(Long projectId, Long taskId, String triggerEvent);

}