package cn.iocoder.yudao.module.pms.dal.mysql.project;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.dal.dataobject.project.PmsProjectDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProjectMapper extends BaseMapperX<PmsProjectDO> {

    /**
     * 按项目编号前缀统计总数（包含已软删除记录），用于自动生成唯一编号
     */
    @Select("SELECT COUNT(*) FROM pms_project WHERE project_code LIKE CONCAT(#{prefix}, '%')")
    long countByCodePrefixIncludeDeleted(@Param("prefix") String prefix);

}
