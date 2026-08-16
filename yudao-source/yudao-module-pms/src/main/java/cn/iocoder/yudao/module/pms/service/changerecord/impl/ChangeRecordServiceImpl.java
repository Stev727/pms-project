package cn.iocoder.yudao.module.pms.service.changerecord.impl;

import cn.iocoder.yudao.module.pms.dal.dataobject.changerecord.PmsChangeRecordDO;
import cn.iocoder.yudao.module.pms.dal.mysql.changerecord.ChangeRecordMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.dal.dataobject.project.PmsProjectDO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.service.changerecord.ChangeRecordService;
import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import cn.iocoder.yudao.module.pms.dal.mysql.task.TaskMapper;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import cn.iocoder.yudao.module.pms.service.dingtalk.DingTalkNotifyService;
import org.springframework.beans.factory.annotation.Value;
import java.util.Collections;

@Service
public class ChangeRecordServiceImpl implements ChangeRecordService {

    @Resource
    private ChangeRecordMapper changeRecordMapper;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private TaskMapper taskMapper;
    @Resource
    private SecurityFrameworkService securityFrameworkService;
    @Resource
    private DingTalkNotifyService dingTalkNotifyService;
    @Value("${pms.notify.frontend-base-url:https://pms.topsunpower.cc}")
    private String frontendBaseUrl;

    @Override
    public Long createChangeRecord(PmsChangeRecordDO entity) {
        entity.setApprovalStatus("pending");
        entity.setChangeStatus("pending_review");
        // 自动指派审批人：优先任务审核人(reviewerId)，回退项目PM(projectManagerId)
        if (entity.getApproverId() == null) {
            Long approverId = resolveApprover(entity.getProjectId(), entity.getAffectedTasks());
            entity.setApproverId(approverId);
        }
        changeRecordMapper.insert(entity);
        // 提交变更后通知审批人审核（钉钉工作通知 + 站内信 + 待办）
        notifyApprover(entity);
        return entity.getChangeId();
    }

    /**
     * 解析变更审批人：优先取任务审核人(reviewerId)，回退项目PM(projectManagerId)
     */
    private Long resolveApprover(Long projectId, String taskIdStr) {
        if (taskIdStr != null && !taskIdStr.trim().isEmpty()) {
            try {
                PmsTaskDO task = taskMapper.selectById(Long.parseLong(taskIdStr.trim()));
                if (task != null && task.getReviewerId() != null) {
                    return task.getReviewerId();
                }
            } catch (NumberFormatException ignored) { }
        }
        PmsProjectDO project = projectMapper.selectById(projectId);
        if (project != null && project.getProjectManagerId() != null) {
            return project.getProjectManagerId();
        }
        return null;
    }

    /**
     * 变更提交后通知审批人审核（钉钉工作通知 + 站内信 + 待办）
     */
    private void notifyApprover(PmsChangeRecordDO entity) {
        if (entity.getApproverId() == null) return;
        String taskName = "";
        if (entity.getAffectedTasks() != null && !entity.getAffectedTasks().trim().isEmpty()) {
            try {
                PmsTaskDO task = taskMapper.selectById(Long.parseLong(entity.getAffectedTasks().trim()));
                if (task != null && task.getTaskName() != null) taskName = task.getTaskName();
            } catch (NumberFormatException ignored) { }
        }
        String title = "任务变更待审核";
        String content = "任务【" + taskName + "】发起变更，变更编号 " + entity.getChangeCode() + "，请登录系统审核。";
        String detailUrl = frontendBaseUrl + "/pms/project-detail/" + entity.getProjectId()
                + "?taskId=" + entity.getAffectedTasks() + "&tab=changes";
        try {
            dingTalkNotifyService.sendNotifyDirect(title, content,
                    Collections.singletonList(entity.getApproverId()),
                    "task_change_submitted", "change", entity.getChangeId(), detailUrl);
        } catch (Exception e) {
            System.err.println("[PMS] 变更审核通知发送失败, changeId=" + entity.getChangeId() + ", error=" + e.getMessage());
        }
    }

    @Override
    public void reviewChange(Long id, boolean approved, Long approverId) {
        PmsChangeRecordDO record = requireRecord(id);
        requireProjectManager(record, approverId);
        if (!"pending".equals(record.getApprovalStatus())) {
            throw new ServiceException(cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.CHANGE_STATUS_INVALID);
        }
        record.setApproverId(approverId);
        if (approved) {
            record.setApprovalStatus("approved");
            record.setChangeStatus("executed");
            record.setExecuteTime(LocalDateTime.now());
            // 审核通过：同步变更内容到任务
            applyChangeToTask(record);
        } else {
            record.setApprovalStatus("rejected");
            record.setChangeStatus("rejected");
        }
        changeRecordMapper.updateById(record);
    }

