<template>
  <div class="p-20px">
    <!-- 筛选区：部门 + 项目名称 -->
    <ContentWrap>
      <el-form :inline="true" class="mb-0">
        <el-form-item v-if="deptTreeData.length > 0" label="部门">
          <el-tree-select
            v-model="selectedDeptId"
            :data="deptTreeData"
            :props="{ value: 'id', label: 'name', children: 'children' }"
            node-key="id"
            placeholder="全部部门"
            clearable
            check-strictly
            style="width: 220px"
            @change="loadData"
          />
        </el-form-item>
        <el-form-item label="项目名称">
          <el-input
            v-model="projectName"
            placeholder="输入项目名称搜索"
            clearable
            style="width: 220px"
            @clear="loadData"
            @keyup.enter="loadData"
          >
            <template #prefix><Icon icon="ep:search" /></template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData"><Icon icon="ep:refresh" class="mr-5px" />刷新</el-button>
        </el-form-item>
      </el-form>
    </ContentWrap>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="mb-16px">
      <el-col :xs="12" :sm="6" v-for="(card, idx) in statCards" :key="idx">
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

    <!-- 图表行 1：完成率 + 延期占比 -->
    <el-row :gutter="16" class="mb-16px">
      <el-col :span="12">
        <ContentWrap title="部门任务完成率">
          <div ref="completionChartRef" style="width: 100%; height: 320px"></div>
        </ContentWrap>
      </el-col>
      <el-col :span="12">
        <ContentWrap title="延期任务部门占比">
          <div ref="delayPieRef" style="width: 100%; height: 320px"></div>
        </ContentWrap>
      </el-col>
    </el-row>

    <!-- 图表行 2：参与项目数 -->
    <el-row :gutter="16" class="mb-16px">
      <el-col :span="24">
        <ContentWrap title="各部门参与项目数">
          <div ref="projectCountRef" style="width: 100%; height: 300px"></div>
        </ContentWrap>
      </el-col>
    </el-row>

    <!-- 明细表 -->
    <ContentWrap title="部门协作明细">
      <el-table :data="deptList" stripe size="small" style="width: 100%" max-height="420">
        <el-table-column label="部门" prop="deptName" min-width="140" show-overflow-tooltip />
        <el-table-column label="参与项目数" prop="projectCount" width="100" align="center" />
        <el-table-column label="成员数" prop="memberCount" width="90" align="center" />
        <el-table-column label="任务总数" prop="taskTotal" width="90" align="center" />
        <el-table-column label="已完成" prop="taskCompleted" width="80" align="center" />
        <el-table-column label="延期" prop="taskDelayed" width="80" align="center">
          <template #default="{ row }">
            <span :style="{ color: row.taskDelayed > 0 ? '#F53F3F' : '' }">{{ row.taskDelayed }}</span>
          </template>
        </el-table-column>
        <el-table-column label="完成率" width="160">
          <template #default="{ row }">
            <el-progress :percentage="row.completionRate || 0" :stroke-width="8" :color="getCompletionColor(row)" />
          </template>
        </el-table-column>
        <el-table-column label="延期占比" width="100" align="center">
          <template #default="{ row }">{{ row.delayRate }}%</template>
        </el-table-column>
      </el-table>
    </ContentWrap>
  </div>
</template>

<script setup lang="ts">
/**
 * 部门协作分析（PMS 新增菜单）
 * 数据口径：成员部门来自 system_users.dept_id；任务完成/延期按负责人所属部门统计。
 */
import { getDeptStats, getVisibleDeptTree, buildDeptTree, DeptStatVO, PmsDeptTreeNode } from '@/api/pms/dashboard'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'

defineOptions({ name: 'PmsDeptAnalysis' })

const deptList = ref<DeptStatVO[]>([])
const deptTreeData = ref<PmsDeptTreeNode[]>([])
const selectedDeptId = ref<number | string | undefined>(undefined)
const projectName = ref('')

const completionChartRef = ref<HTMLElement>()
const delayPieRef = ref<HTMLElement>()
const projectCountRef = ref<HTMLElement>()
const chartInstances: echarts.ECharts[] = []

// 统计卡片
const statCards = computed(() => {
  const list = deptList.value
  const deptCount = list.filter(d => d.deptId !== 0).length
  const memberSum = list.reduce((s, d) => s + (d.memberCount || 0), 0)
  const totalTasks = list.reduce((s, d) => s + (d.taskTotal || 0), 0)
  const completedTasks = list.reduce((s, d) => s + (d.taskCompleted || 0), 0)
  const delayedTasks = list.reduce((s, d) => s + (d.taskDelayed || 0), 0)
  const avgCompletion = totalTasks > 0 ? Math.round((completedTasks / totalTasks) * 1000) / 10 : 0
  return [
    { label: '参与部门', value: deptCount, sub: '含任务的部门', icon: 'ep:office-building', color: '#2468F2', bg: '#DCE7FF' },
    { label: '参与成员', value: memberSum, sub: '去重人数', icon: 'ep:user', color: '#722ED1', bg: '#F0E8FF' },
    { label: '平均完成率', value: avgCompletion + '%', sub: '已完成 ' + completedTasks + '/' + totalTasks, icon: 'ep:circle-check', color: '#00B42A', bg: '#E8FFEA' },
    { label: '延期任务', value: delayedTasks, sub: delayedTasks > 0 ? '需关注' : '暂无', icon: 'ep:warning-filled', color: '#F53F3F', bg: '#FFECE8' }
  ]
})

