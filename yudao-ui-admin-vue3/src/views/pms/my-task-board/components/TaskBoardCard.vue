<template>
  <!-- 表头模式 -->
  <div v-if="showHeader" class="task-row task-header" :class="{ 'hide-project': hideProject }">
    <div v-if="!hideProject" class="row-cell cell-project"><span class="header-text">项目</span></div>
    <div class="row-name"><span class="header-text">任务名称</span></div>
    <div class="row-cell cell-owner"><span class="header-text">责任人</span></div>
    <div class="row-cell cell-helper"><span class="header-text">协助人</span></div>
    <div class="row-cell cell-date"><span class="header-text">计划日期</span></div>
    <div class="row-cell cell-complete"><span class="header-text">完成日期</span></div>
    <div class="row-cell cell-delay"><span class="header-text">延期</span></div>
    <div class="row-cell cell-status"><span class="header-text">状态</span></div>
    <div class="row-cell cell-review"><span class="header-text">审核</span></div>
    <div class="row-cell cell-progress"><span class="header-text">进度</span></div>
  </div>

  <!-- 数据行 -->
  <div
    v-else
    class="task-row"
    :class="{ 'hide-project': hideProject }"
    @click="emit('detail', task)"
  >
    <!-- 项目名称（独立列；日常任务留空） -->
    <div v-if="!hideProject" class="row-cell cell-project" :title="projectName || ''">
      <el-tag v-if="projectName" type="primary" size="small" effect="plain" class="project-tag">{{ projectName }}</el-tag>
      <span v-else class="cell-empty">—</span>
    </div>

    <!-- 名称 + 标签 -->
    <div class="row-name" :title="task.taskName">
      <span class="name-text">{{ task.taskName }}</span>
      <el-tag v-if="isDaily" type="warning" size="small" effect="plain" class="row-tag">日常</el-tag>
      <el-tag v-if="task.taskType" size="small" effect="plain" type="info" class="row-tag">{{ typeName }}</el-tag>
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

    <!-- 完成日期：已完成显示实际完成日期（延期完成红色），未完成显示 — -->
    <div class="row-cell cell-complete">
      <span v-if="task.actualCompleteDate" :class="{ 'complete-late': completionDelayDays > 0 }">🏁 {{ formatDate(task.actualCompleteDate) }}</span>
      <span v-else class="cell-empty">—</span>
    </div>

    <!-- 延期 -->
    <div class="row-cell cell-delay">
      <el-tag v-if="delayDays > 0" type="danger" size="small" effect="dark" class="delay-tag">延期 {{ delayDays }} 天</el-tag>
      <el-tag v-else-if="completionDelayDays > 0" type="warning" size="small" effect="dark" class="delay-tag">延期 {{ completionDelayDays }} 天完成</el-tag>
      <span v-else class="cell-empty">—</span>
    </div>

    <!-- 状态 -->
    <div class="row-cell cell-status">
      <span class="status-tag" :style="statusStyle">{{ statusLabel }}</span>
    </div>

    <!-- 审核 -->
    <div class="row-cell cell-review">
      <el-tag v-if="reviewBadge" :type="reviewBadge.type" size="small" effect="plain" class="row-tag">{{ reviewBadge.label }}</el-tag>
      <span v-else class="cell-empty">—</span>
    </div>

    <!-- 进度：纯文字 + 进度条（统一基线 24px） -->
    <div class="row-cell cell-progress">
      <el-progress :percentage="task.progress || 0" :stroke-width="6" :color="progressColor" class="progress-bar" />
    </div>

    <!-- 操作按钮：绝对定位右上，不参与 grid（避免列数变化） -->
    <div v-if="canSubmitReview" class="row-action" @click.stop>
      <el-button size="small" type="success" @click="emit('submit-review', task)">提交审核</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { TaskVO } from '@/api/pms/task'
import { taskStatusMap, getDailyTaskTypeOptions, getDynamicTaskTypeOptions, calcDelayDays, priorityMap, formatDate, parseDate } from '../../pms-utils'
import { useUserNames } from '@/hooks/pms/useUserNames'

// 【PMS】日常任务类型下拉统一走系统字典真源
const dailyTaskTypeOptions = getDailyTaskTypeOptions()
// 【PMS】项目任务类型同样走字典真源
const projectTaskTypeOptions = getDynamicTaskTypeOptions()

