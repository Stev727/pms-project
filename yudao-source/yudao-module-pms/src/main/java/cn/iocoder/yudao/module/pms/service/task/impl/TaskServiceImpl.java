package cn.iocoder.yudao.module.pms.service.task.impl;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import cn.iocoder.yudao.module.pms.dal.mysql.task.TaskMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.tasklog.TaskLogMapper;
import cn.iocoder.yudao.module.pms.dal.dataobject.tasklog.PmsTaskLogDO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import java.time.LocalDateTime;
import cn.iocoder.yudao.module.pms.service.task.TaskService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

@Service
public class TaskServiceImpl implements TaskService {

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private TaskLogMapper taskLogMapper;

    @Override
    public Long createTask(PmsTaskDO entity) {
        taskMapper.insert(entity);

        return entity.getTaskId();
    }

    @Override
    public void simulateDingtalkConfirm(Long taskId) {
        PmsTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ServiceException(500, "任务不存在");
        }

        PmsTaskDO update = new PmsTaskDO();
        update.setTaskId(taskId);
        update.setCompleteStatus("in_progress");
        taskMapper.updateById(update);

        // 记录任务日志
        try {
            PmsTaskLogDO log = new PmsTaskLogDO();
            log.setTaskId(taskId);
            log.setOperationType("dingtalk_confirm");
            log.setOperationTime(LocalDateTime.now());
            log.setOperationContent("钉钉确认模拟：任务状态变更为进行中");
            taskLogMapper.insert(log);
        } catch (Exception e) {
            // 日志失败不影响主流程
        }
    }

    @Override
    public void submitCompletion(Long taskId) {
        PmsTaskDO task = requireTask(taskId);
        if (!"in_progress".equals(task.getCompleteStatus())) {
            throw new IllegalStateException("只有进行中的任务可以提交完成");
        }
        task.setCompleteStatus("completion_pending_review");
        taskMapper.updateById(task);
    }

    @Override
    public void reviewCompletion(Long taskId, boolean approved) {
        PmsTaskDO task = requireTask(taskId);
        if (!"completion_pending_review".equals(task.getCompleteStatus())) {
            throw new IllegalStateException("只有待完成审核的任务可以审核");
        }
        if (approved) {
            task.setCompleteStatus("completed");
            task.setProgress(100);
            task.setActualCompleteDate(java.time.LocalDate.now());
        } else {
            task.setCompleteStatus("in_progress");
        }
        taskMapper.updateById(task);
    }

    private PmsTaskDO requireTask(Long taskId) {
        PmsTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        return task;
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

    @Override
    public List<PmsTaskDO> getTaskList(Long mainOwnerId, Long projectId) {
        return taskMapper.selectList(new LambdaQueryWrapperX<PmsTaskDO>()
            .eqIfPresent(PmsTaskDO::getMainOwnerId, mainOwnerId)
            .eqIfPresent(PmsTaskDO::getProjectId, projectId)
            .orderByAsc(PmsTaskDO::getSortOrder));
    }

}
