<template>
  <div class="pms-workbench">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="mb-16px">
      <el-col :span="6" v-for="card in statCards" :key="card.key">
        <el-card shadow="hover" class="stat-card" :class="{ active: activeTab === card.key }" @click="switchTab(card.key)">
          <div class="stat-content">
            <div class="stat-icon" :style="{ background: card.bg, color: card.color }">
              <el-icon :size="24"><component :is="card.icon" /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ card.value }}</div>
              <div class="stat-label">{{ card.label }}</div>
              <div class="stat-sub">{{ card.sub }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 任务列表 Tab -->
    <ContentWrap>
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane v-for="tab in tabConfig" :key="tab.name" :label="tab.label" :name="tab.name">
          <template v-for="group in groupedTasks" :key="group.title">
            <div class="group-title">{{ group.title }}</div>
            <div
              v-for="t in group.items"
              :key="String(t.taskId)"
              class="task-row"
              @click="openTaskDetail(t)"
            >
              <el-tag size="small" :color="taskStatusMap[t.completeStatus]?.borderColor" effect="dark">
                {{ taskStatusMap[t.completeStatus]?.label || t.completeStatus }}
              </el-tag>
              <span class="task-name">{{ t.taskName }}</span>
              <span class="task-project">{{ getProjectName(t.projectId) }}</span>
              <span class="task-date">{{ formatDate(t.planEndDate) }}</span>
              <span v-if="t.completeStatus === 'delayed'" class="task-delay">
                延期 {{ calcDelayDays(t.planEndDate) }} 天
              </span>
              <span v-if="t.progress != null && t.progress > 0" class="task-progress">
                {{ t.progress }}%
              </span>
              <el-icon class="task-arrow"><ArrowRight /></el-icon>
            </div>
            <el-empty v-if="group.items.length === 0" description="暂无任务" :image-size="60" />
          </template>
        </el-tab-pane>
      </el-tabs>
    </ContentWrap>

    <!-- 图表区 -->
    <el-row :gutter="16" class="mt-16px">
      <el-col :span="10">
        <ContentWrap title="本月完成率">
          <div ref="completionChartRef" style="height: 260px" />
        </ContentWrap>
      </el-col>
      <el-col :span="14">
        <ContentWrap title="本月任务量统计">
          <div ref="volumeChartRef" style="height: 260px" />
        </ContentWrap>
      </el-col>
    </el-row>

    <!-- 任务详情抽屉 -->
    <TaskDetailDrawer
      v-if="drawerVisible"
      :task-id="currentTaskId"
      :project-id="currentProjectId"
      @close="drawerVisible = false"
      @refresh="loadTasks"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, defineAsyncComponent } from 'vue'
import * as echarts from 'echarts'
import { getTaskList, TaskVO } from '@/api/pms/task'
import { getProjectList, ProjectVO } from '@/api/pms/project'
import { taskStatusMap, formatDate, calcDelayDays } from '../pms-utils'
import { useCache } from '@/hooks/web/useCache'

defineOptions({ name: 'PmsWorkbench' })

const { wsCache } = useCache()
const TaskDetailDrawer = defineAsyncComponent(() => import('../project-detail/TaskDetailDrawer.vue'))

const activeTab = ref('not_started')
const allTasks = ref<TaskVO[]>([])
const projects = ref<ProjectVO[]>([])
const loading = ref(false)
const drawerVisible = ref(false)
const currentTaskId = ref('')
const currentProjectId = ref('')

const completionChartRef = ref<HTMLElement>()
const volumeChartRef = ref<HTMLElement>()
let completionChart: echarts.ECharts | null = null
let volumeChart: echarts.ECharts | null = null

// Tab 配置
const tabConfig = [
  { label: '待办', name: 'not_started' },
  { label: '进行中', name: 'in_progress' },
  { label: '已延期', name: 'delayed' },
  { label: '已完成', name: 'completed' }
]

