package cn.iocoder.yudao.module.pms.controller.admin.approvalrecord;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.dal.dataobject.approvalrecord.PmsApprovalRecordDO;
import cn.iocoder.yudao.module.pms.service.approvalrecord.ApprovalRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 审批记录")
@RestController
@RequestMapping("/pms/approval-record")
@Validated
public class ApprovalRecordController {

    @Resource
    private ApprovalRecordService approvalRecordService;

    @PostMapping("/create")
    @Operation(summary = "创建审批记录")
    @PreAuthorize("@ss.hasPermission('pms:approval:create')")
    public CommonResult<Long> create(@RequestBody PmsApprovalRecordDO entity) {
        return success(approvalRecordService.createApprovalRecord(entity));
    }

    @PutMapping("/update")
    @Operation(summary = "更新审批记录")
    @PreAuthorize("@ss.hasPermission('pms:approval:update')")
    public CommonResult<Boolean> update(@RequestBody PmsApprovalRecordDO entity) {
        approvalRecordService.updateApprovalRecord(entity);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除审批记录")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:approval:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        approvalRecordService.deleteApprovalRecord(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取审批记录")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:approval:query')")
    public CommonResult<PmsApprovalRecordDO> get(@RequestParam("id") Long id) {
        return success(approvalRecordService.getApprovalRecord(id));
    }

    @GetMapping("/list")
    @Operation(summary = "获取审批记录列表")
    @PreAuthorize("@ss.hasPermission('pms:approval:query')")
    public CommonResult<List<PmsApprovalRecordDO>> list() {
        return success(approvalRecordService.getApprovalRecordList());
    }

}