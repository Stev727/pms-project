package cn.iocoder.yudao.module.pms.controller.admin.report.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 周报报表行 VO（前端表格 + Excel 通用）
 *
 * 4 分类任务行 + 一列「分类」区分（时间段口径）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WeeklyReportRowVO {

    /** 分类：📌时间段内应完成 / 📋进行中 / 🗓️后续计划 / ⏰已延期 */
    private String category;

    /** 项目名 */
    private String projectName;

    /** 任务名 */
    private String taskName;

    /** 责任人昵称 */
    private String mainOwnerName;

    /** 计划开始日期 */
    private LocalDate planStartDate;

    /** 计划结束日期 */
    private LocalDate planEndDate;

    /** 实际完成日期 */
    private LocalDate actualCompleteDate;

    /** 完成状态原始值（not_started/in_progress/...） */
    private String completeStatus;

    /** 完成状态中文标签 */
    private String completeStatusLabel;

    /** 进度 0-100 */
    private Integer progress;

    /** 逾期天数（仅已延期分类） */
    private Long overdueDays;

    /** 任务ID（前端点击行打开详情用） */
    private Long taskId;
}
