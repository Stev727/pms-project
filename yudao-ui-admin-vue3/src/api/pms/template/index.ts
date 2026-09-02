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


// ==================== 阶段任务 Excel 导入（全量覆盖） ====================

/** 下载阶段任务导入模板（预填当前模板已有数据，便于增量修改） */
export const getStageTaskImportTemplate = (projectId: string | number) => {
  return request.download({
    url: '/pms/template/get-stage-task-import-template',
    params: { projectId }
  })
}

/** Excel 批量导入阶段任务（全量覆盖：软删现有阶段/任务后按文件重建）
 *  返回原始 blob 响应：content-type 为 json 即成功，否则为错误行 Excel */
export const importStageTask = (projectId: string | number, file: File) => {
  const data = new FormData()
  data.append('file', file)
  return request.postOriginal({
    url: '/pms/template/import-stage-task',
    params: { projectId },
    data,
    headersType: 'multipart/form-data',
    responseType: 'blob'
  })
}
