package cn.iocoder.yudao.module.system.controller.admin.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;

@Schema(description = "管理后台 - 钉钉免登 Request VO")
@Data
public class DingTalkLoginReqVO {

    @Schema(description = "钉钉免登授权码", requiredMode = Schema.RequiredMode.REQUIRED, example = "xxx")
    @NotEmpty(message = "钉钉免登授权码不能为空")
    private String authCode;

}
