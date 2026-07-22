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
}
