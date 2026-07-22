package cn.iocoder.yudao.module.pms.dal.mysql.dingtalk;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.dal.dataobject.dingtalk.PmsDingTalkUserDO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PmsDingTalkUserMapper extends BaseMapperX<PmsDingTalkUserDO> {

    default PmsDingTalkUserDO selectByUserId(Long userId) {
        return selectOne(PmsDingTalkUserDO::getUserId, userId);
    }

    default PmsDingTalkUserDO selectByDingTalkUserId(String dingtalkUserId) {
        return selectOne(PmsDingTalkUserDO::getDingtalkUserId, dingtalkUserId);
    }

    default List<PmsDingTalkUserDO> selectListByUserIds(List<Long> userIds) {
        return selectList(PmsDingTalkUserDO::getUserId, userIds);
    }
}
