package cn.iocoder.yudao.module.pms.dal.dataobject.taskoutput;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;


/**
 * 任务输出物 DO
 */
@TableName("pms_task_output")
@Data
@EqualsAndHashCode(callSuper = true)
public class PmsTaskOutputDO extends TenantBaseDO {
    /**
     * 关联主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long outputId;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 文档ID
     */
    private Long documentId;

    /**
     * 是否必需
     */
    private Boolean isRequired;

    /**
     * 输出物名称
     */
    private String outputName;

    /**
     * 排序号
     */
    private Integer sortOrder;

}