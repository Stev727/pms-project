package cn.iocoder.yudao.module.pms.dal.mysql.dingtalk;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.dal.dataobject.dingtalk.PmsDingTalkTodoDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * PMS 钉钉待办映射 Mapper（#4 钉钉待办）
 *
 * <p>类名按 dingtalk 包既有约定带 Pms 前缀（与 {@code PmsDingTalkUserMapper} 一致）。
 */
@Mapper
public interface PmsDingTalkTodoMapper extends BaseMapperX<PmsDingTalkTodoDO> {

    /**
     * 按业务任务ID查全部待办映射（含失败/已完成）。
     * 用于"任务完成时把对应待办标记完成"。
     */
    default List<PmsDingTalkTodoDO> selectListByBizTaskId(Long bizTaskId) {
        return selectList(new LambdaQueryWrapperX<PmsDingTalkTodoDO>()
                .eq(PmsDingTalkTodoDO::getBizTaskId, bizTaskId));
    }

    /**
     * 按业务任务ID + 状态查询。用于只完成"已创建成功(pending)"的待办。
     */
    default List<PmsDingTalkTodoDO> selectListByBizTaskIdAndStatus(Long bizTaskId, String status) {
        return selectList(new LambdaQueryWrapperX<PmsDingTalkTodoDO>()
                .eq(PmsDingTalkTodoDO::getBizTaskId, bizTaskId)
                .eq(PmsDingTalkTodoDO::getStatus, status));
    }

    /**
     * 按系统用户ID + 业务任务ID查单条。用于幂等性校验（避免同一任务对同一人重复创建待办）。
     */
    default PmsDingTalkTodoDO selectByBizTaskIdAndUserId(Long bizTaskId, Long userId) {
        return selectOne(new LambdaQueryWrapperX<PmsDingTalkTodoDO>()
                .eq(PmsDingTalkTodoDO::getBizTaskId, bizTaskId)
                .eq(PmsDingTalkTodoDO::getUserId, userId));
    }

}

