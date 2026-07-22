package cn.iocoder.yudao.module.pms.dal.mysql.changerecord;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.dal.dataobject.changerecord.PmsChangeRecordDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChangeRecordMapper extends BaseMapperX<PmsChangeRecordDO> {

}