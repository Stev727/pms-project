package cn.iocoder.yudao.module.pms.controller.admin.template.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模板阶段任务 Excel 导入行 VO（单 Sheet 设计）
 *
 * 列序：阶段序号 / 阶段名称 / 任务序号 / 任务名称 / 任务类型 /
 *       里程碑 / 关键路径 / 工期(天) / 输出要求
 *
 * 说明：
 *  - 同一阶段序号的行归为一个阶段；组内阶段名称必须一致
 *  - 阶段序号从 1 开始连续；任务序号在阶段内从 1 开始连续
 *  - 任务类型支持中文标签（设计任务/设计）或英文值（design）；
 *    留空默认为 other（其他）
 *  - 里程碑/关键路径填 是/否，留空默认否
 *  - 导入为全量覆盖：现有阶段与任务会被软删后按本文件重建
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TemplateStageTaskImportExcel {

    @ExcelProperty("阶段序号")
    private Integer stageNo;

    @ExcelProperty("阶段名称")
    private String stageName;

    @ExcelProperty("任务序号")
    private Integer taskNo;

    @ExcelProperty("任务名称")
    private String taskName;

    @ExcelProperty("任务类型")
    private String taskType;

    @ExcelProperty("里程碑")
    private String milestone;

    @ExcelProperty("关键路径")
    private String criticalPath;

    @ExcelProperty("工期(天)")
    private Integer cycle;

    @ExcelProperty("输出要求")
    private String outputRequirement;
}
