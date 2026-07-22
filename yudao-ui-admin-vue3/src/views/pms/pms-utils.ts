/**
 * PMS 前端公共工具函数
 * 阶段色板、状态映射、格式化等
 */

// ==================== 阶段色板 ====================
export const phaseColorMap: Record<string, { color: string; bg: string; border: string; label: string }> = {
  initiation: { color: '#2468F2', bg: '#DCE7FF', border: '#2468F2', label: '立项' },
  design: { color: '#722ED1', bg: '#F0E8FF', border: '#722ED1', label: '设计' },
  development: { color: '#0FC6C2', bg: '#E0FFFE', border: '#0FC6C2', label: '开发' },
  testing: { color: '#FF7D00', bg: '#FFF7E8', border: '#FF7D00', label: '测试' },
  acceptance: { color: '#00B42A', bg: '#E8FFEA', border: '#00B42A', label: '验收' },
  closure: { color: '#86909C', bg: '#F2F3F5', border: '#C9CDD4', label: '结项' },
  // 中文别名
  '立项': { color: '#2468F2', bg: '#DCE7FF', border: '#2468F2', label: '立项' },
  '设计': { color: '#722ED1', bg: '#F0E8FF', border: '#722ED1', label: '设计' },
  '开发': { color: '#0FC6C2', bg: '#E0FFFE', border: '#0FC6C2', label: '开发' },
  '测试': { color: '#FF7D00', bg: '#FFF7E8', border: '#FF7D00', label: '测试' },
  '验收': { color: '#00B42A', bg: '#E8FFEA', border: '#00B42A', label: '验收' },
  '结项': { color: '#86909C', bg: '#F2F3F5', border: '#C9CDD4', label: '结项' },
}

// ==================== 任务状态色板 ====================
export const taskStatusMap: Record<string, { textColor: string; bgColor: string; borderColor: string; label: string; type: string }> = {
  not_started: { textColor: '#4E5969', bgColor: '#F2F3F5', borderColor: '#E5E6EB', label: '未开始', type: 'info' },
  in_progress: { textColor: '#1A56DB', bgColor: '#DCE7FF', borderColor: '#2468F2', label: '进行中', type: 'primary' },
  reported: { textColor: '#722ED1', bgColor: '#F0E8FF', borderColor: '#722ED1', label: '已填报', type: 'primary' },
  completed: { textColor: '#009A29', bgColor: '#E8FFEA', borderColor: '#00B42A', label: '已完成', type: 'success' },
  delayed: { textColor: '#CB2634', bgColor: '#FFECE8', borderColor: '#F53F3F', label: '已延期', type: 'danger' },
  closed: { textColor: '#86909C', bgColor: '#F7F8FA', borderColor: '#C9CDD4', label: '已关闭', type: 'info' },
  paused: { textColor: '#D25F00', bgColor: '#FFF7E8', borderColor: '#FF7D00', label: '已暂停', type: 'warning' },
  cancelled: { textColor: '#86909C', bgColor: '#F7F8FA', borderColor: '#C9CDD4', label: '已取消', type: 'info' },
}

// ==================== 项目状态映射 ====================
export const projectStatusMap: Record<string, { label: string; type: string; color: string }> = {
  initiating: { label: '立项中', type: 'info', color: '#86909C' },
  planning: { label: '计划中', type: 'warning', color: '#FF7D00' },
  in_progress: { label: '进行中', type: 'primary', color: '#2468F2' },
  completed: { label: '已完成', type: 'success', color: '#00B42A' },
  archived: { label: '已归档', type: 'info', color: '#86909C' },
  paused: { label: '已暂停', type: 'warning', color: '#FF7D00' },
  cancelled: { label: '已取消', type: 'info', color: '#86909C' },
}

// ==================== 优先级映射 ====================
export const priorityMap: Record<string, { label: string; color: string }> = {
  urgent: { label: '紧急', color: '#F53F3F' },
  high: { label: '高', color: '#FF7D00' },
  normal: { label: '普通', color: '#4E5969' },
  low: { label: '低', color: '#86909C' },
}

// ==================== 延期严重程度 ====================
export const delaySeverityMap = [
  { maxDays: 3, color: '#FF7D00', label: '轻微', icon: 'Warning' },
  { maxDays: 7, color: '#F77234', label: '一般', icon: 'Warning' },
  { maxDays: Infinity, color: '#F53F3F', label: '严重', icon: 'CircleClose' },
]

export function getDelaySeverity(days: number) {
  return delaySeverityMap.find(s => days <= s.maxDays) || delaySeverityMap[delaySeverityMap.length - 1]
}

