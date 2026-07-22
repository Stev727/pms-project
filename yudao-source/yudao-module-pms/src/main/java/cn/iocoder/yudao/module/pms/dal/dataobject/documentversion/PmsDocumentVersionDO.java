package cn.iocoder.yudao.module.pms.dal.dataobject.documentversion;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/**
 * 文档版本 DO
 */
@TableName("pms_document_version")
@Data
@EqualsAndHashCode(callSuper = true)
public class PmsDocumentVersionDO extends TenantBaseDO {
    /**
     * 版本主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long versionId;

    /**
     * 文档ID
     */
    private Long documentId;

    /**
     * 版本号
     */
    private String versionNo;

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 上传人ID
     */
    private Long uploadBy;

    /**
     * 上传时间
     */
    private LocalDateTime uploadTime;

    /**
     * 变更说明
     */
    private String changeDescription;

}