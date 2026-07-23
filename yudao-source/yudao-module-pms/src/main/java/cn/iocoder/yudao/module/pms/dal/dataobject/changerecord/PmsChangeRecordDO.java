package cn.iocoder.yudao.module.pms.dal.dataobject.changerecord;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 变更记录 DO
 */
@TableName("pms_change_record")
@Data
@EqualsAndHashCode(callSuper = true)
public class PmsChangeRecordDO extends TenantBaseDO {
    /**
     * 变更主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long changeId;

    /**
     * 关联项目ID
     */
    private Long projectId;

    /**
     * 变更编号
     */
    private String changeCode;

    /**
     * 变更类型
     */
    private String changeType;

    /**
     * 变更原因
     */
    private String changeReason;

    /**
     * 变更内容
     */
    private String changeDescription;

    /**
     * 发起人ID
     */
    private Long initiatorId;

    /**
     * 审批记录ID
     */
    private Long approvalId;

    /**
     * 审批人ID
     */
    private Long approverId;

    /**
     * 审批状态
     */
    private String approvalStatus;

    /**
     * 受影响任务
     */
    private String affectedTasks;

    /**
     * 成本影响
     */
    private BigDecimal costImpact;

    /**
     * 进度影响
     */
    private Integer scheduleImpact;

    /**
     * 执行时间
     */
    private LocalDateTime executeTime;

    /**
     * 变更前内容
     */
    @TableField("before_content")
    private String beforeContent;

    /**
     * 变更后内容
     */
    @TableField("after_content")
    private String afterContent;

    /**
     * 变更状态
     */
    @TableField("change_status")
    private String changeStatus;

    /**
     * 变更前状态快照（对应数据库 before_state 列）
     */
    @TableField("before_state")
    private String beforeState;

    /**
     * 变更后状态快照（对应数据库 after_state 列）
     */
    @TableField("after_state")
    private String afterState;

}