package cn.iocoder.yudao.module.pms.dal.dataobject.dingtalk;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PMS 钉钉待办映射 DO（#4 钉钉待办）
 *
 * <p>用途：保存 PMS 任务 ↔ 钉钉待办 ID 的映射，用于：
 * <ul>
 *   <li>任务完成时把对应钉钉待办标记完成</li>
 *   <li>失败降级追溯（status=failed 的记录可由定时任务补偿重试）</li>
 * </ul>
 *
 * <p>注意：本表只记录"已尝试创建"的待办，不保证全部成功。
 * 失败的记录 status=failed，但 bizTaskId 仍可重复创建（业务幂等性由调用方控制）。
 */
@TableName("pms_dingtalk_todo")
@Data
@EqualsAndHashCode(callSuper = true)
public class PmsDingTalkTodoDO extends TenantBaseDO {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联业务ID（一般是 pms_task.task_id）
     */
    private Long bizTaskId;

    /**
     * 接收人系统用户ID（system_users.id）
     */
    private Long userId;

    /**
     * 接收人钉钉 unionId（用于创建待办）
     */
    private String dingtalkUnionId;

    /**
     * 钉钉待办ID（创建成功后返回）
     */
    private String dingTodoId;

    /**
     * 待办状态：pending / completed / failed
     */
    private String status;

    /**
     * 待办标题（创建时传入）
     */
    private String title;

    /**
     * 待办内容（创建时传入）
     */
    private String content;

    /**
     * 创建时间（与 creator/create_time 区分，这里特指钉钉待办创建时间）
     */
    private LocalDateTime todoCreateTime;

    /**
     * 完成时间
     */
    private LocalDateTime todoCompleteTime;

    /**
     * 失败原因（status=failed 时记录）
     */
    private String failReason;

}

