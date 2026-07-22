import request from '@/config/axios'

export interface MaterialTrackVO {
  trackId: string | number
  projectId?: number
  taskId?: number
  materialCode?: string
  materialName?: string
  supplier?: string
  quantity?: number
  unit?: string
  planOrderDate?: string
  latestOrderDate?: string
  actualOrderDate?: string
  planDeliveryDate?: string
  actualDeliveryDate?: string
  deliveryDeviation?: number
  warningStatus?: string
  responsibleId?: number
  currentStatus?: string
  createTime?: string
}

export const createMaterialTrack = (data: MaterialTrackVO) => {
  return request.post({ url: '/pms/material-track/create', data })
}

export const updateMaterialTrack = (data: MaterialTrackVO) => {
  return request.put({ url: '/pms/material-track/update', data })
}

export const deleteMaterialTrack = (id: number) => {
  return request.delete({ url: '/pms/material-track/delete?id=' + id })
}

export const getMaterialTrack = (id: number) => {
  return request.get({ url: '/pms/material-track/get?id=' + id })
}

export const getMaterialTrackList = () => {
  return request.get({ url: '/pms/material-track/list' })
}
