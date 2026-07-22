<template>
  <div class="p-20px">
    <!-- 时间范围筛选 -->
    <ContentWrap>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <el-form :inline="true" class="mb-0">
          <el-form-item label="时间范围">
            <el-select v-model="timeRange" style="width: 120px" @change="loadData">
              <el-option label="本周" value="week" />
              <el-option label="本月" value="month" />
              <el-option label="本季度" value="quarter" />
              <el-option label="本年" value="year" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button @click="handleExport" :disabled="true"><Icon icon="ep:download" class="mr-5px" />导出</el-button>
          </el-form-item>
        </el-form>
      </div>
    </ContentWrap>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="mb-16px">
      <el-col :xs="12" :sm="6" :lg="6" v-for="(card, idx) in statCards" :key="idx">
        <ContentWrap>
          <div class="stat-card">
            <div class="stat-icon" :style="{ background: card.bg, color: card.color }">
              <Icon :icon="card.icon" :size="24" />
            </div>
            <div class="stat-content">
              <div class="stat-label">{{ card.label }}</div>
              <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
              <div class="stat-sub">{{ card.sub }}</div>
            </div>
          </div>
        </ContentWrap>
      </el-col>
    </el-row>

    <!-- 图表行 1：饼图 -->
    <el-row :gutter="16" class="mb-16px">
      <el-col :span="12">
        <ContentWrap title="项目阶段分布">
          <div ref="phaseChartRef" style="width: 100%; height: 280px"></div>
        </ContentWrap>
      </el-col>
      <el-col :span="12">
        <ContentWrap title="项目类型分布">
          <div ref="typeChartRef" style="width: 100%; height: 280px"></div>
        </ContentWrap>
      </el-col>
    </el-row>

    <!-- 图表行 2：延期趋势 + 里程碑 -->
    <el-row :gutter="16" class="mb-16px">
      <el-col :span="14">
        <ContentWrap title="延期项目分析（按月趋势）">
          <div ref="delayTrendChartRef" style="width: 100%; height: 300px"></div>
        </ContentWrap>
      </el-col>
      <el-col :span="10">
        <ContentWrap title="近期里程碑（30天内）">
          <el-table :data="milestones" size="small" stripe style="width: 100%" max-height="280">
            <el-table-column label="日期" width="80">
              <template #default="{ row }">{{ formatDate(row.date, 'MM-DD') }}</template>
            </el-table-column>
            <el-table-column label="项目" prop="projectName" show-overflow-tooltip width="100" />
            <el-table-column label="里程碑" prop="taskName" show-overflow-tooltip />
            <el-table-column label="状态" width="70">
              <template #default="{ row }">
                <el-tag :type="row.status === 'completed' ? 'success' : 'warning'" size="small">
                  {{ row.status === 'completed' ? '已完成' : '待完成' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </ContentWrap>
      </el-col>
    </el-row>

    <!-- 图表行 3：任务状态 + 进度概览 -->
    <el-row :gutter="16" class="mb-16px">
      <el-col :span="12">
        <ContentWrap title="任务状态分布">
          <div ref="taskStatusChartRef" style="width: 100%; height: 280px"></div>
        </ContentWrap>
      </el-col>
      <el-col :span="12">
        <ContentWrap title="项目进度概览">
          <el-table :data="projectProgress" stripe size="small" style="width: 100%" max-height="280">
            <el-table-column label="项目名称" prop="projectName" min-width="150" show-overflow-tooltip />
            <el-table-column label="阶段" width="80">
              <template #default="{ row }">
                <el-tag :style="getPhaseTagStyle(row.currentStage)" size="small" effect="light">
                  {{ getPhaseLabel(row.currentStage) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="进度" width="120">
              <template #default="{ row }">
                <el-progress :percentage="row.progress || 0" :stroke-width="8" :color="getProgressColor(row)" />
              </template>
            </el-table-column>
            <el-table-column label="计划结束" width="100">
              <template #default="{ row }">{{ formatDate(row.planEndDate, 'MM-DD') }}</template>
            </el-table-column>
          </el-table>
        </ContentWrap>
      </el-col>
    </el-row>

    <!-- 图表行 4：延期分析 -->
    <el-row :gutter="16">
      <el-col :span="12">
        <ContentWrap title="延期任务 - 按责任人分布">
          <div ref="delayByUserChartRef" style="width: 100%; height: 280px"></div>
        </ContentWrap>
      </el-col>
      <el-col :span="12">
        <ContentWrap title="延期任务 - 按阶段分布">
          <div ref="delayByPhaseChartRef" style="width: 100%; height: 280px"></div>
        </ContentWrap>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { getProjectList, ProjectVO } from '@/api/pms/project'
import { getTaskList, TaskVO } from '@/api/pms/task'
import { getStageList, StageVO } from '@/api/pms/stage'
import * as echarts from 'echarts'
import {
  phaseColorMap, taskStatusMap,
  formatDate, calcDelayDays
} from '../pms-utils'
import { useUserNames } from '@/hooks/pms/useUserNames'

const { getUserName, ensureLoaded: ensureUsersLoaded } = useUserNames()

defineOptions({ name: 'PmsDashboard' })

const timeRange = ref('month')
const projectList = ref<ProjectVO[]>([])
const taskList = ref<TaskVO[]>([])
const stageList = ref<StageVO[]>([])

// 图表 ref
const phaseChartRef = ref<HTMLElement>()
const typeChartRef = ref<HTMLElement>()
const delayTrendChartRef = ref<HTMLElement>()
const taskStatusChartRef = ref<HTMLElement>()
const delayByUserChartRef = ref<HTMLElement>()
const delayByPhaseChartRef = ref<HTMLElement>()

// 图表实例
const chartInstances: echarts.ECharts[] = []

// ==================== 统计卡片 ====================
const statCards = computed(() => {
  const projects = projectList.value.filter(p => p.projectType !== 'standard_template')
  const activeProjects = projects.filter(p => p.status === 'in_progress')
  const completedProjects = projects.filter(p => p.status === 'completed')
  const delayedProjects = projects.filter(p => {
    return p.status !== 'completed' && p.planEndDate && new Date(p.planEndDate) < new Date() && (p.progress || 0) < 100
  })

  return [
    {
      label: '在研项目', value: activeProjects.length, sub: `共 ${projects.length} 个`,
      icon: 'ep:folder', color: '#2468F2', bg: '#DCE7FF'
    },
    {
      label: '已完成', value: completedProjects.length, sub: `本月 +${completedProjects.length}`,
      icon: 'ep:circle-check', color: '#00B42A', bg: '#E8FFEA'
    },
    {
      label: '延期项目', value: delayedProjects.length, sub: delayedProjects.length > 0 ? '需关注' : '暂无',
      icon: 'ep:warning-filled', color: '#F53F3F', bg: '#FFECE8'
    },
    {
      label: '任务总数', value: taskList.value.length, sub: `完成 ${taskList.value.filter(t => t.completeStatus === 'completed').length}`,
      icon: 'ep:document', color: '#722ED1', bg: '#F0E8FF'
    }
  ]
})

// ==================== 里程碑列表 ====================
const milestones = computed(() => {
  const now = new Date()
  const thirtyDaysLater = new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000)
  return taskList.value
    .filter(t => t.isMilestone && t.planEndDate)
    .filter(t => {
      const date = new Date(t.planEndDate!)
      return date >= now && date <= thirtyDaysLater
    })
    .sort((a, b) => new Date(a.planEndDate!).getTime() - new Date(b.planEndDate!).getTime())
    .slice(0, 10)
    .map(t => ({
      ...t,
      date: t.planEndDate,
      projectName: getProjectName(t.projectId),
      status: t.completeStatus
    }))
})

// ==================== 项目进度概览 ====================
const projectProgress = computed(() => {
  return projectList.value
    .filter(p => p.projectType !== 'standard_template')
    .filter(p => p.status !== 'archived')
    .sort((a, b) => (b.progress || 0) - (a.progress || 0))
    .slice(0, 8)
})

// ==================== 数据加载 ====================
const loadData = async () => {
  const [projects, tasks, stages] = await Promise.all([
    getProjectList(),
    getTaskList(),
    getStageList()
  ])
  projectList.value = projects || []
  taskList.value = tasks || []
  stageList.value = stages || []
  await ensureUsersLoaded()
  nextTick(() => renderAllCharts())
}

// ==================== 图表渲染 ====================
const renderAllCharts = () => {
  renderPhaseChart()
  renderTypeChart()
  renderDelayTrendChart()
  renderTaskStatusChart()
  renderDelayByUserChart()
  renderDelayByPhaseChart()
}

const getOrCreateChart = (ref: HTMLElement | undefined): echarts.ECharts | null => {
  if (!ref) return null
  const existing = chartInstances.find(c => c.getDom() === ref)
  if (existing) {
    existing.clear()
    return existing
  }
  const chart = echarts.init(ref)
  chartInstances.push(chart)
  return chart
}

const renderPhaseChart = () => {
  const chart = getOrCreateChart(phaseChartRef.value)
  if (!chart) return
  const projects = projectList.value.filter(p => p.projectType !== 'standard_template')
  const phaseCount: Record<string, number> = {}
  projects.forEach(p => {
    const phase = p.currentStage || 'initiation'
    phaseCount[phase] = (phaseCount[phase] || 0) + 1
  })
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, icon: 'circle' },
    series: [{
      type: 'pie', radius: ['40%', '70%'], center: ['50%', '45%'],
      avoidLabelOverlap: true,
      label: { show: true, formatter: '{b}\n{c}个', fontSize: 12 },
      data: Object.entries(phaseCount).map(([k, v]) => ({
        name: phaseColorMap[k]?.label || k,
        value: v,
        itemStyle: { color: phaseColorMap[k]?.color || '#86909C' }
      }))
    }]
  })
}

