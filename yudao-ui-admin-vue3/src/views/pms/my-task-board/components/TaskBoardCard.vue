<template>
  <!-- 表头模式 -->
  <div v-if="showHeader" class="task-row task-header">
    <div class="row-name"><span class="header-text">任务名称</span></div>
    <div class="row-cell cell-owner"><span class="header-text">责任人</span></div>
    <div class="row-cell cell-helper"><span class="header-text">协助人</span></div>
    <div class="row-cell cell-date"><span class="header-text">计划日期</span></div>
    <div class="row-cell cell-delay"><span class="header-text">延期</span></div>
    <div class="row-cell cell-status"><span class="header-text">状态</span></div>
    <div class="row-cell cell-review"><span class="header-text">审核</span></div>
    <div class="row-cell cell-progress"><span class="header-text">进度</span></div>
  </div>
  <!-- 数据行 -->
  <div v-else class="task-row" @click="emit('detail', task)">
    <!-- 名称 + 标签 -->
    <div class="row-name" :title="task.taskName">
      <span class="name-text">{{ task.taskName }}</span>
      <el-tag v-if="isDaily" type="warning" size="small" effect="plain">日常</el-tag>
      <el-tag v-else-if="projectName" type="primary" size="small" effect="plain">{{ projectName }}</el-tag>
      <el-tag v-if="task.taskType" size="small" effect="plain" type="info">{{ typeName }}</el-tag>
    </div>

    <!-- 责任人 -->
    <div class="row-cell cell-owner">👤 {{ ownerName }}</div>

    <!-- 协助人 -->
    <div class="row-cell cell-helper" :title="helperNamesFull">
      <span v-if="helperNames" class="helper-text">🤝 {{ helperNames }}</span>
      <span v-else class="cell-empty">—</span>
    </div>

    <!-- 计划日期 -->
    <div class="row-cell cell-date">📅 {{ formatDate(task.planStartDate) }} ~ {{ formatDate(task.planEndDate) }}</div>

    <!-- 延期 -->
    <div class="row-cell cell-delay">
      <el-tag v-if="delayDays > 0" type="danger" size="small" effect="dark" class="delay-tag">延期 {{ delayDays }} 天</el-tag>
      <span v-else class="cell-empty">—</span>
    </div>

    <!-- 状态 -->
    <div class="row-cell cell-status">
      <el-tag :style="statusStyle" size="small" effect="light">{{ statusLabel }}</el-tag>
    </div>

    <!-- 审核 -->
    <div class="row-cell cell-review">
      <el-tag v-if="reviewBadge" :type="reviewBadge.type" size="small" effect="plain">{{ reviewBadge.label }}</el-tag>
      <span v-else class="cell-empty">—</span>
    </div>

    <!-- 进度 -->
    <div class="row-cell cell-progress">
      <el-progress :percentage="task.progress || 0" :stroke-width="6" :color="progressColor" />
    </div>

    <!-- 操作 -->
    <div v-if="canSubmitReview" class="row-action" @click.stop>
      <el-button size="small" type="success" @click="emit('submit-review', task)">提交审核</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { TaskVO } from '@/api/pms/task'
import { taskStatusMap, dailyTaskTypeOptions, getDailyTaskTypeOptions, calcDelayDays, priorityMap, formatDate } from '../../pms-utils'
import { useUserNames } from '@/hooks/pms/useUserNames'

const props = defineProps<{
  task?: TaskVO
  currentUserId?: string | number
  projectName?: string
  /** 是否渲染为表头行（显示列标题而非数据） */
  showHeader?: boolean
}>()

const emit = defineEmits<{
  (e: 'detail', task: TaskVO): void
  (e: 'submit-review', task: TaskVO): void
}>()

