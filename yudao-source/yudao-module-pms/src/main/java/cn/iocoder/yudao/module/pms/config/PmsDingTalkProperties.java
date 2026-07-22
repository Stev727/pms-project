package cn.iocoder.yudao.module.pms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * PMS 钉钉集成配置
 * 从数据库 pms_dingtalk_config 加载，这里仅作默认值/占位
 */
@Data
@Component
@ConfigurationProperties(prefix = "pms.dingtalk")
public class PmsDingTalkProperties {

    /**
     * 是否启用钉钉集成
     */
    private boolean enabled = true;

    /**
     * 钉钉 API 基础 URL
     */
    private String baseUrl = "https://oapi.dingtalk.com";

    /**
     * 钉钉新 API 基础 URL
     */
    private String newApiBaseUrl = "https://api.dingtalk.com";

    /**
     * Access Token 缓存时间（秒），默认 7000（钉钉 token 有效期 7200 秒）
     */
    private int tokenCacheSeconds = 7000;
}
