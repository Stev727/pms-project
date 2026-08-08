package cn.iocoder.yudao.module.pms.controller.admin.qualityissue.vo;

import cn.idev.excel.annotation.ExcelProperty;
import cn.idev.excel.annotation.write.style.ContentFontStyle;
import cn.idev.excel.annotation.write.style.ContentStyle;
import cn.idev.excel.enums.BooleanEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 质量问题 Excel 导入失败回执行 VO
 *
 * 设计：
 *  - 首列「错误信息」前置，单元格红色背景白字（@ContentStyle + @ContentFontStyle），
 *    方便用户在 Excel 中一眼定位错误。
 *  - 其余列保持与 {@link QualityIssueImportExcel} 相同的列序，便于用户复制修正后再提交。
 *  - 标红粒度为"错误信息列整列标红"（FastExcel 注解限制，无法按行标红），
 *    由于此文件所有行均为错误行，整列标红与逐行标红效果一致。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QualityIssueImportErrorExcel {

    /**
     * 错误信息（首列，标红显示）
     */
    @ExcelProperty("错误信息")
    @ContentStyle(fillForegroundColor = 22) // 22 = HSSFColor.RED.index
    @ContentFontStyle(color = 9, bold = BooleanEnum.TRUE) // 9 = HSSFColor.WHITE.index
    private String errorMessage;

    @ExcelProperty("标题")
    private String issueTitle;

    @ExcelProperty("类型")
    private String issueType;

    @ExcelProperty("严重程度")
    private String severity;

    @ExcelProperty("描述")
    private String issueDescription;

    @ExcelProperty("责任人(姓名)")
    private String responsiblePersonName;

    @ExcelProperty("发现人(姓名)")
    private String discovererName;

    @ExcelProperty("发现日期")
    private LocalDate discoveredDate;

    @ExcelProperty("期望完成日期")
    private LocalDate dueDate;

    @ExcelProperty("关联任务(名称)")
    private String taskName;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("整改要求")
    private String rectificationRequirement;
}
