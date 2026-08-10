package cn.iocoder.yudao.module.pms.dal.dataobject.dingtalk;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PMS 钉钉集成配置 DO
 */
@TableName("pms_dingtalk_config")
@Data
@EqualsAndHashCode(callSuper = true)
public class PmsDingTalkConfigDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long configId;

    /**
     * 钉钉应用 AppKey
     */
    private String appKey;

    /**
     * 钉钉应用 AppSecret
     */
    private String appSecret;

    /**
     * 钉钉应用 AgentId（工作通知用）
     */
    private Long agentId;

    /**
     * 机器人编码（机器人单聊消息用，从钉钉开发者后台→应用→机器人配置页复制）
     * 非空时通知优先以机器人身份发送（显示为独立对话），否则降级为工作通知
     */
    private String robotCode;

    /**
     * 企业 CorpId
     */
    private String corpId;

    /**
     * 组织架构同步间隔(秒)
     */
    private Integer syncDeptInterval;

    /**
     * 是否启用钉钉通知
     */
    private Boolean notifyEnabled;

    /**
     * 是否启用免登
     */
    private Boolean ssoEnabled;

    /**
     * 是否启用组织架构同步
     */
    private Boolean syncEnabled;

    /**
     * 状态
     */
    private String status;

    /**
     * 备注
     */
    private String remark;
}
