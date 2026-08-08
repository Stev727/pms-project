package cn.iocoder.yudao.module.pms.dal.mysql.message;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.dal.dataobject.message.PmsMessageDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * PMS 站内消息 Mapper（#4 站内消息中心）
 *
 * <p>类名不带 Pms 前缀，遵循新代码统一约定。
 */
@Mapper
public interface MessageMapper extends BaseMapperX<PmsMessageDO> {

    /**
     * 查询某用户的消息列表（按 readStatus 过滤，按时间倒序）。
     *
     * @param receiverId 接收人ID，必填
     * @param readStatus 0 未读 / 1 已读 / null 全部
     */
    default List<PmsMessageDO> selectListByReceiver(Long receiverId, Integer readStatus) {
        return selectList(new LambdaQueryWrapperX<PmsMessageDO>()
                .eqIfPresent(PmsMessageDO::getReceiverId, receiverId)
                .eqIfPresent(PmsMessageDO::getReadStatus, readStatus)
                .orderByDesc(PmsMessageDO::getCreateTime));
    }

    /**
     * 统计某用户未读消息数。
     */
    default Long countUnread(Long receiverId) {
        if (receiverId == null) {
            return 0L;
        }
        return selectCount(new LambdaQueryWrapperX<PmsMessageDO>()
                .eq(PmsMessageDO::getReceiverId, receiverId)
                .eq(PmsMessageDO::getReadStatus, 0));
    }

    /**
     * 查询某用户全部未读消息ID列表（用于一键已读）。
     */
    default List<PmsMessageDO> selectUnreadList(Long receiverId) {
        return selectList(new LambdaQueryWrapperX<PmsMessageDO>()
                .eq(PmsMessageDO::getReceiverId, receiverId)
                .eq(PmsMessageDO::getReadStatus, 0));
    }

}

