package cn.iocoder.yudao.module.pms.dal.dataobject.projectpermission;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目权限默认模板 DO
 *
 * 租户级默认配置，新项目创建时按 role_code 套用到 pms_project_permission。
 */
@TableName("pms_project_permission_template")
@Data
@EqualsAndHashCode(callSuper = true)
public class PmsProjectPermissionTemplateDO extends TenantBaseDO {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long templateId;

    /**
     * 角色编码（如 pm / member / qa_lead）
     */
    private String roleCode;

    /**
     * 角色显示名，创建项目时用于初始化 pms_project_role.role_name
     */
    private String roleName;

    /**
     * 权限点编码，取值见 PmsPermKeyEnum
     */
    private String permKey;

    /**
     * 是否允许：true 允许 / false 禁止
     */
    private Boolean allowed;

    /**
     * 排序号
     */
    private Integer sortOrder;

}

