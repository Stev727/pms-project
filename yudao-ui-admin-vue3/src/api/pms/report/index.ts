import request from '@/config/axios'

/**
 * PMS 周报报表 API（独立菜单「周报报表」）
 *
 * 维度优先级：deptId > userId > 本人
 *
 * 与现有「周报看板」完全独立，不复用 weekly-report API。
 */

/** 周报报表行 VO（前端表格 + Excel 通用结构，4 分类任务行） */
export interface WeeklyReportRowVO {
  category?: string
  projectName?: string
  taskName?: string
  mainOwnerName?: string
  planStartDate?: string
  planEndDate?: string
  actualCompleteDate?: string
  completeStatus?: string
  completeStatusLabel?: string
  progress?: number
  overdueDays?: number
  taskId?: number
}

/** 周报报表分页入参（统计时间段口径，用户主动筛选） */
export interface WeeklyReportPageReq {
  startDate?: string
  endDate?: string
  deptId?: number | string
  userId?: number | string
  pageNo?: number
  pageSize?: number
}

/**
 * 周报报表分页查询（5 区块扁平化为单页表格，统计时间段口径）
 */
export const getWeeklyReportPage = (params: WeeklyReportPageReq) => {
  return request.get({ url: '/pms/report/weekly/page', params })
}

/**
 * 周报报表导出（按当前筛选条件，单 Sheet 分类列，统计时间段口径）
 */
export const exportWeeklyReportXls = (params: { startDate?: string; endDate?: string; deptId?: number | string; userId?: number | string }) => {
  return request.download({ url: '/pms/report/weekly/export', params })
}

/** 部门简单结构（与后端 DeptRespDTO 对齐） */
export interface WeeklyReportDept {
  id: number
  name: string
  parentId?: number
  status?: number
}

/** 可见部门 + 登录人默认部门 */
export interface WeeklyReportDeptResp {
  depts: WeeklyReportDept[]
  myDeptId?: number | null
}

/**
 * 获取当前用户可见部门列表 + 登录人所在部门（默认选中用）
 */
export const getWeeklyReportDepts = () => {
  return request.get({ url: '/pms/report/weekly/depts' })
}
