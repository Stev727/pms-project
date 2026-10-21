package cn.iocoder.yudao.module.pms.service.projectstage.impl;

import cn.iocoder.yudao.module.pms.dal.dataobject.projectstage.PmsProjectStageDO;
import cn.iocoder.yudao.module.pms.dal.mysql.projectstage.ProjectStageMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.dal.dataobject.project.PmsProjectDO;
import cn.iocoder.yudao.module.pms.dal.mysql.task.TaskMapper;
import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import cn.iocoder.yudao.module.pms.service.projectstage.ProjectStageService;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

@Service
public class ProjectStageServiceImpl implements ProjectStageService {

    @Resource
    private ProjectStageMapper projectStageMapper;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private TaskMapper taskMapper;
    @Resource
    private SecurityFrameworkService securityFrameworkService;

    @Override
    public Long createProjectStage(PmsProjectStageDO entity) {
        requireProjectManager(entity.getProjectId());
        projectStageMapper.insert(entity);
        // 兜底：项目尚未记录当前阶段时（current_stage 为空），以新阶段为当前阶段
        PmsProjectDO project = projectMapper.selectById(entity.getProjectId());
        if (project != null
                && (project.getCurrentStage() == null || project.getCurrentStage().isEmpty())) {
            PmsProjectDO upd = new PmsProjectDO();
            upd.setProjectId(entity.getProjectId());
            upd.setCurrentStage(entity.getStageName());
            projectMapper.updateById(upd);
        }
        return entity.getStageId();
    }

    @Override
    public void updateProjectStage(PmsProjectStageDO entity) {
        requireProjectManager(entity.getProjectId());
        PmsProjectStageDO old = projectStageMapper.selectById(entity.getStageId());
        projectStageMapper.updateById(entity);
        // 阶段改名时联动更新项目 current_stage，防止列表残留旧阶段名
        if (old != null && entity.getStageName() != null
                && !entity.getStageName().equals(old.getStageName())) {
            PmsProjectDO project = projectMapper.selectById(entity.getProjectId());
            if (project != null && old.getStageName().equals(project.getCurrentStage())) {
                PmsProjectDO upd = new PmsProjectDO();
                upd.setProjectId(entity.getProjectId());
                upd.setCurrentStage(entity.getStageName());
                projectMapper.updateById(upd);
            }
        }
    }

    @Override
    public void deleteProjectStage(Long id) {
        PmsProjectStageDO stage = projectStageMapper.selectById(id);
        if (stage == null) return;
        requireProjectManager(stage.getProjectId());
        // 级联删除该阶段下的所有任务
        List<PmsTaskDO> tasks = taskMapper.selectList(
            Wrappers.<PmsTaskDO>lambdaQuery().eq(PmsTaskDO::getProjectId, stage.getProjectId())
                .eq(PmsTaskDO::getStageId, id));
        for (PmsTaskDO task : tasks) {
            taskMapper.deleteById(task.getTaskId());
        }
        projectStageMapper.deleteById(id);
        // 删除的是当前阶段时，重置为剩余阶段中 sort_order 最小的一个（无剩余则清空）
        PmsProjectDO project = projectMapper.selectById(stage.getProjectId());
        if (project != null && stage.getStageName().equals(project.getCurrentStage())) {
            List<PmsProjectStageDO> rest = projectStageMapper.selectList(
                Wrappers.<PmsProjectStageDO>lambdaQuery()
                    .eq(PmsProjectStageDO::getProjectId, stage.getProjectId())
                    .orderByAsc(PmsProjectStageDO::getSortOrder)
                    .orderByAsc(PmsProjectStageDO::getStageId));
            // 注意：updateById 默认不写 null，须用 lambdaUpdate 显式 set（清空场景才生效）
            projectMapper.update(null, Wrappers.<PmsProjectDO>lambdaUpdate()
                    .eq(PmsProjectDO::getProjectId, stage.getProjectId())
                    .set(PmsProjectDO::getCurrentStage,
                            rest.isEmpty() ? null : rest.get(0).getStageName()));
        }
    }

    @Override
    public PmsProjectStageDO getProjectStage(Long id) {
        return projectStageMapper.selectById(id);
    }

    @Override
    public List<PmsProjectStageDO> getProjectStageList() {
        // 阶段按 sort_order 升序返回，同序号按主键稳定排序（模板/项目阶段顺序由此保证）
        return projectStageMapper.selectList(
                Wrappers.<PmsProjectStageDO>lambdaQuery()
                        .orderByAsc(PmsProjectStageDO::getSortOrder)
                        .orderByAsc(PmsProjectStageDO::getStageId));
    }

    private void requireProjectManager(Long projectId) {
        if (securityFrameworkService.hasAnyRoles("super_admin")) return;
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        PmsProjectDO project = projectMapper.selectById(projectId);
        // 模板项目不校验项目经理，由菜单权限控制
        if (project != null && "standard_template".equals(project.getProjectType())) return;
        if (project == null || !java.util.Objects.equals(project.getProjectManagerId(), userId)) {
            throw new ServiceException(ErrorCodeConstants.PROJECT_MANAGER_REQUIRED);
        }
    }

}