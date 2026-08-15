package cn.iocoder.yudao.module.pms.controller.admin.materialtrack.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 物料跟踪 Excel 导入行 VO
 *
 * 模板列：物料名称 / 物料编码 / 供应商 / 数量 / 单位 / 计划下单日期 / 承诺交期 / 当前状态
 *
 * 说明：
 *  - 物料名称为必填项，其余可选
 *  - 日期字段使用 LocalDate，FastExcel 自动按 yyyy-MM-dd 解析
 *  - 当前状态可选值：未下单(not_ordered) / 已下单(ordered) / 已到货(delivered) / 延期(delayed)
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MaterialTrackImportExcel {

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
