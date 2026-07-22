package cn.iocoder.yudao.module.pms.dal.dataobject.notifylog;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/**
 * 通知日志 DO
 */
@TableName("pms_notify_log")
@Data
@EqualsAndHashCode(callSuper = true)
public class PmsNotifyLogDO extends TenantBaseDO {
    /**
     * 日志主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long logId;

    /**
     * 通知规则ID
     */
    private Long ruleId;

    /**
     * 触发事件
     */
    private String triggerEvent;

    /**
     * 通知对象
     */
    private String notifyTarget;

    /**
     * 通知对象名称
     */
    private String targetName;

    /**
     * 发送渠道
     */
    private String channel;

    /**
     * 发送时间
     */
    private LocalDateTime sendTime;

    /**
     * 发送状态
     */
    private String sendStatus;

    /**
     * 发送结果
     */
    private String sendResult;

    /**
     * 消息标题
     */
    private String title;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 关联业务类型
     */
    private String businessType;

    /**
     * 关联业务ID
     */
    private Long businessId;

    /**
     * 阅读时间
     */
    private LocalDateTime readTime;

}