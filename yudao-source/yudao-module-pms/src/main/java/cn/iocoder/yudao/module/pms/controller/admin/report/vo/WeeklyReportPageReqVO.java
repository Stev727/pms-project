package cn.iocoder.yudao.module.pms.controller.admin.report.vo;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 周报报表分页查询入参
 *
 * 统计时间段 [startDate, endDate]（用户主动筛选，不再按自然周推算）：
 *   应完成   = 计划结束日期落在时间段内
 *   进行中   = 未完成 + 任务窗口与时间段重叠
 *   后续计划 = 未完成 + 计划开始 > 时间段末
 *   已延期   = 未完成 + 计划结束 < 时间段始
 *   动态     = 状态/进度变更发生在时间段内
 *
 * 维度优先级：deptId > userId > 本人
 */
@Data
public class WeeklyReportPageReqVO {

    /** 时间段开始 yyyy-MM-dd；为空默认本周一 */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    /** 时间段结束 yyyy-MM-dd；为空默认今天 */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    /** 部门ID（含下级部门所有人员）；优先级高于 userId */
    private Long deptId;

    /** 目标人员ID；为空=本人；0=全部（仅管理员） */
    private Long userId;

    /** 页码（默认 1） */
    private Integer pageNo = 1;

    /** 每页大小（默认 20） */
    private Integer pageSize = 20;
}
