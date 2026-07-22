package cn.iocoder.yudao.module.pms.controller.admin.performancemonthly;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.dal.dataobject.performancemonthly.PmsPerformanceMonthlyDO;
import cn.iocoder.yudao.module.pms.service.performancemonthly.PerformanceMonthlyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 月度绩效")
@RestController
@RequestMapping("/pms/performance-monthly")
@Validated
public class PerformanceMonthlyController {

    @Resource
    private PerformanceMonthlyService performanceMonthlyService;

    @PostMapping("/create")
    @Operation(summary = "创建月度绩效")
    @PreAuthorize("@ss.hasPermission('pms:performance:create')")
    public CommonResult<Long> create(@RequestBody PmsPerformanceMonthlyDO entity) {
        return success(performanceMonthlyService.createPerformanceMonthly(entity));
    }

    @PutMapping("/update")
    @Operation(summary = "更新月度绩效")
    @PreAuthorize("@ss.hasPermission('pms:performance:update')")
    public CommonResult<Boolean> update(@RequestBody PmsPerformanceMonthlyDO entity) {
        performanceMonthlyService.updatePerformanceMonthly(entity);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除月度绩效")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:performance:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        performanceMonthlyService.deletePerformanceMonthly(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取月度绩效")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:performance:query')")
    public CommonResult<PmsPerformanceMonthlyDO> get(@RequestParam("id") Long id) {
        return success(performanceMonthlyService.getPerformanceMonthly(id));
    }

    @GetMapping("/list")
    @Operation(summary = "获取月度绩效列表")
    @PreAuthorize("@ss.hasPermission('pms:performance:query')")
    public CommonResult<List<PmsPerformanceMonthlyDO>> list() {
        return success(performanceMonthlyService.getPerformanceMonthlyList());
    }

}