const props = defineProps<{
  task?: TaskVO
  currentUserId?: string | number
  projectName?: string
  /** 隐藏「项目」列（看板按项目分组时组标题已含项目名，行内列冗余） */
  hideProject?: boolean
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
  // 项目任务：走 pms_task_type 字典翻译；字典查不到回落 taskType 原文（兼容性）
  return projectTaskTypeOptions.find(o => o.value === props.task.taskType)?.label
    || props.task.taskType || '-'
})
// 延期天数：未完成且计划结束日期早于今天
const delayDays = computed(() => calcDelayDays(props.task.planEndDate, props.task.completeStatus))
// 延期完成天数：已完成但实际完成日期晚于计划结束日期（延期后完成的任务也要体现延期事实）
const completionDelayDays = computed(() => {
  if (props.task?.completeStatus !== 'completed') return 0
  const end = parseDate(props.task.planEndDate)
  const actual = parseDate(props.task.actualCompleteDate)
  if (!end || !actual) return 0
  const days = Math.floor((actual.getTime() - end.getTime()) / (1000 * 60 * 60 * 24))
  return days > 0 ? days : 0
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
/*
  【P0 修复：行内容对齐】
  之前 flex + align-items: center 在多变高度子元素下不可靠，
  改为 CSS Grid：每列固定宽，表头/数据严格按列对齐；
  row-cell 高度统一 28px 与 el-tag small 一致，所有内容视觉同基线。
*/
.task-row {
  display: grid;
  position: relative;
  column-gap: 14px;
  padding: 12px 20px;
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
  min-height: 56px;
  /* 10 列：项目/任务名称/责任人/协助人/计划日期/完成日期/延期/状态/审核/进度 */
  grid-template-columns:
    120px                                    /* 项目 */
    minmax(180px, 1fr)                       /* 任务名称 */
    78px                                     /* 责任人 */
    minmax(80px, 150px)                      /* 协助人 */
    minmax(120px, 1fr)                       /* 计划日期 */
    88px                                     /* 完成日期 */
    100px                                    /* 延期 */
    60px                                     /* 状态 */
    56px                                     /* 审核 */
    minmax(80px, 1fr);                       /* 进度 */
}
.task-row.hide-project {
  /* 去掉项目列：9 列对齐 */
  grid-template-columns:
    minmax(180px, 1fr)
    78px
    minmax(80px, 150px)
    minmax(120px, 1fr)
    88px
    100px
    60px
    56px
    minmax(80px, 1fr);
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
  line-height: 28px;
  display: inline-block;
}
/* 名称区：弹性宽度，充分利用可用空间 */
.row-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #1d2129;
  overflow: hidden;
  min-height: 28px;
  min-width: 0;
}
.name-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 28px;
}
/* 通用单元格：固定基线高 28px，所有内容视觉中心对齐 */
.row-cell {
  display: flex;
  align-items: center;
  font-size: 12px;
  color: #4e5969;
  white-space: nowrap;
  overflow: hidden;
  min-width: 0;
  min-height: 28px;
  line-height: 28px;
}
/* 空占位：与 el-tag 同高，避免基线偏移 */
.cell-empty {
  color: #c9cdd4;
  display: inline-block;
  line-height: 28px;
  vertical-align: middle;
}
.cell-project .project-tag { max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.helper-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 28px;
}
.complete-late { color: #f53f3f; font-weight: 600; }
.cell-complete .cell-empty,
.cell-delay .cell-empty,
.cell-review .cell-empty { color: #d8dde6; }

/* 状态：去掉 el-tag，改用行内 span + 背景色，高度与 cell-empty 一致 = 22px */
.status-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 22px;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  line-height: 20px;
  white-space: nowrap;
  border: 1px solid transparent;
}

/* 看板卡内的标签（日常/任务类型/审核等） */
.row-tag {
  height: 22px;
  padding: 0 8px;
  line-height: 20px;
  flex-shrink: 0;
}

.delay-tag {
  font-weight: 600;
  height: 22px;
  padding: 0 8px;
  line-height: 20px;
  animation: pulse 1.8s ease-in-out infinite;
}

/* 进度：cell 内一行进度条 + 不强制对齐 */
.progress-bar {
  flex: 1;
  min-width: 0;
  margin: 0;
}

/* 操作按钮：绝对定位右上，不参与 grid 列对齐 */
.row-action {
  position: absolute;
  top: 50%;
  right: 16px;
  transform: translateY(-50%);
  z-index: 1;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.65; }
}
</style>
