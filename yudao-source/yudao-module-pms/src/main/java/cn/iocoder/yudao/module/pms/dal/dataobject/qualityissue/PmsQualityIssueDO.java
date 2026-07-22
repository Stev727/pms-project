package cn.iocoder.yudao.module.pms.dal.dataobject.qualityissue;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/**
 * 质量问题 DO
 */
@TableName("pms_quality_issue")
@Data
@EqualsAndHashCode(callSuper = true)
public class PmsQualityIssueDO extends TenantBaseDO {
    /**
     * 问题主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long issueId;

    /**
     * 关联项目ID
     */
    private Long projectId;

    /**
     * 关联任务ID
     */
    private Long taskId;

    /**
     * 问题编号
     */
    private String issueCode;

    /**
     * 问题描述
     */
    private String issueDescription;

    /**
     * 严重度
     */
    private String severity;

    /**
     * 影响范围
     */
    private String impactScope;

    /**
     * 根因分类
     */
    private String rootCauseCategory;

    /**
     * 根因详细
     */
    private String rootCauseDetail;

    /**
     * 指派人ID
     */
    private Long assigneeId;

    /**
     * 解决人ID
     */
    private Long resolverId;

    /**
     * 验证人ID
     */
    private Long verifierId;

    /**
     * 问题状态
     */
    private String status;

    /**
     * 解决方案
     */
    private String solution;

    /**
     * 验证结果
     */
    private String verifyResult;

    /**
     * 复发标记
     */
    private Boolean recurFlag;

    /**
     * 关闭时间
     */
    private LocalDateTime closeTime;

    /**
     * 责任部门ID
     */
    private Long deptId;

}