const { getUserName } = useUserNames()
const isDaily = computed(() =>
  props.task.projectId === null || props.task.projectId === undefined || props.task.projectId === 0
)
const ownerName = computed(() => getUserName(props.task.mainOwnerId))
// 协助人：helperIds 是逗号分隔的用户ID字符串
const helperNames = computed(() => {
  const ids = props.task.helperIds
  if (!ids) return ''
  const idList = ids.split(',').map((s: string) => s.trim()).filter(Boolean)
  if (idList.length === 0) return ''
  const names = idList.map((id: string) => getUserName(Number(id) || id)).filter(Boolean)
  // 超过2人时截断显示
  return names.length > 2 ? `${names.slice(0, 2).join('、')} 等${names.length}人` : names.join('、')
})
const helperNamesFull = computed(() => {
  const ids = props.task.helperIds
  if (!ids) return ''
  const idList = ids.split(',').map((s: string) => s.trim()).filter(Boolean)
  return idList.map((id: string) => getUserName(Number(id) || id)).filter(Boolean).join('、')
})
const typeName = computed(() => {
  if (isDaily.value) {
    return getDailyTaskTypeOptions().find(o => o.value === props.task.taskType)?.label
      || dailyTaskTypeOptions.find(o => o.value === props.task.taskType)?.label
      || '其他'
  }
  return props.task.taskType || '-'
})
// 延期天数：未完成且计划结束日期早于今天
const delayDays = computed(() => calcDelayDays(props.task.planEndDate, props.task.completeStatus))
const statusLabel = computed(() => taskStatusMap[props.task.completeStatus || '']?.label || '-')
const statusStyle = computed(() => {
  const s = taskStatusMap[props.task.completeStatus || '']
  return s ? `color:${s.textColor};background:${s.bgColor};border-color:${s.borderColor};` : ''
})
const reviewBadge = computed(() => {
  const rs = props.task.reviewStatus
  if (rs === 'submitted') return { label: '待审核', type: 'warning' as const }
  if (rs === 'completed') return { label: '已审核', type: 'success' as const }
  if (rs === 'rejected') return { label: '已驳回', type: 'danger' as const }
  return null
})
const progressColor = computed(() => {
  if (props.task.completeStatus === 'completed') return '#00B42A'
  return '#2468F2'
})
const canSubmitReview = computed(() => {
  const uid = String(props.currentUserId)
  const isOwner = String(props.task.mainOwnerId) === uid
  const active = props.task.completeStatus === 'in_progress' || props.task.completeStatus === 'delayed'
  const canRs = !props.task.reviewStatus || props.task.reviewStatus === 'none' || props.task.reviewStatus === 'rejected'
  return isOwner && active && canRs
})
</script>

<style scoped>
.task-row {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 16px 20px;
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
  min-height: 58px;
}
.task-row:hover {
  background: #f7f8fa;
  border-color: #c9cdd4;
}
/* 表头行 */
.task-header {
  background: #f2f3f5;
  cursor: default;
  font-weight: 600;
  border-bottom: 2px solid #e5e6eb;
}
.task-header:hover {
  background: #f2f3f5;
}
.header-text {
  color: #1d2129;
  font-size: 13px;
  font-weight: 600;
}
/* 名称区：弹性宽度，充分利用可用空间 */
.row-name {
  flex: 2 1 220px;
  min-width: 180px;
  max-width: 380px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #1d2129;
  overflow: hidden;
}
.name-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 通用单元格 */
.row-cell {
  display: flex;
  align-items: center;
  font-size: 12px;
  color: #4e5969;
  white-space: nowrap;
}
.cell-empty {
  color: #c9cdd4;
}
.cell-owner { width: 88px; flex-shrink: 0; }
.cell-helper { flex: 1 1 120px; min-width: 90px; max-width: 180px; }
.helper-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cell-date { flex: 1 1 145px; min-width: 130px; }
.cell-delay { width: 88px; flex-shrink: 0; justify-content: flex-start; }
.cell-status { width: 80px; flex-shrink: 0; justify-content: flex-start; }
.cell-review { width: 70px; flex-shrink: 0; justify-content: flex-start; }
.cell-progress { flex: 1 1 110px; min-width: 90px; }
.row-action { flex-shrink: 0; margin-left: auto; }
.delay-tag {
  font-weight: 600;
  animation: pulse 1.8s ease-in-out infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.65; }
}
</style>