import request from '@/config/axios'

export interface TemplateVO {
  templateId: string | number
  templateName: string
  templateType: string
  applicableStage?: string
  versionNo?: string
  fileName?: string
  storagePath?: string
  standardFlag?: boolean
  status?: string
  usageInstructions?: string
  downloadCount?: number
  uploadBy?: number
  uploadTime?: string
  createTime?: string
}

export const createTemplate = (data: TemplateVO) => {
  return request.post({ url: '/pms/template/create', data })
}

export const updateTemplate = (data: TemplateVO) => {
  return request.put({ url: '/pms/template/update', data })
}

export const deleteTemplate = (id: number) => {
  return request.delete({ url: '/pms/template/delete?id=' + id })
}

export const getTemplate = (id: number) => {
  return request.get({ url: '/pms/template/get?id=' + id })
}

export const getTemplateList = () => {
  return request.get({ url: '/pms/template/list' })
}
