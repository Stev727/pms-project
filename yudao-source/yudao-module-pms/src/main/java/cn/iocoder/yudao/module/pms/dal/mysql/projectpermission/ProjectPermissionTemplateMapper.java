package cn.iocoder.yudao.module.pms.dal.mysql.projectpermission;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.dal.dataobject.projectpermission.PmsProjectPermissionTemplateDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProjectPermissionTemplateMapper extends BaseMapperX<PmsProjectPermissionTemplateDO> {

    /**
     * 查询全部默认权限模板，按 sortOrder 升序
     */
    default List<PmsProjectPermissionTemplateDO> selectAllOrdered() {
        return selectList(new LambdaQueryWrapperX<PmsProjectPermissionTemplateDO>()
                .orderByAsc(PmsProjectPermissionTemplateDO::getSortOrder)
                .orderByAsc(PmsProjectPermissionTemplateDO::getTemplateId));
    }

}

