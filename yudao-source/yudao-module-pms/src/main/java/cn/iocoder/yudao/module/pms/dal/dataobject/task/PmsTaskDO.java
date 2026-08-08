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
 *
 * ============================ 改造说明 ============================
 * 版本：v2（在线上原文件基础上改造，原有字段一个未动，仅在末尾追加新字段）
 * 改造内容：
 *   【#1 子任务层级】
 *     + level        任务层级（1=顶层，最多 3 级）           对应列 `level`
 *     + reviewerId   审核人（子任务默认=父任务主责任人）      对应列 reviewer_id
 *   【#3 任务派发审核】
 *     + assignerId    派发人                                 对应列 assigner_id
 *     + reviewStatus  审核状态 none/submitted/completed/rejected  对应列 review_status
 *     + reviewComment 审核意见 / 驳回原因                      对应列 review_comment
 *     + reviewPolicy  任务级审核策略覆盖（空=跟随项目）         对应列 review_policy
 * 依赖 DDL：sql/01_子任务层级.sql、sql/03_派发审核.sql
 * ==================================================================
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

    /**
     * 完成说明
     */
    private String completionNote;

    // ==================== #1 子任务层级（新增） ====================

    /**
     * 任务层级：1=顶层任务，2/3=子任务，最大 3 级。
     * 由后端按父任务自动计算，前端不需要传。
     */
    private Integer level;

    /**
     * 审核人ID。
     * 子任务默认 = 父任务的主责任人；顶层任务默认 = 项目经理。
     * 创建 / 派发时若未显式指定，由后端自动解析填充。
     */
    private Long reviewerId;

    // ==================== #3 任务派发审核（新增） ====================

    /**
     * 派发人ID：执行「派发任务」操作的用户
     */
    private Long assignerId;

    /**
     * 审核状态：none 未提交 / submitted 待审核 / completed 已通过 / rejected 已驳回。
     * 取值见 PmsTaskReviewStatusEnum
     */
    private String reviewStatus;

    /**
     * 审核意见 / 驳回原因。驳回时必填
     */
    private String reviewComment;

    /**
     * 任务级审核策略覆盖：need_review / self_review / skip。
     * 为空表示跟随项目级 pms_project.review_policy。取值见 PmsReviewPolicyEnum
     */
    private String reviewPolicy;

}

