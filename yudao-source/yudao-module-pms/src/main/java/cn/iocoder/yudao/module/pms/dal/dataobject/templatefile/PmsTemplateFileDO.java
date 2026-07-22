package cn.iocoder.yudao.module.pms.dal.dataobject.templatefile;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/**
 * 模板附件 DO
 */
@TableName("pms_template_file")
@Data
@EqualsAndHashCode(callSuper = true)
public class PmsTemplateFileDO extends TenantBaseDO {
    /**
     * 文件主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long fileId;

    /**
     * 模板ID
     */
    private Long templateId;

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
     * 版本号
     */
    private String versionNo;

    /**
     * 状态
     */
    private String status;

    /**
     * 上传人
     */
    private Long uploadBy;

    /**
     * 上传时间
     */
    private LocalDateTime uploadTime;

}