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
 *
 * 【#7 改造】追加权限分级字段：
 * - visibility：可见范围（public 项目全员 / role 指定角色 / private 仅上传人+项目经理）
 * - allowedRoleIds：允许查看的角色ID列表（JSON 数组，visibility=role 时生效）
 * - allowDownload：是否允许下载（0/1）
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
     * 存储路径（完整 URL，前端 window.open 可直接下载）
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
     * 权限标识（存量字段，#7 后由 visibility/allowedRoleIds/allowDownload 替代，保留兼容）
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

    // ========== #7 文档权限分级 新增字段 ==========

    /**
     * 可见范围：
     * - public：项目全员可见
     * - role：指定角色可见（配合 allowedRoleIds）
     * - private：仅上传人 + 项目经理可见
     */
    private String visibility;

    /**
     * 允许查看的角色ID列表（JSON 数组字符串，如 "[101,102,103]"）。
     * visibility=role 时生效。
     */
    private String allowedRoleIds;

    /**
     * 是否允许下载（0=禁止，1=允许）。null 视为允许（兼容存量数据）。
     */
    private Boolean allowDownload;

}

