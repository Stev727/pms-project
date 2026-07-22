package cn.iocoder.yudao.module.pms.dal.dataobject.performancemonthly;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

/**
 * 月度绩效 DO
 */
@TableName("pms_performance_monthly")
@Data
@EqualsAndHashCode(callSuper = true)
public class PmsPerformanceMonthlyDO extends TenantBaseDO {
    /**
     * 绩效主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long performanceId;

    /**
     * 统计月份
     */
    private String statMonth;

    /**
     * 责任人用户ID
     */
    private Long userId;

    /**
     * 部门ID
     */
    private Long deptId;

    /**
     * 负责任务总数
     */
    private Integer responsibleTaskCount;

    /**
     * 已完成任务数
     */
    private Integer completedCount;

    /**
     * 按时完成数
     */
    private Integer onTimeCount;

    /**
     * 延期任务数
     */
    private Integer delayedCount;

    /**
     * 按时完成率
     */
    private BigDecimal onTimeRate;

    /**
     * 延期天数合计
     */
    private Integer totalDelayDays;

    /**
     * 平均延期天数
     */
    private BigDecimal avgDelayDays;

    /**
     * 协助任务数
     */
    private Integer assistTaskCount;

    /**
     * 协助完成数
     */
    private Integer assistCompletedCount;

    /**
     * 协助延期数
     */
    private Integer assistDelayedCount;

    /**
     * 参与项目ID列表
     */
    private String projectIds;

    /**
     * 质量问题数
     */
    private Integer qualityIssueCount;

    /**
     * 变更数
     */
    private Integer changeCount;

}