import request from '@/config/axios'

export interface ApprovalRecordVO {
  approvalId: string
  projectId?: string
  taskId?: number
  changeId?: number
  approvalType?: string
  approvalNo?: string
  approvalStatus?: string
  initiatorId?: number
  initiateTime?: string
  completeTime?: string
  approvalOpinion?: string
  approverId?: number
  approverName?: string
  oaCallbackStatus?: string
  oaCallbackTime?: string
  createTime?: string
}

export const createApprovalRecord = (data: ApprovalRecordVO) => {
  return request.post({ url: '/pms/approval-record/create', data })
}

export const updateApprovalRecord = (data: ApprovalRecordVO) => {
  return request.put({ url: '/pms/approval-record/update', data })
}

export const deleteApprovalRecord = (id: number) => {
  return request.delete({ url: '/pms/approval-record/delete?id=' + id })
}

export const getApprovalRecord = (id: number) => {
  return request.get({ url: '/pms/approval-record/get?id=' + id })
}

export const getApprovalRecordList = () => {
  return request.get({ url: '/pms/approval-record/list' })
}
