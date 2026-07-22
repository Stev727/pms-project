package cn.iocoder.yudao.module.pms.controller.admin.materialtrack;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.dal.dataobject.materialtrack.PmsMaterialTrackDO;
import cn.iocoder.yudao.module.pms.service.materialtrack.MaterialTrackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 物料跟踪")
@RestController
@RequestMapping("/pms/material-track")
@Validated
public class MaterialTrackController {

    @Resource
    private MaterialTrackService materialTrackService;

    @PostMapping("/create")
    @Operation(summary = "创建物料跟踪")
    @PreAuthorize("@ss.hasPermission('pms:material:create')")
    public CommonResult<Long> create(@RequestBody PmsMaterialTrackDO entity) {
        return success(materialTrackService.createMaterialTrack(entity));
    }

    @PutMapping("/update")
    @Operation(summary = "更新物料跟踪")
    @PreAuthorize("@ss.hasPermission('pms:material:update')")
    public CommonResult<Boolean> update(@RequestBody PmsMaterialTrackDO entity) {
        materialTrackService.updateMaterialTrack(entity);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除物料跟踪")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:material:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
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
    @PreAuthorize("@ss.hasPermission('pms:material:query')")
    public CommonResult<List<PmsMaterialTrackDO>> list() {
        return success(materialTrackService.getMaterialTrackList());
    }

}