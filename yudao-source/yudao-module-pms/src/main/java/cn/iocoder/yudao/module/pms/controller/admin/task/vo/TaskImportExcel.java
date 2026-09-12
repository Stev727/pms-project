package cn.iocoder.yudao.module.pms.controller.admin.task.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 任务批量导入 Excel 行 DTO（追加式导入：只新增任务，不修改已有任务）
 *
 * 列说明：
 *  - 阶段序号/阶段名称：已有阶段仅填名称即可（序号忽略）；新阶段名称+序号必填（自动创建）
 *  - 纯阶段行（任务序号+任务名称均空）：已有阶段=参考行跳过；新阶段=声明创建空阶段
 *  - 任务序号：阶段内唯一（不强制连续），落库为 sortOrder
 *  - 父任务名称：留空=顶层任务；填写=该阶段内的顶层任务下挂子任务（最多两级）
 *  - 负责人/协助人：工号优先，其次姓名精确匹配；协助人多人用逗号分隔
 *  - 日期：yyyy-MM-dd，开始/结束须同时填写，且开始 ≤ 结束
 */
@Data
public class TaskImportExcel {

    @ExcelProperty("阶段序号")
    private Integer stageNo;

    @ExcelProperty("阶段名称")
    private String stageName;

    @ExcelProperty("任务序号")
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
}
