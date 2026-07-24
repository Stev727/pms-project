import request from '@/config/axios'

export interface TaskVO {
  taskId: string
  projectId: string
  stageId?: string
  parentTaskId?: string
  predecessorTaskIds?: string  // 前置任务ID列表(逗号分隔)
  taskCode?: string
  taskName: string
  description?: string
  taskType?: string
  cycle?: number
  priority?: string
  planStartDate?: string
  planEndDate?: string
  firstDelayedPlanEndDate?: string  // 一次延迟计划结束日期
  secondDelayedPlanEndDate?: string // 二次延迟计划结束日期
  actualCompleteDate?: string
  outputRequirement?: string
  completionStandard?: string
  mainOwnerId?: number
  helperIds?: string
  deptId?: number
  helperDeptIds?: string  // 协助部门ID列表(逗号分隔)
  isCriticalPath?: boolean
  isMilestone?: boolean
  completeStatus?: string
  isDispatched?: boolean
  exceptionReason?: string  // 异常原因说明
  improvementPlan?: string  // 改善方案
  estimatedHours?: number
  actualHours?: number
  sortOrder?: number
  progress?: number
  dingtalkReminderEnabled?: boolean  // 延迟钉钉提醒开关
  approvalIds?: string  // 关联审批单号
  createTime?: string
}

export const createTask = (data: TaskVO) => {
  return request.post({ url: '/pms/task/create', data })
}

export const updateTask = (data: TaskVO) => {
  return request.put({ url: '/pms/task/update', data })
}

export const dispatchTask = (taskId: string | number) => {
  return request.post({ url: '/pms/task/dispatch', params: { taskId } })
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

export const simulateDingtalkConfirm = (taskId: string | number) => {
  return request.post({ url: '/pms/task/simulate-dingtalk-confirm', params: { taskId } })
}

export const submitTaskCompletion = (taskId: string | number) => {
  return request.post({ url: '/pms/task/completion/submit', params: { taskId } })
}

export const reviewTaskCompletion = (taskId: string | number, approved: boolean) => {
  return request.put({ url: '/pms/task/completion/review', params: { taskId, approved } })
}
