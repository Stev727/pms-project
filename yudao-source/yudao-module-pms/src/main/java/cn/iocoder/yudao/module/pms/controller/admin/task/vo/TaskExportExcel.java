package cn.iocoder.yudao.module.pms.controller.admin.task.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 项目任务导出 Excel VO
 *
 * 导出字段（按 PMS 任务列表核心字段）：
 *   编号 / 名称 / 所属阶段 / 负责人 / 协助人 / 责任部门 / 任务类型 / 优先级 /
 *   层级 / 完成状态 / 审核状态 / 进度 / 计划开始 / 到期日 / 实际完成 /
 *   是否里程碑 / 是否关键路径
 *
 * 说明：
 *  - 日期统一序列化为 yyyy-MM-dd 字符串，避免 LocalDate 直接写出数字 / 数组。
 *  - 状态 / 类型 / 优先级统一转中文标签，与前端 pms-utils 保持一致。
 *  - 负责人 / 协助人 / 责任部门统一转昵称 / 部门名。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskExportExcel {

    @ExcelProperty("编号")
    private String taskCode;

    @ExcelProperty("名称")
    private String taskName;

    @ExcelProperty("所属阶段")
    private String stageName;

    @ExcelProperty("负责人")
    private String mainOwnerName;

    @ExcelProperty("协助人")
    private String helperNames;

    @ExcelProperty("责任部门")
    private String deptName;

    @ExcelProperty("任务类型")
    private String taskTypeLabel;

    @ExcelProperty("优先级")
    private String priorityLabel;

    @ExcelProperty("层级")
    private String levelLabel;

    @ExcelProperty("完成状态")
    private String completeStatusLabel;

    @ExcelProperty("审核状态")
    private String reviewStatusLabel;

    @ExcelProperty("进度")
    private Integer progress;

    @ExcelProperty("计划开始")
    private String planStartDate;

    @ExcelProperty("到期日")
    private String planEndDate;

    @ExcelProperty("实际完成")
    private String actualCompleteDate;

    @ExcelProperty("是否里程碑")
    private String isMilestoneLabel;

    @ExcelProperty("是否关键路径")
    private String isCriticalPathLabel;
}
