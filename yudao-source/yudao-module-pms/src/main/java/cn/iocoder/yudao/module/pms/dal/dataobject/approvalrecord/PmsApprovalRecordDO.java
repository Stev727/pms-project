package cn.iocoder.yudao.module.pms.dal.dataobject.approvalrecord;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/**
 * 审批记录 DO
 */
@TableName("pms_approval_record")
@Data
@EqualsAndHashCode(callSuper = true)
public class PmsApprovalRecordDO extends TenantBaseDO {
    /**
     * 审批主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long approvalId;

    /**
     * 关联项目ID
     */
    private Long projectId;

    /**
     * 关联任务ID
     */
    private Long taskId;

    /**
     * 关联变更ID
     */
    private Long changeId;

    /**
     * 审批类型
     */
    private String approvalType;

    /**
     * 审批单号
     */
    private String approvalNo;

    /**
     * 审批状态
     */
    private String approvalStatus;

    /**
     * 发起人ID
     */
    private Long initiatorId;

    /**
     * 发起时间
     */
    private LocalDateTime initiateTime;

    /**
     * 完成时间
     */
    private LocalDateTime completeTime;

    /**
     * 审批意见
     */
    private String approvalOpinion;

    /**
     * 审批人ID
     */
    private Long approverId;

    /**
     * 审批人姓名
     */
    private String approverName;

    /**
     * OA回调状态
     */
    private String oaCallbackStatus;

    /**
     * OA回调时间
     */
    private LocalDateTime oaCallbackTime;

}