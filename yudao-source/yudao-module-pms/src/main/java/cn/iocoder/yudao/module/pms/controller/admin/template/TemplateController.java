package cn.iocoder.yudao.module.pms.controller.admin.template;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.dal.dataobject.template.PmsTemplateDO;
import cn.iocoder.yudao.module.pms.service.template.TemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 模板")
@RestController
@RequestMapping("/pms/template")
@Validated
public class TemplateController {

    @Resource
    private TemplateService templateService;

    @PostMapping("/create")
    @Operation(summary = "创建模板")
    @PreAuthorize("@ss.hasPermission('pms:template:create')")
    public CommonResult<Long> create(@RequestBody PmsTemplateDO entity) {
        return success(templateService.createTemplate(entity));
    }

    @PutMapping("/update")
    @Operation(summary = "更新模板")
    @PreAuthorize("@ss.hasPermission('pms:template:update')")
    public CommonResult<Boolean> update(@RequestBody PmsTemplateDO entity) {
        templateService.updateTemplate(entity);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除模板")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:template:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        templateService.deleteTemplate(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取模板")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:template:query')")
    public CommonResult<PmsTemplateDO> get(@RequestParam("id") Long id) {
        return success(templateService.getTemplate(id));
    }

    @GetMapping("/list")
    @Operation(summary = "获取模板列表")
    @PreAuthorize("@ss.hasPermission('pms:template:query')")
    public CommonResult<List<PmsTemplateDO>> list() {
        return success(templateService.getTemplateList());
    }

}