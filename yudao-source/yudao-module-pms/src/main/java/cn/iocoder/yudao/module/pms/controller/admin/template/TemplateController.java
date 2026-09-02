package cn.iocoder.yudao.module.pms.controller.admin.template;

import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.module.pms.controller.admin.template.vo.TemplateStageTaskImportErrorExcel;
import cn.iocoder.yudao.module.pms.controller.admin.template.vo.TemplateStageTaskImportExcel;
import cn.iocoder.yudao.module.pms.controller.admin.template.vo.TemplateStageTaskImportRespVO;
import cn.iocoder.yudao.module.pms.dal.dataobject.template.PmsTemplateDO;
import cn.iocoder.yudao.module.pms.service.template.TemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 模板 Controller
 *
 * ============================ 改造说明 ============================
 * 新增（模板阶段任务 Excel 批量导入，模板=project_type='standard_template' 的项目）：
 *  - GET  /pms/template/get-stage-task-import-template：下载导入模板（预填当前模板数据）
 *  - POST /pms/template/import-stage-task：全量覆盖导入（校验失败返回标红错误 Excel）
 *  - 权限沿用模板菜单级 pms:template:query（模板权限已简化为菜单级）
 * ==================================================================
 */
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

    // ==================== 阶段任务 Excel 批量导入 ====================

    @GetMapping("/get-stage-task-import-template")
    @Operation(summary = "下载模板阶段任务导入模板（预填当前模板数据）")
    @Parameter(name = "projectId", description = "模板项目ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:template:query')")
    public void getStageTaskImportTemplate(HttpServletResponse response,
                                           @RequestParam("projectId") Long projectId) throws IOException {
        List<TemplateStageTaskImportExcel> rows = templateService.getStageTaskTemplateRows(projectId);
        ExcelUtils.write(response, "模板阶段任务导入模板.xlsx", "阶段任务", TemplateStageTaskImportExcel.class, rows);
    }

    @PostMapping("/import-stage-task")
    @Operation(summary = "Excel 批量导入模板阶段任务（全量覆盖）")
    @Parameters({
            @Parameter(name = "file", description = "Excel 文件", required = true),
            @Parameter(name = "projectId", description = "模板项目ID", required = true)
    })
    @PreAuthorize("@ss.hasPermission('pms:template:query')")
    public void importStageTask(@RequestParam("file") MultipartFile file,
                                @RequestParam("projectId") Long projectId,
                                HttpServletResponse response) throws IOException {
        // 1. 解析 Excel
        List<TemplateStageTaskImportExcel> rows = ExcelUtils.read(file, TemplateStageTaskImportExcel.class);

        // 2. 整批校验 + 全量覆盖重建（事务内软删现有阶段/任务后按文件重建）
        TemplateStageTaskImportRespVO result = templateService.importStageTask(projectId, rows);

        if (result.getSuccess()) {
            // 3a. 校验通过 → 返回 JSON 成功响应
            response.setContentType("application/json;charset=UTF-8");
            CommonResult<TemplateStageTaskImportRespVO> cr = CommonResult.success(result);
            response.getWriter().write(JSONUtil.toJsonStr(cr));
        } else {
            // 3b. 校验失败 → 输出标红错误 Excel 供用户下载修正后重试
            ExcelUtils.write(response, "模板阶段任务导入错误.xlsx", "错误行",
                    TemplateStageTaskImportErrorExcel.class, result.getFailureRows());
        }
    }
}
