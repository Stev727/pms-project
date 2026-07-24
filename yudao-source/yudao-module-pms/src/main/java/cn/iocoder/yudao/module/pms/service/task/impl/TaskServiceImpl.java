package cn.iocoder.yudao.module.pms.service.task.impl;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.dal.dataobject.notifylog.PmsNotifyLogDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.notifyrule.PmsNotifyRuleDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import cn.iocoder.yudao.module.pms.dal.mysql.notifylog.NotifyLogMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.notifyrule.NotifyRuleMapper;
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
    private NotifyRuleMapper notifyRuleMapper;

    @Resource
    private NotifyLogMapper notifyLogMapper;

    @Resource
    private TaskLogMapper taskLogMapper;

    @Override
    public Long createTask(PmsTaskDO entity) {
        taskMapper.insert(entity);

        // 创建后立即触发通知（钉钉机器人通知）
        List<PmsNotifyRuleDO> rules = notifyRuleMapper.selectList(
            new LambdaQueryWrapperX<PmsNotifyRuleDO>()
                .eq(PmsNotifyRuleDO::getTriggerEvent, "task_dispatched")
                .eq(PmsNotifyRuleDO::getStatus, "enabled")
        );

        for (PmsNotifyRuleDO rule : rules) {
            PmsNotifyLogDO log = new PmsNotifyLogDO();
            log.setRuleId(rule.getRuleId());
            log.setTriggerEvent("task_dispatched");
            log.setBusinessId(entity.getTaskId());
            log.setBusinessType("task");
            log.setNotifyTarget(String.valueOf(entity.getMainOwnerId()));
            log.setChannel(rule.getNotifyChannel());
            String template = rule.getTemplateContent();
            if (template == null) {
                template = "【PMS】项目 {项目名} 有新任务：{任务名称}，预计开始 {计划开始日期}，预计结束 {计划结束日期}，请点击确认";
            }
            String content = template
                .replace("{任务名称}", entity.getTaskName() != null ? entity.getTaskName() : "")
                .replace("{计划开始日期}", entity.getPlanStartDate() != null ? entity.getPlanStartDate().toString() : "未排期")
                .replace("{计划结束日期}", entity.getPlanEndDate() != null ? entity.getPlanEndDate().toString() : "未排期")
                .replace("{项目名}", "未知项目");
            log.setContent(content);
            log.setSendStatus("pending");
            notifyLogMapper.insert(log);
        }

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
