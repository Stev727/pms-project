package cn.iocoder.yudao.module.pms.service.tasklog.impl;

import cn.iocoder.yudao.module.pms.dal.dataobject.tasklog.PmsTaskLogDO;
import cn.iocoder.yudao.module.pms.dal.mysql.tasklog.TaskLogMapper;
import cn.iocoder.yudao.module.pms.service.tasklog.TaskLogService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

@Service
public class TaskLogServiceImpl implements TaskLogService {

    @Resource
    private TaskLogMapper taskLogMapper;

    @Override
    public Long createTaskLog(PmsTaskLogDO entity) {
        taskLogMapper.insert(entity);
        return entity.getLogId();
    }

    @Override
    public void updateTaskLog(PmsTaskLogDO entity) {
        taskLogMapper.updateById(entity);
    }

    @Override
    public void deleteTaskLog(Long id) {
        taskLogMapper.deleteById(id);
    }

    @Override
    public PmsTaskLogDO getTaskLog(Long id) {
        return taskLogMapper.selectById(id);
    }

    @Override
    public List<PmsTaskLogDO> getTaskLogList() {
        return taskLogMapper.selectList(null);
    }

}