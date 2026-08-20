package cn.iocoder.yudao.module.pms.dal.mysql.projectpermission;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.dal.dataobject.projectpermission.PmsProjectPermissionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Mapper
public interface ProjectPermissionMapper extends BaseMapperX<PmsProjectPermissionDO> {

    /**
     * 查询某项目的完整权限矩阵
     */
    default List<PmsProjectPermissionDO> selectListByProjectId(Long projectId) {
        return selectList(new LambdaQueryWrapperX<PmsProjectPermissionDO>()
                .eq(PmsProjectPermissionDO::getProjectId, projectId));
    }

    /**
     * 查询某项目下指定角色集合的全部允许记录
     */
    default List<PmsProjectPermissionDO> selectAllowedByRoleIds(Long projectId, Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapperX<PmsProjectPermissionDO>()
                .eq(PmsProjectPermissionDO::getProjectId, projectId)
                .in(PmsProjectPermissionDO::getRoleId, roleIds)
                .eq(PmsProjectPermissionDO::getAllowed, true));
    }

    /**
     * 判断某项目下指定角色集合是否有某权限点的允许记录
     */
    default boolean existsAllowed(Long projectId, Collection<Long> roleIds, String permKey) {
        if (roleIds == null || roleIds.isEmpty()) {
            return false;
        }
        // 显式过滤 deleted=0：DO 虽无 @TableLogic，但历史数据/迁移可能留下 deleted=1 行
        // （例如曾启用软删除后再关闭）。不显式过滤会让"已撤销"的权限通过旧行继续放行。
        return selectCount(new LambdaQueryWrapperX<PmsProjectPermissionDO>()
                .eq(PmsProjectPermissionDO::getProjectId, projectId)
                .in(PmsProjectPermissionDO::getRoleId, roleIds)
                .eq(PmsProjectPermissionDO::getPermKey, permKey)
                .eq(PmsProjectPermissionDO::getAllowed, true)
                .eq(PmsProjectPermissionDO::getDeleted, false)) > 0;
    }

    /**
     * 删除某项目的全部权限记录（整体覆盖保存前调用）
     */
    default void deleteByProjectId(Long projectId) {
        delete(new LambdaQueryWrapperX<PmsProjectPermissionDO>()
                .eq(PmsProjectPermissionDO::getProjectId, projectId));
    }

    /**
     * 删除某角色的全部权限记录（删除角色时调用）
     */
    default void deleteByRoleId(Long projectId, Long roleId) {
        delete(new LambdaQueryWrapperX<PmsProjectPermissionDO>()
                .eq(PmsProjectPermissionDO::getProjectId, projectId)
                .eq(PmsProjectPermissionDO::getRoleId, roleId));
    }

}

