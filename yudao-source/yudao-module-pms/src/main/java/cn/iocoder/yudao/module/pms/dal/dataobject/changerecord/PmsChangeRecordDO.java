package cn.iocoder.yudao.module.pms.dal.dataobject.changerecord;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
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

}