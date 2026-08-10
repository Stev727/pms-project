package cn.iocoder.yudao.module.pms.controller.admin.task.vo;

import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import lombok.Data;

import java.util.List;

/**
 * 我的任务看板 聚合返回结构（#日常任务 / 我的任务看板）
 *
 * 三块数据：
 *   legacyTasks   历史遗留任务：计划开始早于查询范围起点且未完成（含项目 + 日常）
 *   projectGroups 时间段内的项目任务，按项目分组（组内按 plan_start_date 升序）
 *   dailyTasks    时间段内的日常任务（project_id 为 NULL），按 plan_start_date 升序
 */
@Data
public class TaskBoardVO {

    /**
     * 历史遗留任务
     */
    private List<PmsTaskDO> legacyTasks;

    /**
     * 时间段内项目任务（按项目分组）
     */
    private List<ProjectTaskGroup> projectGroups;

    /**
     * 时间段内日常任务（无项目）
     */
    private List<PmsTaskDO> dailyTasks;

    /**
     * 项目任务分组
     */
    @Data
    public static class ProjectTaskGroup {
        private Long projectId;
        private String projectName;
        private List<PmsTaskDO> tasks;
    }
}
