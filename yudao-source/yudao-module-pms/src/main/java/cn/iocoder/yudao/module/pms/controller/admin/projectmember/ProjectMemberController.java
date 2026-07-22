package cn.iocoder.yudao.module.pms.controller.admin.projectmember;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.dal.dataobject.projectmember.PmsProjectMemberDO;
import cn.iocoder.yudao.module.pms.service.projectmember.ProjectMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 项目成员")
@RestController
@RequestMapping("/pms/project-member")
@Validated
public class ProjectMemberController {

    @Resource
    private ProjectMemberService projectMemberService;

    @PostMapping("/create")
    @Operation(summary = "创建项目成员")
    @PreAuthorize("@ss.hasPermission('pms:member:create')")
    public CommonResult<Long> create(@RequestBody PmsProjectMemberDO entity) {
        return success(projectMemberService.createProjectMember(entity));
    }

    @PutMapping("/update")
    @Operation(summary = "更新项目成员")
    @PreAuthorize("@ss.hasPermission('pms:member:update')")
    public CommonResult<Boolean> update(@RequestBody PmsProjectMemberDO entity) {
        projectMemberService.updateProjectMember(entity);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除项目成员")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:member:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        projectMemberService.deleteProjectMember(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取项目成员")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:member:query')")
    public CommonResult<PmsProjectMemberDO> get(@RequestParam("id") Long id) {
        return success(projectMemberService.getProjectMember(id));
    }

    @GetMapping("/list")
    @Operation(summary = "获取项目成员列表")
    @PreAuthorize("@ss.hasPermission('pms:member:query')")
    public CommonResult<List<PmsProjectMemberDO>> list() {
        return success(projectMemberService.getProjectMemberList());
    }

}