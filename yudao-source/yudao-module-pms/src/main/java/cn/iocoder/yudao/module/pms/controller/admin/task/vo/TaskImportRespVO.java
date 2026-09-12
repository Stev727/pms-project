package cn.iocoder.yudao.module.pms.controller.admin.task.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 任务批量导入结果 VO
 */
@Data
@Builder
public class TaskImportRespVO {

    /** 是否整体成功（任一行校验失败即 false，整批不落库） */
    private Boolean success;

    /** 新创建的阶段数（含纯阶段行声明 + 任务行上的新阶段） */
    private Integer stageCount;

    /** 新创建的任务数（顶层 + 子任务） */
    private Integer taskCount;

    /** 校验失败的行（success=false 时返回，用于生成错误 Excel） */
    private List<TaskImportErrorExcel> failureRows;
}
