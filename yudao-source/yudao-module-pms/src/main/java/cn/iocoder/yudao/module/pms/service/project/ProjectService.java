package cn.iocoder.yudao.module.pms.service.project;

import cn.iocoder.yudao.module.pms.controller.admin.project.vo.ProjectCreateBundleReqVO;
import cn.iocoder.yudao.module.pms.dal.dataobject.project.PmsProjectDO;
import java.util.List;

/**
 * 项目 Service 接口
 */
public interface ProjectService {

    Long createProject(PmsProjectDO entity);

    Long createProjectBundle(ProjectCreateBundleReqVO request);

    void updateProject(PmsProjectDO entity);

    void deleteProject(Long id);

    PmsProjectDO getProject(Long id);

    /**
     * 获取项目列表（含权限过滤）
     * @param projectType 项目类型，可选。传 "standard_template" 时按部门过滤模板；不传时按用户参与过滤项目
     */
    List<PmsProjectDO> getProjectList(String projectType);

}
