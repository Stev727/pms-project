package cn.iocoder.yudao.module.pms.service.projectmember;

import cn.iocoder.yudao.module.pms.dal.dataobject.projectmember.PmsProjectMemberDO;
import java.util.List;

/**
 * 项目成员 Service 接口
 */
public interface ProjectMemberService {

    Long createProjectMember(PmsProjectMemberDO entity);

    void updateProjectMember(PmsProjectMemberDO entity);

    void deleteProjectMember(Long id);

    PmsProjectMemberDO getProjectMember(Long id);

    List<PmsProjectMemberDO> getProjectMemberList();

}