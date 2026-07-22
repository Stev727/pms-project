package cn.iocoder.yudao.module.pms.controller.admin.document;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.dal.dataobject.document.PmsDocumentDO;
import cn.iocoder.yudao.module.pms.service.document.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 文档")
@RestController
@RequestMapping("/pms/document")
@Validated
public class DocumentController {

    @Resource
    private DocumentService documentService;

    @PostMapping("/create")
    @Operation(summary = "创建文档")
    @PreAuthorize("@ss.hasPermission('pms:document:create')")
    public CommonResult<Long> create(@RequestBody PmsDocumentDO entity) {
        return success(documentService.createDocument(entity));
    }

    @PutMapping("/update")
    @Operation(summary = "更新文档")
    @PreAuthorize("@ss.hasPermission('pms:document:update')")
    public CommonResult<Boolean> update(@RequestBody PmsDocumentDO entity) {
        documentService.updateDocument(entity);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除文档")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:document:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        documentService.deleteDocument(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取文档")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:document:query')")
    public CommonResult<PmsDocumentDO> get(@RequestParam("id") Long id) {
        return success(documentService.getDocument(id));
    }

    @GetMapping("/list")
    @Operation(summary = "获取文档列表")
    @PreAuthorize("@ss.hasPermission('pms:document:query')")
    public CommonResult<List<PmsDocumentDO>> list() {
        return success(documentService.getDocumentList());
    }

}