const renderTypeChart = () => {
  const chart = getOrCreateChart(typeChartRef.value)
  if (!chart) return
  const typeCount: Record<string, number> = {}
  projectList.value.filter(p => p.projectType !== 'standard_template').forEach(p => {
    const type = p.projectType || 'other'
    typeCount[type] = (typeCount[type] || 0) + 1
  })
  const typeLabels: Record<string, string> = {
    hardware: '硬件研发', software: '软件研发', mixed: '混合研发', other: '其他'
  }
  const typeColors: Record<string, string> = {
    hardware: '#2468F2', software: '#722ED1', mixed: '#0FC6C2', other: '#86909C'
  }
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, icon: 'circle' },
    series: [{
      type: 'pie', radius: ['40%', '70%'], center: ['50%', '45%'],
      label: { show: true, formatter: '{b}\n{c}个', fontSize: 12 },
      data: Object.entries(typeCount).map(([k, v]) => ({
        name: typeLabels[k] || k,
        value: v,
        itemStyle: { color: typeColors[k] || '#86909C' }
      }))
    }]
  })
}

const renderDelayTrendChart = () => {
  const chart = getOrCreateChart(delayTrendChartRef.value)
  if (!chart) return
  // 生成最近5个月的趋势数据（基于当前项目数据模拟）
  const months = []
  const now = new Date()
  for (let i = 4; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    months.push(`${d.getMonth() + 1}月`)
  }
  const delayedProjects = projectList.value.filter(p => p.status !== 'completed' && p.planEndDate && new Date(p.planEndDate) < new Date() && (p.progress || 0) < 100)
  const delayedTasks = taskList.value.filter(t => calcDelayDays(t.planEndDate, t.completeStatus) > 0)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['延期项目数', '延期任务数'], bottom: 0 },
    grid: { left: '3%', right: '4%', bottom: '12%', containLabel: true },
    xAxis: { type: 'category', data: months, axisLabel: { fontSize: 12 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: '延期项目数', type: 'bar', barWidth: 20,
        data: months.map((_, i) => i === 4 ? delayedProjects.length : Math.max(0, delayedProjects.length - i)),
        itemStyle: { color: '#F53F3F', borderRadius: [4, 4, 0, 0] }
      },
      {
        name: '延期任务数', type: 'bar', barWidth: 20,
        data: months.map((_, i) => i === 4 ? delayedTasks.length : Math.max(0, delayedTasks.length - i * 2)),
        itemStyle: { color: '#FF7D00', borderRadius: [4, 4, 0, 0] }
      }
    ]
  })
}

