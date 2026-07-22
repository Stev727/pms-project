package cn.iocoder.yudao.module.pms.dal.dataobject.dingtalk;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PMS 钉钉用户映射 DO
 */
@TableName("pms_dingtalk_user")
@Data
@EqualsAndHashCode(callSuper = true)
public class PmsDingTalkUserDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 系统用户ID（system_users.id）
     */
    private Long userId;

    /**
     * 钉钉用户ID（userid）
     */
    private String dingtalkUserId;

    /**
     * 钉钉unionId
     */
    private String dingtalkUnionId;

    /**
     * 钉钉用户姓名
     */
    private String name;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 手机号
     */
    private String mobile;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 工号
     */
    private String jobNumber;

    /**
     * 职位
     */
    private String title;

    /**
     * 工作地
     */
    private String workPlace;

    /**
     * 最后同步时间
     */
    private java.util.Date lastSyncTime;
}
