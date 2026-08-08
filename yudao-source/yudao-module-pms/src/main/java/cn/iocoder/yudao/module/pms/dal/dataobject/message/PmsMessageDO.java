package cn.iocoder.yudao.module.pms.dal.dataobject.message;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PMS 站内消息 DO（#4 站内消息中心）
 *
 * <p>用途：保存 PMS 业务事件触发的站内消息（任务派发/审核/逾期等），
 * 支持前端"消息铃铛 + 未读红点 + 列表抽屉 + 标记已读"。
 *
 * <p>与 yudao 自带 system 站内信（{@code system_notify_message}）的区别：
 * <ul>
 *   <li>yudao 系统站内信基于"模板 + 占位符"，需要预先维护模板</li>
 *   <li>PMS 业务消息更贴近业务（关联 bizType/bizId），可直接点击跳转任务详情</li>
 *   <li>不与系统站内信互斥，两者并存；前端铃铛单独呈现 PMS 业务消息</li>
 * </ul>
 */
@TableName("pms_message")
@Data
@EqualsAndHashCode(callSuper = true)
public class PmsMessageDO extends TenantBaseDO {

    /**
     * 消息主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long messageId;

    /**
     * 接收人系统用户ID
     */
    private Long receiverId;

    /**
     * 消息标题
     */
    private String title;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 业务类型：task / project / change / quality 等
     */
    private String bizType;

    /**
     * 业务ID（如 task_id），用于前端点击跳转
     */
    private Long bizId;

    /**
     * 触发事件（与 PmsNotifyRule.triggerEvent 对齐）：
     * task_dispatched / task_review_submitted / task_review_approved /
     * task_review_rejected / task_review_auto_passed / task_overdue / ...
     */
    private String triggerEvent;

    /**
     * 阅读状态：0 未读 / 1 已读
     */
    private Integer readStatus;

    /**
     * 阅读时间
     */
    private LocalDateTime readTime;

}

