package cn.iocoder.yudao.module.pms.dal.dataobject.dingtalk;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PMS 钉钉部门映射 DO
 */
@TableName("pms_dingtalk_dept")
@Data
@EqualsAndHashCode(callSuper = true)
public class PmsDingTalkDeptDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 系统部门ID（system_dept.id）
     */
    private Long deptId;

    /**
     * 钉钉部门ID
     */
    private Long dingtalkDeptId;

    /**
     * 钉钉父部门ID
     */
    private Long dingtalkParentId;

    /**
     * 钉钉部门名称
     */
    private String deptName;

    /**
     * 最后同步时间
     */
    private java.util.Date lastSyncTime;
}
