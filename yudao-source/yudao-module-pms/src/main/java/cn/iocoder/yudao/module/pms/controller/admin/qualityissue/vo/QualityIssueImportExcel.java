package cn.iocoder.yudao.module.pms.controller.admin.qualityissue.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 质量问题 Excel 导入行 VO
 *
 * 严格对应用户提供的 11 列模板：
 *  标题 / 类型 / 严重程度 / 描述 / 责任人(姓名) /
 *  发现人(姓名) / 发现日期 / 期望完成日期 / 关联任务(名称) / 状态 / 整改要求
 *
 * 说明：
 *  - 责任人/发现人均按姓名匹配 system_users.nickname；0 匹配或重名 → 该行报错
 *  - 关联任务按名称在本项目内匹配 pms_task.task_name；0 匹配或重名 → 该行报错
 *  - 日期字段使用 LocalDate，FastExcel 自动按 "yyyy-MM-dd" 解析
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QualityIssueImportExcel {

    @ExcelProperty("标题")
    private String issueTitle;

    @ExcelProperty("类型")
    private String issueType;

    @ExcelProperty("严重程度")
    private String severity;

    @ExcelProperty("描述")
    private String issueDescription;

    @ExcelProperty("责任人(姓名)")
    private String responsiblePersonName;

    @ExcelProperty("发现人(姓名)")
    private String discovererName;

    @ExcelProperty("发现日期")
    private LocalDate discoveredDate;

    @ExcelProperty("期望完成日期")
    private LocalDate dueDate;

    @ExcelProperty("关联任务(名称)")
    private String taskName;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("整改要求")
    private String rectificationRequirement;
}

