package cn.iocoder.yudao.module.pms.service.taskdependency.impl;

import cn.iocoder.yudao.module.pms.dal.dataobject.taskdependency.PmsTaskDependencyDO;
import cn.iocoder.yudao.module.pms.dal.mysql.taskdependency.TaskDependencyMapper;
import cn.iocoder.yudao.module.pms.service.taskdependency.TaskDependencyService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

@Service
public class TaskDependencyServiceImpl implements TaskDependencyService {

    @Resource
    private TaskDependencyMapper taskDependencyMapper;

    @Override
    public Long createTaskDependency(PmsTaskDependencyDO entity) {
        taskDependencyMapper.insert(entity);
        return entity.getDependencyId();
    }

    @Override
    public void updateTaskDependency(PmsTaskDependencyDO entity) {
        taskDependencyMapper.updateById(entity);
    }

    @Override
    public void deleteTaskDependency(Long id) {
        taskDependencyMapper.deleteById(id);
    }

    @Override
    public PmsTaskDependencyDO getTaskDependency(Long id) {
        return taskDependencyMapper.selectById(id);
    }

    @Override
    public List<PmsTaskDependencyDO> getTaskDependencyList() {
        return taskDependencyMapper.selectList(null);
    }

}