package cn.iocoder.yudao.module.pms.dal.mysql.dingtalk;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.dal.dataobject.dingtalk.PmsDingTalkDeptDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PmsDingTalkDeptMapper extends BaseMapperX<PmsDingTalkDeptDO> {

    default PmsDingTalkDeptDO selectByDingTalkDeptId(Long dingtalkDeptId) {
        return selectOne(PmsDingTalkDeptDO::getDingtalkDeptId, dingtalkDeptId);
    }
}
