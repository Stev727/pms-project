package cn.iocoder.yudao.module.pms.controller.admin.notifyrule;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.dal.dataobject.notifyrule.PmsNotifyRuleDO;
import cn.iocoder.yudao.module.pms.service.notifyrule.NotifyRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 通知规则")
@RestController
@RequestMapping("/pms/notify-rule")
@Validated
public class NotifyRuleController {

    @Resource
    private NotifyRuleService notifyRuleService;

    @PostMapping("/create")
    @Operation(summary = "创建通知规则")
    @PreAuthorize("@ss.hasPermission('pms:notify:create')")
    public CommonResult<Long> create(@RequestBody PmsNotifyRuleDO entity) {
        return success(notifyRuleService.createNotifyRule(entity));
    }

    @PutMapping("/update")
    @Operation(summary = "更新通知规则")
    @PreAuthorize("@ss.hasPermission('pms:notify:update')")
    public CommonResult<Boolean> update(@RequestBody PmsNotifyRuleDO entity) {
        notifyRuleService.updateNotifyRule(entity);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除通知规则")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:notify:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        notifyRuleService.deleteNotifyRule(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取通知规则")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:notify:query')")
    public CommonResult<PmsNotifyRuleDO> get(@RequestParam("id") Long id) {
        return success(notifyRuleService.getNotifyRule(id));
    }

    @GetMapping("/list")
    @Operation(summary = "获取通知规则列表")
    @PreAuthorize("@ss.hasPermission('pms:notify:query')")
    public CommonResult<List<PmsNotifyRuleDO>> list() {
        return success(notifyRuleService.getNotifyRuleList());
    }

}