package cn.iocoder.yudao.module.pms.controller.admin.task.vo;

import lombok.Data;

import java.util.List;

/**
 * 批量派发结果 VO（逐条独立成败，不整批回滚）
 */
@Data
public class TaskBatchDispatchRespVO {

    /** 成功条数 */
    private Integer successCount;

    /** 失败条数 */
    private Integer failureCount;

    /** 逐条结果明细 */
    private List<Item> items;

    @Data
    public static class Item {
        /** 任务ID */
        private Long taskId;
        /** 任务名称 */
        private String taskName;
        /** 负责人姓名 */
        private String ownerName;
        /** 是否派发成功 */
        private Boolean success;
        /** 失败原因（success=false 时）或通知告警（success=true 但通知失败） */
        private String reason;
        /** 负责人是否由「统一设置」补齐 */
        private Boolean ownerPatched;
        /** 钉钉通知是否发送成功 */
        private Boolean notified;
    }
}
