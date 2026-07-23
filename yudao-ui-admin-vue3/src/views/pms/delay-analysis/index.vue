<template>
  <div class="pms-delay-analysis">
    <!-- 顶部工具栏 -->
    <ContentWrap>
      <div class="toolbar">
        <el-select v-model="timeRange" placeholder="时间范围" style="width: 120px">
          <el-option label="本周" value="week" />
          <el-option label="本月" value="month" />
          <el-option label="本季度" value="quarter" />
          <el-option label="本年" value="year" />
        </el-select>
        <el-button @click="exportData" v-if="false">
          <el-icon><Download /></el-icon> 导出
        </el-button>
      </div>
    </ContentWrap>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="mb-16px">
      <el-col :span="6" v-for="card in statCards" :key="card.key">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" :style="{ background: card.bg, color: card.color }">
              <Icon :icon="card.iconRef" :size="24" />
            </div>
            <div class="stat-info">
              <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
              <div class="stat-label">{{ card.label }}</div>
              <div class="stat-sub">{{ card.sub }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区 -->
    <el-row :gutter="16" class="mb-16px">
      <el-col :span="12">
        <ContentWrap title="按责任人分布">
          <div ref="byUserChartRef" style="height: 280px" />
        </ContentWrap>
      </el-col>
      <el-col :span="12">
        <ContentWrap title="按部门分布">
          <div ref="byDeptChartRef" style="height: 280px" />
        </ContentWrap>
      </el-col>
    </el-row>
    <ContentWrap title="按项目阶段分布" class="mb-16px">
      <div ref="byPhaseChartRef" style="height: 240px" />
    </ContentWrap>

    <!-- 延期任务列表 -->
    <ContentWrap title="延期任务列表">
      <div class="list-toolbar">
        <el-select v-model="filterUser" placeholder="责任人" clearable filterable style="width: 140px">
          <el-option v-for="u in userList" :key="u.id" :label="`${u.nickname} (${u.username})`" :value="String(u.id)" />
        </el-select>
        <el-select v-model="filterPhase" placeholder="阶段" clearable style="width: 140px">
          <el-option v-for="s in stages" :key="s.stageId" :label="s.stageName" :value="String(s.stageId)" />
        </el-select>
        <el-select v-model="filterSeverity" placeholder="严重程度" clearable style="width: 120px">
          <el-option label="轻微(1-3天)" value="minor" />
          <el-option label="一般(4-7天)" value="moderate" />
          <el-option label="严重(>7天)" value="severe" />
        </el-select>
        <el-button @click="filterList">查询</el-button>
      </div>
      <el-table :data="pagedDelayTasks" border size="small" @row-click="openTaskDetail">
        <el-table-column label="任务名称" min-width="180">
          <template #default="{ row }">
            <el-tag :color="getSeverityColor(row)" effect="dark" size="small">{{ getSeverityLabel(row) }}</el-tag>
            <span class="ml-8px">{{ row.taskName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="projectName" label="项目" width="120" />
        <el-table-column label="负责人" width="80">
          <template #default="{ row }">{{ getUserName(row.mainOwnerId) }}</template>
        </el-table-column>
        <el-table-column label="延期天数" width="90" align="center">
          <template #default="{ row }">
            <span :style="{ color: getSeverityColor(row), fontWeight: 600 }">{{ calcDelayDays(row.planEndDate, row.completeStatus) }} 天</span>
          </template>
        </el-table-column>
        <el-table-column prop="exceptionReason" label="延期原因" min-width="160">
          <template #default="{ row }">
            {{ row.exceptionReason || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="planEndDate" label="计划完成日" width="110">
          <template #default="{ row }">{{ formatDate(row.planEndDate) }}</template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="currentPage" v-model:page-size="pageSize"
        :total="filteredDelayTasks.length" layout="total, prev, pager, next"
        class="mt-12px" style="justify-content: flex-end"
      />
    </ContentWrap>

    <TaskDetailDrawer ref="taskDrawerRef" @refresh="loadData" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, defineAsyncComponent } from 'vue'
import * as echarts from 'echarts'
import { getTaskList, TaskVO } from '@/api/pms/task'
import { getProjectList, ProjectVO } from '@/api/pms/project'
import { getStageList, StageVO } from '@/api/pms/stage'
import { phaseColorMap, formatDate, calcDelayDays, getDelaySeverity } from '../pms-utils'
import { Download } from '@element-plus/icons-vue'
import { useUserNames } from '@/hooks/pms/useUserNames'

defineOptions({ name: 'PmsDelayAnalysis' })
const TaskDetailDrawer = defineAsyncComponent(() => import('../project-detail/TaskDetailDrawer.vue'))

const { userList, getUserName, ensureLoaded: ensureUsersLoaded } = useUserNames()
const timeRange = ref('month')
const allTasks = ref<TaskVO[]>([])
const projects = ref<ProjectVO[]>([])
const stages = ref<StageVO[]>([])
const loading = ref(false)
const taskDrawerRef = ref()

const filterUser = ref('')
const filterPhase = ref('')
const filterSeverity = ref('')
const currentPage = ref(1)
const pageSize = ref(20)

const byUserChartRef = ref<HTMLElement>()
const byDeptChartRef = ref<HTMLElement>()
const byPhaseChartRef = ref<HTMLElement>()
let charts: echarts.ECharts[] = []

const delayTasks = computed(() => {
  let tasks = allTasks.value.filter(t => t.completeStatus === 'delayed' || (t.planEndDate && calcDelayDays(t.planEndDate, t.completeStatus) > 0))
  // 按时间范围过滤
  if (timeRange.value && tasks.length > 0) {
    const now = new Date()
    let startDate: Date
    switch (timeRange.value) {
      case 'week':
        // 7天前
        startDate = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 7)
        break
      case 'month':
        // 本月1日
        startDate = new Date(now.getFullYear(), now.getMonth(), 1)
        break
      case 'quarter':
        // 本季度首日
        startDate = new Date(now.getFullYear(), Math.floor(now.getMonth() / 3) * 3, 1)
        break
      case 'year':
        // 本年1月1日
        startDate = new Date(now.getFullYear(), 0, 1)
        break
      default:
        return tasks
    }
    tasks = tasks.filter(t => {
      if (!t.planEndDate) return false
      const endDate = new Date(t.planEndDate)
      return endDate >= startDate
    })
  }
  return tasks
})

const filteredDelayTasks = computed(() => {
  let result = delayTasks.value
  if (filterUser.value) {
    result = result.filter(t => String(t.mainOwnerId) === filterUser.value)
  }
  if (filterPhase.value) {
    result = result.filter(t => String(t.stageId) === filterPhase.value)
  }
  if (filterSeverity.value) {
    result = result.filter(t => {
      const days = calcDelayDays(t.planEndDate, t.completeStatus)
      const sev = getDelaySeverity(days)
      if (filterSeverity.value === 'minor') return sev.label === '轻微'
      if (filterSeverity.value === 'moderate') return sev.label === '一般'
      if (filterSeverity.value === 'severe') return sev.label === '严重'
      return true
    })
  }
  return result
})

const pagedDelayTasks = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredDelayTasks.value.slice(start, start + pageSize.value)
})

const statCards = computed(() => {
  const tasks = delayTasks.value
  const delays = tasks.map(t => calcDelayDays(t.planEndDate, t.completeStatus))
  const avgDelay = delays.length ? (delays.reduce((a, b) => a + b, 0) / delays.length).toFixed(1) : '0'
  const severe = delays.filter(d => d > 7).length
  const total = allTasks.value.length || 1
  return [
    { key: 'total', label: '延期任务总数', value: tasks.length, sub: '全部项目', iconRef: 'ep:warning', color: '#F53F3F', bg: '#FFECE8' },
    { key: 'avg', label: '平均延期天数', value: avgDelay + '天', sub: '所有延期任务', iconRef: 'ep:timer', color: '#FF7D00', bg: '#FFF7E8' },
    { key: 'rate', label: '延期率', value: ((tasks.length / total) * 100).toFixed(1) + '%', sub: `${tasks.length}/${total}`, iconRef: 'ep:pie-chart', color: '#722ED1', bg: '#F0E8FF' },
    { key: 'severe', label: '严重延期(>7天)', value: severe, sub: '需立即处理', iconRef: 'ep:circle-close', color: '#CB2634', bg: '#FFECE8' }
  ]
})

function getSeverityColor(task: TaskVO): string {
  return getDelaySeverity(calcDelayDays(task.planEndDate, task.completeStatus)).color
}

function getSeverityLabel(task: TaskVO): string {
  return getDelaySeverity(calcDelayDays(task.planEndDate, task.completeStatus)).label
}

function getProjectName(projectId: any): string {
  return projects.value.find(p => String(p.projectId) === String(projectId))?.projectName || '-'
}

function openTaskDetail(row: TaskVO) {
  taskDrawerRef.value?.open(row)
}

function filterList() { currentPage.value = 1 }

function exportData() { console.log('导出延期任务列表') }

function initCharts() {
  const byUser: Record<string, number> = {}
  const byDept: Record<string, number> = {}
  const byPhase: Record<string, number> = {}

  delayTasks.value.forEach(t => {
    const user = t.mainOwnerId ? getUserName(t.mainOwnerId) : '未分配'
    byUser[user] = (byUser[user] || 0) + 1
    // 按部门分组：根据负责人的部门信息统计（fallback 到项目名）
    const userId = t.mainOwnerId ? String(t.mainOwnerId) : ''
    const userObj = userList.value.find(u => String(u.id) === userId)
    const dept = userObj?.deptName || userObj?.deptId || getProjectName(t.projectId)
    byDept[dept] = (byDept[dept] || 0) + 1
    const stage = stages.value.find(s => s.stageId === t.stageId)?.stageName || '未分组'
    byPhase[stage] = (byPhase[stage] || 0) + 1
  })

  // 按责任人
  if (byUserChartRef.value) {
    const chart = echarts.init(byUserChartRef.value)
    charts.push(chart)
    const sorted = Object.entries(byUser).sort((a, b) => b[1] - a[1]).slice(0, 10)
    chart.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: '3%', right: '8%', top: '5%', bottom: '3%', containLabel: true },
      xAxis: { type: 'value' },
      yAxis: { type: 'category', data: sorted.map(s => s[0]), inverse: true },
      series: [{ type: 'bar', barWidth: 18, data: sorted.map(s => s[1]), label: { show: true, position: 'right' }, itemStyle: { color: '#F53F3F' } }]
    })
  }

  // 按部门/项目
  if (byDeptChartRef.value) {
    const chart = echarts.init(byDeptChartRef.value)
    charts.push(chart)
    const sorted = Object.entries(byDept).sort((a, b) => b[1] - a[1])
    chart.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: '3%', right: '8%', top: '5%', bottom: '3%', containLabel: true },
      xAxis: { type: 'value' },
      yAxis: { type: 'category', data: sorted.map(s => s[0]), inverse: true },
      series: [{ type: 'bar', barWidth: 18, data: sorted.map(s => s[1]), label: { show: true, position: 'right' }, itemStyle: { color: '#FF7D00' } }]
    })
  }

  // 按阶段
  if (byPhaseChartRef.value) {
    const chart = echarts.init(byPhaseChartRef.value)
    charts.push(chart)
    const entries = Object.entries(byPhase)
    chart.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: '3%', right: '8%', top: '5%', bottom: '3%', containLabel: true },
      xAxis: { type: 'value' },
      yAxis: { type: 'category', data: entries.map(e => e[0]), inverse: true },
      series: [{
        type: 'bar', barWidth: 18,
        data: entries.map(e => {
          // 用阶段名称查找颜色，同时也尝试 stageCode（如 stages 可能有 code 字段）
          const stageObj = stages.value.find(s => s.stageName === e[0])
          const colorKey = stageObj?.stageName || e[0]
          return { value: e[1], itemStyle: { color: phaseColorMap[colorKey]?.color || phaseColorMap[e[0]]?.color || '#2468F2' } }
        }),
        label: { show: true, position: 'right' }
      }]
    })
  }
}

