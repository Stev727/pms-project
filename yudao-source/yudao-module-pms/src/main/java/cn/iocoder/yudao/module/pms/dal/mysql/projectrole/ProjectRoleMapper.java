package cn.iocoder.yudao.module.pms.dal.mysql.projectrole;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.dal.dataobject.projectrole.PmsProjectRoleDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ProjectRoleMapper extends BaseMapperX<PmsProjectRoleDO> {

    /**
     * 查询某项目下全部角色，按 sortOrder 升序
     */
    default List<PmsProjectRoleDO> selectListByProjectId(Long projectId) {
        return selectList(new LambdaQueryWrapperX<PmsProjectRoleDO>()
                .eq(PmsProjectRoleDO::getProjectId, projectId)
                .orderByAsc(PmsProjectRoleDO::getSortOrder)
                .orderByAsc(PmsProjectRoleDO::getRoleId));
    }

    /**
     * 按项目 + 角色编码精确查询
     */
    default PmsProjectRoleDO selectByProjectIdAndRoleCode(Long projectId, String roleCode) {
        return selectOne(new LambdaQueryWrapperX<PmsProjectRoleDO>()
                .eq(PmsProjectRoleDO::getProjectId, projectId)
                .eq(PmsProjectRoleDO::getRoleCode, roleCode));
    }

    /**
     * 按项目 + 角色编码集合批量查询（用于把成员的 roleCode 换成 roleId）
     */
    default List<PmsProjectRoleDO> selectListByProjectIdAndRoleCodes(Long projectId, Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapperX<PmsProjectRoleDO>()
                .eq(PmsProjectRoleDO::getProjectId, projectId)
                .in(PmsProjectRoleDO::getRoleCode, roleCodes));
    }

    /**
     * 删除某项目下全部角色（项目删除时清理）
     */
    default void deleteByProjectId(Long projectId) {
        delete(new LambdaQueryWrapperX<PmsProjectRoleDO>()
                .eq(PmsProjectRoleDO::getProjectId, projectId));
    }

}