// ==================== 格式化函数 ====================
// 解析多种日期格式：字符串 | Date | 数组(Java LocalDate序列化如[2026,8,1]) | 时间戳数字
function parseDate(date: any): Date | null {
  if (!date) return null
  if (date instanceof Date) return date
  if (typeof date === 'number') return new Date(date)
  if (typeof date === 'string') {
    // 兼容 "2026-08-01" / "2026-08-01 10:30:00" / ISO格式
    const d = new Date(date.replace(' ', 'T'))
    return isNaN(d.getTime()) ? null : d
  }
  if (Array.isArray(date)) {
    // Java LocalDate/LocalDateTime 序列化为 [year, month, day, hour?, minute?, second?]
    const [y, m, dd, h = 0, min = 0, s = 0] = date
    return new Date(y, m - 1, dd, h, min, s)
  }
  return null
}

export function formatDate(date: any, format: string = 'YYYY-MM-DD'): string {
  const d = parseDate(date)
  if (!d || isNaN(d.getTime())) return '-'
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const h = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  return format
    .replace('YYYY', String(y))
    .replace('MM', m)
    .replace('DD', day)
    .replace('HH', h)
    .replace('mm', min)
}

export function formatDateRange(start?: any, end?: any): string {
  if (!start && !end) return '-'
  return `${formatDate(start)} ~ ${formatDate(end)}`
}

// 计算延期天数
export function calcDelayDays(planEndDate?: any, completeStatus?: string): number {
  if (!planEndDate || completeStatus === 'completed' || completeStatus === 'cancelled' || completeStatus === 'closed') return 0
  const end = parseDate(planEndDate)
  if (!end) return 0
  const now = new Date()
  if (end >= now) return 0
  return Math.floor((now.getTime() - end.getTime()) / (1000 * 60 * 60 * 24))
}

// 计算工期天数
export function calcDuration(startDate?: any, endDate?: any): number {
  if (!startDate || !endDate) return 0
  const start = parseDate(startDate)
  const end = parseDate(endDate)
  if (!start || !end) return 0
  return Math.max(1, Math.floor((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)) + 1)
}

// ==================== 字典选项 ====================
// 注意：这些硬编码选项是 fallback，优先从字典管理页面维护的数据加载。
// 字典数据存储在 localStorage `pms_dict_data` 中，通过 PmsDict 页面维护。
export function loadDictOptions(dictKey: string): { value: string; label: string; color?: string }[] {
  try {
    const saved = localStorage.getItem('pms_dict_data')
    if (saved) {
      const dictData = JSON.parse(saved)
      if (dictData[dictKey] && Array.isArray(dictData[dictKey])) {
        return dictData[dictKey]
          .filter((item: any) => item.status === 0)
          .sort((a: any, b: any) => (a.sort || 0) - (b.sort || 0))
          .map((item: any) => ({ value: item.value, label: item.label, color: item.color }))
      }
    }
  } catch { /* fallback to hardcoded */ }
  return []
}

// 获取字典选项（优先从维护数据，fallback 到硬编码）
export function getDictOptions(dictKey: string, fallback: { value: string; label: string; color?: string }[]): { value: string; label: string; color?: string }[] {
  const dynamic = loadDictOptions(dictKey)
  return dynamic.length > 0 ? dynamic : fallback
}

export const projectTypeOptions = [
  { value: 'new_dev', label: '新研发项目' },
  { value: 'modification', label: '改型项目' },
  { value: 'cost_down', label: '降本项目' },
  { value: 'platform', label: '平台项目' },
]

export const taskTypeOptions = [
  { value: 'design', label: '设计任务' },
  { value: 'review', label: '评审任务' },
  { value: 'testing', label: '测试任务' },
  { value: 'procurement', label: '采购任务' },
  { value: 'prototyping', label: '试制任务' },
  { value: 'documentation', label: '文档任务' },
  { value: 'approval', label: '审批任务' },
  { value: 'supplier_synergy', label: '供应商协同' },
  { value: 'other', label: '其他' },
]

export const priorityOptions = [
  { value: 'urgent', label: '紧急' },
  { value: 'high', label: '高' },
  { value: 'normal', label: '普通' },
  { value: 'low', label: '低' },
]

// ==================== 动态字典选项（支持字典维护） ====================
export function getDynamicTaskTypeOptions() {
  return getDictOptions('pms_task_type', taskTypeOptions)
}

export function getDynamicProjectTypeOptions() {
  return getDictOptions('pms_project_type', projectTypeOptions)
}

export function getDynamicPriorityOptions() {
  return getDictOptions('pms_priority', priorityOptions)
}
