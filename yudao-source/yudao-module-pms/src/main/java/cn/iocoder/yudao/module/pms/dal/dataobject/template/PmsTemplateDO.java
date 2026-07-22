package cn.iocoder.yudao.module.pms.dal.dataobject.template;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/**
 * 模板 DO
 */
@TableName("pms_template")
@Data
@EqualsAndHashCode(callSuper = true)
public class PmsTemplateDO extends TenantBaseDO {
    /**
     * 模板主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long templateId;

    /**
     * 模板名称
     */
    private String templateName;

    /**
     * 模板类型
     */
    private String templateType;

    /**
     * 适用阶段
     */
    private String applicableStage;

    /**
     * 版本号
     */
    private String versionNo;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 存储路径
     */
    private String storagePath;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 是否标准板
     */
    private Boolean standardFlag;

    /**
     * 状态
     */
    private String status;

    /**
     * 使用说明
     */
    private String usageInstructions;

    /**
     * 下载次数
     */
    private Integer downloadCount;

    /**
     * 上传人
     */
    private Long uploadBy;

    /**
     * 上传时间
     */
    private LocalDateTime uploadTime;

}