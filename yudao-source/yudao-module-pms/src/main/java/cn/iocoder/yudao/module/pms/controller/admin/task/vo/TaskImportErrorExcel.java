package cn.iocoder.yudao.module.pms.controller.admin.task.vo;

import cn.idev.excel.annotation.ExcelProperty;
import cn.idev.excel.annotation.write.style.ContentFontStyle;
import cn.idev.excel.annotation.write.style.ContentStyle;
import cn.idev.excel.enums.BooleanEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务批量导入失败回执行 VO
 *
 * 设计（与模板阶段任务导入错误 Excel 保持一致）：
 *  - 首列「错误信息」前置，单元格红色背景白字，方便用户一眼定位错误
 *  - 其余列与 {@link TaskImportExcel} 列序相同，修正后可直接重新提交
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskImportErrorExcel {

    /**
     * 错误信息（首列，标红显示）
     */
    @ExcelProperty("错误信息")
    @ContentStyle(fillForegroundColor = 22) // 22 = HSSFColor.RED.index
    @ContentFontStyle(color = 9, bold = BooleanEnum.TRUE) // 9 = HSSFColor.WHITE.index
    private String errorMessage;

    @ExcelProperty("阶段序号(必填)")
    private Integer stageNo;

    @ExcelProperty("阶段名称(新阶段必填)")
    private String stageName;

    @ExcelProperty("任务序号(选填)")
    private Integer taskNo;

    @ExcelProperty("任务名称")
    private String taskName;

    @ExcelProperty("父任务名称")
    private String parentTaskName;

    @ExcelProperty("任务类型")
    private String taskType;

    @ExcelProperty("优先级")
    private String priority;

    @ExcelProperty("负责人")
    private String ownerName;

    @ExcelProperty("协助人")
    private String helperNames;

    @ExcelProperty("计划开始日期")
    private String planStartDate;

    @ExcelProperty("计划结束日期")
    private String planEndDate;

    @ExcelProperty("备注")
    private String remark;

    public static TaskImportErrorExcel of(TaskImportExcel row, String errorMessage) {
        TaskImportErrorExcel err = new TaskImportErrorExcel();
        err.setErrorMessage(errorMessage);
        if (row != null) {
            err.setStageNo(row.getStageNo());
            err.setStageName(row.getStageName());
            err.setTaskNo(row.getTaskNo());
            err.setTaskName(row.getTaskName());
            err.setParentTaskName(row.getParentTaskName());
            err.setTaskType(row.getTaskType());
            err.setPriority(row.getPriority());
            err.setOwnerName(row.getOwnerName());
            err.setHelperNames(row.getHelperNames());
            err.setPlanStartDate(row.getPlanStartDate());
            err.setPlanEndDate(row.getPlanEndDate());
            err.setRemark(row.getRemark());
        }
        return err;
    }
}
