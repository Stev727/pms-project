package cn.iocoder.yudao.module.pms.controller.admin.task.vo;

import cn.iocoder.yudao.module.pms.dal.dataobject.project.PmsProjectDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 周报看板聚合返回 VO
 *
 * 四区块：
 *   A 上周完成（actual_complete_date 落在上周）
 *   B 本周计划（未完成 + 计划窗口与本周重叠）
 *   C 上周延期（已启动/流转过但未完成且逾期到上周末）
 *   D 上周动态（任务状态/进度变更日志，方案2 精确前后值）
 */
@Data
public class TaskWeeklyReportVO {

    private LocalDate weekStart;
    private LocalDate weekEnd;
    private LocalDate lastWeekStart;
    private LocalDate lastWeekEnd;
    private Long targetUserId;
    private Boolean isAdmin;
    private Boolean isLeader;

    private List<PmsTaskDO> lastWeekCompleted;
    private List<PmsTaskDO> thisWeekPlan;
    private List<DelayedTaskVO> lastWeekDelayed;
    private List<TaskChangeLogVO> lastWeekChanges;

    @Data
    public static class DelayedTaskVO {
        private PmsTaskDO task;
        private Long overdueDays;
    }

    @Data
    public static class TaskChangeLogVO {
        private Long taskId;
        private String taskName;
        private String projectName;
        private List<ChangeItemVO> changes;
    }

    @Data
    public static class ChangeItemVO {
        private String operationType;
        private String beforeValue;
        private String afterValue;
        private LocalDateTime operationTime;
        private String operatorName;
    }
}
