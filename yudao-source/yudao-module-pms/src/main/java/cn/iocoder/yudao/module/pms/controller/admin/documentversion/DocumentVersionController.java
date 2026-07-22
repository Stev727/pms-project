package cn.iocoder.yudao.module.pms.controller.admin.documentversion;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.dal.dataobject.documentversion.PmsDocumentVersionDO;
import cn.iocoder.yudao.module.pms.service.documentversion.DocumentVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 文档版本")
@RestController
@RequestMapping("/pms/document-version")
@Validated
public class DocumentVersionController {

    @Resource
    private DocumentVersionService documentVersionService;

    @PostMapping("/create")
    @Operation(summary = "创建文档版本")
    @PreAuthorize("@ss.hasPermission('pms:document_version:create')")
    public CommonResult<Long> create(@RequestBody PmsDocumentVersionDO entity) {
        return success(documentVersionService.createDocumentVersion(entity));
    }

    @PutMapping("/update")
    @Operation(summary = "更新文档版本")
    @PreAuthorize("@ss.hasPermission('pms:document_version:update')")
    public CommonResult<Boolean> update(@RequestBody PmsDocumentVersionDO entity) {
        documentVersionService.updateDocumentVersion(entity);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除文档版本")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:document_version:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        documentVersionService.deleteDocumentVersion(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取文档版本")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:document_version:query')")
    public CommonResult<PmsDocumentVersionDO> get(@RequestParam("id") Long id) {
        return success(documentVersionService.getDocumentVersion(id));
    }

    @GetMapping("/list")
    @Operation(summary = "获取文档版本列表")
    @PreAuthorize("@ss.hasPermission('pms:document_version:query')")
    public CommonResult<List<PmsDocumentVersionDO>> list() {
        return success(documentVersionService.getDocumentVersionList());
    }

}