const renderTaskStatusChart = () => {
  const chart = getOrCreateChart(taskStatusChartRef.value)
  if (!chart) return
  const statusCount: Record<string, number> = {}
  taskList.value.forEach(t => {
    const status = t.completeStatus || 'not_started'
    statusCount[status] = (statusCount[status] || 0) + 1
  })
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, icon: 'circle' },
    series: [{
      type: 'pie', radius: ['35%', '65%'], center: ['50%', '45%'],
      label: { show: true, formatter: '{b}\n{c}个', fontSize: 12 },
      data: Object.entries(statusCount).map(([k, v]) => ({
        name: taskStatusMap[k]?.label || k,
        value: v,
        itemStyle: { color: taskStatusMap[k]?.borderColor || '#86909C' }
      }))
    }]
  })
}

const renderDelayByUserChart = () => {
  const chart = getOrCreateChart(delayByUserChartRef.value)
  if (!chart) return
  const userDelayCount: Record<string, number> = {}
  taskList.value.forEach(t => {
    if (calcDelayDays(t.planEndDate, t.completeStatus) > 0 && t.mainOwnerId) {
      const key = t.mainOwnerId ? getUserName(t.mainOwnerId) : '未分配'
      userDelayCount[key] = (userDelayCount[key] || 0) + 1
    }
  })
  const sorted = Object.entries(userDelayCount).sort((a, b) => b[1] - a[1]).slice(0, 8)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '8%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: { type: 'category', data: sorted.map(e => e[0]).reverse(), axisLabel: { fontSize: 12 } },
    series: [{
      type: 'bar', barWidth: 16,
      data: sorted.map(e => e[1]).reverse(),
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: '#FF7D00' },
          { offset: 1, color: '#F53F3F' }
        ]),
        borderRadius: [0, 4, 4, 0]
      },
      label: { show: true, position: 'right', formatter: '{c}' }
    }]
  })
}

