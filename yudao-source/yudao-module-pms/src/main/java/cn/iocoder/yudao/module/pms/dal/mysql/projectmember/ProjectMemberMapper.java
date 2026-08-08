package cn.iocoder.yudao.module.pms.dal.mysql.projectmember;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.dal.dataobject.projectmember.PmsProjectMemberDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.stream.Collectors;

@Mapper
public interface ProjectMemberMapper extends BaseMapperX<PmsProjectMemberDO> {

    /**
     * 【#2 新增】查询某用户在某项目的全部有效成员记录
     *
     * 注意：status 为 null 的历史数据视为有效（与前端 useProjectMembers 的判定保持一致）
     */
    default List<PmsProjectMemberDO> selectListByProjectIdAndUserId(Long projectId, Long userId) {
        return selectList(new LambdaQueryWrapperX<PmsProjectMemberDO>()
                .eq(PmsProjectMemberDO::getProjectId, projectId)
                .eq(PmsProjectMemberDO::getUserId, userId));
    }

    /**
     * 【#2 新增】查询某用户在某项目的角色编码列表（已过滤失效成员）
     */
    default List<String> selectRoleCodes(Long projectId, Long userId) {
        return selectListByProjectIdAndUserId(projectId, userId).stream()
                .filter(m -> m.getStatus() == null || "active".equals(m.getStatus()))
                .map(PmsProjectMemberDO::getRoleCode)
                .filter(code -> code != null && !code.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 【#2 新增】查询某项目的全部有效成员
     */
    default List<PmsProjectMemberDO> selectActiveListByProjectId(Long projectId) {
        return selectList(new LambdaQueryWrapperX<PmsProjectMemberDO>()
                .eq(PmsProjectMemberDO::getProjectId, projectId)).stream()
                .filter(m -> m.getStatus() == null || "active".equals(m.getStatus()))
                .collect(Collectors.toList());
    }

}

