package cn.iocoder.yudao.module.pms.service.overdueremind;

import java.util.List;
import java.util.Map;

/**
 * 超期提请 Service（迭代14）
 *
 * 提供「超期提醒」板块的查询与监控人/通知人配置能力。
 */
public interface OverdueRemindService {

    /**
     * 分页查询超期任务（计划结束日期已过且未完成，排除模板项目）
     *
     * @param deptId   责任人部门筛选（可空）
     * @param userId   责任人筛选（可空）
     * @param projectId 项目筛选（可空）
     * @param pageNo   页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 超期任务分页，行字段：taskId/taskName/projectId/projectName/mainOwnerId/mainOwnerName/
     *         deptId/deptName/planEndDate/overdueDays/completeStatus
     */
    Map<String, Object> getOverdueTaskPage(Long deptId, Long userId, Long projectId, Integer pageNo, Integer pageSize);

    /**
     * 获取超期提请监控配置列表。
     * 每条配置 = 一个通知人 + 其监控的责任人（overdue_summary 规则：notifyTarget=通知人，triggerCondition=责任人逗号串）
     *
     * @return 列表项字段：notifyUserId（通知人）、ownerIds（被监控责任人 List<Long>）
     */
    List<Map<String, Object>> getMonitorConfigs();

    /**
     * 保存超期提请监控配置（全量替换）。
     * 每条配置 Map 需含：notifyUserId（通知人 userId）、ownerIds（被监控责任人 userId 列表）。
     * 每条配置每日 09:00 独立推送：通知人只收到其监控责任人的超期任务汇总（钉钉+站内信）。
     */
    void updateMonitorConfigs(List<Map<String, Object>> configs);
}
