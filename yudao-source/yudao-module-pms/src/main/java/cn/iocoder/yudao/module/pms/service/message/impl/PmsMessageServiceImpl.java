package cn.iocoder.yudao.module.pms.service.message.impl;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.dal.dataobject.message.PmsMessageDO;
import cn.iocoder.yudao.module.pms.dal.mysql.message.MessageMapper;
import cn.iocoder.yudao.module.pms.service.message.PmsMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * PMS 站内消息 Service 实现（#4 站内消息中心）
 *
 * <p>设计要点：
 * <ul>
 *   <li>{@link #sendMessage} 内部不抛异常，落库失败仅记日志，避免阻塞业务主流程</li>
 *   <li>{@link #markRead} / {@link #markAllRead} 校验 receiverId = 当前登录用户，防止越权</li>
 *   <li>未读数接口给前端铃铛轮询用，每 30s 调一次</li>
 * </ul>
 */
@Service
@Slf4j
public class PmsMessageServiceImpl implements PmsMessageService {

    /** 铃铛下拉未读消息最大条数 */
    private static final int UNREAD_LIST_LIMIT = 50;

    /** 未读状态 */
    private static final int READ_STATUS_UNREAD = 0;
    /** 已读状态 */
    private static final int READ_STATUS_READ = 1;

    @Resource
    private MessageMapper messageMapper;

    // ==================================================================
    // 发送
    // ==================================================================

    @Override
    public Long sendMessage(Long receiverId, String title, String content, String bizType, Long bizId, String triggerEvent) {
        if (receiverId == null) {
            log.warn("[PmsMessage] sendMessage 接收人为空，跳过: bizType={}, bizId={}", bizType, bizId);
            return null;
        }
        try {
            PmsMessageDO message = new PmsMessageDO();
            message.setReceiverId(receiverId);
            message.setTitle(title);
            message.setContent(content);
            message.setBizType(bizType);
            message.setBizId(bizId);
            message.setTriggerEvent(triggerEvent);
            message.setReadStatus(READ_STATUS_UNREAD);
            messageMapper.insert(message);
            return message.getMessageId();
        } catch (Exception e) {
            log.error("[PmsMessage] sendMessage 落库失败: receiverId={}, bizType={}, bizId={}",
                    receiverId, bizType, bizId, e);
            return null;
        }
    }

    @Override
    public Long sendMessage(Long receiverId, String title, String content, String bizType, Long bizId) {
        return sendMessage(receiverId, title, content, bizType, bizId, null);
    }

    // ==================================================================
    // 查询
    // ==================================================================

    @Override
    public PageResult<PmsMessageDO> listMyMessages(Integer readStatus, PageParam pageParam) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            return new PageResult<>(Collections.emptyList(), 0L);
        }
        LambdaQueryWrapperX<PmsMessageDO> wrapper = new LambdaQueryWrapperX<PmsMessageDO>()
                .eq(PmsMessageDO::getReceiverId, userId)
                .eqIfPresent(PmsMessageDO::getReadStatus, readStatus)
                .orderByDesc(PmsMessageDO::getCreateTime);
        return messageMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public List<PmsMessageDO> listMyUnread() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            return Collections.emptyList();
        }
        List<PmsMessageDO> list = messageMapper.selectListByReceiver(userId, READ_STATUS_UNREAD);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        if (list.size() > UNREAD_LIST_LIMIT) {
            return list.subList(0, UNREAD_LIST_LIMIT);
        }
        return list;
    }

    @Override
    public long countUnread() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            return 0L;
        }
        Long count = messageMapper.countUnread(userId);
        return count == null ? 0L : count;
    }

    // ==================================================================
    // 已读
    // ==================================================================

    @Override
    public int markRead(List<Long> messageIds) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null || messageIds == null || messageIds.isEmpty()) {
            return 0;
        }
        // 校验：只能标记自己接收的消息
        List<PmsMessageDO> messages = messageMapper.selectBatchIds(messageIds);
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        List<Long> validIds = new ArrayList<>();
        for (PmsMessageDO m : messages) {
            if (Objects.equals(m.getReceiverId(), userId) && READ_STATUS_UNREAD == (m.getReadStatus() == null ? 0 : m.getReadStatus())) {
                validIds.add(m.getMessageId());
            }
        }
        if (validIds.isEmpty()) {
            return 0;
        }
        int updated = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Long id : validIds) {
            try {
                PmsMessageDO update = new PmsMessageDO();
                update.setMessageId(id);
                update.setReadStatus(READ_STATUS_READ);
                update.setReadTime(now);
                messageMapper.updateById(update);
                updated++;
            } catch (Exception e) {
                log.error("[PmsMessage] markRead 单条更新失败: messageId={}", id, e);
            }
        }
        return updated;
    }

    @Override
    public int markAllRead() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            return 0;
        }
        List<PmsMessageDO> unread = messageMapper.selectUnreadList(userId);
        if (unread == null || unread.isEmpty()) {
            return 0;
        }
        List<Long> ids = new ArrayList<>();
        for (PmsMessageDO m : unread) {
            ids.add(m.getMessageId());
        }
        return markRead(ids);
    }

}

