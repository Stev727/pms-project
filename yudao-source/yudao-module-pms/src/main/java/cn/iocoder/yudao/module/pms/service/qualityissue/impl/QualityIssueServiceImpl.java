package cn.iocoder.yudao.module.pms.service.qualityissue.impl;

import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;
import cn.iocoder.yudao.module.pms.controller.admin.qualityissue.vo.QualityIssueImportErrorExcel;
import cn.iocoder.yudao.module.pms.controller.admin.qualityissue.vo.QualityIssueImportExcel;
import cn.iocoder.yudao.module.pms.controller.admin.qualityissue.vo.QualityIssueImportRespVO;
import cn.iocoder.yudao.module.pms.dal.dataobject.qualityissue.PmsQualityIssueDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import cn.iocoder.yudao.module.pms.dal.mysql.qualityissue.QualityIssueMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.task.TaskMapper;
import cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.pms.service.dingtalk.DingTalkNotifyService;
import cn.iocoder.yudao.module.pms.service.qualityissue.QualityIssueService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 质量问题 Service 实现
 *
 * 改造说明（#8 质量问题 Excel 批量导入）：
 *  - 新增 importQualityIssueList：整批校验 + 整批回滚 + 逐条触发责任人通知
 *  - 新增 getQualityIssueListByProjectId
 *  - 通知通过 {@link TransactionSynchronization#afterCommit()} 在事务提交后触发，
 *    通知失败仅告警不影响业务数据（与既有派发通知行为一致）
 *  - 权限校验由 Controller 层 @PreAuthorize + {@link ProjectPermissionService#checkPermission}
 *    双层把关，Service 内不重复校验
 */
@Slf4j
@Service
public class QualityIssueServiceImpl implements QualityIssueService {

    /**
     * 通知触发事件标识（与既有派发通知 triggerEvent 保持命名风格）
     */
    private static final String NOTIFY_TRIGGER_EVENT = "quality_issue_import";
    private static final String NOTIFY_BUSINESS_TYPE = "pms_quality_issue";

    @Resource
    private QualityIssueMapper qualityIssueMapper;
    @Resource
    private TaskMapper taskMapper;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private DingTalkNotifyService dingTalkNotifyService;

    // ==================== 基础 CRUD（保持原样） ====================

    @Override
    public Long createQualityIssue(PmsQualityIssueDO entity) {
        qualityIssueMapper.insert(entity);
        return entity.getIssueId();
    }

    @Override
    public void updateQualityIssue(PmsQualityIssueDO entity) {
        qualityIssueMapper.updateById(entity);
    }

    @Override
    public void deleteQualityIssue(Long id) {
        validateIssueExists(id);
        qualityIssueMapper.deleteById(id);
    }

    @Override
    public PmsQualityIssueDO getQualityIssue(Long id) {
        return qualityIssueMapper.selectById(id);
    }

    @Override
    public List<PmsQualityIssueDO> getQualityIssueList() {
        return qualityIssueMapper.selectList(null);
    }

    @Override
    public List<PmsQualityIssueDO> getQualityIssueListByProjectId(Long projectId) {
        return qualityIssueMapper.selectListByProjectId(projectId);
    }

    private void validateIssueExists(Long id) {
        if (id == null || qualityIssueMapper.selectById(id) == null) {
            throw new cn.iocoder.yudao.framework.common.exception.ServiceException(
                    ErrorCodeConstants.QUALITY_ISSUE_NOT_EXISTS);
        }
    }

    // ==================== #8 Excel 批量导入 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QualityIssueImportRespVO importQualityIssueList(Long projectId, List<QualityIssueImportExcel> rows) {
        // 0. 空文件 / 无数据行
        if (rows == null || rows.isEmpty()) {
            throw new cn.iocoder.yudao.framework.common.exception.ServiceException(
                    ErrorCodeConstants.QUALITY_IMPORT_FILE_EMPTY);
        }

        // 1. 整批校验
        List<PmsQualityIssueDO> toInsert = new ArrayList<>(rows.size());
        List<QualityIssueImportErrorExcel> failureRows = new ArrayList<>();

        // 姓名匹配缓存：name → userId（同姓名重复行只匹配一次）
        Map<String, Long> nameUserIdCache = new HashMap<>();
        // 任务名匹配缓存：taskName → taskId
        Map<String, Long> taskNameIdCache = new HashMap<>();

        int rowIndex = 1; // Excel 行号从 1 开始（0 行为表头）
        for (QualityIssueImportExcel row : rows) {
            rowIndex++;
            List<String> errors = new ArrayList<>();

            // 1.1 必填项校验
            validateRequired(row, errors);

            // 1.2 日期合理性校验
            validateDates(row, errors);

            // 1.3 责任人姓名匹配
            Long responsibleUserId = null;
            if (row.getResponsiblePersonName() != null && !row.getResponsiblePersonName().isEmpty()) {
                responsibleUserId = matchUserByName(row.getResponsiblePersonName(), nameUserIdCache, errors, "责任人");
            }

            // 1.4 发现人姓名匹配
            Long discovererUserId = null;
            if (row.getDiscovererName() != null && !row.getDiscovererName().isEmpty()) {
                discovererUserId = matchUserByName(row.getDiscovererName(), nameUserIdCache, errors, "发现人");
            }

            // 1.5 关联任务名匹配（非必填，填了才校验）
            Long taskId = null;
            if (row.getTaskName() != null && !row.getTaskName().isEmpty()) {
                taskId = matchTaskByName(row.getTaskName(), projectId, taskNameIdCache, errors);
            }

            // 1.6 任一校验失败 → 收集错误行，不进入插入阶段
            if (!errors.isEmpty()) {
                failureRows.add(buildErrorExcel(row, String.join("；", errors)));
                continue;
            }

            // 1.7 构造 DO
            PmsQualityIssueDO issue = new PmsQualityIssueDO();
            issue.setProjectId(projectId);
            issue.setTaskId(taskId);
            issue.setIssueTitle(row.getIssueTitle());
            issue.setIssueType(row.getIssueType());
            issue.setSeverity(row.getSeverity());
            issue.setIssueDescription(row.getIssueDescription());
            issue.setResponsiblePerson(row.getResponsiblePersonName()); // 姓名字符串保留，便于列表展示
            issue.setAssigneeId(responsibleUserId); // userId 用于钉钉通知
            issue.setDiscovererId(discovererUserId);
            issue.setDiscoveredDate(row.getDiscoveredDate());
            issue.setDueDate(row.getDueDate());
            issue.setStatus(row.getStatus());
            // "整改要求"映射到既有 solution 字段（DO 无独立 rectification_requirement 字段，避免再 ALTER）
            issue.setSolution(row.getRectificationRequirement());
            issue.setSource("excel_import"); // 标记来源
            toInsert.add(issue);
        }

        // 2. 存在错误行 → 整批不落库，返回错误行供 Controller 生成错误 Excel 下载
        if (!failureRows.isEmpty()) {
            log.warn("[importQualityIssueList][projectId={} 共 {} 行校验失败，整批不落库]",
                    projectId, failureRows.size());
            return QualityIssueImportRespVO.builder()
                    .success(false)
                    .successCount(0)
                    .failureRows(failureRows)
                    .build();
        }

        // 3. 全部校验通过 → 批量插入
        qualityIssueMapper.insertBatch(toInsert);

        // 4. 注册 afterCommit 回调：事务提交后逐条触发责任人通知
        //    通知失败仅告警，不影响已落库的业务数据
        final List<PmsQualityIssueDO> insertedSnapshot = new ArrayList<>(toInsert);
        final Long pid = projectId;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notifyQualityIssueCreatedInternal(insertedSnapshot, pid);
                }
            });
        } else {
            // 兜底：无事务上下文（理论上不会到这里，但保险起见直接通知）
            log.warn("[importQualityIssueList][无事务上下文，直接触发通知]");
            notifyQualityIssueCreatedInternal(insertedSnapshot, pid);
        }

        return QualityIssueImportRespVO.builder()
                .success(true)
                .successCount(toInsert.size())
                .failureRows(Collections.emptyList())
                .build();
    }

    /**
     * 必填项校验（与模板对齐：标题/类型/严重程度/描述/责任人/发现人/发现日期/期望完成日期/状态）
     */
    private void validateRequired(QualityIssueImportExcel row, List<String> errors) {
        if (isBlank(row.getIssueTitle())) {
            errors.add("标题不能为空");
        }
        if (isBlank(row.getIssueType())) {
            errors.add("类型不能为空");
        }
        if (isBlank(row.getSeverity())) {
            errors.add("严重程度不能为空");
        }
        if (isBlank(row.getIssueDescription())) {
            errors.add("描述不能为空");
        }
        if (isBlank(row.getResponsiblePersonName())) {
            errors.add("责任人(姓名)不能为空");
        }
        if (isBlank(row.getDiscovererName())) {
            errors.add("发现人(姓名)不能为空");
        }
        if (row.getDiscoveredDate() == null) {
            errors.add("发现日期不能为空");
        }
        if (row.getDueDate() == null) {
            errors.add("期望完成日期不能为空");
        }
        if (isBlank(row.getStatus())) {
            errors.add("状态不能为空");
        }
    }

    /**
     * 日期合理性校验：期望完成日期不能早于发现日期
     */
    private void validateDates(QualityIssueImportExcel row, List<String> errors) {
        LocalDate discovered = row.getDiscoveredDate();
        LocalDate due = row.getDueDate();
        if (discovered != null && due != null && due.isBefore(discovered)) {
            errors.add("期望完成日期不能早于发现日期");
        }
    }

    /**
     * 按姓名精确匹配 system_users.nickname
     *  - 0 匹配 → 错误
     *  - >1 匹配（重名）→ 错误
     *  - =1 匹配 → 返回 userId
     *
     * @param name         待匹配姓名
     * @param cache        姓名→userId 缓存（同姓名重复行只匹配一次）
     * @param errors       错误收集容器
     * @param fieldLabel   字段中文名（用于错误提示）
     * @return 匹配到的 userId，匹配失败返回 null
     */
    private Long matchUserByName(String name, Map<String, Long> cache, List<String> errors, String fieldLabel) {
        // 命中缓存直接返回（缓存里 null 也表示匹配失败，仍要触发错误）
        if (cache.containsKey(name)) {
            Long cached = cache.get(name);
            if (cached == null) {
                errors.add(fieldLabel + "「" + name + "」未找到对应用户");
            }
            return cached;
        }
        // AdminUserApi.getUserListByNickname 是 LIKE 模糊匹配，需在内存里做精确比较
        List<AdminUserRespDTO> candidates = adminUserApi.getUserListByNickname(name);
        List<AdminUserRespDTO> exactMatches = new ArrayList<>();
        if (candidates != null) {
            for (AdminUserRespDTO u : candidates) {
                if (Objects.equals(u.getNickname(), name)) {
                    exactMatches.add(u);
                }
            }
        }
        if (exactMatches.isEmpty()) {
            cache.put(name, null);
            errors.add(fieldLabel + "「" + name + "」未找到对应用户");
            return null;
        }
        if (exactMatches.size() > 1) {
            cache.put(name, null);
            errors.add(fieldLabel + "「" + name + "」存在多个同名用户，请联系管理员处理");
            return null;
        }
        Long userId = exactMatches.get(0).getId();
        cache.put(name, userId);
        return userId;
    }

    /**
     * 在本项目内按任务名精确匹配 pms_task.task_name
     *  - 0 匹配 → 错误
     *  - >1 匹配（重名）→ 错误
     *  - =1 匹配 → 返回 taskId
     *
     * @param taskName  待匹配任务名
     * @param projectId 项目ID（限定本项目内）
     * @param cache     任务名→taskId 缓存
     * @param errors    错误收集容器
     * @return 匹配到的 taskId，匹配失败返回 null
     */
    private Long matchTaskByName(String taskName, Long projectId, Map<String, Long> cache, List<String> errors) {
        if (cache.containsKey(taskName)) {
            Long cached = cache.get(taskName);
            if (cached == null) {
                errors.add("关联任务「" + taskName + "」在本项目内未找到或存在重名");
            }
            return cached;
        }
        List<PmsTaskDO> candidates = taskMapper.selectList(
                new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<PmsTaskDO>()
                        .eq(PmsTaskDO::getProjectId, projectId)
                        .eq(PmsTaskDO::getTaskName, taskName));
        if (candidates == null || candidates.isEmpty()) {
            cache.put(taskName, null);
            errors.add("关联任务「" + taskName + "」在本项目内未找到");
            return null;
        }
        if (candidates.size() > 1) {
            cache.put(taskName, null);
            errors.add("关联任务「" + taskName + "」在本项目内存在重名，请联系项目经理处理");
            return null;
        }
        Long taskId = candidates.get(0).getTaskId();
        cache.put(taskName, taskId);
        return taskId;
    }

    /**
     * 构造错误 Excel 行（保留原始数据 + 错误信息）
     */
    private QualityIssueImportErrorExcel buildErrorExcel(QualityIssueImportExcel row, String errorMsg) {
        return QualityIssueImportErrorExcel.builder()
                .errorMessage(errorMsg)
                .issueTitle(row.getIssueTitle())
                .issueType(row.getIssueType())
                .severity(row.getSeverity())
                .issueDescription(row.getIssueDescription())
                .responsiblePersonName(row.getResponsiblePersonName())
                .discovererName(row.getDiscovererName())
                .discoveredDate(row.getDiscoveredDate())
                .dueDate(row.getDueDate())
                .taskName(row.getTaskName())
                .status(row.getStatus())
                .rectificationRequirement(row.getRectificationRequirement())
                .build();
    }

    /**
     * 逐条触发责任人钉钉通知。
     * 通知失败仅告警，不抛异常，不影响业务数据。
     */
    private void notifyQualityIssueCreatedInternal(List<PmsQualityIssueDO> issues, Long projectId) {
        if (issues == null || issues.isEmpty()) {
            return;
        }
        for (PmsQualityIssueDO issue : issues) {
            try {
                Long receiverId = issue.getAssigneeId();
                if (receiverId == null) {
                    log.warn("[notifyQualityIssueCreated][issueId={} 责任人 userId 为空，跳过通知]", issue.getIssueId());
                    continue;
                }
                String title = "您有新的质量问题待处理：" + (issue.getIssueTitle() == null ? issue.getIssueCode() : issue.getIssueTitle());
                StringBuilder content = new StringBuilder();
                content.append("质量问题标题：").append(issue.getIssueTitle()).append("\n");
                content.append("类型：").append(issue.getIssueType() == null ? "-" : issue.getIssueType()).append("\n");
                content.append("严重程度：").append(issue.getSeverity() == null ? "-" : issue.getSeverity()).append("\n");
                content.append("期望完成日期：").append(issue.getDueDate() == null ? "-" : issue.getDueDate()).append("\n");
                content.append("描述：").append(issue.getIssueDescription() == null ? "-" : issue.getIssueDescription()).append("\n");
                if (issue.getSolution() != null) {
                    content.append("整改要求：").append(issue.getSolution()).append("\n");
                }
                boolean ok = dingTalkNotifyService.sendNotifyDirect(
                        title,
                        content.toString(),
                        java.util.Collections.singletonList(receiverId),
                        NOTIFY_TRIGGER_EVENT,
                        NOTIFY_BUSINESS_TYPE,
                        issue.getIssueId(),
                        null);
                if (!ok) {
                    log.warn("[notifyQualityIssueCreated][issueId={} 钉钉通知发送失败]", issue.getIssueId());
                }
            } catch (Exception e) {
                // 通知失败仅告警，不中断循环，不影响业务数据
                log.error("[notifyQualityIssueCreated][issueId={} 通知异常]", issue.getIssueId(), e);
            }
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

}

