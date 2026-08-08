package cn.iocoder.yudao.module.pms.controller.admin.document;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.controller.admin.document.vo.PreviewResultVO;
import cn.iocoder.yudao.module.pms.dal.dataobject.document.PmsDocumentDO;
import cn.iocoder.yudao.module.pms.service.document.DocumentPreviewService;
import cn.iocoder.yudao.module.pms.service.document.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 文档 Controller
 *
 * 【#6/#7 改造】新增端点：
 * - GET /preview：预览元信息
 * - GET /preview-file：预览文件字节流（PDF/图片）
 * - GET /download：下载（校验 allowDownload，增加 downloadCount）
 * - GET /list-by-project：按项目 + 用户权限过滤的文档列表
 *
 * 原有 5 个端点（create/update/delete/get/list）保持不变，老调用方无缝兼容。
 */
@Tag(name = "管理后台 - 文档")
@RestController
@RequestMapping("/pms/document")
@Validated
public class DocumentController {

    @Resource
    private DocumentService documentService;
    @Resource
    private DocumentPreviewService documentPreviewService;

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
    @Operation(summary = "获取文档列表（全量，管理用）")
    @PreAuthorize("@ss.hasPermission('pms:document:query')")
    public CommonResult<List<PmsDocumentDO>> list() {
        return success(documentService.getDocumentList());
    }

    // ========== #7 新增：按项目 + 权限过滤的列表 ==========

    @GetMapping("/list-by-project")
    @Operation(summary = "按项目获取文档列表（按用户权限过滤）")
    @Parameter(name = "projectId", description = "项目ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:document:query')")
    public CommonResult<List<PmsDocumentDO>> listByProject(@RequestParam("projectId") Long projectId) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return success(documentService.getDocumentListForUser(projectId, userId));
    }

    // ========== #6 新增：预览 ==========

    @GetMapping("/preview")
    @Operation(summary = "获取文档预览信息")
    @Parameter(name = "docId", description = "文档ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:document:query')")
    public CommonResult<PreviewResultVO> preview(@RequestParam("docId") Long docId) {
        return success(documentPreviewService.preview(docId));
    }

    @GetMapping("/preview-file")
    @Operation(summary = "获取文档预览文件（PDF/图片字节流）")
    @Parameter(name = "docId", description = "文档ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:document:query')")
    public void previewFile(@RequestParam("docId") Long docId, HttpServletResponse response) {
        documentPreviewService.previewFile(docId, response);
    }

    // ========== #7 新增：下载 ==========

    @GetMapping("/download")
    @Operation(summary = "下载文档")
    @Parameter(name = "docId", description = "文档ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:document:query')")
    public void download(@RequestParam("docId") Long docId, HttpServletResponse response) {
        // downloadFile 内部校验 canDownload 权限（含 canView + allowDownload），输出原文件字节流。
        documentPreviewService.downloadFile(docId, response);
        // 下载成功后累加下载计数
        documentService.incrementDownloadCount(docId);
    }

}

