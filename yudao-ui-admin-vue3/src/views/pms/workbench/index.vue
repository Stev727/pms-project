<template>
  <div class="pms-workbench">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="mb-16px">
      <el-col :span="6" v-for="card in statCards" :key="card.key">
        <el-card shadow="hover" class="stat-card" :class="{ active: activeTab === card.key }" @click="switchTab(card.key)">
          <div class="stat-content">
            <div class="stat-icon" :style="{ background: card.bg, color: card.color }">
              <Icon :icon="card.iconRef" :size="24" />
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

    <!-- 今日待办 -->
    <el-card class="mb-16px" header="今日待办">
      <el-empty v-if="loading" description="加载中..." :image-size="60" />
      <el-empty v-else-if="todayTasks.length === 0" description="今日暂无到期或进行中的任务" :image-size="60" />
      <div v-for="t in todayTasks" :key="t.taskId" class="task-item" @click="openTaskDetail(t)">
        <el-tag size="small" :type="getPriorityTag(t.priority)">{{ getPriorityLabel(t.priority) }}</el-tag>
        <span class="ml-8px">{{ t.taskName }}</span>
        <span class="ml-auto text-gray">{{ t.taskCode || '-' }}</span>
      </div>
    </el-card>

    <!-- 待审核快捷入口 -->
    <el-card class="mb-16px" header="需要我处理的">
      <div class="flex gap-16px">
        <div class="quick-action" @click="goToReviewCenter">
          <el-badge :value="pendingReviewCount" :hidden="!pendingReviewCount" :max="99">
            <el-button type="warning" circle><Icon icon="ep:circle-check" /></el-button>
          </el-badge>
          <span class="ml-8px">待审核</span>
        </div>
        <div class="quick-action" @click="goToMyTasks">
          <el-badge :value="myInProgressCount" :hidden="!myInProgressCount" :max="99">
            <el-button type="primary" circle><Icon icon="ep:list" /></el-button>
          </el-badge>
          <span class="ml-8px">我的进行中</span>
        </div>
      </div>
    </el-card>

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
            <el-empty v-if="group.items.length === 0" :description="emptyDescription" :image-size="60" />
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
    <TaskDetailDrawer ref="taskDrawerRef" @refresh="loadTasks" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, defineAsyncComponent } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ArrowRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getTaskList, TaskVO } from '@/api/pms/task'
import { getProjectList, ProjectVO } from '@/api/pms/project'
import { taskStatusMap, formatDate, calcDelayDays } from '../pms-utils'
import { useCache } from '@/hooks/web/useCache'
import { useUserStore } from '@/store/modules/user'
import { useUserNames } from '@/hooks/pms/useUserNames'

defineOptions({ name: 'PmsWorkbench' })

const router = useRouter()
const { wsCache } = useCache()
const userStore = useUserStore()
const { getUserName, ensureLoaded: ensureUsersLoaded } = useUserNames()
const message = ElMessage
const TaskDetailDrawer = defineAsyncComponent(() => import('../project-detail/TaskDetailDrawer.vue'))

const currentUserId = computed(() => {
  const uid = userStore.getUser?.id
  if (uid) return String(uid)
  // fallback: 从 wsCache 获取用户信息
  try {
    const cached = wsCache.get('user') || wsCache.get('userInfo') || {}
    return String(cached?.user?.id || cached?.id || '')
  } catch {
    return ''
  }
})

// 今日待办：今天到期 + 我的进行中任务
const todayTasks = computed(() => {
  const today = new Date().toISOString().split('T')[0]
  return allTasks.value.filter(t => {
    if (t.completeStatus === 'completed') return false
    // 今天到期的任务 + 进行中的任务
    const endDate = t.planEndDate
    if (endDate) {
      const endStr = Array.isArray(endDate)
        ? `${endDate[0]}-${String(endDate[1]).padStart(2,'0')}-${String(endDate[2]).padStart(2,'0')}`
        : String(endDate).split('T')[0]
      if (endStr === today) return true
    }
    return t.completeStatus === 'in_progress' && String(t.mainOwnerId) === currentUserId.value
  }).slice(0, 10)
})

