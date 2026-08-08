import request from '@/config/axios'

/**
 * PMS 站内消息 API（#4 站内消息中心）
 *
 * 后端：cn.iocoder.yudao.module.pms.controller.admin.message.MessageController
 * 路径前缀：/pms/message
 */

export interface PmsMessageVO {
  messageId: string // 雪花ID一律 string
  receiverId: number
  title: string
  content: string
  bizType?: string
  bizId?: string // 雪花ID一律 string
  triggerEvent?: string
  readStatus: number // 0 未读 / 1 已读
  readTime?: string
  createTime?: string
}

/** 未读消息数（铃铛红点） */
export interface PmsUnreadCountVO {
  count: number
}

/** 获取当前用户未读消息数（铃铛轮询用，30s 一次） */
export const getUnreadCount = (): Promise<PmsUnreadCountVO> => {
  return request.get({ url: '/pms/message/unread-count' })
}

/** 获取当前用户未读消息列表（铃铛下拉用，最多 50 条） */
export const getUnreadList = (): Promise<PmsMessageVO[]> => {
  return request.get({ url: '/pms/message/unread-list' })
}

/** 分页查询当前用户消息 */
export const getMessagePage = (params: {
  readStatus?: number
  pageNo?: number
  pageSize?: number
}): Promise<{ list: PmsMessageVO[]; total: number }> => {
  return request.get({ url: '/pms/message/page', params })
}

/** 批量标记已读 */
export const markRead = (messageIds: (string | number)[]): Promise<number> => {
  return request.post({ url: '/pms/message/mark-read', data: messageIds })
}

/** 全部标记已读 */
export const markAllRead = (): Promise<number> => {
  return request.post({ url: '/pms/message/mark-all-read' })
}