    /**
     * 审核通过后，解析 afterState JSON 并更新对应任务的字段
     */
    private void applyChangeToTask(PmsChangeRecordDO record) {
        String taskIdStr = record.getAffectedTasks();
        if (taskIdStr == null || taskIdStr.isEmpty()) return;
        Long taskId;
        try {
            taskId = Long.parseLong(taskIdStr.trim());
        } catch (NumberFormatException e) {
            return;
        }
        PmsTaskDO task = taskMapper.selectById(taskId);
        if (task == null) return;

        String afterState = record.getAfterState();
        if (afterState == null || afterState.isEmpty()) return;

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(afterState);

            if (node.has("taskName") && !node.get("taskName").isNull()) {
                task.setTaskName(node.get("taskName").asText());
            }
            if (node.has("mainOwnerId") && !node.get("mainOwnerId").isNull()) {
                task.setMainOwnerId(node.get("mainOwnerId").asLong());
            }
            if (node.has("planStartDate") && !node.get("planStartDate").isNull()) {
                String dateStr = node.get("planStartDate").asText();
                if (!dateStr.isEmpty()) {
                    task.setPlanStartDate(LocalDate.parse(dateStr));
                }
            }
            if (node.has("planEndDate") && !node.get("planEndDate").isNull()) {
                String dateStr = node.get("planEndDate").asText();
                if (!dateStr.isEmpty()) {
                    task.setPlanEndDate(LocalDate.parse(dateStr));
                }
            }

            taskMapper.updateById(task);
        } catch (Exception e) {
            // 同步失败不影响审核流程，记录错误日志
            System.err.println("[PMS] 变更同步任务失败, changeId=" + record.getChangeId() + ", error=" + e.getMessage());
        }
    }

    @Override
    public void executeApprovedChange(Long id, Long operatorId) {
        PmsChangeRecordDO record = requireRecord(id);
        requireProjectManager(record, operatorId);
        if (!"approved".equals(record.getApprovalStatus()) || !"approved_pending_execution".equals(record.getChangeStatus())) {
            throw new ServiceException(cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.CHANGE_STATUS_INVALID);
        }
        record.setChangeStatus("executed");
        record.setExecuteTime(LocalDateTime.now());
        changeRecordMapper.updateById(record);
    }

    @Override
    public void executeChange(Long id) {
        PmsChangeRecordDO record = changeRecordMapper.selectById(id);
        if (record == null) {
            throw new ServiceException(500, "变更记录不存在");
        }
        if (!"approved_pending_execution".equals(record.getChangeStatus())) {
            throw new ServiceException(500, "只有审批通过的变更才能执行");
        }
        record.setChangeStatus("executed");
        changeRecordMapper.updateById(record);
    }

    private void requireProjectManager(PmsChangeRecordDO record, Long operatorId) {
        // super_admin 豁免
        if (securityFrameworkService.hasAnyRoles("super_admin")) return;
        // 被指派的审批人本人可审核变更
        if (record.getApproverId() != null && java.util.Objects.equals(record.getApproverId(), operatorId)) return;
        // 项目管理员也可审核（兼容旧逻辑）
        PmsProjectDO project = projectMapper.selectById(record.getProjectId());
        if (project == null || !java.util.Objects.equals(project.getProjectManagerId(), operatorId)) {
            throw new ServiceException(cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.PROJECT_MANAGER_REQUIRED);
        }
    }

    private PmsChangeRecordDO requireRecord(Long id) {
        PmsChangeRecordDO record = changeRecordMapper.selectById(id);
        if (record == null) {
            throw new ServiceException(cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.CHANGE_NOT_EXISTS);
        }
        return record;
    }

    @Override
    public void updateChangeRecord(PmsChangeRecordDO entity) {
        changeRecordMapper.updateById(entity);
    }

    @Override
    public void deleteChangeRecord(Long id) {
        changeRecordMapper.deleteById(id);
    }

    @Override
    public PmsChangeRecordDO getChangeRecord(Long id) {
        return changeRecordMapper.selectById(id);
    }

    @Override
    public List<PmsChangeRecordDO> getChangeRecordList() {
        return changeRecordMapper.selectList(null);
    }

}