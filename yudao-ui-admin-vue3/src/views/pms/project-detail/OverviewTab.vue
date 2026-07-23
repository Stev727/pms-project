<template>
  <div class="overview-tab">
    <!-- 项目完成率仪表盘 (PRD-003) -->
    <div class="completion-overview mb-24px">
      <h3>项目完成率</h3>
      <el-progress type="dashboard" :percentage="completionRate" :width="180" class="mb-8px" />
      <p>{{ completedCount }} / {{ totalCount }} 任务已完成</p>
    </div>

    <!-- 任务统计 (PRD-003: 5 项数字) -->
    <div class="task-stats mb-16px">
      <div class="stat-item">未开始 <b>{{ notStartedCount }}</b></div>
      <div class="stat-item">进行中 <b class="text-primary">{{ inProgressCount }}</b></div>
      <div class="stat-item">已暂停 <b>{{ pausedCount }}</b></div>
      <div class="stat-item">待审核 <b class="text-warning">{{ pendingReviewCount }}</b></div>
      <div class="stat-item">已完成 <b class="text-success">{{ completedCount }}</b></div>
      <div class="stat-item">已延期 <b class="text-danger">{{ delayedCount }}</b></div>
    </div>

    <!-- 本周到期任务 -->
    <el-card class="mb-16px" header="本周到期">
      <div v-if="weekDueTasks.length === 0" class="text-center text-gray">暂无本周到期任务</div>
      <div v-for="t in weekDueTasks" :key="t.taskId" class="task-item">
        <span>{{ t.taskName }}</span>
        <el-tag size="small" :type="getDaysColor(t.planEndDate)">{{ formatPlanDate(t.planEndDate) }}</el-tag>
      </div>
    </el-card>

    <!-- 延期任务 -->
    <el-card class="mb-16px" header="延期任务">
      <div v-if="delayedTasks.length === 0" class="text-center text-gray">暂无延期任务</div>
      <div v-for="t in delayedTasks" :key="t.taskId" class="task-item">
        <span class="text-red">{{ t.taskName }}</span>
        <span class="text-red">延期 {{ getDelayDays(t.planEndDate) }} 天</span>
      </div>
    </el-card>

    <!-- 未分配责任人 -->
    <el-card class="mb-16px" header="未分配责任人的任务">
      <div v-if="unassignedTasks.length === 0" class="text-center text-gray">所有任务已分配责任人</div>
      <div v-for="t in unassignedTasks" :key="t.taskId" class="task-item">
        <span>{{ t.taskName }}</span>
        <el-tag size="small" type="warning">未分配</el-tag>
      </div>
    </el-card>

    <!-- 阶段进度卡片 (PRD-003) -->
    <el-row :gutter="16" class="mb-16px">
      <el-col v-for="stage in stageProgress" :key="stage.stageId" :span="4">
        <el-card class="stage-card">
          <h4>{{ stage.stageName }}</h4>
          <el-progress :percentage="stage.rate" :stroke-width="8" />
          <p class="stage-stats">{{ stage.completed }} / {{ stage.total }} 任务</p>
        </el-card>
      </el-col>
    </el-row>

    <!-- 里程碑状态 -->
    <div class="milestone-card mt-16px" v-if="milestones.length > 0">
      <div class="card-title">里程碑</div>
      <el-timeline>
        <el-timeline-item
          v-for="ms in milestones"
          :key="ms.taskId"
          :timestamp="formatDate(ms.planEndDate)"
          placement="top"
          :type="ms.milestoneStatus === 'completed' ? 'success' : ms.isDelayed ? 'danger' : 'primary'"
          :hollow="ms.milestoneStatus !== 'completed'"
        >
          <div class="milestone-item">
            <span class="milestone-name">{{ ms.taskName }}</span>
            <el-tag v-if="ms.milestoneStatus === 'completed'" size="small" type="success">已完成</el-tag>
            <el-tag v-else-if="ms.isDelayed" size="small" type="danger">已延期</el-tag>
            <el-tag v-else-if="ms.milestoneStatus === 'not_started'" size="small" type="info">未开始</el-tag>
            <el-tag v-else size="small" type="primary">进行中</el-tag>
          </div>
        </el-timeline-item>
      </el-timeline>
    </div>

    <!-- 最近动态 -->
    <div class="activity-card mt-16px">
      <div class="card-title">最近动态
        <el-tag size="small" type="info" effect="plain" style="margin-left: 8px">基于任务状态推测</el-tag>
      </div>
      <el-timeline v-if="recentActivities.length > 0">
        <el-timeline-item
          v-for="(activity, index) in recentActivities"
          :key="index"
          :timestamp="activity.time"
          placement="top"
          :type="activity.type"
        >
          {{ activity.content }}
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无最近动态" :image-size="60" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { TaskVO } from '@/api/pms/task'
import { StageVO } from '@/api/pms/stage'
import { formatDate, calcDelayDays } from '../pms-utils'