// 统计卡片
const statCards = computed(() => {
  const myTasks = allTasks.value
  return [
    { key: 'not_started', label: '待办', value: myTasks.filter(t => t.completeStatus === 'not_started').length, sub: '本月新增 ' + myTasks.filter(t => t.completeStatus === 'not_started').length, icon: 'Clock', color: '#4E5969', bg: '#F2F3F5' },
    { key: 'in_progress', label: '进行中', value: myTasks.filter(t => t.completeStatus === 'in_progress').length, sub: '平均进度 ' + Math.round(avgProgress(myTasks, 'in_progress')) + '%', icon: 'Loading', color: '#2468F2', bg: '#DCE7FF' },
    { key: 'delayed', label: '延期', value: myTasks.filter(t => t.completeStatus === 'delayed').length, sub: '最长延期 ' + maxDelay(myTasks) + ' 天', icon: 'Warning', color: '#F53F3F', bg: '#FFECE8' },
    { key: 'completed', label: '已完成', value: myTasks.filter(t => t.completeStatus === 'completed').length, sub: '本月完成', icon: 'CircleCheck', color: '#00B42A', bg: '#E8FFEA' }
  ]
})

function avgProgress(tasks: TaskVO[], status: string): number {
  const list = tasks.filter(t => t.completeStatus === status)
  if (!list.length) return 0
  return list.reduce((sum, t) => sum + (t.progress || 0), 0) / list.length
}

function maxDelay(tasks: TaskVO[]): number {
  const delays = tasks.filter(t => t.completeStatus === 'delayed').map(t => calcDelayDays(t.planEndDate, t.completeStatus))
  return delays.length ? Math.max(...delays) : 0
}

const filteredTasks = computed(() => {
  return allTasks.value.filter(t => t.completeStatus === activeTab.value)
})

// 任务分组计算
const groupedTasks = computed(() => {
  const tasks = filteredTasks.value
  if (activeTab.value === 'delayed') {
    const sorted = [...tasks].sort((a, b) => calcDelayDays(b.planEndDate) - calcDelayDays(a.planEndDate))
    return [{ title: `延期任务 (${sorted.length})`, items: sorted }]
  }
  const now = new Date()
  const weekEnd = new Date(now)
  weekEnd.setDate(now.getDate() + 7)
  const monthEnd = new Date(now)
  monthEnd.setMonth(now.getMonth() + 1)

  const thisWeek = tasks.filter(t => {
    const d = parseDateSafe(t.planEndDate)
    return d && d <= weekEnd && d >= now
  })
  const thisMonth = tasks.filter(t => {
    const d = parseDateSafe(t.planEndDate)
    return d && d > weekEnd && d <= monthEnd
  })
  const later = tasks.filter(t => {
    const d = parseDateSafe(t.planEndDate)
    return d && d > monthEnd
  })

  const groups: { title: string; items: TaskVO[] }[] = []
  if (thisWeek.length) groups.push({ title: `本周到期 (${thisWeek.length})`, items: thisWeek })
  if (thisMonth.length) groups.push({ title: `本月到期 (${thisMonth.length})`, items: thisMonth })
  if (later.length) groups.push({ title: `更远 (${later.length})`, items: later })
  if (!groups.length) groups.push({ title: '暂无任务', items: [] })
  return groups
})

function parseDateSafe(d: any): Date | null {
  if (!d) return null
  if (d instanceof Date) return d
  if (Array.isArray(d)) return new Date(d[0], (d[1] || 1) - 1, d[2] || 1)
  const parsed = new Date(d)
  return isNaN(parsed.getTime()) ? null : parsed
}

function switchTab(key: string) {
  activeTab.value = key
}

function onTabChange() {
  // Tab 切换时可以刷新数据
}

function openTaskDetail(task: TaskVO) {
  currentTaskId.value = String(task.taskId)
  currentProjectId.value = String(task.projectId)
  drawerVisible.value = true
}

function getProjectName(projectId: any): string {
  return projects.value.find(p => String(p.projectId) === String(projectId))?.projectName || '-'
}