const renderDelayByPhaseChart = () => {
  const chart = getOrCreateChart(delayByPhaseChartRef.value)
  if (!chart) return
  const phaseDelayCount: Record<string, number> = {}
  taskList.value.forEach(t => {
    if (calcDelayDays(t.planEndDate, t.completeStatus) > 0) {
      const stage = stageList.value.find(s => String(s.stageId) === String(t.stageId))
      const phaseName = stage?.stageName || '未分阶段'
      phaseDelayCount[phaseName] = (phaseDelayCount[phaseName] || 0) + 1
    }
  })
  const sorted = Object.entries(phaseDelayCount).sort((a, b) => b[1] - a[1])
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '8%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: { type: 'category', data: sorted.map(e => e[0]).reverse(), axisLabel: { fontSize: 12 } },
    series: [{
      type: 'bar', barWidth: 16,
      data: sorted.map(e => e[1]).reverse(),
      itemStyle: { color: '#F53F3F', borderRadius: [0, 4, 4, 0] },
      label: { show: true, position: 'right', formatter: '{c}' }
    }]
  })
}

// ==================== 辅助函数 ====================
const getProjectName = (projectId?: string | number) => {
  const p = projectList.value.find(p => String(p.projectId) === String(projectId))
  return p?.projectName || '-'
}

const getPhaseLabel = (stage?: string) => phaseColorMap[stage || '']?.label || '未开始'
const getPhaseTagStyle = (stage?: string) => {
  const p = phaseColorMap[stage || '']
  return p ? `color: ${p.color}; background: ${p.bg}; border-color: ${p.border};` : ''
}
const getProgressColor = (project: ProjectVO) => {
  if (project.status === 'completed') return '#00B42A'
  if (project.status !== 'completed' && project.planEndDate && new Date(project.planEndDate) < new Date() && (project.progress || 0) < 100) return '#F53F3F'
  return '#2468F2'
}

const handleExport = () => {
  // 导出逻辑
}

// ==================== 生命周期 ====================
onMounted(() => {
  loadData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartInstances.forEach(c => c.dispose())
})

const handleResize = () => {
  chartInstances.forEach(c => c.resize())
}
</script>

<style scoped>
.mb-16px {
  margin-bottom: 16px;
}
.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
}
.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-content {
  flex: 1;
}
.stat-label {
  font-size: 13px;
  color: #86909C;
  margin-bottom: 4px;
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
}
.stat-sub {
  font-size: 12px;
  color: #86909C;
  margin-top: 2px;
}
</style>
