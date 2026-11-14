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

// 监控配置项：通知人 + 被监控责任人
export interface OverdueMonitorConfig {
  notifyUserId?: number
  ownerIds?: number[]
}

// 获取监控配置列表（每条 = 通知人 + 其监控的责任人）
export const getOverdueMonitorConfigs = () => {
  return request.get({ url: '/pms/overdue-remind/monitor-configs' })
}

// 保存监控配置（全量替换）
export const updateOverdueMonitorConfigs = (configs: OverdueMonitorConfig[]) => {
  return request.put({ url: '/pms/overdue-remind/monitor-configs', data: configs })
}
