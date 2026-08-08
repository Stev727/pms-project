package cn.iocoder.yudao.module.pms.service.document;

import cn.iocoder.yudao.module.pms.controller.admin.document.vo.PreviewResultVO;

import javax.servlet.http.HttpServletResponse;

/**
 * 文档预览 Service 接口（#6 文件预览 — LibreOffice headless 转 PDF）
 *
 * 支持的预览类型：
 * - pdf：直接返回
 * - doc/docx/xls/xlsx/ppt/pptx：LibreOffice 转 PDF 后返回
 * - 图片（jpg/png/gif 等）：直接返回原图
 * - txt/md：返回文本内容
 * - 其他：返回不支持
 *
 * 转换结果缓存：同一文件同一版本（updateTime）只转一次，缓存文件落盘到 pms.preview.cache-dir。
 * 并发控制：同一文件的并发预览请求串行化（本地锁）。
 */
public interface DocumentPreviewService {

    /**
     * 获取预览元信息（类型 + URL / 文本内容）
     *
     * @param docId 文档ID
     * @return 预览结果
     */
    PreviewResultVO preview(Long docId);

    /**
     * 输出预览文件字节流（PDF / 图片）到 HttpServletResponse。
     * 供前端用 iframe / img 加载。
     *
     * @param docId    文档ID
     * @param response HTTP 响应
     */
    void previewFile(Long docId, HttpServletResponse response);

    /**
     * 输出下载文件字节流（原文件）到 HttpServletResponse。
     * 校验 canDownload 权限，设置 attachment 头。
     *
     * @param docId    文档ID
     * @param response HTTP 响应
     */
    void downloadFile(Long docId, HttpServletResponse response);

}

