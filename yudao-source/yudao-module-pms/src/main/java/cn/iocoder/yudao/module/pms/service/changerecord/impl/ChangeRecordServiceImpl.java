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

    @Override
    public Long createChangeRecord(PmsChangeRecordDO entity) {
        entity.setApprovalStatus("pending");
        entity.setChangeStatus("pending_review");
        changeRecordMapper.insert(entity);
        return entity.getChangeId();
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