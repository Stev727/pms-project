package cn.iocoder.yudao.module.pms.controller.admin.externalworklog;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.dal.dataobject.externalworklog.PmsExternalWorklogDO;
import cn.iocoder.yudao.module.pms.service.externalworklog.ExternalWorklogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 外部工时")
@RestController
@RequestMapping("/pms/external-worklog")
@Validated
public class ExternalWorklogController {

    @Resource
    private ExternalWorklogService externalWorklogService;

    @PostMapping("/create")
    @Operation(summary = "创建外部工时")
    @PreAuthorize("@ss.hasPermission('pms:external_worklog:create')")
    public CommonResult<Long> create(@RequestBody PmsExternalWorklogDO entity) {
        return success(externalWorklogService.createExternalWorklog(entity));
    }

    @PutMapping("/update")
    @Operation(summary = "更新外部工时")
    @PreAuthorize("@ss.hasPermission('pms:external_worklog:update')")
    public CommonResult<Boolean> update(@RequestBody PmsExternalWorklogDO entity) {
        externalWorklogService.updateExternalWorklog(entity);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除外部工时")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:external_worklog:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        externalWorklogService.deleteExternalWorklog(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取外部工时")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:external_worklog:query')")
    public CommonResult<PmsExternalWorklogDO> get(@RequestParam("id") Long id) {
        return success(externalWorklogService.getExternalWorklog(id));
    }

    @GetMapping("/list")
    @Operation(summary = "获取外部工时列表")
    @PreAuthorize("@ss.hasPermission('pms:external_worklog:query')")
    public CommonResult<List<PmsExternalWorklogDO>> list() {
        return success(externalWorklogService.getExternalWorklogList());
    }

}