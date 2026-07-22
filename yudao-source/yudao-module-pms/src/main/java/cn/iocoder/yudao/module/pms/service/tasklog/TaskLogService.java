package cn.iocoder.yudao.module.pms.service.tasklog;

import cn.iocoder.yudao.module.pms.dal.dataobject.tasklog.PmsTaskLogDO;
import java.util.List;

/**
 * 任务操作日志 Service 接口
 */
public interface TaskLogService {

    Long createTaskLog(PmsTaskLogDO entity);

    void updateTaskLog(PmsTaskLogDO entity);

    void deleteTaskLog(Long id);

    PmsTaskLogDO getTaskLog(Long id);

    List<PmsTaskLogDO> getTaskLogList();

}