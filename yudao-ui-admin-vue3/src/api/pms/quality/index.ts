import request from '@/config/axios'

export interface QualityIssueVO {
  issueId: string | number
  projectId?: number
  taskId?: number
  issueCode?: string
  issueDescription?: string
  severity?: string
  impactScope?: string
  rootCauseCategory?: string
  rootCauseDetail?: string
  assigneeId?: number
  resolverId?: number
  verifierId?: number
  status?: string
  solution?: string
  verifyResult?: string
  recurFlag?: boolean
  closeTime?: string
  deptId?: number
  createTime?: string
}

export const createQualityIssue = (data: QualityIssueVO) => {
  return request.post({ url: '/pms/quality-issue/create', data })
}

export const updateQualityIssue = (data: QualityIssueVO) => {
  return request.put({ url: '/pms/quality-issue/update', data })
}

export const deleteQualityIssue = (id: number) => {
  return request.delete({ url: '/pms/quality-issue/delete?id=' + id })
}

export const getQualityIssue = (id: number) => {
  return request.get({ url: '/pms/quality-issue/get?id=' + id })
}

export const getQualityIssueList = () => {
  return request.get({ url: '/pms/quality-issue/list' })
}
