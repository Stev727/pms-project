package cn.iocoder.yudao.module.pms.service.document.impl;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.config.PmsPreviewProperties;
import cn.iocoder.yudao.module.pms.controller.admin.document.vo.PreviewResultVO;
import cn.iocoder.yudao.module.pms.dal.dataobject.document.PmsDocumentDO;
import cn.iocoder.yudao.module.pms.dal.mysql.document.DocumentMapper;
import cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.pms.service.document.DocumentPermissionService;
import cn.iocoder.yudao.module.pms.service.document.DocumentPreviewService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 文档预览 Service 实现（#6 文件预览）
 *
 * 核心流程：
 * 1. 权限校验（DocumentPermissionService.canView）
 * 2. 判断文件类型 → 确定预览方式
 * 3. Office 文件：下载原文件 → LibreOffice 转 PDF → 缓存 → 返回
 * 4. PDF / 图片：直接返回字节流
 * 5. 文本：读取内容返回
 *
 * 缓存策略：
 * - 缓存 key = docId + "_" + updateTime.getTime()（文件版本变化时自动失效）
 * - 缓存文件路径 = {cacheDir}/{cacheKey}.pdf
 * - 命中缓存直接返回，避免重复转换
 *
 * 并发控制：
 * - 同一 cacheKey 用本地锁串行化（ConcurrentHashMap<String, Object>）
 * - 第一个请求转换，后续请求等待后直接读缓存
 */
@Slf4j
@Service
public class DocumentPreviewServiceImpl implements DocumentPreviewService {

    @Resource
    private DocumentMapper documentMapper;
    @Resource
    private DocumentPermissionService documentPermissionService;
    @Resource
    private PmsPreviewProperties previewProperties;

    /**
     * 同一文件的并发预览请求串行化锁。
     * key = cacheKey（docId + "_" + updateTime）。
     * 单机部署足够；多实例部署建议升级为 Redis 分布式锁（见 README）。
     */
    private final ConcurrentHashMap<String, Object> convertLocks = new ConcurrentHashMap<>();

    /**
     * 预览文件 URL 前缀（Controller 层 mapping）
     */
    private static final String PREVIEW_FILE_URL = "/admin-api/pms/document/preview-file?docId=";

    @Override
    public PreviewResultVO preview(Long docId) {
        if (!previewProperties.isEnabled()) {
            throw new ServiceException(ErrorCodeConstants.DOCUMENT_PREVIEW_UNSUPPORTED);
        }
        PmsDocumentDO doc = documentMapper.selectById(docId);
        if (doc == null) {
            throw new ServiceException(ErrorCodeConstants.DOCUMENT_NOT_EXISTS);
        }
        // 权限校验
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        documentPermissionService.checkView(docId, userId);

        String ext = extractExtension(doc.getFileName());
        PreviewResultVO result = new PreviewResultVO();
        result.setFileName(doc.getFileName());
        result.setFileSize(doc.getFileSize());
        result.setFileType(ext);

        if (!previewProperties.isSupported(ext)) {
            result.setPreviewType("unsupported");
            return result;
        }

        if (previewProperties.isTextExtension(ext)) {
            // 文本：读取内容返回
            String textContent = readTextContent(doc);
            result.setPreviewType("text");
            result.setTextContent(textContent);
            return result;
        }

        // PDF / 图片 / Office 转 PDF：返回 previewFileUrl，前端用 iframe/img 加载
        result.setPreviewType(previewProperties.isImageExtension(ext) ? "image" : "pdf");
        result.setPreviewFileUrl(PREVIEW_FILE_URL + docId);
        return result;
    }

    @Override
    public void previewFile(Long docId, HttpServletResponse response) {
        if (!previewProperties.isEnabled()) {
            throw new ServiceException(ErrorCodeConstants.DOCUMENT_PREVIEW_UNSUPPORTED);
        }
        PmsDocumentDO doc = documentMapper.selectById(docId);
        if (doc == null) {
            throw new ServiceException(ErrorCodeConstants.DOCUMENT_NOT_EXISTS);
        }
        // 权限校验
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        documentPermissionService.checkView(docId, userId);

        String ext = extractExtension(doc.getFileName());
        if (!previewProperties.isSupported(ext)) {
            throw new ServiceException(ErrorCodeConstants.DOCUMENT_PREVIEW_UNSUPPORTED);
        }

        try {
            if (previewProperties.isImageExtension(ext)) {
                // 图片：直接返回原图字节
                byte[] bytes = downloadFile(doc.getStoragePath());
                setResponseHeaders(response, doc.getFileName(), "image/" + ext, bytes.length);
                response.getOutputStream().write(bytes);
                response.getOutputStream().flush();
                return;
            }

            if ("pdf".equalsIgnoreCase(ext)) {
                // 原生 PDF：直接下载字节返回，不落临时文件
                byte[] bytes = downloadFile(doc.getStoragePath());
                setResponseHeaders(response, doc.getFileName(), "application/pdf", bytes.length);
                response.getOutputStream().write(bytes);
                response.getOutputStream().flush();
                return;
            }

            // Office 转 PDF：走缓存 + 转换
            File pdfFile = resolvePdfFile(doc, ext);
            byte[] bytes = Files.readAllBytes(pdfFile.toPath());
            setResponseHeaders(response, doc.getFileName(), "application/pdf", bytes.length);
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("[previewFile] 文档({})预览文件输出失败", docId, e);
            throw new ServiceException(ErrorCodeConstants.DOCUMENT_CONVERT_FAILED);
        }
    }

