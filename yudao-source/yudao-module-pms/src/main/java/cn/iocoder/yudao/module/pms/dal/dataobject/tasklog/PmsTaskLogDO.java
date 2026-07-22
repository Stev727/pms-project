package cn.iocoder.yudao.module.pms.dal.dataobject.tasklog;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/**
 * 任务操作日志 DO
 */
@TableName("pms_task_log")
@Data
@EqualsAndHashCode(callSuper = true)
public class PmsTaskLogDO extends TenantBaseDO {
    /**
     * 日志主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long logId;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 操作类型
     */
    private String operationType;

    /**
     * 操作人ID
     */
    private Long operatorId;

    /**
     * 操作人姓名
     */
    private String operatorName;

    /**
     * 操作时间
     */
    private LocalDateTime operationTime;

    /**
     * 操作内容
     */
    private String operationContent;

    /**
     * 变更前值
     */
    private String beforeValue;

    /**
     * 变更后值
     */
    private String afterValue;

}