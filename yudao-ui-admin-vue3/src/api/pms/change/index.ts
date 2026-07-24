import request from '@/config/axios'

export interface ChangeRecordVO {
  changeId: string
  projectId?: string
  changeCode?: string
  changeType?: string
  changeReason?: string
  changeDescription?: string
  initiatorId?: number
  approvalId?: string
  approverId?: number
  approvalStatus?: string
  affectedTasks?: string
  costImpact?: number
  scheduleImpact?: number
  executeTime?: string
  createTime?: string
}

export const createChangeRecord = (data: ChangeRecordVO) => {
  return request.post({ url: '/pms/change-record/create', data })
}

export const updateChangeRecord = (data: ChangeRecordVO) => {
  return request.put({ url: '/pms/change-record/update', data })
}

export const deleteChangeRecord = (id: string | number) => {
  return request.delete({ url: '/pms/change-record/delete?id=' + id })
}

export const getChangeRecord = (id: string | number) => {
  return request.get({ url: '/pms/change-record/get?id=' + id })
}

export const getChangeRecordList = () => {
  return request.get({ url: '/pms/change-record/list' })
}

export const reviewChangeRecord = (id: string | number, approved: boolean) => {
  return request.put({ url: '/pms/change-record/review', params: { id, approved } })
}

export const executeChangeRecord = (id: string | number) => {
  return request.put({ url: '/pms/change-record/execute', params: { id } })
}
