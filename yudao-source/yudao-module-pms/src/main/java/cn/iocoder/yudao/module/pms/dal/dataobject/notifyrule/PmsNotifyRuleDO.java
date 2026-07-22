package cn.iocoder.yudao.module.pms.dal.dataobject.notifyrule;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalTime;

/**
 * 通知规则 DO
 */
@TableName("pms_notify_rule")
@Data
@EqualsAndHashCode(callSuper = true)
public class PmsNotifyRuleDO extends TenantBaseDO {
    /**
     * 规则主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long ruleId;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 触发事件
     */
    private String triggerEvent;

    /**
     * 触发条件
     */
    private String triggerCondition;

    /**
     * 通知对象
     */
    private String notifyTarget;

    /**
     * 通知渠道
     */
    private String notifyChannel;

    /**
     * 时间规则
     */
    private String timeRule;

    /**
     * 模板标题
     */
    private String templateTitle;

    /**
     * 模板正文
     */
    private String templateContent;

    /**
     * 状态
     */
    private String status;

    /**
     * 是否免打扰
     */
    private Boolean doNotDisturb;

    /**
     * 免打扰开始
     */
    private LocalTime dndStartTime;

    /**
     * 免打扰结束
     */
    private LocalTime dndEndTime;

    /**
     * 是否启用升级
     */
    private Boolean escalationFlag;

    /**
     * 升级条件
     */
    private String escalationCondition;

    /**
     * 升级通知对象
     */
    private String escalationTarget;

    /**
     * 升级通知渠道
     */
    private String escalationChannel;

    /**
     * 升级通知模板
     */
    private String escalationTemplate;

}