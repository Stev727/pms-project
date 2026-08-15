import request from '@/config/axios'

/**
 * 物料跟踪 VO
 *
 * 改造说明（#10）：
 *  - trackId / projectId / taskId 为 19 位雪花 ID，前端一律按 string 处理，禁止 Number() 转换
 *    （参考 _CODE_STYLE_CONTRACT.md §2.2）
 *  - responsibleId 是 4-5 位系统用户 ID，前端展示时按 Number 处理（与既有约定一致）
 */
export interface MaterialTrackVO {
  trackId: string
  projectId?: string
  taskId?: string
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

export const deleteMaterialTrack = (id: string | number) => {
  return request.delete({ url: '/pms/material-track/delete?id=' + id })
}

export const getMaterialTrack = (id: string | number) => {
  return request.get({ url: '/pms/material-track/get?id=' + id })
}

/**
 * 获取物料跟踪列表
 *  - projectId 非空：项目详情页物料 Tab 走此路径（按项目过滤）
 *  - projectId 为空：PMO 全局菜单 /pms/material 走此路径（全量）
 *  雪花 ID 一律按 string 传递，避免精度丢失
 */
export const getMaterialTrackList = (projectId?: string | number) => {
  const url = projectId
    ? '/pms/material-track/list?projectId=' + projectId
    : '/pms/material-track/list'
  return request.get({ url })
}

/**
 * 下载物料跟踪导入模板
 * @param projectId 项目ID（可选，用于权限校验）
 */
export const getMaterialImportTemplate = (projectId?: string | number) => {
  const url = projectId
    ? '/pms/material-track/get-import-template?projectId=' + projectId
    : '/pms/material-track/get-import-template'
  return request.download({ url })
}

/**
 * Excel 批量导入物料跟踪
 * @param projectId 项目ID（导入归属项目）
 * @param file Excel 文件
 */
export const importMaterialTrack = (projectId: string | number, file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('projectId', String(projectId))
  return request.post({ url: '/pms/material-track/import', data: formData, headers: { 'Content-Type': 'multipart/form-data' } })
}