async function loadTasks() {
  loading.value = true
  try {
    const userInfo = wsCache.get('userInfo')
    const currentUserId = userInfo?.id || 1
    const [taskRes, projectRes] = await Promise.all([getTaskList(), getProjectList()])
    allTasks.value = (taskRes as TaskVO[]).filter(t => t.mainOwnerId === currentUserId)
    projects.value = projectRes as ProjectVO[]
  } catch (e) {
    console.error('加载任务失败', e)
  } finally {
    loading.value = false
  }
}

function initCharts() {
  if (completionChartRef.value) {
    completionChart = echarts.init(completionChartRef.value)
    const completed = allTasks.value.filter(t => t.completeStatus === 'completed').length
    const total = allTasks.value.length || 1
    const onTime = allTasks.value.filter(t => t.completeStatus === 'completed' && calcDelayDays(t.planEndDate) === 0).length
    completionChart.setOption({
      tooltip: { trigger: 'item' },
      series: [{
        type: 'pie', radius: ['55%', '75%'], center: ['50%', '50%'],
        data: [
          { value: onTime, name: '按时完成', itemStyle: { color: '#00B42A' } },
          { value: completed - onTime, name: '延期完成', itemStyle: { color: '#FF7D00' } },
          { value: total - completed, name: '未完成', itemStyle: { color: '#E5E6EB' } }
        ],
        label: { show: true, position: 'center', formatter: `{b}\n${((onTime / total) * 100).toFixed(1)}%`, fontSize: 16, fontWeight: 'bold' }
      }]
    })
  }

  if (volumeChartRef.value) {
    volumeChart = echarts.init(volumeChartRef.value)
    const statusData = ['completed', 'in_progress', 'not_started', 'delayed']
    const colors = ['#00B42A', '#2468F2', '#4E5969', '#F53F3F']
    volumeChart.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: '3%', right: '8%', top: '10%', bottom: '5%', containLabel: true },
      xAxis: { type: 'value' },
      yAxis: { type: 'category', data: ['完成', '进行中', '待办', '延期'] },
      series: [{
        type: 'bar', barWidth: 20,
        data: statusData.map((s, i) => ({
          value: allTasks.value.filter(t => t.completeStatus === s).length,
          itemStyle: { color: colors[i] }
        })),
        label: { show: true, position: 'right' }
      }]
    })
  }
}

function handleResize() {
  completionChart?.resize()
  volumeChart?.resize()
}

onMounted(async () => {
  await loadTasks()
  await nextTick()
  initCharts()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  completionChart?.dispose()
  volumeChart?.dispose()
})
</script>

<style scoped lang="scss">
.pms-workbench {
  .stat-card {
    cursor: pointer; transition: all 0.2s;
    &:hover { transform: translateY(-2px); }
    &.active { border-color: var(--el-color-primary); }
    .stat-content { display: flex; align-items: center; gap: 12px; }
    .stat-icon {
      width: 48px; height: 48px; border-radius: 10px;
      display: flex; align-items: center; justify-content: center; flex-shrink: 0;
    }
    .stat-info {
      .stat-value { font-size: 28px; font-weight: 700; line-height: 1.2; }
      .stat-label { font-size: 14px; color: var(--el-text-color-secondary); }
      .stat-sub { font-size: 12px; color: var(--el-text-color-placeholder); }
    }
  }

  .group-title {
    font-size: 13px; font-weight: 600; color: var(--el-text-color-secondary);
    margin: 12px 0 8px; padding-left: 4px;
  }

  .task-row {
    display: flex; align-items: center; gap: 8px; padding: 10px 12px;
    border: 1px solid var(--el-border-color-lighter); border-radius: 6px;
    margin-bottom: 8px; cursor: pointer; transition: all 0.15s;
    &:hover { background: var(--el-fill-color-light); border-color: var(--el-color-primary-light-5); }
    .task-name { flex: 1; font-size: 14px; font-weight: 500; }
    .task-project { font-size: 12px; color: var(--el-text-color-secondary); }
    .task-date { font-size: 12px; color: var(--el-text-color-secondary); }
    .task-delay { font-size: 12px; color: #F53F3F; font-weight: 600; }
    .task-progress { font-size: 12px; color: #2468F2; font-weight: 600; }
    .task-arrow { color: var(--el-text-color-placeholder); }
  }
}
</style>
