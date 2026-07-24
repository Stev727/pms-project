package cn.iocoder.yudao.module.pms.service.project.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.pms.controller.admin.project.vo.ProjectCreateBundleReqVO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.dal.dataobject.notifyrule.PmsNotifyRuleDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.projectmember.PmsProjectMemberDO;
import cn.iocoder.yudao.module.pms.dal.mysql.notifyrule.NotifyRuleMapper;
import cn.iocoder.yudao.module.pms.dal.dataobject.project.PmsProjectDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.projectstage.PmsProjectStageDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.taskdependency.PmsTaskDependencyDO;
import cn.iocoder.yudao.module.pms.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.projectmember.ProjectMemberMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.projectstage.ProjectStageMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.task.TaskMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.taskdependency.TaskDependencyMapper;
import cn.iocoder.yudao.module.pms.service.project.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProjectServiceImpl implements ProjectService {

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private ProjectMemberMapper projectMemberMapper;

    @Resource
    private NotifyRuleMapper notifyRuleMapper;

    @Resource
    private ProjectStageMapper projectStageMapper;

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private TaskDependencyMapper taskDependencyMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProject(PmsProjectDO entity) {
        // 项目编号为空时自动生成唯一编号
        if (StrUtil.isBlank(entity.getProjectCode())) {
            entity.setProjectCode(generateProjectCode());
        }

        projectMapper.insert(entity);
        Long projectId = entity.getProjectId();

        // 基于模板复制阶段、任务、依赖
        if (entity.getTemplateId() != null) {
            copyTemplateTasks(entity, projectId);
        }

        return projectId;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProjectBundle(ProjectCreateBundleReqVO request) {
        if (request == null || request.getProject() == null) {
            throw new ServiceException(cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.PROJECT_REQUIRED);
        }
        List<PmsProjectMemberDO> members = request.getMembers() == null ? List.of() : request.getMembers();
        List<PmsTaskDO> tasks = request.getTasks() == null ? List.of() : request.getTasks();
        Set<Long> memberUserIds = members.stream().map(PmsProjectMemberDO::getUserId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        boolean invalidOwner = tasks.stream().map(PmsTaskDO::getMainOwnerId)
                .filter(java.util.Objects::nonNull).anyMatch(ownerId -> !memberUserIds.contains(ownerId));
        if (invalidOwner) {
            throw new ServiceException(cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.TASK_OWNER_NOT_MEMBER);
        }

        PmsProjectDO project = request.getProject();
        Long templateId = project.getTemplateId();
        // Bundle 模式由前端提交最终任务清单，只复制模板阶段，避免 createProject 再复制一遍模板任务。
        project.setTemplateId(null);
        Long projectId = createProject(project);
        Map<Long, Long> stageIdMap = copyTemplateStages(templateId, projectId);
        if (templateId != null) {
            PmsProjectDO templateUpdate = new PmsProjectDO();
            templateUpdate.setProjectId(projectId);
            templateUpdate.setTemplateId(templateId);
            projectMapper.updateById(templateUpdate);
            project.setTemplateId(templateId);
        }
        for (PmsProjectMemberDO member : members) {
            member.setProjectId(projectId);
            projectMemberMapper.insert(member);
        }
        for (PmsTaskDO task : tasks) {
            task.setProjectId(projectId);
            if (task.getStageId() != null) {
                task.setStageId(stageIdMap.get(task.getStageId()));
            }
            taskMapper.insert(task);
        }
        List<PmsNotifyRuleDO> rules;
        if (request.getNotifyModeId() != null) {
            rules = notifyRuleMapper.selectList(PmsNotifyRuleDO::getModeId, request.getNotifyModeId());
        } else {
            rules = request.getNotifyRules() == null ? List.of() : request.getNotifyRules();
        }
        for (PmsNotifyRuleDO sourceRule : rules) {
            PmsNotifyRuleDO rule = new PmsNotifyRuleDO();
            BeanUtil.copyProperties(sourceRule, rule, "ruleId", "projectId", "taskId", "scopeType", "modeId", "sourceModeId",
                    "creator", "createTime", "updater", "updateTime", "deleted");
            rule.setProjectId(projectId);
            rule.setTaskId(null);
            rule.setScopeType("project");
            rule.setModeId(null);
            rule.setSourceModeId(request.getNotifyModeId());
            notifyRuleMapper.insert(rule);
        }
        return projectId;
    }
    private Map<Long, Long> copyTemplateStages(Long templateId, Long projectId) {
        Map<Long, Long> stageIdMap = new HashMap<>();
        if (templateId == null) {
            return stageIdMap;
        }
        List<PmsProjectStageDO> templateStages = projectStageMapper.selectList(
                PmsProjectStageDO::getProjectId, templateId);
        templateStages.sort(Comparator.comparingInt(stage ->
                stage.getSortOrder() == null ? 0 : stage.getSortOrder()));
        for (PmsProjectStageDO stage : templateStages) {
            PmsProjectStageDO newStage = new PmsProjectStageDO();
            BeanUtil.copyProperties(stage, newStage,
                    "stageId", "projectId", "actualStartDate", "actualEndDate", "progress", "status");
            newStage.setProjectId(projectId);
            newStage.setStatus("not_started");
            newStage.setProgress(0);
            projectStageMapper.insert(newStage);
            stageIdMap.put(stage.getStageId(), newStage.getStageId());
        }
        if (!templateStages.isEmpty()) {
            PmsProjectDO projectUpdate = new PmsProjectDO();
            projectUpdate.setProjectId(projectId);
            projectUpdate.setCurrentStage(templateStages.get(0).getStageName());
            projectMapper.updateById(projectUpdate);
        }
        return stageIdMap;
    }

    /**
     * 自动生成项目编号，格式：PRJ-YYYYMMDD-NNNNNNNN，按当前最大序号递增
     * 已删除记录也参与占号，避免唯一索引冲突
     */
    private String generateProjectCode() {
        String prefix = "PRJ-" + DateUtil.format(new Date(), "yyyyMMdd") + "-";
        // 原生SQL查询，绕过MyBatis Plus软删除拦截器，确保已删除记录也参与占号
        long count = projectMapper.countByCodePrefixIncludeDeleted(prefix);
        return prefix + String.format("%08d", count + 1);
    }

    /**
     * 复制模板项目的阶段、任务、依赖关系到新项目
     */
    private void copyTemplateTasks(PmsProjectDO entity, Long newProjectId) {
        // 1. 复制阶段
        Long templateId = entity.getTemplateId();
        List<PmsProjectStageDO> templateStages = projectStageMapper.selectList(PmsProjectStageDO::getProjectId, templateId);
        if (CollUtil.isEmpty(templateStages)) {
            return;
        }
        templateStages.sort(Comparator.comparingInt(PmsProjectStageDO::getSortOrder));

        Map<Long, Long> stageIdMap = new HashMap<>();
        for (PmsProjectStageDO stage : templateStages) {
            PmsProjectStageDO newStage = new PmsProjectStageDO();
            BeanUtil.copyProperties(stage, newStage, "stageId", "projectId", "actualStartDate", "actualEndDate", "progress", "status");
            newStage.setProjectId(newProjectId);
            newStage.setStatus("not_started");
            newStage.setProgress(0);
            projectStageMapper.insert(newStage);
            stageIdMap.put(stage.getStageId(), newStage.getStageId());
        }

        // 更新项目当前阶段为第一阶段
        PmsProjectDO projectUpdate = new PmsProjectDO();
        projectUpdate.setProjectId(newProjectId);
        projectUpdate.setCurrentStage(templateStages.get(0).getStageName());
        projectMapper.updateById(projectUpdate);

        // 2. 复制任务（先不处理 parentTaskId 和依赖）
        List<PmsTaskDO> templateTasks = taskMapper.selectList(PmsTaskDO::getProjectId, templateId);
        if (CollUtil.isEmpty(templateTasks)) {
            return;
        }
        templateTasks.sort(Comparator.comparingInt(t -> t.getSortOrder() == null ? 0 : t.getSortOrder()));

        Map<Long, Long> taskIdMap = new HashMap<>();
        // P0-02 修复: 根据项目 planStartDate 和任务 cycle 计算每个任务的计划日期
        LocalDate projectStartDate = entity.getPlanStartDate();
        LocalDate projectEndDate = entity.getPlanEndDate();
        for (PmsTaskDO task : templateTasks) {
            PmsTaskDO newTask = new PmsTaskDO();
            BeanUtil.copyProperties(task, newTask,
                    "taskId", "projectId", "stageId", "parentTaskId", "actualCompleteDate",
                    "completeStatus", "progress", "isDispatched", "dispatchTime",
                    "delayDate", "delayLevel", "exceptionReason", "improvementPlan",
                    "reviewOpinion", "actualHours", "planStartDate", "planEndDate");
            newTask.setProjectId(newProjectId);
            newTask.setStageId(stageIdMap.get(task.getStageId()));
            newTask.setCompleteStatus("not_started");
            newTask.setProgress(0);
            // 计算任务计划日期: planStartDate = 项目开始日期, planEndDate = planStartDate + cycle 天
            if (projectStartDate != null) {
                Integer cycle = task.getCycle() != null ? task.getCycle() : 5;
                LocalDate taskStart = projectStartDate;
                LocalDate taskEnd = taskStart.plusDays(cycle);
                if (projectEndDate != null && taskEnd.isAfter(projectEndDate)) {
                    taskEnd = projectEndDate;
                }
                newTask.setPlanStartDate(taskStart);
                newTask.setPlanEndDate(taskEnd);
            }
            taskMapper.insert(newTask);
            taskIdMap.put(task.getTaskId(), newTask.getTaskId());
        }

        // 3. 更新任务 parentTaskId
        for (PmsTaskDO task : templateTasks) {
            if (task.getParentTaskId() != null && taskIdMap.containsKey(task.getParentTaskId())) {
                PmsTaskDO updateTask = new PmsTaskDO();
                updateTask.setTaskId(taskIdMap.get(task.getTaskId()));
                updateTask.setParentTaskId(taskIdMap.get(task.getParentTaskId()));
                taskMapper.updateById(updateTask);
            }
        }

        // 4. 复制任务依赖关系
        List<Long> templateTaskIds = templateTasks.stream().map(PmsTaskDO::getTaskId).toList();
        List<PmsTaskDependencyDO> templateDeps = taskDependencyMapper.selectList(PmsTaskDependencyDO::getTaskId, templateTaskIds);
        if (CollUtil.isEmpty(templateDeps)) {
            return;
        }
        for (PmsTaskDependencyDO dep : templateDeps) {
            if (!taskIdMap.containsKey(dep.getTaskId()) || !taskIdMap.containsKey(dep.getPreTaskId())) {
                continue;
            }
            PmsTaskDependencyDO newDep = new PmsTaskDependencyDO();
            BeanUtil.copyProperties(dep, newDep, "dependencyId");
            newDep.setTaskId(taskIdMap.get(dep.getTaskId()));
            newDep.setPreTaskId(taskIdMap.get(dep.getPreTaskId()));
            taskDependencyMapper.insert(newDep);
        }
    }

    @Override
    public void updateProject(PmsProjectDO entity) {
        projectMapper.updateById(entity);
    }

    @Override
    public void deleteProject(Long id) {
        projectMapper.deleteById(id);
    }

    @Override
    public PmsProjectDO getProject(Long id) {
        PmsProjectDO project = projectMapper.selectById(id);
        if (project != null) {
            fillProjectProgress(project);
        }
        return project;
    }

    @Override
    public List<PmsProjectDO> getProjectList() {
        List<PmsProjectDO> projects = projectMapper.selectList(null);
        if (projects != null && !projects.isEmpty()) {
            for (PmsProjectDO project : projects) {
                fillProjectProgress(project);
            }
        }
        return projects;
    }

    /**
     * 实时计算项目的 progress 和当前阶段状态
     * - progress = 已完成任务数 * 100 / 总任务数
     * - 若有任务进行中或已完成，且项目状态为 initiating，则更新为 in_progress
     */
    private void fillProjectProgress(PmsProjectDO project) {
        List<PmsTaskDO> tasks = taskMapper.selectList(PmsTaskDO::getProjectId, project.getProjectId());
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        long completed = tasks.stream()
                .filter(t -> "completed".equals(t.getCompleteStatus()))
                .count();
        int progress = (int) (completed * 100 / tasks.size());
        project.setProgress(progress);

        long inProgressCount = tasks.stream()
                .filter(t -> "in_progress".equals(t.getCompleteStatus())
                        || "completed".equals(t.getCompleteStatus()))
                .count();
        if (inProgressCount > 0 && "initiating".equals(project.getStatus())) {
            project.setStatus("in_progress");
        }
    }

}
