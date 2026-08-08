import request from '@/config/axios'
import { getAccessToken } from '@/utils/auth'

/**
 * 【#6/#7 改造】文档 API
 * - 追加 visibility/allowedRoleIds/allowDownload 字段（#7）
 * - 新增 preview/previewFile/download/listByProject 接口（#6/#7）
 *
 * 雪花ID约定：documentId/projectId/taskId 一律 string；uploadBy 是系统用户ID用 number。
 */

export interface DocumentVO {
  documentId: string
  projectId?: string
  taskId?: string
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
  // ========== #7 新增 ==========
  /** 可见范围：public | role | private */
  visibility?: string
  /** 允许查看的角色ID列表（JSON 数组字符串，如 "[101,102]"），visibility=role 时生效 */
  allowedRoleIds?: string
  /** 是否允许下载 */
  allowDownload?: boolean
}

/** 预览结果 */
export interface PreviewResultVO {
  /** 预览类型：pdf | image | text | unsupported */
  previewType: string
  /** 预览文件 URL（pdf/image 有效），指向 /admin-api/pms/document/preview-file?docId=xxx */
  previewFileUrl?: string
  /** 文本内容（仅 previewType=text） */
  textContent?: string
  fileName?: string
  fileSize?: number
  fileType?: string
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

/** 全量列表（管理用，原接口保留） */
export const getDocumentList = () => {
  return request.get({ url: '/pms/document/list' })
}

/**
 * 【#7 新增】按项目获取文档列表（后端按用户权限过滤）
 * @param projectId 项目ID（string，雪花ID）
 */
export const getDocumentListByProject = (projectId: string | number) => {
  return request.get({ url: '/pms/document/list-by-project?projectId=' + projectId })
}

/**
 * 【#6 新增】获取文档预览信息
 */
export const previewDocument = (docId: string | number) => {
  return request.get({ url: '/pms/document/preview?docId=' + docId })
}

/**
 * 【#6 新增】获取预览文件 Blob（PDF/图片）。
 * 用 axios 带 token 请求，前端 createObjectURL 给 iframe/img。
 */
export const fetchPreviewFileBlob = async (docId: string | number): Promise<Blob> => {
  const resp = await request.get({
    url: '/pms/document/preview-file?docId=' + docId,
    responseType: 'blob'
  })
  // request.get 在 responseType='blob' 时直接返回 Blob
  return resp as unknown as Blob
}

/**
 * 【#7 新增】下载文档（触发浏览器下载）。
 * 用隐藏 a 标签 + token fetch，避免 token 丢失。
 */
export const downloadDocument = async (docId: string | number, fileName?: string) => {
  const resp = await request.get({
    url: '/pms/document/download?docId=' + docId,
    responseType: 'blob'
  })
  const blob = resp as unknown as Blob
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName || `document_${docId}`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  window.URL.revokeObjectURL(url)
}

/** 可见范围选项 */
export const VISIBILITY_OPTIONS = [
  { value: 'public', label: '项目全员可见' },
  { value: 'role', label: '指定角色可见' },
  { value: 'private', label: '仅上传人和项目经理' }
]

/** 可见范围中文标签 */
export const getVisibilityLabel = (v?: string): string => {
  return VISIBILITY_OPTIONS.find((o) => o.value === v)?.label || v || '-'
}

