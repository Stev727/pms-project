package cn.iocoder.yudao.module.pms.service.task.impl;

import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import cn.iocoder.yudao.module.pms.dal.mysql.task.TaskMapper;
import cn.iocoder.yudao.module.pms.service.task.TaskService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

@Service
public class TaskServiceImpl implements TaskService {

    @Resource
    private TaskMapper taskMapper;

    @Override
    public Long createTask(PmsTaskDO entity) {
        taskMapper.insert(entity);
        return entity.getTaskId();
    }

    @Override
    public void updateTask(PmsTaskDO entity) {
        taskMapper.updateById(entity);
    }

    @Override
    public void deleteTask(Long id) {
        taskMapper.deleteById(id);
    }

    @Override
    public PmsTaskDO getTask(Long id) {
        return taskMapper.selectById(id);
    }

    @Override
    public List<PmsTaskDO> getTaskList() {
        return taskMapper.selectList(null);
    }

}