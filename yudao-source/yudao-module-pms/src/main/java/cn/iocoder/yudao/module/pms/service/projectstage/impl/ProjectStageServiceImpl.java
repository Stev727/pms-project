package cn.iocoder.yudao.module.pms.service.projectstage.impl;

import cn.iocoder.yudao.module.pms.dal.dataobject.projectstage.PmsProjectStageDO;
import cn.iocoder.yudao.module.pms.dal.mysql.projectstage.ProjectStageMapper;
import cn.iocoder.yudao.module.pms.service.projectstage.ProjectStageService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

@Service
public class ProjectStageServiceImpl implements ProjectStageService {

    @Resource
    private ProjectStageMapper projectStageMapper;

    @Override
    public Long createProjectStage(PmsProjectStageDO entity) {
        projectStageMapper.insert(entity);
        return entity.getStageId();
    }

    @Override
    public void updateProjectStage(PmsProjectStageDO entity) {
        projectStageMapper.updateById(entity);
    }

    @Override
    public void deleteProjectStage(Long id) {
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

}