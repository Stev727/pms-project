package cn.iocoder.yudao.module.pms.dal.dataobject.project;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 项目 DO
 */
@TableName("pms_project")
@Data
@EqualsAndHashCode(callSuper = true)
public class PmsProjectDO extends TenantBaseDO {
    /**
     * 项目主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long projectId;

    /**
     * 项目编号
     */
    private String projectCode;

    /**
     * 项目名称
     */
    private String projectName;

    /**
     * 项目类型
     */
    private String projectType;

    /**
     * 项目状态
     */
    private String status;

    /**
     * 当前阶段
     */
    private String currentStage;

    /**
     * 优先级
     */
    private String priority;

    /**
     * 进度百分比
     */
    private Integer progress;

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
     * 项目经理ID
     */
    private Long projectManagerId;

    /**
     * 责任部门ID
     */
    private Long deptId;

    /**
     * 创建方式
     */
    private String createMethod;

    /**
     * 模板ID
     */
    private Long templateId;

    /**
     * 项目预算
     */
    private BigDecimal budget;

    /**
     * 项目描述
     */
    private String description;

    /**
     * 附件路径
     */
    private String attachmentPath;

    /**
     * 是否重点项目
     */
    private Boolean isKeyProject;

    /**
     * 是否已归档
     */
    private Boolean archived;

}