const pendingReviewCount = computed(() =>
  allTasks.value.filter(t => t.completeStatus === 'pending_review').length
)

const myInProgressCount = computed(() =>
  allTasks.value.filter(t =>
    t.completeStatus === 'in_progress' &&
    String(t.mainOwnerId) === currentUserId.value
  ).length
)

const goToReviewCenter = () => {
  // 找到第一个有待审核任务的项目
  const task = allTasks.value.find(t => t.completeStatus === 'pending_review')
  if (task) {
    router.push(`/pms/project-detail/${task.projectId}?tab=review-center`)
  } else {
    message.info('暂无待审核任务')
  }
}

const goToMyTasks = () => {
  router.push('/pms/task')
}

const getPriorityTag = (priority?: string) => {
  const map: Record<string, string> = { high: 'danger', medium: 'warning', low: 'info' }
  return map[priority || ''] || 'info'
}

const getPriorityLabel = (priority?: string) => {
  const map: Record<string, string> = { high: '高', medium: '中', low: '低' }
  return map[priority || ''] || '普通'
}

const activeTab = ref('not_started')
const allTasks = ref<TaskVO[]>([])
const projects = ref<ProjectVO[]>([])
const loading = ref(false)
const taskDrawerRef = ref()

const completionChartRef = ref<HTMLElement>()
const volumeChartRef = ref<HTMLElement>()
let completionChart: echarts.ECharts | null = null
let volumeChart: echarts.ECharts | null = null

// Tab 配置
const tabConfig = [
  { label: '待接收', name: 'pending_accept' },
  { label: '未开始', name: 'not_started' },
  { label: '进行中', name: 'in_progress' },
  { label: '已暂停', name: 'paused' },
  { label: '待审核', name: 'pending_review' },
  { label: '已延期', name: 'delayed' },
  { label: '已完成', name: 'completed' }
]

// 统计卡片
const statCards = computed(() => {
  const myTasks = allTasks.value
  return [
    { key: 'not_started', label: '待办', value: myTasks.filter(t => t.completeStatus === 'not_started').length, sub: '本月新增 ' + myTasks.filter(t => t.completeStatus === 'not_started').length, iconRef: 'ep:clock', color: '#4E5969', bg: '#F2F3F5' },
    { key: 'in_progress', label: '进行中', value: myTasks.filter(t => t.completeStatus === 'in_progress').length, sub: '平均进度 ' + Math.round(avgProgress(myTasks, 'in_progress')) + '%', iconRef: 'ep:loading', color: '#2468F2', bg: '#DCE7FF' },
    { key: 'delayed', label: '延期', value: myTasks.filter(t => isDelayedTask(t)).length, sub: '最长延期 ' + maxDelay(myTasks) + ' 天', iconRef: 'ep:warning', color: '#F53F3F', bg: '#FFECE8' },
    { key: 'completed', label: '已完成', value: myTasks.filter(t => t.completeStatus === 'completed').length, sub: '本月完成', iconRef: 'ep:circle-check', color: '#00B42A', bg: '#E8FFEA' }
  ]
})

function avgProgress(tasks: TaskVO[], status: string): number {
  const list = tasks.filter(t => t.completeStatus === status)
  if (!list.length) return 0
  return list.reduce((sum, t) => sum + (t.progress || 0), 0) / list.length
}

function maxDelay(tasks: TaskVO[]): number {
  const delays = tasks.filter(t => isDelayedTask(t)).map(t => calcDelayDays(t.planEndDate, t.completeStatus))
  return delays.length ? Math.max(...delays) : 0
}

// 判断任务是否延期: planEndDate < today && completeStatus !== 'completed'
function isDelayedTask(t: TaskVO): boolean {
  if (t.completeStatus === 'completed') return false
  const d = parseDateSafe(t.planEndDate)
  if (!d) return false
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return d < today
}

