package cn.iocoder.yudao.module.pms.dal.dataobject.externalworklog;

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
 * 外部工时 DO
 */
@TableName("pms_external_worklog")
@Data
@EqualsAndHashCode(callSuper = true)
public class PmsExternalWorklogDO extends TenantBaseDO {
    /**
     * 工时日志主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long worklogId;

    /**
     * 关联项目ID
     */
    private Long projectId;

    /**
     * 关联任务ID
     */
    private Long taskId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 工作日期
     */
    private LocalDate workDate;

    /**
     * 工时数
     */
    private BigDecimal workHours;

    /**
     * 工作内容
     */
    private String workContent;

    /**
     * 结算标记
     */
    private Boolean settleFlag;

    /**
     * 结算时间
     */
    private LocalDateTime settleTime;

    /**
     * 结算金额
     */
    private BigDecimal settleAmount;

}