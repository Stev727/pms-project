import request from '@/config/axios'

// 超期任务分页查询
export const getOverdueTaskPage = (params: {
  deptId?: number
  userId?: number
  projectId?: number
  pageNo?: number
  pageSize?: number
}) => {
  return request.get({ url: '/pms/overdue-remind/page', params })
}

// 获取超期提请监控人/通知人列表
export const getOverdueReceivers = () => {
  return request.get({ url: '/pms/overdue-remind/receivers' })
}

// 保存超期提请监控人/通知人列表
export const updateOverdueReceivers = (userIds: number[]) => {
  return request.put({ url: '/pms/overdue-remind/receivers', data: userIds })
}
