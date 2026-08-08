package cn.iocoder.yudao.module.pms.controller.admin.document.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文档预览结果 VO（#6 文件预览）
 *
 * 因为预览结果需要聚合类型/URL/文本内容等多个字段，按契约 1.1 节「确需聚合结构才建 VO」此处新建。
 */
@Data
@Schema(description = "管理后台 - 文档预览结果 Response VO")
public class PreviewResultVO {

    /**
     * 预览类型：
     * - pdf：PDF 文件（含原生 PDF 和 Office 转换后的 PDF），前端用 iframe 加载 previewFileUrl
     * - image：图片，前端用 img 标签加载 previewFileUrl
     * - text：纯文本，前端用 <pre> 渲染 textContent
     * - unsupported：不支持预览，前端显示下载按钮
     */
    @Schema(description = "预览类型")
    private String previewType;

    /**
     * 预览文件 URL（pdf/image 类型有效）。
     * 指向 /admin-api/pms/document/preview-file?docId=xxx，前端需带 token 请求拿 Blob 再 createObjectURL。
     */
    @Schema(description = "预览文件 URL")
    private String previewFileUrl;

    /**
     * 文本内容（仅 previewType=text 时有值）
     */
    @Schema(description = "文本内容")
    private String textContent;

    /**
     * 文件名
     */
    @Schema(description = "文件名")
    private String fileName;

    /**
     * 文件大小（字节）
     */
    @Schema(description = "文件大小")
    private Long fileSize;

    /**
     * 文件类型（扩展名）
     */
    @Schema(description = "文件类型")
    private String fileType;

}

