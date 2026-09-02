package cn.iocoder.yudao.module.pms.controller.admin.template.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 模板阶段任务 Excel 导入结果 VO
 *
 * - 校验通过：success=true，failureRows 为空，stageCount/taskCount 为重建数量
 * - 校验失败：success=false，failureRows 为每行错误信息（Controller 据此生成错误 Excel），计数为 0
 */
@Schema(description = "管理后台 - 模板阶段任务导入结果 Response VO")
@Data
@Builder
public class TemplateStageTaskImportRespVO {

    @Schema(description = "是否全部校验通过", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean success;

    @Schema(description = "重建的阶段数（仅校验通过时 > 0）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer stageCount;

    @Schema(description = "重建的任务数（仅校验通过时 > 0）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer taskCount;

    @Schema(description = "失败行集合（含原始行数据 + 错误信息）", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<TemplateStageTaskImportErrorExcel> failureRows;
}
