package cn.iocoder.yudao.module.pms.dal.dataobject.projectpermission;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目权限矩阵 DO
 *
 * 一行 = 某项目下某角色对某权限点的授权结果。
 * 无记录视为「不允许」。
 */
@TableName("pms_project_permission")
@Data
@EqualsAndHashCode(callSuper = true)
public class PmsProjectPermissionDO extends TenantBaseDO {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long permId;

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 项目角色ID，对应 pms_project_role.role_id
     */
    private Long roleId;

    /**
     * 权限点编码，取值见 PmsPermKeyEnum
     */
    private String permKey;

    /**
     * 是否允许：true 允许 / false 禁止
     */
    private Boolean allowed;

}

