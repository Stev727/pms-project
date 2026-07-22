package cn.iocoder.yudao.module.pms.dal.dataobject.document;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/**
 * 文档 DO
 */
@TableName("pms_document")
@Data
@EqualsAndHashCode(callSuper = true)
public class PmsDocumentDO extends TenantBaseDO {
    /**
     * 文档主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long documentId;

    /**
     * 关联项目ID
     */
    private Long projectId;

    /**
     * 关联任务ID
     */
    private Long taskId;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 文档分类
     */
    private String category;

    /**
     * 上传人ID
     */
    private Long uploadBy;

    /**
     * 上传时间
     */
    private LocalDateTime uploadTime;

    /**
     * 版本号
     */
    private String versionNo;

    /**
     * 存储路径
     */
    private String storagePath;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 下载次数
     */
    private Integer downloadCount;

    /**
     * 权限标识
     */
    private String permissionFlag;

    /**
     * 文档描述
     */
    private String description;

    /**
     * 标签
     */
    private String tags;

}