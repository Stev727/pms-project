package cn.iocoder.yudao.module.pms.controller.admin.changerecord;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.dal.dataobject.changerecord.PmsChangeRecordDO;
import cn.iocoder.yudao.module.pms.service.changerecord.ChangeRecordService;
import cn.iocoder.yudao.module.pms.enums.PmsPermKeyEnum;
import cn.iocoder.yudao.module.pms.service.projectpermission.ProjectPermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 变更记录")
@RestController
@RequestMapping("/pms/change-record")
@Validated
public class ChangeRecordController {

    @Resource
    private ChangeRecordService changeRecordService;

    @Autowired(required = false)
    private ProjectPermissionService projectPermissionService;

    /**
     * 项目级权限校验兜底：变更记录无专属权限点，按语义借用任务权限点：
     * - 审核（review）→ task_review
     * - 执行/更新/删除（改任务数据）→ task_edit
     * 日常变更（projectId=null）跳过，仅菜单级 @PreAuthorize 把关。
     */
    private void requireProjectPerm(Long projectId, String permKey) {
        if (projectPermissionService == null || projectId == null) {
            return;
        }
        projectPermissionService.checkPermission(projectId, permKey);
    }

    private Long getChangeProjectId(Long changeId) {
        if (changeId == null) return null;
        PmsChangeRecordDO change = changeRecordService.getChangeRecord(changeId);
        return change == null ? null : change.getProjectId();
    }

    @PostMapping("/create")
    @Operation(summary = "创建变更记录")
    @PreAuthorize("@ss.hasPermission('pms:change:create')")
    public CommonResult<Long> create(@RequestBody PmsChangeRecordDO entity) {
        return success(changeRecordService.createChangeRecord(entity));
    }

    @PostMapping("/review")
    @Operation(summary = "项目经理审核任务变更")
    @PreAuthorize("@ss.hasPermission('pms:change:update')")
    public CommonResult<Boolean> review(@RequestParam("id") Long id,
                                        @RequestParam("approved") boolean approved) {
        requireProjectPerm(getChangeProjectId(id), PmsPermKeyEnum.TASK_REVIEW.getKey());
        changeRecordService.reviewChange(id, approved, cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

    @PostMapping("/execute")
    @Operation(summary = "执行已审批通过的变更")
    @Parameter(name = "id", description = "变更编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:change:update')")
    public CommonResult<Boolean> executeChange(@RequestParam("id") Long id) {
        requireProjectPerm(getChangeProjectId(id), PmsPermKeyEnum.TASK_EDIT.getKey());
        changeRecordService.executeChange(id);
        return success(true);
    }

    @PutMapping("/update")
    @Operation(summary = "更新变更记录")
    @PreAuthorize("@ss.hasPermission('pms:change:update')")
    public CommonResult<Boolean> update(@RequestBody PmsChangeRecordDO entity) {
        requireProjectPerm(entity.getProjectId(), PmsPermKeyEnum.TASK_EDIT.getKey());
        changeRecordService.updateChangeRecord(entity);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除变更记录")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:change:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        requireProjectPerm(getChangeProjectId(id), PmsPermKeyEnum.TASK_EDIT.getKey());
        changeRecordService.deleteChangeRecord(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取变更记录")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:change:query')")
    public CommonResult<PmsChangeRecordDO> get(@RequestParam("id") Long id) {
        return success(changeRecordService.getChangeRecord(id));
    }

    @GetMapping("/list")
    @Operation(summary = "获取变更记录列表")
    @PreAuthorize("@ss.hasPermission('pms:change:query')")
    public CommonResult<List<PmsChangeRecordDO>> list() {
        return success(changeRecordService.getChangeRecordList());
    }

}
