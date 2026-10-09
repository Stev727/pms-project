package cn.iocoder.yudao.module.pms.controller.admin.report.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 周报报表行 VO（前端表格 + Excel 通用）
 *
 * 5 区块合并到一行 + 一列「分类」区分。
 * 不适用的字段留空。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WeeklyReportRowVO {

    /** 分类：📌上周应完成 / 📋本周计划 / 🗓️未来计划 / ⏰历史延期 / 🔄上周动态 */
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

    /** 逾期天数（仅历史延期分类） */
    private Long overdueDays;

    /** 操作类型（仅上周动态分类）：status_change / progress_update */
    private String operationType;

    /** 操作类型中文标签 */
    private String operationTypeLabel;

    /** 变更前值 */
    private String beforeValue;

    /** 变更后值 */
    private String afterValue;

    /** 操作时间 */
    private LocalDateTime operationTime;

    /** 操作人 */
    private String operatorName;

    /** 任务ID（前端点击行打开详情用） */
    private Long taskId;
}
