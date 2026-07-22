package cn.iocoder.yudao.module.pms.controller.admin.qualityissue;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.dal.dataobject.qualityissue.PmsQualityIssueDO;
import cn.iocoder.yudao.module.pms.service.qualityissue.QualityIssueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 质量问题")
@RestController
@RequestMapping("/pms/quality-issue")
@Validated
public class QualityIssueController {

    @Resource
    private QualityIssueService qualityIssueService;

    @PostMapping("/create")
    @Operation(summary = "创建质量问题")
    @PreAuthorize("@ss.hasPermission('pms:quality:create')")
    public CommonResult<Long> create(@RequestBody PmsQualityIssueDO entity) {
        return success(qualityIssueService.createQualityIssue(entity));
    }

    @PutMapping("/update")
    @Operation(summary = "更新质量问题")
    @PreAuthorize("@ss.hasPermission('pms:quality:update')")
    public CommonResult<Boolean> update(@RequestBody PmsQualityIssueDO entity) {
        qualityIssueService.updateQualityIssue(entity);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除质量问题")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:quality:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        qualityIssueService.deleteQualityIssue(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取质量问题")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:quality:query')")
    public CommonResult<PmsQualityIssueDO> get(@RequestParam("id") Long id) {
        return success(qualityIssueService.getQualityIssue(id));
    }

    @GetMapping("/list")
    @Operation(summary = "获取质量问题列表")
    @PreAuthorize("@ss.hasPermission('pms:quality:query')")
    public CommonResult<List<PmsQualityIssueDO>> list() {
        return success(qualityIssueService.getQualityIssueList());
    }

}