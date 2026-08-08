package cn.iocoder.yudao.module.pms.controller.admin.qualityissue;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.module.pms.controller.admin.qualityissue.vo.QualityIssueImportErrorExcel;
import cn.iocoder.yudao.module.pms.controller.admin.qualityissue.vo.QualityIssueImportExcel;
import cn.iocoder.yudao.module.pms.controller.admin.qualityissue.vo.QualityIssueImportRespVO;
import cn.iocoder.yudao.module.pms.dal.dataobject.qualityissue.PmsQualityIssueDO;
import cn.iocoder.yudao.module.pms.enums.PmsPermKeyEnum;
import cn.iocoder.yudao.module.pms.service.projectpermission.ProjectPermissionService;
import cn.iocoder.yudao.module.pms.service.qualityissue.QualityIssueService;
import cn.hutool.json.JSONUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 质量问题 Controller
 *
 * 改造说明（#8 质量问题 Excel 批量导入）：
 *  - 新增 GET  /pms/quality-issue/get-import-template：下载导入模板（含示例行）
 *  - 新增 POST /pms/quality-issue/import：批量导入（整批校验+回滚，失败返回标红错误 Excel）
 *  - 改造 GET  /pms/quality-issue/list：新增可选参数 projectId，按项目过滤
 *  - 权限叠加：菜单级 @PreAuthorize('pms:quality:*') + 项目级 ProjectPermissionService.checkPermission
 *    （ProjectPermissionService 以 @Autowired(required=false) 注入，#2 未部署时为 null，
 *     菜单级 @PreAuthorize 兜底；与 README_任务模块.md §3.2 一致）
 */
@Tag(name = "管理后台 - 质量问题")
@RestController
@RequestMapping("/pms/quality-issue")
@Validated
public class QualityIssueController {

    @Resource
    private QualityIssueService qualityIssueService;

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
    @Operation(summary = "创建质量问题")
    @PreAuthorize("@ss.hasPermission('pms:quality:create')")
    public CommonResult<Long> create(@RequestBody PmsQualityIssueDO entity) {
        requireProjectPerm(entity.getProjectId(), PmsPermKeyEnum.QUALITY_ADD.getKey());
        return success(qualityIssueService.createQualityIssue(entity));
    }

    @PutMapping("/update")
    @Operation(summary = "更新质量问题")
    @PreAuthorize("@ss.hasPermission('pms:quality:update')")
    public CommonResult<Boolean> update(@RequestBody PmsQualityIssueDO entity) {
        requireProjectPerm(entity.getProjectId(), PmsPermKeyEnum.QUALITY_EDIT.getKey());
        qualityIssueService.updateQualityIssue(entity);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除质量问题")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:quality:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        PmsQualityIssueDO issue = qualityIssueService.getQualityIssue(id);
        if (issue != null) {
            requireProjectPerm(issue.getProjectId(), PmsPermKeyEnum.QUALITY_DELETE.getKey());
        }
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
    @Parameter(name = "projectId", description = "项目ID，传则按项目过滤", required = false)
    @PreAuthorize("@ss.hasPermission('pms:quality:query')")
    public CommonResult<List<PmsQualityIssueDO>> list(
            @RequestParam(value = "projectId", required = false) Long projectId) {
        if (projectId != null) {
            // 项目级权限校验：查询本项目质量问题需 quality_view
            requireProjectPerm(projectId, PmsPermKeyEnum.QUALITY_VIEW.getKey());
            return success(qualityIssueService.getQualityIssueListByProjectId(projectId));
        }
        return success(qualityIssueService.getQualityIssueList());
    }

    // ==================== #8 Excel 批量导入 ====================

    @GetMapping("/get-import-template")
    @Operation(summary = "下载质量问题导入模板")
    @Parameter(name = "projectId", description = "项目ID（可选，仅用于权限校验，模板内容相同）", required = false)
    @PreAuthorize("@ss.hasPermission('pms:quality:query')")
    public void getImportTemplate(HttpServletResponse response,
                                  @RequestParam(value = "projectId", required = false) Long projectId) throws IOException {
        // 项目级权限：能进项目的人才能下模板（避免泄露字段结构）
        if (projectId != null) {
            requireProjectPerm(projectId, PmsPermKeyEnum.QUALITY_VIEW.getKey());
        }
        // 模板附示例行，引导用户填写
        List<QualityIssueImportExcel> demo = Arrays.asList(
                QualityIssueImportExcel.builder()
                        .issueTitle("示例：登录页接口返回 500")
                        .issueType("实现缺陷")
                        .severity("high")
                        .issueDescription("测试环境登录接口偶发 500，影响冒烟")
                        .responsiblePersonName("张三")
                        .discovererName("李四")
                        .discoveredDate(java.time.LocalDate.of(2026, 8, 6))
                        .dueDate(java.time.LocalDate.of(2026, 8, 13))
                        .taskName("登录模块联调")
                        .status("open")
                        .rectificationRequirement("8/13 前修复并补充单元测试")
                        .build()
        );
        ExcelUtils.write(response, "质量问题导入模板.xlsx", "质量问题", QualityIssueImportExcel.class, demo);
    }

    @PostMapping("/import")
    @Operation(summary = "Excel 批量导入质量问题")
    @Parameters({
            @Parameter(name = "file", description = "Excel 文件", required = true),
            @Parameter(name = "projectId", description = "项目ID（导入归属项目）", required = true)
    })
    @PreAuthorize("@ss.hasPermission('pms:quality:create')")
    public void importExcel(@RequestParam("file") MultipartFile file,
                            @RequestParam("projectId") Long projectId,
                            HttpServletResponse response) throws IOException {
        // 项目级权限叠加：本项目 quality_import 权限点
        requireProjectPerm(projectId, PmsPermKeyEnum.QUALITY_IMPORT.getKey());

        // 1. 解析 Excel
        List<QualityIssueImportExcel> rows = ExcelUtils.read(file, QualityIssueImportExcel.class);

        // 2. 整批校验 + 批量插入（通知在 Service 内 afterCommit 异步触发）
        QualityIssueImportRespVO result = qualityIssueService.importQualityIssueList(projectId, rows);

        if (result.getSuccess()) {
            // 3a. 校验通过 → 返回 JSON 成功响应
            response.setContentType("application/json;charset=UTF-8");
            CommonResult<QualityIssueImportRespVO> cr = CommonResult.success(result);
            response.getWriter().write(JSONUtil.toJsonStr(cr));
        } else {
            // 3b. 校验失败 → 输出标红错误 Excel 供用户下载修正后重试
            ExcelUtils.write(response, "质量问题导入错误.xlsx", "错误行",
                    QualityIssueImportErrorExcel.class, result.getFailureRows());
        }
    }

}
