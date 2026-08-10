<template>
  <el-card class="task-board-card" shadow="hover" @click="emit('detail', task)">
    <div class="card-top">
      <span class="card-name" :title="task.taskName">{{ task.taskName }}</span>
      <el-tag v-if="isDaily" type="warning" size="small" effect="plain">日常</el-tag>
      <el-tag v-else-if="projectName" type="primary" size="small" effect="plain">{{ projectName }}</el-tag>
    </div>

    <div class="card-meta">
      <span>👤 {{ ownerName }}</span>
      <span v-if="task.taskType">· {{ typeName }}</span>
    </div>

    <div class="card-dates">
      📅 {{ formatDate(task.planStartDate) }} ~ {{ formatDate(task.planEndDate) }}
    </div>

    <div class="card-bottom">
      <el-tag :style="statusStyle" size="small" effect="light">{{ statusLabel }}</el-tag>
      <el-tag v-if="reviewBadge" :type="reviewBadge.type" size="small" effect="plain">{{ reviewBadge.label }}</el-tag>
      <el-progress
        :percentage="task.progress || 0"
        :stroke-width="6"
        :color="progressColor"
        style="flex: 1; margin-left: 8px"
      />
    </div>

    <div v-if="canSubmitReview" class="card-action" @click.stop>
      <el-button size="small" type="success" @click="emit('submit-review', task)">提交审核</el-button>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { TaskVO } from '@/api/pms/task'
import { taskStatusMap, dailyTaskTypeOptions, priorityMap, formatDate } from '../../pms-utils'
import { useUserNames } from '@/hooks/pms/useUserNames'

const props = defineProps<{
  task: TaskVO
  currentUserId?: string | number
  projectName?: string
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
const typeName = computed(() => {
  if (isDaily.value) {
    return dailyTaskTypeOptions.find(o => o.value === props.task.taskType)?.label || '其他'
  }
  return props.task.taskType || '-'
})
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
.task-board-card {
  cursor: pointer;
  transition: all 0.2s;
}
.task-board-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}
.card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 8px;
}
.card-name {
  font-size: 14px;
  font-weight: 600;
  color: #1d2129;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-meta {
  font-size: 12px;
  color: #4e5969;
  margin-bottom: 4px;
}
.card-dates {
  font-size: 12px;
  color: #86909c;
  margin-bottom: 8px;
}
.card-bottom {
  display: flex;
  align-items: center;
  gap: 6px;
}
.card-action {
  margin-top: 10px;
  text-align: right;
}
</style>
