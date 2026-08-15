package cn.iocoder.yudao.module.pms.controller.admin.materialtrack;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.module.pms.controller.admin.materialtrack.vo.MaterialTrackImportErrorExcel;
import cn.iocoder.yudao.module.pms.controller.admin.materialtrack.vo.MaterialTrackImportExcel;
import cn.iocoder.yudao.module.pms.controller.admin.materialtrack.vo.MaterialTrackImportRespVO;
import cn.iocoder.yudao.module.pms.dal.dataobject.materialtrack.PmsMaterialTrackDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.materialtrack.PmsMaterialTrackDO;
import cn.iocoder.yudao.module.pms.enums.PmsPermKeyEnum;
import cn.iocoder.yudao.module.pms.service.materialtrack.MaterialTrackService;
import cn.iocoder.yudao.module.pms.service.projectpermission.ProjectPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 物料跟踪 Controller
 *
 * 改造说明（#10 物料跟踪嵌入项目详情）：
 *  - /list 新增可选参数 projectId：
 *      * 非空：项目详情页物料 Tab 走此路径，叠加项目级 material_view 权限
 *      * 空：PMO 全局菜单 /pms/material 走此路径，仅菜单级 pms:material:query 把关
 *  - create/update/delete 叠加项目级 material_add/material_edit/material_delete 权限
 *  - 原全局菜单 /pms/material 保留不变，与项目详情 Tab 共用同一后端
 *  - ProjectPermissionService 以 @Autowired(required=false) 注入，#2 未部署时为 null，
 *    菜单级 @PreAuthorize 兜底（与 README_任务模块.md §3.2 一致）
 */

import cn.hutool.json.JSONUtil;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Tag(name = "管理后台 - 物料跟踪")
@RestController
@RequestMapping("/pms/material-track")
@Validated
public class MaterialTrackController {

    @Resource
    private MaterialTrackService materialTrackService;

    @Autowired(required = false)
    private ProjectPermissionService projectPermissionService;

    /**
     * 项目级权限校验兜底：#2 未部署时跳过，仅菜单级 @PreAuthorize 把关
     */
    private void requireProjectPerm(Long projectId, String permKey) {
        if (projectPermissionService == null || projectId == null) {
            return;
        }
        projectPermissionService.checkPermission(projectId, permKey);
    }

    @PostMapping("/create")
    @Operation(summary = "创建物料跟踪")
    @PreAuthorize("@ss.hasPermission('pms:material:create')")
    public CommonResult<Long> create(@RequestBody PmsMaterialTrackDO entity) {
        requireProjectPerm(entity.getProjectId(), PmsPermKeyEnum.MATERIAL_ADD.getKey());
        return success(materialTrackService.createMaterialTrack(entity));
    }

    @PutMapping("/update")
    @Operation(summary = "更新物料跟踪")
    @PreAuthorize("@ss.hasPermission('pms:material:update')")
    public CommonResult<Boolean> update(@RequestBody PmsMaterialTrackDO entity) {
        requireProjectPerm(entity.getProjectId(), PmsPermKeyEnum.MATERIAL_EDIT.getKey());
        materialTrackService.updateMaterialTrack(entity);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除物料跟踪")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:material:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        PmsMaterialTrackDO track = materialTrackService.getMaterialTrack(id);
        if (track != null) {
            requireProjectPerm(track.getProjectId(), PmsPermKeyEnum.MATERIAL_DELETE.getKey());
        }
        materialTrackService.deleteMaterialTrack(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取物料跟踪")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:material:query')")
    public CommonResult<PmsMaterialTrackDO> get(@RequestParam("id") Long id) {
        return success(materialTrackService.getMaterialTrack(id));
    }

    @GetMapping("/list")
    @Operation(summary = "获取物料跟踪列表")
    @Parameter(name = "projectId", description = "项目ID，传则按项目过滤并叠加项目级权限", required = false)
    @PreAuthorize("@ss.hasPermission('pms:material:query')")
    public CommonResult<List<PmsMaterialTrackDO>> list(
            @RequestParam(value = "projectId", required = false) Long projectId) {
        if (projectId != null) {
            // 项目级权限：查本项目物料需 material_view
            requireProjectPerm(projectId, PmsPermKeyEnum.MATERIAL_VIEW.getKey());
        }
        return success(materialTrackService.getMaterialTrackList(projectId));
    }



    // ==================== Excel 批量导入 ====================

    @GetMapping("/get-import-template")
    @Operation(summary = "下载物料跟踪导入模板")
    @Parameter(name = "projectId", description = "项目ID（可选，仅用于权限校验）", required = false)
    @PreAuthorize("@ss.hasPermission('pms:material:query')")
    public void getImportTemplate(HttpServletResponse response,
                                  @RequestParam(value = "projectId", required = false) Long projectId) throws IOException {
        if (projectId != null) {
            requireProjectPerm(projectId, PmsPermKeyEnum.MATERIAL_VIEW.getKey());
        }
        List<MaterialTrackImportExcel> demo = Arrays.asList(
                MaterialTrackImportExcel.builder()
                        .materialName("示例：无刷电机 60V 2000W")
                        .materialCode("WCL-001")
                        .supplier("示例供应商")
                        .quantity(new java.math.BigDecimal("10"))
                        .unit("个")
                        .planOrderDate(java.time.LocalDate.of(2026, 8, 20))
                        .planDeliveryDate(java.time.LocalDate.of(2026, 9, 1))
                        .currentStatus("not_ordered")
                        .build()
        );
        ExcelUtils.write(response, "物料跟踪导入模板.xlsx", "物料跟踪", MaterialTrackImportExcel.class, demo);
    }

    @PostMapping("/import")
    @Operation(summary = "Excel 批量导入物料跟踪")
    @io.swagger.v3.oas.annotations.Parameters({
            @io.swagger.v3.oas.annotations.Parameter(name = "file", description = "Excel 文件", required = true),
            @io.swagger.v3.oas.annotations.Parameter(name = "projectId", description = "项目ID（导入归属项目）", required = true)
    })
    @PreAuthorize("@ss.hasPermission('pms:material:create')")
    public void importExcel(@RequestParam("file") MultipartFile file,
                            @RequestParam("projectId") Long projectId,
                            HttpServletResponse response) throws IOException {
        requireProjectPerm(projectId, PmsPermKeyEnum.MATERIAL_ADD.getKey());

        List<MaterialTrackImportExcel> rows = ExcelUtils.read(file, MaterialTrackImportExcel.class);
        MaterialTrackImportRespVO result = materialTrackService.importMaterialTrackList(projectId, rows);

        if (result.getSuccess()) {
            response.setContentType("application/json;charset=UTF-8");
            CommonResult<MaterialTrackImportRespVO> cr = CommonResult.success(result);
            response.getWriter().write(JSONUtil.toJsonStr(cr));
        } else {
            ExcelUtils.write(response, "物料跟踪导入错误.xlsx", "错误行",
                    MaterialTrackImportErrorExcel.class, result.getFailureRows());
        }
    }

}
