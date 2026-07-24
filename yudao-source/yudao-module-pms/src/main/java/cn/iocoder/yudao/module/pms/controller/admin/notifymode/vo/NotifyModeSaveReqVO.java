package cn.iocoder.yudao.module.pms.controller.admin.notifymode.vo;

import cn.iocoder.yudao.module.pms.dal.dataobject.notifymode.PmsNotifyModeDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.notifyrule.PmsNotifyRuleDO;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class NotifyModeSaveReqVO {
    private PmsNotifyModeDO mode;
    private List<PmsNotifyRuleDO> rules = new ArrayList<>();
}