defineOptions({ name: 'OverviewTab' })

const props = defineProps<{
  tasks: TaskVO[]
  stages: StageVO[]
  project: any
}>()

// ==================== 任务统计 (PRD-003) ====================
const totalCount = computed(() => props.tasks.length)
const completedCount = computed(() => props.tasks.filter(t => t.completeStatus === 'completed').length)
const inProgressCount = computed(() => props.tasks.filter(t => t.completeStatus === 'in_progress').length)
const delayedCount = computed(() => props.tasks.filter(t => {
  if (t.completeStatus === 'completed' || t.completeStatus === 'cancelled') return false
  return calcDelayDays(t.planEndDate, t.completeStatus) > 0
}).length)
const notStartedCount = computed(() => props.tasks.filter(t => t.completeStatus === 'not_started').length)
const pausedCount = computed(() => props.tasks.filter(t => t.completeStatus === 'paused').length)
const pendingReviewCount = computed(() => props.tasks.filter(t => t.completeStatus === 'pending_review').length)
const completionRate = computed(() => {
  if (!totalCount.value) return 0
  return Math.round(completedCount.value / totalCount.value * 100)
})

// ==================== 辅助函数 ====================
const parseDate = (date: any) => {
  if (!date) return new Date()
  if (Array.isArray(date)) return new Date(date[0], date[1] - 1, date[2])
  return new Date(date)
}

const formatPlanDate = (date: any) => {
  return formatDate(date, 'MM-DD')
}

const getDaysColor = (planEndDate: any) => {
  const end = parseDate(planEndDate)
  const diff = Math.ceil((end.getTime() - Date.now()) / (1000 * 60 * 60 * 24))
  if (diff < 0) return 'danger'
  if (diff <= 2) return 'warning'
  return 'primary'
}

const getDelayDays = (planEndDate: any) => {
  if (!planEndDate) return 0
  const end = parseDate(planEndDate)
  const diff = Math.floor((Date.now() - end.getTime()) / (1000 * 60 * 60 * 24))
  return diff > 0 ? diff : 0
}

// ==================== 任务分类列表 (PRD-003) ====================
const weekDueTasks = computed(() => {
  const now = new Date()
  const weekLater = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000)
  return props.tasks.filter(t => {
    if (!t.planEndDate || t.completeStatus === 'completed') return false
    const end = parseDate(t.planEndDate)
    return end >= now && end <= weekLater
  })
})

const delayedTasks = computed(() => {
  return props.tasks.filter(t => {
    if (!t.planEndDate || t.completeStatus === 'completed') return false
    return parseDate(t.planEndDate) < new Date()
  })
})

const unassignedTasks = computed(() => {
  return props.tasks.filter(t => !t.mainOwnerId && t.completeStatus !== 'completed')
})

// ==================== 阶段进度 (PRD-003) ====================
const stageProgress = computed(() => {
  return props.stages.map(stage => {
    const stageTasks = props.tasks.filter(t => String(t.stageId) === String(stage.stageId))
    const completed = stageTasks.filter(t => t.completeStatus === 'completed').length
    const total = stageTasks.length
    return {
      ...stage,
      total,
      completed,
      rate: total ? Math.round(completed / total * 100) : 0
    }
  })
})

// ==================== 里程碑 ====================
const milestones = computed(() => {
  return props.tasks
    .filter(t => t.isMilestone)
    .map(t => ({
      ...t,
      milestoneStatus: t.completeStatus || 'not_started',
      isDelayed: calcDelayDays(t.planEndDate, t.completeStatus) > 0
    }))
    .sort((a, b) => {
      if (!a.planEndDate) return 1
      if (!b.planEndDate) return -1
      return new Date(a.planEndDate).getTime() - new Date(b.planEndDate).getTime()
    })
})

// ==================== 最近动态（基于任务状态变化模拟） ====================
// 注意：当前 recentActivities 是基于任务数据（已完成/延期状态）模拟生成，并非真实操作日志。
// 如果后续有操作日志 API（如 getOperationLog），应改为调用真实 API 获取动态数据。
const recentActivities = computed(() => {
  const activities: any[] = []
  const recent = [...props.tasks]
    .filter(t => t.completeStatus === 'completed' && t.actualCompleteDate)
    .sort((a, b) => {
      if (!a.actualCompleteDate) return 1
      if (!b.actualCompleteDate) return -1
      return new Date(b.actualCompleteDate).getTime() - new Date(a.actualCompleteDate).getTime()
    })
    .slice(0, 5)

  for (const task of recent) {
    activities.push({
      content: `任务「${task.taskName}」已完成`,
      time: formatDate(task.actualCompleteDate, 'MM-DD HH:mm'),
      type: 'success'
    })
  }

  const delayed = props.tasks
    .filter(t => calcDelayDays(t.planEndDate, t.completeStatus) > 0)
    .slice(0, 3)
  for (const task of delayed) {
    activities.push({
      content: `任务「${task.taskName}」已延期 ${calcDelayDays(task.planEndDate, task.completeStatus)} 天`,
      time: formatDate(new Date(), 'MM-DD HH:mm'),
      type: 'danger'
    })
  }

  if (activities.length === 0) {
    activities.push({ content: '项目创建成功', time: formatDate(props.project?.createTime), type: 'primary' })
  }

  return activities.slice(0, 8)
})
</script>

