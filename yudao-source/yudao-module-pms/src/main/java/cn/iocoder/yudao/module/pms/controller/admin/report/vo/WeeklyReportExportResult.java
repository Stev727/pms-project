package cn.iocoder.yudao.module.pms.controller.admin.report.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 周报报表导出结果包装
 *
 * 文件名：周报报表_{部门名}_{基准日期}.xlsx
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WeeklyReportExportResult {

    /** Excel 文件名（含 .xlsx 后缀） */
    private String fileName;

    /** 导出数据（已按区块排序填充） */
    private List<WeeklyReportExportVO> data;
}
