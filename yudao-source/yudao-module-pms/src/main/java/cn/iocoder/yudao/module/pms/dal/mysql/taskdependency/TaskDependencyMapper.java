package cn.iocoder.yudao.module.pms.dal.mysql.taskdependency;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.dal.dataobject.taskdependency.PmsTaskDependencyDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskDependencyMapper extends BaseMapperX<PmsTaskDependencyDO> {

}