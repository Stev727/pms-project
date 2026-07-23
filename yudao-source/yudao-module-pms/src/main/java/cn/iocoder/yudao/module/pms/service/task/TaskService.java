package cn.iocoder.yudao.module.pms.service.task;

import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import java.util.List;

/**
 * 任务 Service 接口
 */
public interface TaskService {

    Long createTask(PmsTaskDO entity);

    void updateTask(PmsTaskDO entity);

    void deleteTask(Long id);

    PmsTaskDO getTask(Long id);

    List<PmsTaskDO> getTaskList();

    List<PmsTaskDO> getTaskList(Long mainOwnerId, Long projectId);

}