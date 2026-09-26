package cn.iocoder.yudao.module.pms.controller.admin.task.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "管理后台 - 批量设置任务输出物开关 Request VO")
@Data
public class TaskBatchDeliverableReqVO {

    @Schema(description = "任务编号列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "[1,2,3]")
    @NotEmpty(message = "请选择要设置的任务")
    private List<Long> taskIds;

    @Schema(description = "是否要求输出物", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @NotNull(message = "请选择是否要求输出物")
    private Boolean requireDeliverable;
}
