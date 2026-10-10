package cn.iocoder.yudao.module.pms.controller.admin.report.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 周报报表导出 Excel VO
 *
 * 单 Sheet：4 分类任务行合并到一张表，用「分类」列区分。
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
}
