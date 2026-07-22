package cn.iocoder.yudao.module.pms.controller.admin.templatefile;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.dal.dataobject.templatefile.PmsTemplateFileDO;
import cn.iocoder.yudao.module.pms.service.templatefile.TemplateFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 模板附件")
@RestController
@RequestMapping("/pms/template-file")
@Validated
public class TemplateFileController {

    @Resource
    private TemplateFileService templateFileService;

    @PostMapping("/create")
    @Operation(summary = "创建模板附件")
    @PreAuthorize("@ss.hasPermission('pms:template_file:create')")
    public CommonResult<Long> create(@RequestBody PmsTemplateFileDO entity) {
        return success(templateFileService.createTemplateFile(entity));
    }

    @PutMapping("/update")
    @Operation(summary = "更新模板附件")
    @PreAuthorize("@ss.hasPermission('pms:template_file:update')")
    public CommonResult<Boolean> update(@RequestBody PmsTemplateFileDO entity) {
        templateFileService.updateTemplateFile(entity);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除模板附件")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:template_file:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        templateFileService.deleteTemplateFile(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取模板附件")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:template_file:query')")
    public CommonResult<PmsTemplateFileDO> get(@RequestParam("id") Long id) {
        return success(templateFileService.getTemplateFile(id));
    }

    @GetMapping("/list")
    @Operation(summary = "获取模板附件列表")
    @PreAuthorize("@ss.hasPermission('pms:template_file:query')")
    public CommonResult<List<PmsTemplateFileDO>> list() {
        return success(templateFileService.getTemplateFileList());
    }

}