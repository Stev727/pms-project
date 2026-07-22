package cn.iocoder.yudao.module.pms.service.projectstage;

import cn.iocoder.yudao.module.pms.dal.dataobject.projectstage.PmsProjectStageDO;
import java.util.List;

/**
 * 项目阶段 Service 接口
 */
public interface ProjectStageService {

    Long createProjectStage(PmsProjectStageDO entity);

    void updateProjectStage(PmsProjectStageDO entity);

    void deleteProjectStage(Long id);

    PmsProjectStageDO getProjectStage(Long id);

    List<PmsProjectStageDO> getProjectStageList();

}