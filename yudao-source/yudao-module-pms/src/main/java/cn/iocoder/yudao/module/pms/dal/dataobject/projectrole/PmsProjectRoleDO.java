package cn.iocoder.yudao.module.pms.dal.dataobject.projectrole;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目角色 DO
 *
 * 项目经理可在项目内自建角色（如 质量负责人 / 采购对接人），
 * 角色编码与 pms_project_member.role_code 关联。
 */
@TableName("pms_project_role")
@Data
@EqualsAndHashCode(callSuper = true)
public class PmsProjectRoleDO extends TenantBaseDO {

    /**
     * 角色主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long roleId;

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 角色显示名（如 质量负责人）
     */
    private String roleName;

    /**
     * 角色编码（项目内唯一，如 qa_lead）
     */
    private String roleCode;

    /**
     * 是否系统内置：0 否 1 是。内置角色不可删除
     */
    private Boolean isSystem;

    /**
     * 排序号，越小越靠前
     */
    private Integer sortOrder;

    /**
     * 备注说明
     */
    private String remark;

}

