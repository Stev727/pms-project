package cn.iocoder.yudao.module.pms.service.task.impl;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.dal.dataobject.notifylog.PmsNotifyLogDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.notifyrule.PmsNotifyRuleDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import cn.iocoder.yudao.module.pms.dal.mysql.notifylog.NotifyLogMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.notifyrule.NotifyRuleMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.task.TaskMapper;
import cn.iocoder.yudao.module.pms.service.task.TaskService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

@Service
public class TaskServiceImpl implements TaskService {

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private NotifyRuleMapper notifyRuleMapper;

    @Resource
    private NotifyLogMapper notifyLogMapper;

    @Override
    public Long createTask(PmsTaskDO entity) {
        taskMapper.insert(entity);
        return entity.getTaskId();
    }

    @Override
    public void updateTask(PmsTaskDO entity) {
        // 获取旧任务
        PmsTaskDO oldTask = taskMapper.selectById(entity.getTaskId());
        taskMapper.updateById(entity);

        // 检查状态变更
        if (oldTask != null && entity.getCompleteStatus() != null) {
            String oldStatus = oldTask.getCompleteStatus();
            String newStatus = entity.getCompleteStatus();

            // not_started -> in_progress (开始任务/派发)
            if ("not_started".equals(oldStatus) && "in_progress".equals(newStatus)) {
                triggerTaskDispatchedNotify(entity);
            }
        }
    }

    /**
     * 触发任务派发通知
     */
    private void triggerTaskDispatchedNotify(PmsTaskDO entity) {
        List<PmsNotifyRuleDO> rules = notifyRuleMapper.selectList(
            new LambdaQueryWrapperX<PmsNotifyRuleDO>()
                .eq(PmsNotifyRuleDO::getTriggerEvent, "task_dispatched")
                .eq(PmsNotifyRuleDO::getStatus, "enabled")
        );

        for (PmsNotifyRuleDO rule : rules) {
            PmsNotifyLogDO log = new PmsNotifyLogDO();
            log.setRuleId(rule.getRuleId());
            log.setTriggerEvent("task_dispatched");
            log.setBusinessType("task");
            log.setBusinessId(entity.getTaskId());
            log.setNotifyTarget(String.valueOf(entity.getMainOwnerId()));
            log.setChannel(rule.getNotifyChannel());
            log.setTitle(rule.getTemplateTitle());

            // 生成通知内容
            String content = rule.getTemplateContent();
            if (content != null) {
                content = content.replace("{任务名称}", entity.getTaskName() != null ? entity.getTaskName() : "")
                    .replace("{计划结束日期}", entity.getPlanEndDate() != null ? entity.getPlanEndDate().toString() : "未排期");
            }
            log.setContent(content);
            log.setSendStatus("pending");
            notifyLogMapper.insert(log);
        }
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
