import request from '@/config/axios'

export interface NotifyRuleVO {
  ruleId: string | number
  ruleName?: string
  triggerEvent?: string
  triggerCondition?: string
  notifyTarget?: string
  notifyChannel?: string
  timeRule?: string
  templateTitle?: string
  templateContent?: string
  status?: string
  doNotDisturb?: boolean
  dndStartTime?: string
  dndEndTime?: string
  escalationFlag?: boolean
  escalationCondition?: string
  escalationTarget?: string
  escalationChannel?: string
  escalationTemplate?: string
  createTime?: string
}

export const createNotifyRule = (data: NotifyRuleVO) => {
  return request.post({ url: '/pms/notify-rule/create', data })
}

export const updateNotifyRule = (data: NotifyRuleVO) => {
  return request.put({ url: '/pms/notify-rule/update', data })
}

export const deleteNotifyRule = (id: number) => {
  return request.delete({ url: '/pms/notify-rule/delete?id=' + id })
}

export const getNotifyRule = (id: number) => {
  return request.get({ url: '/pms/notify-rule/get?id=' + id })
}

export const getNotifyRuleList = () => {
  return request.get({ url: '/pms/notify-rule/list' })
}

export interface NotifyModeVO {
  modeId?: string | number
  modeName: string
  description?: string
  status?: string
  defaultFlag?: boolean
}
export const getNotifyModeList = () => request.get({ url: '/pms/notify-mode/list' })
export const getNotifyMode = (id: string | number) => request.get({ url: '/pms/notify-mode/get?id=' + id })
export const createNotifyMode = (data: { mode: NotifyModeVO; rules: NotifyRuleVO[] }) => request.post({ url: '/pms/notify-mode/create', data })
export const updateNotifyMode = (data: { mode: NotifyModeVO; rules: NotifyRuleVO[] }) => request.put({ url: '/pms/notify-mode/update', data })
export const deleteNotifyMode = (id: string | number) => request.delete({ url: '/pms/notify-mode/delete?id=' + id })
