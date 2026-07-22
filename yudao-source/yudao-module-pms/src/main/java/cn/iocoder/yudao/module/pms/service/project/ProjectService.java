package cn.iocoder.yudao.module.pms.service.project;

import cn.iocoder.yudao.module.pms.dal.dataobject.project.PmsProjectDO;
import java.util.List;

/**
 * 项目 Service 接口
 */
public interface ProjectService {

    Long createProject(PmsProjectDO entity);

    void updateProject(PmsProjectDO entity);

    void deleteProject(Long id);

    PmsProjectDO getProject(Long id);

    List<PmsProjectDO> getProjectList();

}