<style scoped>
.overview-tab { }
.mb-16px { margin-bottom: 16px; }
.mt-16px { margin-top: 16px; }

.stat-card {
  display: flex; align-items: center; gap: 12px; background: #F7F8FA; border-radius: 8px;
  padding: 16px; transition: all 0.2s;
}
.stat-card:hover { background: #EDEFF2; }
.stat-card-danger { background: #FFECE8; }
.stat-icon {
  width: 44px; height: 44px; border-radius: 10px; display: flex; align-items: center; justify-content: center;
}
.stat-label { font-size: 13px; color: #86909C; margin-bottom: 4px; }
.stat-value { font-size: 24px; font-weight: 700; color: #1D2129; }

.card-title { font-size: 15px; font-weight: 600; color: #1D2129; margin-bottom: 16px; }

.progress-ring-card {
  background: #F7F8FA; border-radius: 8px; padding: 20px; text-align: center; height: 100%;
}
.ring-container { margin: 12px 0; display: flex; justify-content: center; }
.ring-detail { display: flex; justify-content: center; gap: 16px; font-size: 13px; color: #4E5969; }

.phase-timeline-card {
  background: #F7F8FA; border-radius: 8px; padding: 20px;
}
.phase-timeline {
  display: flex; align-items: flex-start; gap: 0; overflow-x: auto; padding-bottom: 12px;
}
.phase-item {
  display: flex; flex-direction: column; align-items: center; min-width: 90px; position: relative;
}
.phase-node {
  width: 30px; height: 30px; border-radius: 50%; background: #E5E6EB; color: #86909C;
  display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 600;
  z-index: 1; transition: all 0.3s;
}
.phase-node.active { background: #2468F2; color: #fff; }
.phase-node.completed { background: #00B42A; color: #fff; }
.phase-line {
  position: absolute; top: 15px; left: 60px; width: calc(100% - 30px); height: 2px;
  background: #E5E6EB; z-index: 0;
}
.phase-line.active { background: #00B42A; }
.phase-label { font-size: 12px; font-weight: 600; color: #1D2129; margin-top: 8px; white-space: nowrap; }
.phase-stats { font-size: 11px; color: #86909C; margin-top: 2px; }

.milestone-card {
  background: #F7F8FA; border-radius: 8px; padding: 20px;
}
.milestone-item { display: flex; align-items: center; gap: 8px; }
.milestone-name { font-size: 14px; color: #1D2129; }

.activity-card {
  background: #F7F8FA; border-radius: 8px; padding: 20px;
}

/* PRD-003 新增样式 */
.completion-overview {
  background: #F7F8FA; border-radius: 8px; padding: 24px; text-align: center;
}
.completion-overview h3 {
  font-size: 15px; font-weight: 600; color: #1D2129; margin: 0 0 16px 0;
}
.completion-overview p {
  font-size: 13px; color: #4E5969; margin: 8px 0 0 0;
}
.mb-24px { margin-bottom: 24px; }
.mb-8px { margin-bottom: 8px; }

.task-stats {
  display: flex; gap: 16px; background: #F7F8FA; border-radius: 8px; padding: 16px;
}
.task-stats .stat-item {
  flex: 1; display: flex; flex-direction: column; align-items: center; gap: 6px;
  font-size: 13px; color: #86909C;
}
.task-stats .stat-item b {
  font-size: 22px; font-weight: 700; color: #1D2129;
}
.text-success { color: #00B42A; }
.text-primary { color: #2468F2; }
.text-danger { color: #F53F3F; }
.text-warning { color: #FF7D00; }

.stage-card { text-align: center; }
.stage-card h4 {
  font-size: 13px; font-weight: 600; color: #1D2129; margin: 0 0 12px 0;
}
.stage-card .stage-stats {
  font-size: 12px; color: #86909C; margin: 8px 0 0 0;
}
.task-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 0; border-bottom: 1px solid #F2F3F5; font-size: 13px; color: #1D2129;
}
.task-item:last-child { border-bottom: none; }
.text-center { text-align: center; }
.text-gray { color: #86909C; }
.text-red { color: #F53F3F; }
</style>
