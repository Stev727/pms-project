package cn.iocoder.yudao.module.pms.dal.dataobject.projectstage;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;

/**
 * 项目阶段 DO
 */
@TableName("pms_project_stage")
@Data
@EqualsAndHashCode(callSuper = true)
public class PmsProjectStageDO extends TenantBaseDO {
    /**
     * 阶段主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long stageId;

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 阶段名称
     */
    private String stageName;

    /**
     * 阶段编码
     */
    private String stageCode;

    /**
     * 排序号
     */
    private Integer sortOrder;

    /**
     * 阶段状态
     */
    private String status;

    /**
     * 计划开始日期
     */
    private LocalDate planStartDate;

    /**
     * 计划结束日期
     */
    private LocalDate planEndDate;

    /**
     * 实际开始日期
     */
    private LocalDate actualStartDate;

    /**
     * 实际结束日期
     */
    private LocalDate actualEndDate;

    /**
     * 是否里程碑
     */
    private Boolean isMilestone;

    /**
     * 阶段进度
     */
    private Integer progress;

}