    @Override
    public void downloadFile(Long docId, HttpServletResponse response) {
        PmsDocumentDO doc = documentMapper.selectById(docId);
        if (doc == null) {
            throw new ServiceException(ErrorCodeConstants.DOCUMENT_NOT_EXISTS);
        }
        // 下载权限校验（包含查看权限 + allowDownload）
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        documentPermissionService.checkDownload(docId, userId);

        try {
            byte[] bytes = downloadFile(doc.getStoragePath());
            // attachment 触发浏览器下载
            response.setContentType("application/octet-stream;charset=UTF-8");
            response.setHeader("Content-Length", String.valueOf(bytes.length));
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + sanitizeFileName(doc.getFileName()) + "\"");
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("[downloadFile] 文档({})下载输出失败", docId, e);
            throw new ServiceException(ErrorCodeConstants.DOCUMENT_CONVERT_FAILED);
        }
    }

    // ==================== 核心转换逻辑 ====================

    /**
     * 获取 PDF 文件（Office 转换后的缓存文件）。
     * 原生 PDF 在 previewFile 已直接下载返回，不经过此方法。
     */
    private File resolvePdfFile(PmsDocumentDO doc, String ext) throws Exception {
        // Office 文件：走缓存 + 转换
        String cacheKey = buildCacheKey(doc);
        File cachedPdf = getCachedPdf(cacheKey);
        if (cachedPdf != null && cachedPdf.exists() && cachedPdf.length() > 0) {
            log.info("[resolvePdfFile] 命中缓存 docId={} cacheKey={}", doc.getDocumentId(), cacheKey);
            return cachedPdf;
        }

        // 并发串行化：同一 cacheKey 只允许一个线程转换
        Object lock = convertLocks.computeIfAbsent(cacheKey, k -> new Object());
        try {
            synchronized (lock) {
                // double-check：等待锁期间可能已被其他线程转换完成
                cachedPdf = getCachedPdf(cacheKey);
                if (cachedPdf != null && cachedPdf.exists() && cachedPdf.length() > 0) {
                    log.info("[resolvePdfFile] 等待锁后命中缓存 docId={} cacheKey={}", doc.getDocumentId(), cacheKey);
                    return cachedPdf;
                }

                // 1. 下载原文件到临时目录
                byte[] sourceBytes = downloadFile(doc.getStoragePath());
                File sourceFile = File.createTempFile("pms_source_" + cacheKey + "_", "." + ext);
                Files.write(sourceFile.toPath(), sourceBytes);
                try {
                    // 2. 调用 LibreOffice 转 PDF
                    File outputPdf = convertToPdf(sourceFile, cacheKey);
                    return outputPdf;
                } finally {
                    // 清理临时源文件
                    if (!sourceFile.delete()) {
                        sourceFile.deleteOnExit();
                    }
                }
            }
        } finally {
            convertLocks.remove(cacheKey);
        }
    }

    /**
     * 调用 soffice --headless --convert-to pdf 转换
     */
    private File convertToPdf(File sourceFile, String cacheKey) throws Exception {
        Path cacheDir = Paths.get(previewProperties.getCacheDir());
        if (!Files.exists(cacheDir)) {
            Files.createDirectories(cacheDir);
        }

        File outFile = cacheDir.resolve(cacheKey + ".pdf").toFile();

        ProcessBuilder pb = new ProcessBuilder(
                previewProperties.getSofficePath(),
                "--headless",
                "--convert-to", "pdf",
                "--outdir", cacheDir.toString(),
                sourceFile.getAbsolutePath()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // 消费输出流，避免缓冲区满导致阻塞
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        try (InputStream is = process.getInputStream()) {
            byte[] buf = new byte[1024];
            int n;
            while ((n = is.read(buf)) != -1) {
                outBuf.write(buf, 0, n);
            }
        }

        boolean finished = process.waitFor(previewProperties.getTimeoutSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            log.error("[convertToPdf] LibreOffice 转换超时(>{}s) cacheKey={} out={}",
                    previewProperties.getTimeoutSeconds(), cacheKey, outBuf.toString());
            throw new ServiceException(ErrorCodeConstants.DOCUMENT_CONVERT_FAILED);
        }
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            log.error("[convertToPdf] LibreOffice 转换失败 exitCode={} cacheKey={} out={}",
                    exitCode, cacheKey, outBuf.toString());
            throw new ServiceException(ErrorCodeConstants.DOCUMENT_CONVERT_FAILED);
        }

        // soffice 输出文件名 = 源文件名（去掉原扩展名）+ .pdf
        String sourceName = sourceFile.getName();
        int dotIdx = sourceName.lastIndexOf('.');
        String baseName = dotIdx > 0 ? sourceName.substring(0, dotIdx) : sourceName;
        File actualOutput = cacheDir.resolve(baseName + ".pdf").toFile();

        if (!actualOutput.exists() || actualOutput.length() == 0) {
            log.error("[convertToPdf] 转换后 PDF 文件不存在或为空 cacheKey={} expected={}",
                    cacheKey, actualOutput.getAbsolutePath());
            throw new ServiceException(ErrorCodeConstants.DOCUMENT_CONVERT_FAILED);
        }

        // 重命名为缓存文件名（避免源文件名冲突）
        if (!actualOutput.equals(outFile)) {
            if (outFile.exists()) {
                outFile.delete();
            }
            if (!actualOutput.renameTo(outFile)) {
                // rename 失败则复制
                Files.copy(actualOutput.toPath(), outFile.toPath());
                actualOutput.delete();
            }
        }

        log.info("[convertToPdf] 转换成功 cacheKey={} size={}B", cacheKey, outFile.length());
        return outFile;
    }

    /**
     * 读取文本文件内容
     */
    private String readTextContent(PmsDocumentDO doc) {
        try {
            byte[] bytes = downloadFile(doc.getStoragePath());
            // 限制最大 512KB，避免大文本撑爆前端
            int maxLen = 512 * 1024;
            if (bytes.length > maxLen) {
                return new String(bytes, 0, maxLen, StandardCharsets.UTF_8)
                        + "\n\n... 文件过大，仅显示前 512KB，请下载查看完整内容 ...";
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[readTextContent] 读取文本内容失败 docId={}", doc.getDocumentId(), e);
            throw new ServiceException(ErrorCodeConstants.DOCUMENT_CONVERT_FAILED);
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 从 URL 下载文件字节（storagePath 是完整 HTTP URL）
     */
    private byte[] downloadFile(String url) throws IOException {
        if (url == null || url.isEmpty()) {
            throw new IOException("文件存储路径为空");
        }
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(60_000);
        conn.setRequestMethod("GET");
        try (InputStream is = conn.getInputStream();
             ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            byte[] tmp = new byte[8192];
            int n;
            while ((n = is.read(tmp)) != -1) {
                buf.write(tmp, 0, n);
            }
            return buf.toByteArray();
        } finally {
            conn.disconnect();
        }
    }

    /**
     * 缓存 key = docId + "_" + updateTime.getTime()
     * 文档更新后 updateTime 变化 → cacheKey 变化 → 自动失效
     */
    private String buildCacheKey(PmsDocumentDO doc) {
        long ts = doc.getUpdateTime() != null ? doc.getUpdateTime().toEpochSecond(java.time.ZoneOffset.UTC) : 0L;
        return doc.getDocumentId() + "_" + ts;
    }

    /**
     * 查找缓存文件
     */
    private File getCachedPdf(String cacheKey) {
        File f = Paths.get(previewProperties.getCacheDir(), cacheKey + ".pdf").toFile();
        return f.exists() && f.length() > 0 ? f : null;
    }

    /**
     * 提取文件扩展名（小写，不含点）
     */
    private String extractExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(idx + 1).toLowerCase();
    }

    /**
     * 设置响应头
     */
    private void setResponseHeaders(HttpServletResponse response, String fileName, String contentType, int length) {
        response.setContentType(contentType + ";charset=UTF-8");
        response.setHeader("Content-Length", String.valueOf(length));
        // 预览用 inline，不用 attachment（避免触发下载）
        response.setHeader("Content-Disposition", "inline; filename=\"" + sanitizeFileName(fileName) + "\"");
        // 允许前端跨域加载（iframe 场景）
        response.setHeader("Access-Control-Allow-Origin", "*");
    }

    /**
     * 文件名净化（移除换行等危险字符）
     */
    private String sanitizeFileName(String name) {
        if (name == null) {
            return "preview";
        }
        return name.replaceAll("[\\r\\n\"]", "_");
    }

}

