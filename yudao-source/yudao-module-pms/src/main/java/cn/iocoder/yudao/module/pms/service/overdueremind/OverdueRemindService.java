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
     * 获取超期提请的监控人/通知人 userId 列表（overdue_summary 全局规则的 notifyTarget）
     */
    List<Long> getReceivers();

    /**
     * 保存超期提请的监控人/通知人列表（写入 overdue_summary 全局规则的 notifyTarget）
     */
    void updateReceivers(List<Long> userIds);
}
