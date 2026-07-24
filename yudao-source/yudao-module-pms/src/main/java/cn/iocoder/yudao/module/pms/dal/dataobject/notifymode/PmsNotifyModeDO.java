package cn.iocoder.yudao.module.pms.dal.dataobject.notifymode;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("pms_notify_mode")
@Data
@EqualsAndHashCode(callSuper = true)
public class PmsNotifyModeDO extends TenantBaseDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long modeId;
    private String modeName;
    private String description;
    private String status;
    private Boolean defaultFlag;
}
