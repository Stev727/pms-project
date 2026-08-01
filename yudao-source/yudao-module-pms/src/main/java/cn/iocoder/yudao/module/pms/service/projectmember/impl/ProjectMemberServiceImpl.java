package cn.iocoder.yudao.module.pms.service.projectmember.impl;

import cn.iocoder.yudao.module.pms.dal.dataobject.projectmember.PmsProjectMemberDO;
import cn.iocoder.yudao.module.pms.dal.mysql.projectmember.ProjectMemberMapper;
import cn.iocoder.yudao.module.pms.service.projectmember.ProjectMemberService;
import cn.iocoder.yudao.module.pms.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.dal.dataobject.project.PmsProjectDO;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

@Service
public class ProjectMemberServiceImpl implements ProjectMemberService {

    @Resource
    private ProjectMemberMapper projectMemberMapper;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private SecurityFrameworkService securityFrameworkService;

    @Override
    public Long createProjectMember(PmsProjectMemberDO entity) {
        requireProjectManager(entity.getProjectId());
        projectMemberMapper.insert(entity);
        return entity.getMemberId();
    }

    @Override
    public void updateProjectMember(PmsProjectMemberDO entity) {
        requireProjectManager(entity.getProjectId());
        projectMemberMapper.updateById(entity);
    }

    @Override
    public void deleteProjectMember(Long id) {
        PmsProjectMemberDO member = projectMemberMapper.selectById(id);
        if (member != null) requireProjectManager(member.getProjectId());
        projectMemberMapper.deleteById(id);
    }

    @Override
    public PmsProjectMemberDO getProjectMember(Long id) {
        return projectMemberMapper.selectById(id);
    }

    @Override
    public List<PmsProjectMemberDO> getProjectMemberList() {
        return projectMemberMapper.selectList(null);
    }

    private void requireProjectManager(Long projectId) {
        if (securityFrameworkService.hasAnyRoles("super_admin")) return;
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        PmsProjectDO project = projectMapper.selectById(projectId);
        if (project == null || !java.util.Objects.equals(project.getProjectManagerId(), userId)) {
            throw new ServiceException(ErrorCodeConstants.PROJECT_MANAGER_REQUIRED);
        }
    }

}