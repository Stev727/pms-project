package cn.iocoder.yudao.module.pms.dal.dataobject.projectmember;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/**
 * 项目成员 DO
 */
@TableName("pms_project_member")
@Data
@EqualsAndHashCode(callSuper = true)
public class PmsProjectMemberDO extends TenantBaseDO {
    /**
     * 成员主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long memberId;

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 项目内角色编码
     */
    private String roleCode;

    /**
     * 是否外部成员
     */
    private Boolean isExternal;

    /**
     * 加入时间
     */
    private LocalDateTime joinTime;

    /**
     * 退出时间
     */
    private LocalDateTime quitTime;

    /**
     * 状态
     */
    private String status;

}