package cn.iocoder.yudao.module.pms.service.taskdependency;

import cn.iocoder.yudao.module.pms.dal.dataobject.taskdependency.PmsTaskDependencyDO;
import java.util.List;

/**
 * 任务依赖 Service 接口
 */
public interface TaskDependencyService {

    Long createTaskDependency(PmsTaskDependencyDO entity);

    void updateTaskDependency(PmsTaskDependencyDO entity);

    void deleteTaskDependency(Long id);

    PmsTaskDependencyDO getTaskDependency(Long id);

    List<PmsTaskDependencyDO> getTaskDependencyList();

}