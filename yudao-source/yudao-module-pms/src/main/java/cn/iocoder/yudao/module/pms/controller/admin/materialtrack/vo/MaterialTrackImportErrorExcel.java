package cn.iocoder.yudao.module.pms.controller.admin.materialtrack.vo;

import cn.idev.excel.annotation.ExcelProperty;
import cn.idev.excel.annotation.write.style.ContentFontStyle;
import cn.idev.excel.enums.BooleanEnum;
import cn.idev.excel.annotation.write.style.ContentStyle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 物料跟踪 Excel 导入失败回执行 VO
 *
 * 设计：
 *  - 首列「错误信息」前置，单元格红色背景白字（@ContentStyle + @ContentFontStyle）
 *  - 其余列保持与 MaterialTrackImportExcel 相同的列序，便于用户复制修正后再提交
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MaterialTrackImportErrorExcel {

    /** 错误信息（首列，标红显示） */
    @ExcelProperty("错误信息")
    @ContentStyle(fillForegroundColor = 22)
    @ContentFontStyle(color = 9, bold = BooleanEnum.TRUE)
    private String errorMessage;

    @ExcelProperty("物料名称")
    private String materialName;

    @ExcelProperty("物料编码")
    private String materialCode;

    @ExcelProperty("供应商")
    private String supplier;

    @ExcelProperty("数量")
    private BigDecimal quantity;

    @ExcelProperty("单位")
    private String unit;

    @ExcelProperty("计划下单日期")
    private LocalDate planOrderDate;

    @ExcelProperty("承诺交期")
    private LocalDate planDeliveryDate;

    @ExcelProperty("当前状态")
    private String currentStatus;
}
