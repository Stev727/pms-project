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
        return entity.getStageId();
    }

    @Override
    public void updateProjectStage(PmsProjectStageDO entity) {
        requireProjectManager(entity.getProjectId());
        projectStageMapper.updateById(entity);
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
    }

    @Override
    public PmsProjectStageDO getProjectStage(Long id) {
        return projectStageMapper.selectById(id);
    }

    @Override
    public List<PmsProjectStageDO> getProjectStageList() {
        return projectStageMapper.selectList(null);
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