async function loadData() {
  loading.value = true
  try {
    const [taskRes, projectRes, stageRes] = await Promise.all([getTaskList(), getProjectList(), getStageList()])
    allTasks.value = taskRes as TaskVO[]
    projects.value = projectRes as ProjectVO[]
    stages.value = stageRes as StageVO[]
    await ensureUsersLoaded()
    await nextTick()
    initCharts()
  } catch (e) {
    console.error('加载数据失败', e)
  } finally {
    loading.value = false
  }
}

function handleResize() { charts.forEach(c => c?.resize()) }

onMounted(async () => {
  await loadData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  charts.forEach(c => c?.dispose())
  charts = []
})
</script>

<style scoped lang="scss">
.pms-delay-analysis {
  .toolbar { display: flex; gap: 12px; align-items: center; justify-content: flex-end; }
  .stat-card {
    .stat-content { display: flex; align-items: center; gap: 12px; }
    .stat-icon {
      width: 48px; height: 48px; border-radius: 10px;
      display: flex; align-items: center; justify-content: center; flex-shrink: 0;
    }
    .stat-value { font-size: 28px; font-weight: 700; line-height: 1.2; }
    .stat-label { font-size: 14px; color: var(--el-text-color-secondary); }
    .stat-sub { font-size: 12px; color: var(--el-text-color-placeholder); }
  }
  .list-toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
}
</style>
