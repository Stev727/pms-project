package cn.iocoder.yudao.module.pms.dal.dataobject.taskdependency;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;


/**
 * 任务依赖 DO
 */
@TableName("pms_task_dependency")
@Data
@EqualsAndHashCode(callSuper = true)
public class PmsTaskDependencyDO extends TenantBaseDO {
    /**
     * 依赖关系主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long dependencyId;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 前置任务ID
     */
    private Long preTaskId;

    /**
     * 依赖类型
     */
    private String dependencyType;

    /**
     * 是否强制依赖
     */
    private Boolean isMandatory;

    /**
     * 延隔天数
     */
    private Integer lagDays;

}