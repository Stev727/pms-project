package cn.iocoder.yudao.module.pms.controller.admin.changerecord;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.dal.dataobject.changerecord.PmsChangeRecordDO;
import cn.iocoder.yudao.module.pms.service.changerecord.ChangeRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 变更记录")
@RestController
@RequestMapping("/pms/change-record")
@Validated
public class ChangeRecordController {

    @Resource
    private ChangeRecordService changeRecordService;

    @PostMapping("/create")
    @Operation(summary = "创建变更记录")
    @PreAuthorize("@ss.hasPermission('pms:change:create')")
    public CommonResult<Long> create(@RequestBody PmsChangeRecordDO entity) {
        return success(changeRecordService.createChangeRecord(entity));
    }

    @PutMapping("/review")
    @Operation(summary = "项目经理审核任务变更")
    @PreAuthorize("@ss.hasPermission(pms:change:update)")
    public CommonResult<Boolean> review(@RequestParam("id") Long id,
                                        @RequestParam("approved") boolean approved,
                                        @RequestParam("approverId") Long approverId) {
        changeRecordService.reviewChange(id, approved, approverId);
        return success(true);
    }

    @PutMapping("/execute")
    @Operation(summary = "项目经理执行审核通过的任务变更")
    @PreAuthorize("@ss.hasPermission(pms:change:update)")
    public CommonResult<Boolean> execute(@RequestParam("id") Long id) {
        changeRecordService.executeApprovedChange(id);
        return success(true);
    }

    @PutMapping("/update")
    @Operation(summary = "更新变更记录")
    @PreAuthorize("@ss.hasPermission('pms:change:update')")
    public CommonResult<Boolean> update(@RequestBody PmsChangeRecordDO entity) {
        changeRecordService.updateChangeRecord(entity);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除变更记录")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:change:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        changeRecordService.deleteChangeRecord(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取变更记录")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:change:query')")
    public CommonResult<PmsChangeRecordDO> get(@RequestParam("id") Long id) {
        return success(changeRecordService.getChangeRecord(id));
    }

    @GetMapping("/list")
    @Operation(summary = "获取变更记录列表")
    @PreAuthorize("@ss.hasPermission('pms:change:query')")
    public CommonResult<List<PmsChangeRecordDO>> list() {
        return success(changeRecordService.getChangeRecordList());
    }

}