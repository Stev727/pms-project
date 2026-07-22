import request from '@/config/axios'

export interface TaskVO {
  taskId: string
  projectId: string
  stageId?: string
  parentTaskId?: number
  taskCode?: string
  taskName: string
  description?: string
  taskType?: string
  cycle?: number
  priority?: string
  planStartDate?: string
  planEndDate?: string
  actualCompleteDate?: string
  outputRequirement?: string
  completionStandard?: string
  mainOwnerId?: number
  helperIds?: string
  deptId?: number
  isCriticalPath?: boolean
  isMilestone?: boolean
  completeStatus?: string
  isDispatched?: boolean
  estimatedHours?: number
  actualHours?: number
  sortOrder?: number
  progress?: number
  createTime?: string
}

export const createTask = (data: TaskVO) => {
  return request.post({ url: '/pms/task/create', data })
}

export const updateTask = (data: TaskVO) => {
  return request.put({ url: '/pms/task/update', data })
}

export const deleteTask = (id: string) => {
  return request.delete({ url: '/pms/task/delete?id=' + id })
}

export const getTask = (id: string) => {
  return request.get({ url: '/pms/task/get?id=' + id })
}

export const getTaskList = () => {
  return request.get({ url: '/pms/task/list' })
}
