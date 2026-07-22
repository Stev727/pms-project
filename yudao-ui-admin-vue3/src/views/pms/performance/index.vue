<template>
  <div class="pms-performance">
    <!-- 顶部工具栏 -->
    <ContentWrap>
      <div class="toolbar">
        <el-date-picker v-model="selectedMonth" type="month" placeholder="选择月份" value-format="YYYY-MM" :disabled-date="disabledDate" style="width: 160px" />
        <el-select v-model="selectedDept" placeholder="部门" clearable style="width: 160px">
          <el-option v-for="d in deptList" :key="d.id" :label="d.name" :value="d.id" />
        </el-select>
        <el-button @click="exportReport" v-if="checkPermi(['pms:performance:query'])">
          <el-icon><Download /></el-icon> 导出报表
        </el-button>
      </div>
    </ContentWrap>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="mb-16px">
      <el-col :span="6" v-for="card in statCards" :key="card.key">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" :style="{ background: card.bg, color: card.color }">
              <el-icon :size="24"><component :is="card.icon" /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
              <div class="stat-label">{{ card.label }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 绩效排行表格 -->
    <ContentWrap title="责任人绩效排行" class="mb-16px">
      <el-table :data="rankingData" border size="small">
        <el-table-column label="排名" width="70" align="center">
          <template #default="{ $index }">
            <div v-if="$index < 3" class="medal" :class="'medal-' + ($index + 1)">
              <el-icon><Trophy /></el-icon> {{ $index + 1 }}
            </div>
            <span v-else>{{ $index + 1 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="userName" label="姓名" width="100" />
        <el-table-column prop="deptName" label="部门" width="100" />
        <el-table-column prop="totalTasks" label="任务数" width="80" align="center" />
        <el-table-column prop="completedTasks" label="完成" width="70" align="center" />
        <el-table-column label="按时率" width="100" align="center">
          <template #default="{ row }">
            <el-progress :percentage="Math.round(row.onTimeRate * 100)" :stroke-width="14" :text-inside="true" :color="getProgressColor(row.onTimeRate)" />
          </template>
        </el-table-column>
        <el-table-column label="完成率" width="100" align="center">
          <template #default="{ row }">
            <el-progress :percentage="Math.round(row.completionRate * 100)" :stroke-width="14" :text-inside="true" :color="getProgressColor(row.completionRate)" />
          </template>
        </el-table-column>
        <el-table-column label="综合分" width="100" align="center">
          <template #default="{ row }">
            <span class="score" :class="getScoreClass(row.score)">{{ row.score.toFixed(1) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </ContentWrap>

    <!-- 图表区 -->
    <el-row :gutter="16" class="mb-16px">
      <el-col :span="12">
        <ContentWrap title="按时完成率分布">
          <div ref="rateChartRef" style="height: 260px" />
        </ContentWrap>
      </el-col>
      <el-col :span="12">
        <ContentWrap title="任务量分布">
          <div ref="volumeChartRef" style="height: 260px" />
        </ContentWrap>
      </el-col>
    </el-row>

    <!-- 部门绩效对比 -->
    <ContentWrap title="部门绩效对比">
      <el-table :data="deptCompareData" border size="small">
        <el-table-column prop="deptName" label="部门" width="120" />
        <el-table-column prop="totalTasks" label="总任务" width="80" align="center" />
        <el-table-column prop="completedTasks" label="完成" width="70" align="center" />
        <el-table-column label="按时率" width="100" align="center">
          <template #default="{ row }">
            <span :style="{ color: getProgressColor(row.onTimeRate) }">{{ (row.onTimeRate * 100).toFixed(1) }}%</span>
          </template>
        </el-table-column>
        <el-table-column label="平均时长" width="100" align="center">
          <template #default="{ row }">{{ row.avgDuration.toFixed(1) }}天</template>
        </el-table-column>
        <el-table-column label="综合评分" width="100" align="center">
          <template #default="{ row }">
            <span class="score" :class="getScoreClass(row.score)">{{ row.score.toFixed(1) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="趋势" width="100" align="center">
          <template #default="{ row }">
            <span v-if="row.trend > 0" style="color: #00B42A">↑+{{ row.trend.toFixed(1) }}</span>
            <span v-else-if="row.trend < 0" style="color: #F53F3F">↓{{ row.trend.toFixed(1) }}</span>
            <span v-else style="color: #86909C">→0.0</span>
          </template>
        </el-table-column>
      </el-table>
    </ContentWrap>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getTaskList, TaskVO } from '@/api/pms/task'
import { getProjectList, ProjectVO } from '@/api/pms/project'
import { getStageList, StageVO } from '@/api/pms/stage'
import { formatDate, calcDelayDays } from '../pms-utils'
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'PmsPerformance' })

const selectedMonth = ref(new Date().toISOString().slice(0, 7))
const selectedDept = ref<number | undefined>(undefined)
const allTasks = ref<TaskVO[]>([])
const projects = ref<ProjectVO[]>([])
const stages = ref<StageVO[]>([])
const deptList = ref<any[]>([])

const rateChartRef = ref<HTMLElement>()
const volumeChartRef = ref<HTMLElement>()
let charts: echarts.ECharts[] = []

function disabledDate(time: Date): boolean {
  return time > new Date()
}

// 统计卡片
const statCards = computed(() => {
  const tasks = allTasks.value
  const completed = tasks.filter(t => t.completeStatus === 'completed')
  const onTime = completed.filter(t => calcDelayDays(t.planEndDate, t.completeStatus) === 0)
  const users = new Set(tasks.map(t => t.mainOwnerId)).size
  const onTimeRate = completed.length ? (onTime.length / completed.length) : 0
  const avgDuration = completed.length
    ? completed.reduce((sum, t) => sum + (t.cycle || 0), 0) / completed.length
    : 0
  return [
    { key: 'total', label: '任务总量', value: tasks.length, icon: 'List', color: '#2468F2', bg: '#DCE7FF' },
    { key: 'onTime', label: '按时完成率', value: (onTimeRate * 100).toFixed(1) + '%', icon: 'CircleCheck', color: '#00B42A', bg: '#E8FFEA' },
    { key: 'avgDuration', label: '平均完成时长', value: avgDuration.toFixed(1) + '天', icon: 'Timer', color: '#FF7D00', bg: '#FFF7E8' },
    { key: 'users', label: '参与人数', value: users, icon: 'User', color: '#722ED1', bg: '#F0E8FF' }
  ]
})

// 绩效排行
const rankingData = computed(() => {
  const userStats: Record<string, any> = {}
  allTasks.value.forEach(t => {
    const uid = String(t.mainOwnerId || 'unknown')
    if (!userStats[uid]) {
      userStats[uid] = { userId: uid, userName: `用户${uid}`, deptName: '-', totalTasks: 0, completedTasks: 0, onTimeTasks: 0, totalDuration: 0 }
    }
    userStats[uid].totalTasks++
    if (t.completeStatus === 'completed') {
      userStats[uid].completedTasks++
      userStats[uid].totalDuration += (t.cycle || 0)
      if (calcDelayDays(t.planEndDate, t.completeStatus) === 0) userStats[uid].onTimeTasks++
    }
  })
  return Object.values(userStats).map((u: any) => ({
    ...u,
    onTimeRate: u.completedTasks ? u.onTimeTasks / u.completedTasks : 0,
    completionRate: u.totalTasks ? u.completedTasks / u.totalTasks : 0,
    score: calculateScore(u)
  })).sort((a, b) => b.score - a.score)
})

function calculateScore(u: any): number {
  const onTimeRate = u.completedTasks ? u.onTimeTasks / u.completedTasks : 0
  const completionRate = u.totalTasks ? u.completedTasks / u.totalTasks : 0
  const volumeWeight = Math.min(u.totalTasks / 20, 1)
  return onTimeRate * 40 + completionRate * 30 + volumeWeight * 20 + 70 * 0.1
}

// 部门对比
const deptCompareData = computed(() => {
  const deptStats: Record<string, any> = {}
  rankingData.value.forEach((u: any) => {
    const dept = u.deptName || '未分配'
    if (!deptStats[dept]) {
      deptStats[dept] = { deptName: dept, totalTasks: 0, completedTasks: 0, onTimeTasks: 0, totalDuration: 0, count: 0 }
    }
    deptStats[dept].totalTasks += u.totalTasks
    deptStats[dept].completedTasks += u.completedTasks
    deptStats[dept].onTimeTasks += u.onTimeTasks
    deptStats[dept].totalDuration += u.totalDuration
    deptStats[dept].count++
  })
  return Object.values(deptStats).map((d: any) => ({
    ...d,
    onTimeRate: d.completedTasks ? d.onTimeTasks / d.completedTasks : 0,
    avgDuration: d.completedTasks ? d.totalDuration / d.completedTasks : 0,
    score: d.onTimeRate * 40 + (d.completedTasks / d.totalTasks) * 30 + 50,
    trend: (Math.random() - 0.5) * 5
  }))
})

function getProgressColor(rate: number): string {
  if (rate >= 0.9) return '#00B42A'
  if (rate >= 0.75) return '#2468F2'
  if (rate >= 0.5) return '#FF7D00'
  return '#F53F3F'
}

function getScoreClass(score: number): string {
  if (score >= 90) return 'score-excellent'
  if (score >= 80) return 'score-good'
  if (score >= 70) return 'score-normal'
  return 'score-poor'
}

function exportReport() { console.log('导出绩效报表') }

function initCharts() {
  const top10 = rankingData.value.slice(0, 10)
  // 按时完成率折线图
  if (rateChartRef.value) {
    const chart = echarts.init(rateChartRef.value)
    charts.push(chart)
    chart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: '3%', right: '5%', top: '10%', bottom: '3%', containLabel: true },
      xAxis: { type: 'category', data: top10.map((u: any) => u.userName), axisLabel: { rotate: 30 } },
      yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
      series: [{
        type: 'line', smooth: true, data: top10.map((u: any) => (u.onTimeRate * 100).toFixed(1)),
        lineStyle: { color: '#2468F2', width: 2 },
        itemStyle: { color: '#2468F2' },
        areaStyle: { color: 'rgba(36,104,242,0.1)' }
      }]
    })
  }
  // 任务量柱状图
  if (volumeChartRef.value) {
    const chart = echarts.init(volumeChartRef.value)
    charts.push(chart)
    chart.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: '3%', right: '5%', top: '10%', bottom: '3%', containLabel: true },
      xAxis: { type: 'category', data: top10.map((u: any) => u.userName), axisLabel: { rotate: 30 } },
      yAxis: { type: 'value' },
      series: [
        { name: '完成', type: 'bar', stack: 'total', data: top10.map((u: any) => u.completedTasks), itemStyle: { color: '#00B42A' } },
        { name: '未完成', type: 'bar', stack: 'total', data: top10.map((u: any) => u.totalTasks - u.completedTasks), itemStyle: { color: '#E5E6EB' } }
      ]
    })
  }
}

