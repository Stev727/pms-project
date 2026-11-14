package cn.iocoder.yudao.module.pms.controller.admin.overdueremind;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.service.overdueremind.OverdueRemindService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - PMS 超期提请")
@RestController
@RequestMapping("/pms/overdue-remind")
@Validated
public class OverdueRemindController {

    @Resource
    private OverdueRemindService overdueRemindService;

    @GetMapping("/page")
    @Operation(summary = "分页查询超期任务")
    @PreAuthorize("@ss.hasPermission('pms:overdue-remind:query')")
    @Parameter(name = "deptId", description = "责任人部门筛选")
    @Parameter(name = "userId", description = "责任人筛选")
    @Parameter(name = "projectId", description = "项目筛选")
    public CommonResult<Map<String, Object>> getOverdueTaskPage(
            @RequestParam(value = "deptId", required = false) Long deptId,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "pageNo", required = false, defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
        return success(overdueRemindService.getOverdueTaskPage(deptId, userId, projectId, pageNo, pageSize));
    }

    @GetMapping("/monitor-configs")
    @Operation(summary = "获取超期提请监控配置（通知人+被监控责任人）")
    @PreAuthorize("@ss.hasPermission('pms:overdue-remind:query')")
    public CommonResult<List<Map<String, Object>>> getMonitorConfigs() {
        return success(overdueRemindService.getMonitorConfigs());
    }

    @PutMapping("/monitor-configs")
    @Operation(summary = "保存超期提请监控配置（全量替换，每条=通知人+被监控责任人）")
    @PreAuthorize("@ss.hasPermission('pms:overdue-remind:update')")
    public CommonResult<Boolean> updateMonitorConfigs(@RequestBody List<Map<String, Object>> configs) {
        overdueRemindService.updateMonitorConfigs(configs);
        return success(true);
    }
}
