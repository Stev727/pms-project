package cn.iocoder.yudao.module.pms.controller.admin.report.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 周报报表导出 Excel VO
 *
 * 单 Sheet：所有任务 + 周报动态合并到一张表，用「分类」列区分。
 * 不适用的列留空，避免阅读时跨多个 Sheet 切换。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WeeklyReportExportVO {

    @ExcelProperty("分类")
    private String category;

    @ExcelProperty("项目")
    private String projectName;

    @ExcelProperty("任务名")
    private String taskName;

    @ExcelProperty("责任人")
    private String mainOwnerName;

    @ExcelProperty("计划开始")
    private String planStartDate;

    @ExcelProperty("计划结束")
    private String planEndDate;

    @ExcelProperty("实际完成")
    private String actualCompleteDate;

    @ExcelProperty("完成状态")
    private String completeStatusLabel;

    @ExcelProperty("进度")
    private Integer progress;

    @ExcelProperty("逾期天数")
    private Long overdueDays;

    @ExcelProperty("操作类型")
    private String operationTypeLabel;

    @ExcelProperty("变更前")
    private String beforeValue;

    @ExcelProperty("变更后")
    private String afterValue;

    @ExcelProperty("操作时间")
    private String operationTime;

    @ExcelProperty("操作人")
    private String operatorName;
}