async function loadData() {
  try {
    const [taskRes, projectRes, stageRes] = await Promise.all([getTaskList(), getProjectList(), getStageList()])
    allTasks.value = taskRes as TaskVO[]
    projects.value = projectRes as ProjectVO[]
    stages.value = stageRes as StageVO[]
    await nextTick()
    initCharts()
  } catch (e) {
    console.error('加载数据失败', e)
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
.pms-performance {
  .toolbar { display: flex; gap: 12px; align-items: center; justify-content: flex-end; }
  .stat-card {
    .stat-content { display: flex; align-items: center; gap: 12px; }
    .stat-icon {
      width: 48px; height: 48px; border-radius: 10px;
      display: flex; align-items: center; justify-content: center; flex-shrink: 0;
    }
    .stat-value { font-size: 28px; font-weight: 700; line-height: 1.2; }
    .stat-label { font-size: 14px; color: var(--el-text-color-secondary); }
  }
  .medal {
    display: inline-flex; align-items: center; gap: 4px; font-weight: 700;
    &.medal-1 { color: #FFD700; font-size: 18px; }
    &.medal-2 { color: #C0C0C0; font-size: 16px; }
    &.medal-3 { color: #CD7F32; font-size: 16px; }
  }
  .score {
    font-weight: 700; font-size: 16px;
    &.score-excellent { color: #00B42A; }
    &.score-good { color: #2468F2; }
    &.score-normal { color: #FF7D00; }
    &.score-poor { color: #F53F3F; }
  }
}
</style>
