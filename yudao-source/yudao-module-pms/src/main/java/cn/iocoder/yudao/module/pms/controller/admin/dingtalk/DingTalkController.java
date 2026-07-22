package cn.iocoder.yudao.module.pms.controller.admin.dingtalk;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.dal.dataobject.dingtalk.PmsDingTalkConfigDO;
import cn.iocoder.yudao.module.pms.service.dingtalk.DingTalkApiService;
import cn.iocoder.yudao.module.pms.service.dingtalk.DingTalkNotifyService;
import cn.iocoder.yudao.module.pms.service.dingtalk.DingTalkSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 钉钉集成管理 Controller
 */
@RestController
@RequestMapping("/pms/dingtalk")
@Tag(name = "钉钉集成管理")
@Slf4j
public class DingTalkController {

    @Resource
    private DingTalkApiService dingTalkApiService;

    @Resource
    private DingTalkNotifyService dingTalkNotifyService;

    @Resource
    private DingTalkSyncService dingTalkSyncService;

    // ==================== 配置管理 ====================

    @GetMapping("/config")
    @Operation(summary = "获取钉钉配置")
    @PreAuthorize("@ss.hasPermission('pms:dingtalk:query')")
    public CommonResult<PmsDingTalkConfigDO> getConfig() {
        return success(dingTalkApiService.getConfig());
    }

    @PutMapping("/config")
    @Operation(summary = "更新钉钉配置")
    @PreAuthorize("@ss.hasPermission('pms:dingtalk:update')")
    public CommonResult<Boolean> updateConfig(@RequestBody PmsDingTalkConfigDO config) {
        dingTalkApiService.updateConfig(config);
        return success(true);
    }

    @GetMapping("/test")
    @Operation(summary = "测试钉钉连接")
    @PreAuthorize("@ss.hasPermission('pms:dingtalk:query')")
    public CommonResult<String> testConnection() {
        return success(dingTalkApiService.testConnection());
    }

    // ==================== 通知管理 ====================

    @PostMapping("/notify/check")
    @Operation(summary = "手动触发通知检查")
    @PreAuthorize("@ss.hasPermission('pms:dingtalk:notify')")
    public CommonResult<String> triggerNotifyCheck() {
        dingTalkNotifyService.executeDailyNotifyCheck();
        return success("通知检查已触发，请查看日志");
    }

    @PostMapping("/notify/test")
    @Operation(summary = "发送测试通知")
    @PreAuthorize("@ss.hasPermission('pms:dingtalk:notify')")
    public CommonResult<Boolean> sendTestNotify(
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam("userId") Long userId) {
        boolean result = dingTalkNotifyService.sendNotifyDirect(
                title, content, List.of(userId), "manual_test", "test", null);
        return success(result);
    }

    // ==================== 组织架构同步 ====================

    @PostMapping("/sync/all")
    @Operation(summary = "全量同步组织架构")
    @PreAuthorize("@ss.hasPermission('pms:dingtalk:sync')")
    public CommonResult<Map<String, Object>> syncAll() {
        return success(dingTalkSyncService.syncAll());
    }

    @PostMapping("/sync/depts")
    @Operation(summary = "同步部门列表")
    @PreAuthorize("@ss.hasPermission('pms:dingtalk:sync')")
    public CommonResult<Map<String, Object>> syncDepartments() {
        return success(dingTalkSyncService.syncDepartments());
    }

    @PostMapping("/sync/users")
    @Operation(summary = "同步用户列表")
    @PreAuthorize("@ss.hasPermission('pms:dingtalk:sync')")
    public CommonResult<Map<String, Object>> syncUsers() {
        return success(dingTalkSyncService.syncUsers());
    }

    @GetMapping("/sync/status")
    @Operation(summary = "获取同步状态")
    @PreAuthorize("@ss.hasPermission('pms:dingtalk:query')")
    public CommonResult<Map<String, Object>> getSyncStatus() {
        return success(dingTalkSyncService.getSyncStatus());
    }
}
