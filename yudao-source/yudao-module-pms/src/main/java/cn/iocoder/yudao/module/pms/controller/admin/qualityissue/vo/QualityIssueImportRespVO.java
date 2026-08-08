package cn.iocoder.yudao.module.pms.controller.admin.qualityissue.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 质量问题 Excel 导入结果 VO
 *
 * - 校验通过：success=true，failureRows 为空，successCount 为插入条数
 * - 校验失败：success=false，failureRows 为每行错误信息（Controller 据此生成错误 Excel），successCount=0
 */
@Schema(description = "管理后台 - 质量问题导入结果 Response VO")
@Data
@Builder
public class QualityIssueImportRespVO {

    @Schema(description = "是否全部校验通过", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean success;

    @Schema(description = "成功插入条数（仅校验通过时 > 0）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer successCount;

    @Schema(description = "失败行集合（含原始行数据 + 错误信息）", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<QualityIssueImportErrorExcel> failureRows;

}

