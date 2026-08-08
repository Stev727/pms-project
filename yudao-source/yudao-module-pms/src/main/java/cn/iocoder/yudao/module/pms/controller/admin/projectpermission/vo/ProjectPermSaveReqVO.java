package cn.iocoder.yudao.module.pms.controller.admin.projectpermission.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 项目权限矩阵保存 Request VO
 *
 * 整体覆盖语义：后端按 grantedPairs 全量重写该项目的权限记录。
 */
@Schema(description = "管理后台 - 项目权限矩阵保存 Request VO")
@Data
public class ProjectPermSaveReqVO {

    @Schema(description = "项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @Schema(description = "已勾选的授权项，格式 roleId:permKey")
    private List<String> grantedPairs;

}

