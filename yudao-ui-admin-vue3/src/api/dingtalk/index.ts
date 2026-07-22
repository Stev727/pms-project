import request from '@/config/axios'

// 获取钉钉配置
export const getDingTalkConfig = () => {
  return request.get({ url: '/pms/dingtalk/config' })
}

// 更新钉钉配置
export const updateDingTalkConfig = (data: any) => {
  return request.put({ url: '/pms/dingtalk/config', data })
}

// 测试钉钉连接
export const testDingTalkConnection = () => {
  return request.get({ url: '/pms/dingtalk/test' })
}

// 手动触发通知检查
export const triggerNotifyCheck = () => {
  return request.post({ url: '/pms/dingtalk/notify/check' })
}

// 发送测试通知
export const sendTestNotify = (params: { title: string; content: string; userId: number }) => {
  return request.post({ url: '/pms/dingtalk/notify/test', params })
}

// 全量同步组织架构
export const syncAll = () => {
  return request.post({ url: '/pms/dingtalk/sync/all' })
}

// 同步部门
export const syncDepartments = () => {
  return request.post({ url: '/pms/dingtalk/sync/depts' })
}

// 同步用户
export const syncUsers = () => {
  return request.post({ url: '/pms/dingtalk/sync/users' })
}

// 获取同步状态
export const getSyncStatus = () => {
  return request.get({ url: '/pms/dingtalk/sync/status' })
}
