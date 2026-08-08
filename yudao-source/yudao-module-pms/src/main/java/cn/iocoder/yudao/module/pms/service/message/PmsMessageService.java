package cn.iocoder.yudao.module.pms.service.message;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.dal.dataobject.message.PmsMessageDO;

import java.util.List;

/**
 * PMS 站内消息 Service（#4 站内消息中心）
 *
 * <p>负责：
 * <ul>
 *   <li>给业务事件（任务派发/审核/逾期）落站内消息</li>
 *   <li>提供前端铃铛组件调用的未读数查询、列表分页、标记已读、全部已读</li>
 * </ul>
 */
public interface PmsMessageService {

    /**
     * 发送站内消息（落库）。
     * 不会抛业务异常，仅记日志，避免阻塞调用方主流程。
     *
     * @param receiverId    接收人系统用户ID
     * @param title         消息标题
     * @param content       消息内容
     * @param bizType       业务类型（task / project / change / quality）
     * @param bizId         业务ID（如 taskId）
     * @param triggerEvent  触发事件（与 PmsNotifyRule.triggerEvent 对齐）
     * @return 消息ID，失败返回 null
     */
    Long sendMessage(Long receiverId, String title, String content, String bizType, Long bizId, String triggerEvent);

    /**
     * 简化重载：不区分 triggerEvent 的场景。
     */
    Long sendMessage(Long receiverId, String title, String content, String bizType, Long bizId);

    /**
     * 当前登录用户消息列表分页查询。
     *
     * @param readStatus 0 未读 / 1 已读 / null 全部
     * @param pageParam  分页参数
     */
    PageResult<PmsMessageDO> listMyMessages(Integer readStatus, PageParam pageParam);

    /**
     * 当前登录用户的未读消息列表（不分页，最多 50 条，给铃铛下拉用）。
     */
    List<PmsMessageDO> listMyUnread();

    /**
     * 标记指定消息为已读。仅能标记自己接收的消息。
     *
     * @param messageIds 消息ID列表
     * @return 实际更新条数
     */
    int markRead(List<Long> messageIds);

    /**
     * 当前登录用户全部消息标记已读。
     *
     * @return 实际更新条数
     */
    int markAllRead();

    /**
     * 当前登录用户未读消息数（铃铛红点用）。
     */
    long countUnread();

}

