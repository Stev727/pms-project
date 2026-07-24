package cn.iocoder.yudao.module.pms.service.project.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.pms.dal.dataobject.project.PmsProjectDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.projectstage.PmsProjectStageDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.taskdependency.PmsTaskDependencyDO;
import cn.iocoder.yudao.module.pms.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.projectstage.ProjectStageMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.task.TaskMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.taskdependency.TaskDependencyMapper;
import cn.iocoder.yudao.module.pms.service.project.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProjectServiceImpl implements ProjectService {

    @Resource
    private ProjectMapper projectMapper;

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
            copyTemplateTasks(entity.getTemplateId(), projectId);
        }

        return projectId;
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
    private void copyTemplateTasks(Long templateId, Long newProjectId) {
        // 1. 复制阶段
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
            PmsTaskDO newTask = new PmsTaskDO();
            BeanUtil.copyProperties(task, newTask,
                    "taskId", "projectId", "stageId", "parentTaskId", "actualCompleteDate",
                    "completeStatus", "progress", "isDispatched", "dispatchTime",
                    "delayDate", "delayLevel", "exceptionReason", "improvementPlan",
                    "reviewOpinion", "actualHours");
            newTask.setProjectId(newProjectId);
            newTask.setStageId(stageIdMap.get(task.getStageId()));
            newTask.setCompleteStatus("not_started");
            newTask.setProgress(0);
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
        return projectMapper.selectById(id);
    }

    @Override
    public List<PmsProjectDO> getProjectList() {
        return projectMapper.selectList(null);
    }

}
