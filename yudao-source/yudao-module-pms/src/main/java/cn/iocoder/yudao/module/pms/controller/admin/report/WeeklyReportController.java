package cn.iocoder.yudao.module.pms.controller.admin.report;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.module.pms.controller.admin.report.vo.*;
import cn.iocoder.yudao.module.pms.service.report.WeeklyReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * 周报报表 Controller
 *
 * 独立菜单「周报报表」（任务中心 → 周报报表）；
 * 与现有「周报看板」功能完全独立，不修改 TaskController / weekly-report 页面。
 *
 * 维度优先级：deptId > userId > 本人
 */
@Tag(name = "PMS - 周报报表")
@RestController
@RequestMapping("/pms/report/weekly")
@Validated
public class WeeklyReportController {

    @Resource
    private WeeklyReportService weeklyReportService;

    @GetMapping("/depts")
    @Operation(summary = "获取当前用户可见部门列表 + 登录人默认部门（前端筛选器用）")
    @PreAuthorize("@ss.hasPermission('pms:task:query')")
    public CommonResult<WeeklyReportDeptRespVO> depts() {
        return success(weeklyReportService.getVisibleDepts());
    }

    @GetMapping("/page")
    @Operation(summary = "周报报表分页查询（5 区块扁平化，单页表格）")
    @Parameter(name = "date", description = "基准日期 yyyy-MM-dd，默认今天")
    @Parameter(name = "deptId", description = "部门ID（含下级部门所有人员）；优先级高于 userId")
    @Parameter(name = "userId", description = "目标人员ID；为空=本人；0=全部（仅管理员）")
    @Parameter(name = "pageNo", description = "页码，默认 1")
    @Parameter(name = "pageSize", description = "每页大小，默认 20")
    @PreAuthorize("@ss.hasPermission('pms:task:query')")
    public CommonResult<List<WeeklyReportRowVO>> page(
            @RequestParam(value = "date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestParam(value = "deptId", required = false) Long deptId,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "pageNo", required = false, defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") Integer pageSize) {
        WeeklyReportPageReqVO req = new WeeklyReportPageReqVO();
        req.setDate(date);
        req.setDeptId(deptId);
        req.setUserId(userId);
        req.setPageNo(pageNo);
        req.setPageSize(pageSize);
        return success(weeklyReportService.getWeeklyReportPage(req));
    }

    @GetMapping("/export")
    @Operation(summary = "导出周报报表（按当前筛选条件，单 Sheet 分类列）")
    @Parameter(name = "date", description = "基准日期 yyyy-MM-dd，默认今天")
    @Parameter(name = "deptId", description = "部门ID（含下级部门所有人员），优先级高于 userId")
    @Parameter(name = "userId", description = "目标人员ID；为空=本人；0=全部（仅管理员）")
    @PreAuthorize("@ss.hasPermission('pms:task:query')")
    public void export(HttpServletResponse response,
                       @RequestParam(value = "date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
                       @RequestParam(value = "deptId", required = false) Long deptId,
                       @RequestParam(value = "userId", required = false) Long userId) throws IOException {
        WeeklyReportExportResult pkg = weeklyReportService.exportWeeklyReport(date, deptId, userId);
        ExcelUtils.write(response, pkg.getFileName(), "周报报表", WeeklyReportExportVO.class, pkg.getData());
    }

    private static <T> CommonResult<T> success(T data) {
        return CommonResult.success(data);
    }
}
