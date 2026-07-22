package cn.iocoder.yudao.module.pms.service.taskoutput;

import cn.iocoder.yudao.module.pms.dal.dataobject.taskoutput.PmsTaskOutputDO;
import java.util.List;

/**
 * 任务输出物 Service 接口
 */
public interface TaskOutputService {

    Long createTaskOutput(PmsTaskOutputDO entity);

    void updateTaskOutput(PmsTaskOutputDO entity);

    void deleteTaskOutput(Long id);

    PmsTaskOutputDO getTaskOutput(Long id);

    List<PmsTaskOutputDO> getTaskOutputList();

}