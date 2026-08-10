import request from '@/config/axios'

/**
 * 任务相关 API
 *
 * ============================ 改造说明 ============================
 * 版本：v2（在线上原文件基础上改造）
 * 新增字段（对应 #1 子任务层级 / #3 派发审核）：
 *   level          层级，1=顶层，最多 3 级
 *   reviewerId     审核人（子任务默认=父任务主责任人；顶层默认=项目经理）
 *   assignerId     派发人（派发动作执行人）
 *   reviewStatus   审核状态：none/submitted/completed/rejected
 *   reviewComment  驳回原因（驳回时必填）
 *   reviewPolicy   审核策略：need_review/self_review/skip（单任务覆盖，空则继承项目）
 * 新增接口：
 *   getSubTaskList / getTaskTree / updateTaskProgress（#1）
 *   submitReview / approveReview / rejectReview / getMyReviewTaskList（#3）
 * 注：雪花ID（taskId/projectId/stageId）一律按 string 处理，避免精度丢失。
 * ================================================================
 */
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
  completionNote?: string  // 完成说明
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
  // ===== #1 子任务层级 =====
  level?: number
  // ===== #3 派发审核 =====
  reviewerId?: number
  assignerId?: number
  reviewStatus?: string
  reviewComment?: string
  reviewPolicy?: string
  createTime?: string
}

// ==================== 原有接口（签名保持不变） ====================
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

export const getTaskList = (params?: { mainOwnerId?: number; projectId?: string | number; projectType?: string }) => {
  return request.get({ url: '/pms/task/list', params })
}

export const simulateDingtalkConfirm = (taskId: string | number) => {
  return request.post({ url: '/pms/task/simulate-dingtalk-confirm', params: { taskId } })
}

export const acceptTask = (taskId: string | number) => {
  return request.post({ url: '/pms/task/accept', params: { taskId } })
}

export const submitTaskCompletion = (taskId: string | number, actualCompleteDate?: string, completionNote?: string) => {
  return request.post({ url: '/pms/task/submit-completion', params: { taskId, actualCompleteDate, completionNote } })
}

export const reviewTaskCompletion = (taskId: string | number, approved: boolean, reviewOpinion?: string) => {
  return request.post({ url: '/pms/task/review-completion', params: { taskId, approved, reviewOpinion } })
}

// ==================== #1 子任务层级（新增） ====================
/** 获取直接子任务列表 */
export const getSubTaskList = (parentTaskId: string | number) => {
  return request.get({ url: '/pms/task/children?parentTaskId=' + parentTaskId })
}

/** 获取项目全部任务（含层级字段），前端自行组装成树 */
export const getTaskTree = (projectId: string | number) => {
  return request.get({ url: '/pms/task/tree?projectId=' + projectId })
}

/** 进度填报（自动向上汇总父任务进度） */
export const updateTaskProgress = (taskId: string | number, progress: number) => {
  return request.put({ url: '/pms/task/progress', params: { taskId, progress } })
}

// ==================== #3 派发审核（新增） ====================
/** 提交审核：in_progress -> submitted */
export const submitReview = (taskId: string | number, comment?: string) => {
  return request.post({ url: '/pms/task/submit-review', params: { taskId, reviewComment: comment } })
}

/** 审核通过：submitted -> completed */
export const approveReview = (taskId: string | number, comment?: string) => {
  return request.post({ url: '/pms/task/approve-review', params: { taskId, reviewComment: comment } })
}

/** 审核驳回：submitted -> rejected（原因必填） */
export const rejectReview = (taskId: string | number, comment: string) => {
  return request.post({ url: '/pms/task/reject-review', params: { taskId, reviewComment: comment } })
}

/** 待我审核的任务列表（审核中心联动） */
export const getMyReviewTaskList = (projectId?: string | number, reviewStatus?: string) => {
  return request.get({
    url: '/pms/task/my-review-list',
    params: { projectId, reviewStatus }
  })
}

// ==================== 日常任务 / 我的任务看板（新增） ====================
/** 我的任务看板聚合查询：历史遗留 + 项目任务分组 + 日常任务 */
export const getTaskBoard = (params: {
  userIds?: (string | number)[]
  dateFrom: string
  dateTo: string
  includeSubordinates?: boolean
}) => {
  return request.get({ url: '/pms/task/board', params })
}

/** 部门审核中心：待我（直属领导）审核的日常任务列表 */
export const getDeptReviewList = () => {
  return request.get({ url: '/pms/task/dept-review-list' })
}