const filteredTasks = computed(() => {
  if (activeTab.value === 'delayed') {
    return allTasks.value.filter(t => isDelayedTask(t))
  }
  return allTasks.value.filter(t => t.completeStatus === activeTab.value)
})

// 任务分组计算
const groupedTasks = computed(() => {
  const tasks = filteredTasks.value
  if (activeTab.value === 'delayed') {
    const sorted = [...tasks].sort((a, b) => calcDelayDays(b.planEndDate, b.completeStatus) - calcDelayDays(a.planEndDate, a.completeStatus))
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
  if (!groups.length) groups.push({ title: '任务列表', items: [] })
  return groups
})

function parseDateSafe(d: any): Date | null {
  if (!d) return null
  if (d instanceof Date) return d
  if (Array.isArray(d)) return new Date(d[0], (d[1] || 1) - 1, d[2] || 1)
  const parsed = new Date(d)
  return isNaN(parsed.getTime()) ? null : parsed
}

// 空状态描述（根据上下文给出原因说明）
const emptyDescription = computed(() => {
  if (loading.value) return '加载中...'
  if (allTasks.value.length === 0) {
    return '您当前没有负责的任务，请在项目管理中创建项目并分配任务给您'
  }
  const tab = tabConfig.find(t => t.name === activeTab.value)
  return `${tab?.label || '当前'}分类暂无任务`
})

function switchTab(key: string) {
  activeTab.value = key
}

function onTabChange() {
  loadTasks()
}

function openTaskDetail(task: TaskVO) {
  taskDrawerRef.value?.open(task)
}

function getProjectName(projectId: any): string {
  return projects.value.find(p => String(p.projectId) === String(projectId))?.projectName || '-'
}

async function loadTasks() {
  loading.value = true
  try {
    const uid = currentUserId.value
    if (!uid) {
      ElMessage.warning('用户信息未加载，请刷新页面或重新登录')
      allTasks.value = []
      loading.value = false
      return
    }
    const [taskRes, projectRes] = await Promise.all([getTaskList(), getProjectList()])
    const taskList = Array.isArray(taskRes) ? taskRes : ((taskRes as any)?.list || [])
    allTasks.value = taskList.filter(t => String(t.mainOwnerId) === uid)
    projects.value = projectRes as ProjectVO[]
    await ensureUsersLoaded()
  } catch (e) {
    console.error('加载任务失败', e)
    ElMessage.error('加载任务失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function initCharts() {
  if (completionChartRef.value) {
    completionChart = echarts.init(completionChartRef.value)
    const completed = allTasks.value.filter(t => t.completeStatus === 'completed').length
    const total = allTasks.value.length || 1
    const onTime = allTasks.value.filter(t => t.completeStatus === 'completed' && calcDelayDays(t.planEndDate, t.completeStatus) === 0).length
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

  .task-item {
    display: flex; align-items: center; padding: 10px 12px;
    border: 1px solid var(--el-border-color-lighter); border-radius: 6px;
    margin-bottom: 8px; cursor: pointer; transition: all 0.15s;
    &:hover { background: var(--el-fill-color-light); border-color: var(--el-color-primary-light-5); }
    &:last-child { margin-bottom: 0; }
  }

  .quick-action {
    display: flex; flex-direction: column; align-items: center; cursor: pointer;
    padding: 8px 16px; border-radius: 8px; transition: background 0.15s;
    &:hover { background: var(--el-fill-color-light); }
  }

  .text-gray { color: var(--el-text-color-secondary); }
  .text-red { color: #F53F3F; }
  .text-center { text-align: center; }
  .ml-auto { margin-left: auto; }
  .ml-8px { margin-left: 8px; }
  .py-20px { padding-top: 20px; padding-bottom: 20px; }
  .mb-16px { margin-bottom: 16px; }
  .flex { display: flex; }
  .gap-16px { gap: 16px; }
}
</style>
