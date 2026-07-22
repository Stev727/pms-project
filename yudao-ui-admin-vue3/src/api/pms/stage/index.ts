import request from '@/config/axios'

export interface StageVO {
  stageId: string | number
  projectId: string | number
  stageName: string
  stageCode?: string
  sortOrder?: number
  status?: string
  planStartDate?: string
  planEndDate?: string
  isMilestone?: boolean
  progress?: number
  createTime?: string
}

export const createStage = (data: StageVO) => {
  return request.post({ url: '/pms/project-stage/create', data })
}

export const updateStage = (data: StageVO) => {
  return request.put({ url: '/pms/project-stage/update', data })
}

export const deleteStage = (id: number) => {
  return request.delete({ url: '/pms/project-stage/delete?id=' + id })
}

export const getStage = (id: number) => {
  return request.get({ url: '/pms/project-stage/get?id=' + id })
}

export const getStageList = () => {
  return request.get({ url: '/pms/project-stage/list' })
}
