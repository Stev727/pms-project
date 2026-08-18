package cn.iocoder.yudao.module.pms.controller.admin.dashboard.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 部门协作分析统计 VO")
@Data
public class DeptStatVO {

    @Schema(description = "部门ID")
    private Long deptId;

    @Schema(description = "部门名称")
    private String deptName;

    @Schema(description = "参与项目数（该部门成员参与的项目去重数）")
    private Integer projectCount;

    @Schema(description = "参与成员数")
    private Integer memberCount;

    @Schema(description = "任务总数（负责人属该部门）")
    private Integer taskTotal;

    @Schema(description = "已完成任务数")
    private Integer taskCompleted;

    @Schema(description = "延期任务数")
    private Integer taskDelayed;

    @Schema(description = "完成率（百分比 0-100，保留1位小数）")
    private Double completionRate;

    @Schema(description = "延期任务占全部延期的比例（百分比 0-100，保留1位小数）")
    private Double delayRate;
}
