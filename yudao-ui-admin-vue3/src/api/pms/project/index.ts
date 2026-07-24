import request from '@/config/axios'

export interface ProjectVO {
  projectId: string
  projectCode: string
  projectName: string
  projectType?: string
  status: string
  currentStage?: string
  priority?: string
  progress?: number
  planStartDate?: string
  planEndDate?: string
  actualStartDate?: string
  actualEndDate?: string
  projectManagerId?: number
  deptId?: number
  createMethod?: string
  templateId?: number
  budget?: number
  description?: string
  attachmentPath?: string
  isKeyProject?: boolean
  archived?: boolean
  createTime?: string
}

export const createProject = (data: ProjectVO) => {
  return request.post({ url: '/pms/project/create', data })
}

export interface ProjectCreateBundleVO {
  project: Partial<ProjectVO>
  members: any[]
  tasks: any[]
  notifyRules?: any[]
  notifyModeId?: string | number
}

export const createProjectBundle = (data: ProjectCreateBundleVO) => {
  return request.post({ url: '/pms/project/create-bundle', data })
}

export const updateProject = (data: ProjectVO) => {
  return request.put({ url: '/pms/project/update', data })
}

export const deleteProject = (id: string) => {
  return request.delete({ url: '/pms/project/delete?id=' + id })
}

export const getProject = (id: string) => {
  return request.get({ url: '/pms/project/get?id=' + id })
}

export const getProjectList = () => {
  return request.get({ url: '/pms/project/list' })
}
