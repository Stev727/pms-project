package cn.iocoder.yudao.module.pms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * PMS 文档预览配置（#6 文件预览）
 *
 * 对应 application.yaml 中的 `pms.preview` 配置段。
 * 参考 {@link PmsDingTalkProperties} 的写法：@Component + @ConfigurationProperties。
 */
@Data
@Component
@ConfigurationProperties(prefix = "pms.preview")
public class PmsPreviewProperties {

    /**
     * 是否启用文档在线预览。关闭后预览接口直接返回不支持。
     */
    private boolean enabled = true;

    /**
     * LibreOffice soffice 可执行文件路径。
     * Linux 一般为 /usr/bin/soffice；Mac 可能为 /Applications/LibreOffice.app/Contents/MacOS/soffice。
     */
    private String sofficePath = "soffice";

    /**
     * 预览缓存目录（转换后的 PDF 落盘位置）。
     * 启动时会自动创建该目录。
     */
    private String cacheDir = "/tmp/pms-preview-cache";

    /**
     * 单次转换超时时间（秒）。超时则认为转换失败。
     */
    private int timeoutSeconds = 60;

    /**
     * 支持预览的文件扩展名（小写，不含点）。
     * 不在此列表内的文件类型直接返回 DOCUMENT_PREVIEW_UNSUPPORTED。
     */
    private List<String> supportedExtensions = Arrays.asList(
            // 直接预览
            "pdf",
            // 图片
            "jpg", "jpeg", "png", "gif", "bmp", "webp",
            // LibreOffice 转 PDF
            "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            // 文本
            "txt", "md"
    );

    /**
     * 需要 LibreOffice 转 PDF 的扩展名
     */
    public static final List<String> OFFICE_EXTENSIONS = Arrays.asList(
            "doc", "docx", "xls", "xlsx", "ppt", "pptx"
    );

    /**
     * 图片扩展名（直接返回原图字节）
     */
    public static final List<String> IMAGE_EXTENSIONS = Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp", "webp"
    );

    /**
     * 文本扩展名（直接读取文本内容返回）
     */
    public static final List<String> TEXT_EXTENSIONS = Arrays.asList(
            "txt", "md"
    );

    /**
     * 判断扩展名是否需要 LibreOffice 转换
     */
    public boolean isOfficeExtension(String ext) {
        return ext != null && OFFICE_EXTENSIONS.contains(ext.toLowerCase());
    }

    /**
     * 判断扩展名是否为图片
     */
    public boolean isImageExtension(String ext) {
        return ext != null && IMAGE_EXTENSIONS.contains(ext.toLowerCase());
    }

    /**
     * 判断扩展名是否为文本
     */
    public boolean isTextExtension(String ext) {
        return ext != null && TEXT_EXTENSIONS.contains(ext.toLowerCase());
    }

    /**
     * 判断扩展名是否支持预览
     */
    public boolean isSupported(String ext) {
        if (ext == null) {
            return false;
        }
        return supportedExtensions.contains(ext.toLowerCase());
    }
}

