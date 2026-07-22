package cn.iocoder.yudao.module.pms.service.projectmember.impl;

import cn.iocoder.yudao.module.pms.dal.dataobject.projectmember.PmsProjectMemberDO;
import cn.iocoder.yudao.module.pms.dal.mysql.projectmember.ProjectMemberMapper;
import cn.iocoder.yudao.module.pms.service.projectmember.ProjectMemberService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

@Service
public class ProjectMemberServiceImpl implements ProjectMemberService {

    @Resource
    private ProjectMemberMapper projectMemberMapper;

    @Override
    public Long createProjectMember(PmsProjectMemberDO entity) {
        projectMemberMapper.insert(entity);
        return entity.getMemberId();
    }

    @Override
    public void updateProjectMember(PmsProjectMemberDO entity) {
        projectMemberMapper.updateById(entity);
    }

    @Override
    public void deleteProjectMember(Long id) {
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

}