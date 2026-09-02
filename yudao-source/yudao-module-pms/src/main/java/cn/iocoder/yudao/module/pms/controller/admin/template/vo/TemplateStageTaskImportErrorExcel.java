package cn.iocoder.yudao.module.pms.controller.admin.template.vo;

import cn.idev.excel.annotation.ExcelProperty;
import cn.idev.excel.annotation.write.style.ContentFontStyle;
import cn.idev.excel.annotation.write.style.ContentStyle;
import cn.idev.excel.enums.BooleanEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模板阶段任务 Excel 导入失败回执行 VO
 *
 * 设计（与质量问题导入错误 Excel 保持一致）：
 *  - 首列「错误信息」前置，单元格红色背景白字，方便用户一眼定位错误
 *  - 其余列与 {@link TemplateStageTaskImportExcel} 列序相同，修正后可直接重新提交
 *  - 本文件所有行均为错误行，整列标红即等效逐行标红（FastExcel 注解限制）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TemplateStageTaskImportErrorExcel {

    /**
     * 错误信息（首列，标红显示）
     */
    @ExcelProperty("错误信息")
    @ContentStyle(fillForegroundColor = 22) // 22 = HSSFColor.RED.index
    @ContentFontStyle(color = 9, bold = BooleanEnum.TRUE) // 9 = HSSFColor.WHITE.index
    private String errorMessage;

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
