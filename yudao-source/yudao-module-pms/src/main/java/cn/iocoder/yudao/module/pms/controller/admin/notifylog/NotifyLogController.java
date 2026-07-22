package cn.iocoder.yudao.module.pms.controller.admin.notifylog;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.dal.dataobject.notifylog.PmsNotifyLogDO;
import cn.iocoder.yudao.module.pms.service.notifylog.NotifyLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 通知日志")
@RestController
@RequestMapping("/pms/notify-log")
@Validated
public class NotifyLogController {

    @Resource
    private NotifyLogService notifyLogService;

    @PostMapping("/create")
    @Operation(summary = "创建通知日志")
    @PreAuthorize("@ss.hasPermission('pms:notify_log:create')")
    public CommonResult<Long> create(@RequestBody PmsNotifyLogDO entity) {
        return success(notifyLogService.createNotifyLog(entity));
    }

    @PutMapping("/update")
    @Operation(summary = "更新通知日志")
    @PreAuthorize("@ss.hasPermission('pms:notify_log:update')")
    public CommonResult<Boolean> update(@RequestBody PmsNotifyLogDO entity) {
        notifyLogService.updateNotifyLog(entity);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除通知日志")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:notify_log:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        notifyLogService.deleteNotifyLog(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取通知日志")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:notify_log:query')")
    public CommonResult<PmsNotifyLogDO> get(@RequestParam("id") Long id) {
        return success(notifyLogService.getNotifyLog(id));
    }

    @GetMapping("/list")
    @Operation(summary = "获取通知日志列表")
    @PreAuthorize("@ss.hasPermission('pms:notify_log:query')")
    public CommonResult<List<PmsNotifyLogDO>> list() {
        return success(notifyLogService.getNotifyLogList());
    }

}