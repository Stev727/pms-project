package cn.iocoder.yudao.module.pms.service.dingtalk;

import java.util.List;
import java.util.Map;

/**
 * 钉钉组织架构同步服务
 */
public interface DingTalkSyncService {

    /**
     * 全量同步组织架构（部门 + 用户）
     *
     * @return 同步结果摘要
     */
    Map<String, Object> syncAll();

    /**
     * 同步部门列表
     *
     * @return 新增/更新的部门数量
     */
    Map<String, Object> syncDepartments();

    /**
     * 同步用户列表
     *
     * @return 新增/更新的用户数量
     */
    Map<String, Object> syncUsers();

    /**
     * 获取同步状态摘要
     */
    Map<String, Object> getSyncStatus();

    /**
     * 同步工号到 system_users
     * 从 pms_dingtalk_user 表将 job_number 和 dingtalk_user_id 同步到 system_users
     * @return 同步结果
     */
    Map<String, Object> syncEmployeeNo();

    /**
     * 单人同步（秒级）：按系统用户 ID 拉取该用户的钉钉最新资料并更新
     * 适用于局部资料变更；部门/人员结构变化仍需走全量同步
     *
     * @param systemUserId 系统用户 ID
     * @return 同步结果（status/message/updated 等）
     */
    Map<String, Object> syncSingleUser(Long systemUserId);
}
