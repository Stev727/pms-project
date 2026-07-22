package cn.iocoder.yudao.module.pms.service.taskoutput.impl;

import cn.iocoder.yudao.module.pms.dal.dataobject.taskoutput.PmsTaskOutputDO;
import cn.iocoder.yudao.module.pms.dal.mysql.taskoutput.TaskOutputMapper;
import cn.iocoder.yudao.module.pms.service.taskoutput.TaskOutputService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

@Service
public class TaskOutputServiceImpl implements TaskOutputService {

    @Resource
    private TaskOutputMapper taskOutputMapper;

    @Override
    public Long createTaskOutput(PmsTaskOutputDO entity) {
        taskOutputMapper.insert(entity);
        return entity.getOutputId();
    }

    @Override
    public void updateTaskOutput(PmsTaskOutputDO entity) {
        taskOutputMapper.updateById(entity);
    }

    @Override
    public void deleteTaskOutput(Long id) {
        taskOutputMapper.deleteById(id);
    }

    @Override
    public PmsTaskOutputDO getTaskOutput(Long id) {
        return taskOutputMapper.selectById(id);
    }

    @Override
    public List<PmsTaskOutputDO> getTaskOutputList() {
        return taskOutputMapper.selectList(null);
    }

}