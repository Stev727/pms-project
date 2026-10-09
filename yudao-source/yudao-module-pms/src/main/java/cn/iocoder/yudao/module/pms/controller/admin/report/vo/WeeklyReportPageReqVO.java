package cn.iocoder.yudao.module.pms.controller.admin.report.vo;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 周报报表分页查询入参
 *
 * 维度优先级：deptId > userId > 本人
 */
@Data
public class WeeklyReportPageReqVO {

    /** 基准日期 yyyy-MM-dd；默认今天；按自然周（周一~周日）计算 */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    /** 部门ID（含下级部门所有人员）；优先级高于 userId */
    private Long deptId;

    /** 目标人员ID；为空=本人；0=全部（仅管理员） */
    private Long userId;

    /** 页码（默认 1） */
    private Integer pageNo = 1;

    /** 每页大小（默认 20） */
    private Integer pageSize = 20;
}
