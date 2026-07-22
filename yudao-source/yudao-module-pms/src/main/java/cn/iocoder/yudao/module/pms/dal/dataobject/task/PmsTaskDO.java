package cn.iocoder.yudao.module.pms.dal.dataobject.task;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 任务 DO
 */
@TableName("pms_task")
@Data
@EqualsAndHashCode(callSuper = true)
public class PmsTaskDO extends TenantBaseDO {
    /**
     * 任务主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long taskId;

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 阶段ID
     */
    private Long stageId;

    /**
     * 父任务ID
     */
    private Long parentTaskId;

    /**
     * 任务编号
     */
    private String taskCode;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 任务描述
     */
    private String description;

    /**
     * 任务类型
     */
    private String taskType;

    /**
     * 任务周期
     */
    private Integer cycle;

    /**
     * 优先级
     */
    private String priority;

    /**
     * 计划开始日期
     */
    private LocalDate planStartDate;

    /**
     * 计划完成日期
     */
    private LocalDate planEndDate;

    /**
     * 延迟日期
     */
    private LocalDate delayDate;

    /**
     * 实际完成日期
     */
    private LocalDate actualCompleteDate;

    /**
     * 输出物要求
     */
    private String outputRequirement;

    /**
     * 完成标准
     */
    private String completionStandard;

    /**
     * 主责任人ID
     */
    private Long mainOwnerId;

    /**
     * 协助人ID列表
     */
    private String helperIds;

    /**
     * 责任部门ID
     */
    private Long deptId;

    /**
     * 是否关键路径
     */
    private Boolean isCriticalPath;

    /**
     * 是否里程碑
     */
    private Boolean isMilestone;

    /**
     * 完成状态
     */
    private String completeStatus;

    /**
     * 是否已派发
     */
    private Boolean isDispatched;

    /**
     * 派发时间
     */
    private LocalDateTime dispatchTime;

    /**
     * 是否延迟提醒
     */
    private Boolean delayRemind;

    /**
     * 延迟级别
     */
    private String delayLevel;

    /**
     * 异常原因
     */
    private String exceptionReason;

    /**
     * 改善方案
     */
    private String improvementPlan;

    /**
     * 预估工时
     */
    private BigDecimal estimatedHours;

    /**
     * 实际工时
     */
    private BigDecimal actualHours;

    /**
     * 审批记录ID
     */
    private Long approvalId;

    /**
     * 排序号
     */
    private Integer sortOrder;

    /**
     * 任务进度
     */
    private Integer progress;

    /**
     * 完成确认意见
     */
    private String reviewOpinion;

}