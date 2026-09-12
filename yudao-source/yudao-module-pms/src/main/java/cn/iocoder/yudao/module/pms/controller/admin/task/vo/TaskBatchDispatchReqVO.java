package cn.iocoder.yudao.module.pms.controller.admin.task.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 批量派发请求 VO
 */
@Schema(description = "管理后台 - PMS 任务批量派发 Request VO")
@Data
public class TaskBatchDispatchReqVO {

    @Schema(description = "任务ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "任务ID列表不能为空")
    private List<Long> taskIds;

    @Schema(description = "统一设置的负责人ID（补到未设置负责人的任务上，已设的不覆盖）")
    private Long defaultOwnerId;
}
