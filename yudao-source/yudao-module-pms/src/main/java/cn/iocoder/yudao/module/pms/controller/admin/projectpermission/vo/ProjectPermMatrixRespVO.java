package cn.iocoder.yudao.module.pms.controller.admin.projectpermission.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 项目权限矩阵 Response VO
 *
 * 一次性返回前端渲染矩阵所需的全部数据，避免多次请求。
 */
@Schema(description = "管理后台 - 项目权限矩阵 Response VO")
@Data
public class ProjectPermMatrixRespVO {

    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "项目角色列表（矩阵的列）")
    private List<RoleItem> roles;

    @Schema(description = "权限点定义，按分组（矩阵的行）")
    private List<PermGroup> permGroups;

    @Schema(description = "已授权项，格式 roleId:permKey，前端用 Set 判断勾选态")
    private List<String> grantedPairs;

    @Schema(description = "当前用户能否编辑该矩阵")
    private Boolean editable;

    @Data
    @Schema(description = "角色项")
    public static class RoleItem {

        @Schema(description = "角色ID")
        private Long roleId;

        @Schema(description = "角色显示名")
        private String roleName;

        @Schema(description = "角色编码")
        private String roleCode;

        @Schema(description = "是否系统内置")
        private Boolean isSystem;

        @Schema(description = "排序号")
        private Integer sortOrder;

        @Schema(description = "当前使用该角色的成员数")
        private Integer memberCount;
    }

    @Data
    @Schema(description = "权限点分组")
    public static class PermGroup {

        @Schema(description = "分组名，如 任务/文档/物料")
        private String group;

        @Schema(description = "该分组下的权限点")
        private List<PermItem> items;
    }

    @Data
    @Schema(description = "权限点")
    public static class PermItem {

        @Schema(description = "权限点编码")
        private String permKey;

        @Schema(description = "中文标签")
        private String label;
    }

}

