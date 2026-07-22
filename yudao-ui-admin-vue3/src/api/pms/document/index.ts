import request from '@/config/axios'

export interface DocumentVO {
  documentId: string
  projectId?: string
  taskId?: number
  fileName?: string
  fileType?: string
  category?: string
  uploadBy?: number
  uploadTime?: string
  versionNo?: string
  storagePath?: string
  fileSize?: number
  downloadCount?: number
  permissionFlag?: string
  description?: string
  tags?: string
  createTime?: string
}

export const createDocument = (data: DocumentVO) => {
  return request.post({ url: '/pms/document/create', data })
}

export const updateDocument = (data: DocumentVO) => {
  return request.put({ url: '/pms/document/update', data })
}

export const deleteDocument = (id: string | number) => {
  return request.delete({ url: '/pms/document/delete?id=' + id })
}

export const getDocument = (id: string | number) => {
  return request.get({ url: '/pms/document/get?id=' + id })
}

export const getDocumentList = () => {
  return request.get({ url: '/pms/document/list' })
}
