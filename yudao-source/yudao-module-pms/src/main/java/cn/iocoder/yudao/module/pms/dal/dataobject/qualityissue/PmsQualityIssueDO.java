package cn.iocoder.yudao.module.pms.dal.dataobject.qualityissue;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 质量问题 DO
 *
 * 改造说明（#8 质量问题 Excel 批量导入）：
 *  - 末尾追加 5 个字段：issueTitle / issueType / discoveredDate / discovererId / dueDate
 *  - 配套 SQL：generated/sql/08_质量导入.sql（幂等 ALTER TABLE）
 *  - discoveredDate / dueDate 使用 LocalDate（按契约 §1.3 日期类型约定）
 *  - discovererId 与 assigneeId 同为 Long，分别记录"发现人"和"责任人/指派人"系统用户ID
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
     * 责任人（姓名字符串，保留用于显示；匹配到的 userId 写入 assigneeId）
     */
    private String responsiblePerson;

    /**
     * 来源
     */
    private String source;

    /**
     * 指派人ID（导入场景下复用为"责任人 userId"，用于钉钉通知）
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

    // ==================== #8 新增字段 ====================

    /**
     * 问题标题（#8 新增）
     */
    private String issueTitle;

    /**
     * 问题类型（#8 新增，如 需求缺陷/实现缺陷/流程缺陷/测试遗漏）
     */
    private String issueType;

    /**
     * 发现日期（#8 新增）
     */
    private LocalDate discoveredDate;

    /**
     * 发现人ID（#8 新增，按姓名匹配 system_users.nickname 后写入）
     */
    private Long discovererId;

    /**
     * 期望完成日期（#8 新增）
     */
    private LocalDate dueDate;

}

