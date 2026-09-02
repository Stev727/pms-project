package cn.iocoder.yudao.module.pms.dal.mysql.projectmember;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.dal.dataobject.projectmember.PmsProjectMemberDO;
import org.apache.ibatis.annotations.Mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    /**
     * 查询某用户在某项目的成员记录(含已逻辑删除)。
     * 唯一索引 uk_member_project_user(project_id,user_id) 不含 deleted:
     * 成员被移除(逻辑删除)后再添加, 直接 insert 会 Duplicate entry,
     * 需用本方法查到历史记录做复活。自定义 SQL 不受 MP 逻辑删除拦截。
     */
    @Select("SELECT * FROM pms_project_member WHERE project_id = #{projectId} AND user_id = #{userId} LIMIT 1")
    PmsProjectMemberDO selectOneIncludeDeleted(@Param("projectId") Long projectId, @Param("userId") Long userId);

    /**
     * 复活已逻辑删除的成员记录(自定义 UPDATE, 绕过逻辑删除拦截)
     */
    @Update("UPDATE pms_project_member SET deleted = 0, role_code = #{roleCode}, is_external = #{isExternal}, "
            + "status = #{status}, join_time = #{joinTime}, quit_time = NULL, updater = #{updater}, update_time = NOW() "
            + "WHERE member_id = #{memberId}")
    int reviveById(PmsProjectMemberDO entity);

}