const getCompletionColor = (row: DeptStatVO) => {
  if (row.completionRate >= 80) return '#00B42A'
  if (row.completionRate >= 50) return '#FF7D00'
  return '#F53F3F'
}

const loadData = async () => {
  try {
    const data = await getDeptStats({
      deptId: selectedDeptId.value ? Number(selectedDeptId.value) : undefined,
      projectName: projectName.value.trim() || undefined
    })
    deptList.value = data || []
    nextTick(() => renderAllCharts())
  } catch (e) {
    console.error('加载部门协作数据失败', e)
    ElMessage.error('加载部门协作数据失败，请重试')
  }
}

const loadDeptTree = async () => {
  try {
    const list = await getVisibleDeptTree()
    deptTreeData.value = buildDeptTree(list || [])
  } catch (e) {
    console.warn('加载部门树失败', e)
  }
}

// ==================== 图表 ====================
const getOrCreateChart = (el: HTMLElement | undefined): echarts.ECharts | null => {
  if (!el) return null
  const existing = chartInstances.find(c => c.getDom() === el)
  if (existing) { existing.clear(); return existing }
  const chart = echarts.init(el)
  chartInstances.push(chart)
  return chart
}

const renderAllCharts = () => {
  renderCompletionChart()
  renderDelayPie()
  renderProjectCountChart()
}

const renderCompletionChart = () => {
  const chart = getOrCreateChart(completionChartRef.value)
  if (!chart) return
  const sorted = [...deptList.value].sort((a, b) => (a.completionRate || 0) - (b.completionRate || 0)).slice(-15)
  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: (p: any) => p[0].name + '<br/>完成率 ' + p[0].value + '%（已完成 ' + sorted[p[0].dataIndex].taskCompleted + '/' + sorted[p[0].dataIndex].taskTotal + '）' },
    grid: { left: '3%', right: '8%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
    yAxis: { type: 'category', data: sorted.map(d => d.deptName), axisLabel: { fontSize: 12 } },
    series: [{
      type: 'bar', barWidth: 16,
      data: sorted.map(d => d.completionRate || 0),
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: '#2468F2' }, { offset: 1, color: '#00B42A' }
        ]), borderRadius: [0, 4, 4, 0]
      },
      label: { show: true, position: 'right', formatter: '{c}%' }
    }]
  })
}

const renderDelayPie = () => {
  const chart = getOrCreateChart(delayPieRef.value)
  if (!chart) return
  const data = deptList.value.filter(d => d.taskDelayed > 0)
  const hasData = data.length > 0
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 个 ({d}%)' },
    legend: { bottom: 0, icon: 'circle', type: 'scroll' },
    series: [{
      type: 'pie', radius: ['38%', '68%'], center: ['50%', '45%'],
      label: { show: true, formatter: '{b}\n{c}个', fontSize: 12 },
      data: data.map(d => ({ name: d.deptName, value: d.taskDelayed }))
    }],
    graphic: hasData ? undefined : [{ type: 'text', left: 'center', top: 'middle', style: { text: '暂无延期任务', fontSize: 14, fill: '#86909C' } }]
  })
}

const renderProjectCountChart = () => {
  const chart = getOrCreateChart(projectCountRef.value)
  if (!chart) return
  const sorted = [...deptList.value].filter(d => d.projectCount > 0).sort((a, b) => (a.projectCount || 0) - (b.projectCount || 0))
  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '8%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: { type: 'category', data: sorted.map(d => d.deptName), axisLabel: { fontSize: 12 } },
    series: [{
      type: 'bar', barWidth: 16,
      data: sorted.map(d => d.projectCount || 0),
      itemStyle: { color: '#2468F2', borderRadius: [0, 4, 4, 0] },
      label: { show: true, position: 'right', formatter: '{c} 个项目' }
    }]
  })
}

const handleResize = () => chartInstances.forEach(c => c.resize())

onMounted(async () => {
  await loadDeptTree()
  await loadData()
  window.addEventListener('resize', handleResize)
})
onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartInstances.forEach(c => c.dispose())
})
</script>

<style scoped>
.mb-16px { margin-bottom: 16px; }
.stat-card { display: flex; align-items: center; gap: 16px; }
.stat-icon { width: 48px; height: 48px; border-radius: 8px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.stat-content { flex: 1; }
.stat-label { font-size: 13px; color: #86909C; margin-bottom: 4px; }
.stat-value { font-size: 28px; font-weight: 700; line-height: 1.2; }
.stat-sub { font-size: 12px; color: #86909C; margin-top: 2px; }
.mr-5px { margin-right: 5px; }
</style>
