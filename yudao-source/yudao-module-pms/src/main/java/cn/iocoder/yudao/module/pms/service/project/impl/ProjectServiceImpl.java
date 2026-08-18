package cn.iocoder.yudao.module.pms.service.project.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.temporal.ChronoUnit;
import java.util.*;
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

    @Resource
    private SecurityFrameworkService securityFrameworkService;
    @Resource
    private JdbcTemplate jdbcTemplate;

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
        // 兼容:前端可能发 String 类型 stageId(19位bigint用Number()会丢精度)
        Map<String, Long> stageIdStringMap = new HashMap<>();
        for (Map.Entry<Long, Long> e : stageIdMap.entrySet()) {
            stageIdStringMap.put(String.valueOf(e.getKey()), e.getValue());
        }
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
            Long origStageId = task.getStageId();
            if (origStageId != null) {
                Long mapped = stageIdMap.get(origStageId);
                if (mapped == null) {
                    // 兼容:如果 stageId 是 String 类型(经 Jackson 转换后),用 String key 查找
                    mapped = stageIdStringMap.get(String.valueOf(origStageId));
                }
                task.setStageId(mapped);
            }
            normalizeTaskSchedule(task);
            task.setCompleteStatus("not_started");
            task.setProgress(0);
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
        for (PmsTaskDO task : templateTasks) {
            PmsTaskDO newTask = newTemplateTask(task, newProjectId, stageIdMap.get(task.getStageId()));
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

    static PmsTaskDO newTemplateTask(PmsTaskDO template, Long projectId, Long stageId) {
        PmsTaskDO task = new PmsTaskDO();
        task.setProjectId(projectId);
        task.setStageId(stageId);
        task.setTaskName(template.getTaskName());
        task.setSortOrder(template.getSortOrder());
        task.setCompleteStatus("not_started");
        task.setProgress(0);
        return task;
    }

    private static void normalizeTaskSchedule(PmsTaskDO task) {
        boolean hasStart = task.getPlanStartDate() != null;
        boolean hasEnd = task.getPlanEndDate() != null;
        if (hasStart != hasEnd || (hasStart && task.getPlanEndDate().isBefore(task.getPlanStartDate()))) {
            throw new ServiceException(cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.TASK_DATE_INVALID);
        }
        if (hasStart) {
            task.setCycle((int) ChronoUnit.DAYS.between(task.getPlanStartDate(), task.getPlanEndDate()) + 1);
        } else {
            task.setCycle(null);
        }
    }

    @Override
    public void updateProject(PmsProjectDO entity) {
        projectMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProject(Long id) {
        // 级联软删所有子表数据，避免删除项目后产生孤儿任务/阶段/成员/文档等导致统计虚高
        String[] childTables = {
            "pms_task", "pms_project_stage", "pms_project_member", "pms_document",
            "pms_change_record", "pms_quality_issue", "pms_approval_record",
            "pms_external_worklog", "pms_material_track", "pms_notify_log",
            "pms_notify_rule", "pms_project_permission", "pms_project_role"
        };
        for (String tbl : childTables) {
            try {
                jdbcTemplate.update("UPDATE " + tbl + " SET deleted=1, update_time=NOW() WHERE project_id=? AND deleted=0", id);
            } catch (Exception e) {
                // 某些表可能无 project_id/deleted 列，跳过；不影响主流程
            }
        }
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
    public List<PmsProjectDO> getProjectList(String projectType) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        boolean isAdmin = securityFrameworkService.hasAnyRoles("super_admin");

        // 管理员看全部
        if (isAdmin) {
            LambdaQueryWrapperX<PmsProjectDO> wrapper = new LambdaQueryWrapperX<>();
            wrapper.eqIfPresent(PmsProjectDO::getProjectType, projectType);
            List<PmsProjectDO> projects = projectMapper.selectList(wrapper);
            if (projects != null) {
                for (PmsProjectDO project : projects) {
                    fillProjectProgress(project);
                }
            }
            return projects;
        }

        // 非管理员查看模板：按部门过滤
        if ("standard_template".equals(projectType)) {
            Long deptId = SecurityFrameworkUtils.getLoginUserDeptId();
            if (deptId == null) {
                return Collections.emptyList();
            }
            LambdaQueryWrapperX<PmsProjectDO> tplWrapper = new LambdaQueryWrapperX<>();
            tplWrapper.eq(PmsProjectDO::getProjectType, "standard_template");
            tplWrapper.eq(PmsProjectDO::getDeptId, deptId);
            List<PmsProjectDO> templates = projectMapper.selectList(tplWrapper);
            return templates;
        }

        // 非管理员查看项目：只返回 PM=userId 或项目成员包含 userId 的项目
        // 任务责任人/协助人不能仅凭任务看到项目，必须先成为项目成员
        List<PmsProjectMemberDO> memberships = projectMemberMapper.selectList(
                new LambdaQueryWrapperX<PmsProjectMemberDO>()
                        .eq(PmsProjectMemberDO::getUserId, userId));
        Set<Long> involvedProjectIds = collectInvolvedProjectIds(null, memberships);

        // 2. 查询项目经理是当前用户的项目，或用户有任务参与的项目（排除模板）
        LambdaQueryWrapperX<PmsProjectDO> wrapper = new LambdaQueryWrapperX<>();
        // 兼容历史数据 project_type 为空；SQL 的 NULL != value 不成立，直接 ne 会误删全部旧项目。
        wrapper.and(w -> w.isNull(PmsProjectDO::getProjectType)
                .or().ne(PmsProjectDO::getProjectType, "standard_template"));
        if (involvedProjectIds.isEmpty()) {
            wrapper.eq(PmsProjectDO::getProjectManagerId, userId);
        } else {
            // 修复：拆分链式调用，避免 .eq() 返回父类导致 OR 作用域错误
            wrapper.and(w -> {
                w.eq(PmsProjectDO::getProjectManagerId, userId);
                w.or();
                w.in(PmsProjectDO::getProjectId, involvedProjectIds);
            });
        }

        List<PmsProjectDO> projects = projectMapper.selectList(wrapper);
        if (projects != null) {
            for (PmsProjectDO project : projects) {
                fillProjectProgress(project);
            }
        }
        return projects;
    }

    static Set<Long> collectInvolvedProjectIds(List<PmsTaskDO> tasks,
                                                List<PmsProjectMemberDO> memberships) {
        Set<Long> projectIds = tasks == null ? new java.util.HashSet<>() : tasks.stream()
                .map(PmsTaskDO::getProjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        memberships.stream()
                .filter(member -> member.getStatus() == null || "active".equals(member.getStatus()))
                .map(PmsProjectMemberDO::getProjectId)
                .filter(Objects::nonNull)
                .forEach(projectIds::add);
        return projectIds;
    }

    @Override
    public Map<Long, Long> countByTemplate() {
        // 查询所有使用了模板的真实项目（非模板项目，template_id 不为空），绕过权限过滤
        LambdaQueryWrapperX<PmsProjectDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.isNotNull(PmsProjectDO::getTemplateId);
        wrapper.and(w -> w.isNull(PmsProjectDO::getProjectType)
                .or().ne(PmsProjectDO::getProjectType, "standard_template"));
        List<PmsProjectDO> projects = projectMapper.selectList(wrapper);
        
        // 按 template_id 分组计数
        Map<Long, Long> result = new HashMap<>();
        for (PmsProjectDO project : projects) {
            Long templateId = project.getTemplateId();
            result.merge(templateId, 1L, Long::sum);
        }
        return result;
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
