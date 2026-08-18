<template>
  <div class="p-20px">
    <!-- 筛选区：时间范围 + 部门 + 项目名称（#9 BI 部门数据权限 + 项目维度） -->
    <ContentWrap>
      <el-form :inline="true" class="mb-0">
        <el-form-item label="时间范围">
          <el-select v-model="timeRange" style="width: 120px" @change="loadData">
            <el-option label="本周" value="week" />
            <el-option label="本月" value="month" />
            <el-option label="本季度" value="quarter" />
            <el-option label="本年" value="year" />
          </el-select>
        </el-form-item>
        <!-- #9 部门筛选器：只显示自己有权看的部门树，默认选中自己所在部门 -->
        <el-form-item v-if="deptTreeData.length > 0" label="部门">
          <el-tree-select
            v-model="selectedDeptId"
            :data="deptTreeData"
            :props="{ value: 'id', label: 'name', children: 'children' }"
            node-key="id"
            placeholder="全部可见部门"
            clearable
            check-strictly
            style="width: 220px"
            @change="loadData"
          />
        </el-form-item>
        <!-- 项目名称搜索：统计卡片/图表/列表全部联动过滤 -->
        <el-form-item label="项目名称">
          <el-input
            v-model="searchProjectName"
            placeholder="输入项目名称搜索"
            clearable
            style="width: 220px"
            @clear="handleSearch"
            @keyup.enter="handleSearch"
          >
            <template #prefix><Icon icon="ep:search" /></template>
          </el-input>
        </el-form-item>
      </el-form>
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

    <!-- 项目健康度矩阵 -->
    <el-card class="mt-16px" header="项目健康度">
      <div class="health-matrix">
        <div v-if="projectHealthList.length === 0" class="text-center text-gray py-20px">暂无项目数据，请先创建项目</div>
        <div v-for="p in projectHealthList" :key="p.projectId" class="health-item" @click="goToProject(p.projectId)">
          <el-tag :type="p.healthType" size="small">{{ p.healthLabel }}</el-tag>
          <span class="ml-8px">{{ p.projectName }}</span>
          <span class="ml-auto">{{ p.progress }}%</span>
        </div>
      </div>
    </el-card>

    <!-- 延期项目 TOP 5 -->
    <el-card class="mt-16px" header="延期项目 TOP 5">
      <div v-if="delayedProjects.length === 0" class="text-center text-gray py-20px">暂无延期项目，项目进度均正常</div>
      <div v-for="p in delayedProjects" :key="p.projectId" class="health-item" @click="goToProject(p.projectId)">
        <el-tag type="danger" size="small">延期{{ p.delayDays }}天</el-tag>
        <span class="ml-8px">{{ p.projectName }}</span>
        <span class="ml-auto text-red">{{ p.progress }}%</span>
      </div>
    </el-card>

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
      <el-col :span="12">
        <ContentWrap title="延期项目分析（按月趋势）">
          <div ref="delayTrendChartRef" style="width: 100%; height: 280px"></div>
        </ContentWrap>
      </el-col>
      <el-col :span="12">
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

    <!-- 图表行 5：按部门维度（部门数据权限） -->
    <el-row :gutter="16">
      <el-col :span="12">
        <ContentWrap title="部门任务完成率">
          <div ref="deptCompletionChartRef" style="width: 100%; height: 280px"></div>
        </ContentWrap>
      </el-col>
      <el-col :span="12">
        <ContentWrap title="延期任务 - 按部门分布">
          <div ref="deptDelayChartRef" style="width: 100%; height: 280px"></div>
        </ContentWrap>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
/**
 * PMS 看板（#9 BI 看板按部门数据权限 改造版）
 *
 * 改造点：
 * 1. 顶部新增「部门筛选器」（el-tree-select），只显示自己有权看的部门树，
 *    默认选中当前用户所在部门（如可用）；超管看到全部部门。
 * 2. 数据源从原 getProjectList/getTaskList/getStageList 切换到
 *    /pms/dashboard/{projects,tasks,stages,depts}，由后端 PmsDataScopeService
 *    统一按"超管/部门负责人/普通用户"三档过滤。
 * 3. 部门筛选器选中后，后端按 deptId 二次过滤（含下级部门）。
 *
 * 兼容性：原 ProjectVO/TaskVO/StageVO 结构不变，前端无需改类型。
 */
import {
  getDashboardProjects,
  getDashboardTasks,
  getDashboardStages,
  getVisibleDeptTree,
  buildDeptTree,
  getDeptStats,
  DeptStatVO,
  PmsDeptTreeNode
} from '@/api/pms/dashboard'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import {
  phaseColorMap, taskStatusMap,
  formatDate, calcDelayDays
} from '../pms-utils'
import { useUserNames } from '@/hooks/pms/useUserNames'
import { useUserStore } from '@/store/modules/user'
import { useRouter } from 'vue-router'

const { getUserName, ensureLoaded: ensureUsersLoaded } = useUserNames()
const router = useRouter()
const userStore = useUserStore()

defineOptions({ name: 'PmsDashboard' })

const timeRange = ref('month')
const projectList = ref<any[]>([])
const taskList = ref<any[]>([])
const stageList = ref<any[]>([])

// #9 部门筛选器
const deptTreeData = ref<PmsDeptTreeNode[]>([])
const selectedDeptId = ref<number | string | undefined>(undefined)
const userDeptIdLoaded = ref(false)
// 项目名称搜索（新增筛选维度，联动全部统计卡片/图表/列表）
const searchProjectName = ref('')

// P0-1: 根据 timeRange 计算时间范围起止日期
const timeRangeDates = computed(() => {
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  switch (timeRange.value) {
    case 'week': {
      const dayOfWeek = today.getDay()
      const monday = new Date(today)
      monday.setDate(today.getDate() - (dayOfWeek === 0 ? 6 : dayOfWeek - 1))
      const sunday = new Date(monday)
      sunday.setDate(monday.getDate() + 6)
      return { start: monday, end: sunday }
    }
    case 'month': {
      const first = new Date(now.getFullYear(), now.getMonth(), 1)
      const last = new Date(now.getFullYear(), now.getMonth() + 1, 0)
      return { start: first, end: last }
    }
    case 'quarter': {
      const qStart = Math.floor(now.getMonth() / 3) * 3
      const first = new Date(now.getFullYear(), qStart, 1)
      const last = new Date(now.getFullYear(), qStart + 3, 0)
      return { start: first, end: last }
    }
    case 'year': {
      const first = new Date(now.getFullYear(), 0, 1)
      const last = new Date(now.getFullYear(), 11, 31)
      return { start: first, end: last }
    }
    default:
      return { start: new Date(0), end: new Date(2099, 11, 31) }
  }
})

// 项目名称搜索命中的项目ID集合（null = 未搜索）
const matchingProjectIds = computed<Set<string> | null>(() => {
  const kw = searchProjectName.value.trim().toLowerCase()
  if (!kw) return null
  return new Set(
    projectList.value
      .filter(p => p.projectName && p.projectName.toLowerCase().includes(kw))
      .map(p => String(p.projectId))
  )
})

// 搜索范围内的项目（仅项目名称过滤 + 排除模板，不含时间过滤；供健康度/月度趋势等使用）
const scopedProjects = computed(() => {
  const ids = matchingProjectIds.value
  return projectList.value.filter(p =>
    p.projectType !== 'standard_template' && (ids == null || ids.has(String(p.projectId)))
  )
})

// 搜索范围内的任务（仅项目名称过滤，不含时间过滤）
const scopedTasks = computed(() => {
  const ids = matchingProjectIds.value
  if (ids == null) return taskList.value
  return taskList.value.filter(t => ids.has(String(t.projectId)))
})

// P0-1: 按时间范围 + 项目名称过滤后的项目列表
const filteredProjects = computed(() => {
  const { start, end } = timeRangeDates.value
  return scopedProjects.value.filter(p => {
    if (!p.createTime) return true // 无创建时间的不做过滤
    const t = new Date(p.createTime)
    return t >= start && t <= end
  })
})

// P0-1: 按时间范围 + 项目名称过滤后的任务列表
const filteredTasks = computed(() => {
  const { start, end } = timeRangeDates.value
  return scopedTasks.value.filter(t => {
    if (!t.createTime) return true
    const ct = new Date(t.createTime)
    return ct >= start && ct <= end
  })
})

// 图表 ref
const phaseChartRef = ref<HTMLElement>()
const typeChartRef = ref<HTMLElement>()
const delayTrendChartRef = ref<HTMLElement>()
const taskStatusChartRef = ref<HTMLElement>()
const delayByUserChartRef = ref<HTMLElement>()
const delayByPhaseChartRef = ref<HTMLElement>()
const deptCompletionChartRef = ref<HTMLElement>()
const deptDelayChartRef = ref<HTMLElement>()
const deptStatsData = ref<DeptStatVO[]>([])

// 图表实例
const chartInstances: echarts.ECharts[] = []

// ==================== 统计卡片 ====================
const statCards = computed(() => {
  const projects = filteredProjects.value
  const tasks = filteredTasks.value
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
      label: '任务总数', value: tasks.length, sub: `完成 ${tasks.filter(t => t.completeStatus === 'completed').length}`,
      icon: 'ep:document', color: '#722ED1', bg: '#F0E8FF'
    }
  ]
})

// ==================== 里程碑列表 ====================
const milestones = computed(() => {
  const now = new Date()
  const thirtyDaysLater = new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000)
  return filteredTasks.value
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
  return filteredProjects.value
    .filter(p => p.status !== 'archived')
    .sort((a, b) => (b.progress || 0) - (a.progress || 0))
    .slice(0, 8)
})

// ==================== 项目健康度 ====================
const parseDate = (d: any): Date => {
  if (!d) return new Date(NaN)
  if (d instanceof Date) return d
  if (Array.isArray(d)) return new Date(d[0], (d[1] || 1) - 1, d[2] || 1)
  return new Date(d)
}

const projectHealthList = computed(() => {
  return scopedProjects.value.map(p => {
    // 优先使用后端实时计算的 progress，确保各页面口径统一
    let progress = p.progress || 0
    if (progress === 0) {
      // 后端未提供时，前端按完成任务数占比计算
      const tasks = taskList.value.filter(t => String(t.projectId) === String(p.projectId))
      const total = tasks.length
      const completed = tasks.filter(t => t.completeStatus === 'completed').length
      progress = total > 0 ? Math.round(completed / total * 100) : 0
    }
    // 计算健康度
    let healthType: any = 'success'
    let healthLabel = '正常'
    if (p.planEndDate) {
      const end = parseDate(p.planEndDate)
      const remaining = Math.ceil((end.getTime() - Date.now()) / (1000 * 60 * 60 * 24))
      if (remaining < 0) {
        healthType = 'danger'
        healthLabel = '已延期'
      } else if (remaining < 30 && progress < 50) {
        healthType = 'warning'
        healthLabel = '有风险'
      } else if (remaining < 7 && progress < 80) {
        healthType = 'warning'
        healthLabel = '即将到期'
      }
    }
    return { ...p, progress, healthType, healthLabel }
  })
})

const delayedProjects = computed(() => {
  return projectHealthList.value
    .filter(p => p.healthType === 'danger')
    .map(p => ({
      ...p,
      delayDays: p.planEndDate ? Math.abs(Math.ceil((parseDate(p.planEndDate).getTime() - Date.now()) / (1000 * 60 * 60 * 24))) : 0
    }))
    .sort((a, b) => b.delayDays - a.delayDays)
    .slice(0, 5)
})

const goToProject = (projectId: string | number) => {
  router.push(`/pms/project-detail/${projectId}`)
}

// ==================== #9 部门筛选器初始化 ====================
/**
 * 加载当前用户可见部门树，并默认选中"自己所在部门"（如有权看）。
 * 超管/全局权限：后端返回全部部门树，默认不选中（让超管主动选）。
 * 部门负责人：默认选中自己作为负责人的部门。
 * 普通用户：默认选中自己所在部门。
 */
const loadDeptTree = async () => {
  try {
    const list = await getVisibleDeptTree()
    deptTreeData.value = buildDeptTree(list || [])
    if (deptTreeData.value.length === 0) {
      return
    }
    // 仅首次加载时尝试默认选中
    if (userDeptIdLoaded.value) return
    userDeptIdLoaded.value = true
    // 普通用户：默认选中自己所在部门（如果部门树里包含它）
    const userDeptId = (userStore.getUser as any)?.deptId
    if (userDeptId) {
      const exists = (list || []).some((d) => d.id === userDeptId)
      if (exists) {
        selectedDeptId.value = userDeptId
      }
    }
  } catch (e) {
    // 部门树加载失败不影响主流程：不显示筛选器即可
    console.warn('加载部门树失败', e)
  }
}

// ==================== 数据加载 ====================
const loadData = async () => {
  try {
    // #9：走 BI 数据范围接口
    const deptParam = selectedDeptId.value || undefined
    const [projects, tasks, stages] = await Promise.all([
      getDashboardProjects(deptParam),
      getDashboardTasks(deptParam),
      getDashboardStages(deptParam)
    ])
    projectList.value = projects || []
    taskList.value = tasks || []
    stageList.value = stages || []
    await ensureUsersLoaded()
    await fetchDeptStats()
    nextTick(() => renderAllCharts())
  } catch (e) {
    console.error('加载仪表盘数据失败', e)
    ElMessage.error('加载仪表盘数据失败，请重试')
  }
}

// ==================== 图表渲染 ====================
const renderAllCharts = () => {
  renderPhaseChart()
  renderTypeChart()
  renderDelayTrendChart()
  renderTaskStatusChart()
  renderDelayByUserChart()
  renderDelayByPhaseChart()
  renderDeptCompletionChart()
  renderDeptDelayChart()
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
  const projects = filteredProjects.value
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
  filteredProjects.value.forEach(p => {
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

  const now = new Date()
  const months: string[] = []
  const monthKeys: string[] = []
  for (let i = 4; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
    months.push(`${d.getMonth() + 1}月`)
    monthKeys.push(key)
  }

  const delayedProjectByMonth = new Array(5).fill(0)
  scopedProjects.value.forEach(p => {
    if (p.status === 'completed' || !p.planEndDate) return
    if (new Date(p.planEndDate) >= now || (p.progress || 0) >= 100) return
    const endDate = new Date(p.planEndDate)
    const mKey = `${endDate.getFullYear()}-${String(endDate.getMonth() + 1).padStart(2, '0')}`
    const idx = monthKeys.indexOf(mKey)
    if (idx >= 0) delayedProjectByMonth[idx]++
  })

  const delayedTaskByMonth = new Array(5).fill(0)
  scopedTasks.value.forEach(t => {
    if (calcDelayDays(t.planEndDate, t.completeStatus) <= 0) return
    const endDate = new Date(t.planEndDate!)
    const mKey = `${endDate.getFullYear()}-${String(endDate.getMonth() + 1).padStart(2, '0')}`
    const idx = monthKeys.indexOf(mKey)
    if (idx >= 0) delayedTaskByMonth[idx]++
  })

  const hasAnyData = delayedProjectByMonth.some(v => v > 0) || delayedTaskByMonth.some(v => v > 0)

  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['延期项目数', '延期任务数'], bottom: 0 },
    grid: { left: '3%', right: '4%', bottom: '12%', containLabel: true },
    xAxis: { type: 'category', data: months, axisLabel: { fontSize: 12 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: '延期项目数', type: 'bar', barWidth: 20,
        data: delayedProjectByMonth,
        itemStyle: { color: '#F53F3F', borderRadius: [4, 4, 0, 0] }
      },
      {
        name: '延期任务数', type: 'bar', barWidth: 20,
        data: delayedTaskByMonth,
        itemStyle: { color: '#FF7D00', borderRadius: [4, 4, 0, 0] }
      }
    ],
    graphic: hasAnyData ? undefined : [{
      type: 'text', left: 'center', top: 'middle',
      style: { text: '暂无足够数据', fontSize: 14, fill: '#86909C' }
    }]
  })
}

const renderTaskStatusChart = () => {
  const chart = getOrCreateChart(taskStatusChartRef.value)
  if (!chart) return
  const statusCount: Record<string, number> = {}
  filteredTasks.value.forEach(t => {
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
  filteredTasks.value.forEach(t => {
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
  filteredTasks.value.forEach(t => {
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

// ==================== 部门维度（消费 /pms/dashboard/dept-stats） ====================
const fetchDeptStats = async () => {
  try {
    deptStatsData.value = await getDeptStats({
      deptId: selectedDeptId.value ? Number(selectedDeptId.value) : undefined,
      projectName: searchProjectName.value.trim() || undefined
    }) || []
    nextTick(() => { renderDeptCompletionChart(); renderDeptDelayChart() })
  } catch (e) {
    console.warn('部门统计加载失败', e)
  }
}

const renderDeptCompletionChart = () => {
  const chart = getOrCreateChart(deptCompletionChartRef.value)
  if (!chart) return
  const sorted = [...deptStatsData.value].sort((a, b) => (a.completionRate || 0) - (b.completionRate || 0)).slice(-12)
  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: (p: any) => p[0].name + '<br/>完成率 ' + p[0].value + '%' },
    grid: { left: '3%', right: '8%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
    yAxis: { type: 'category', data: sorted.map(d => d.deptName), axisLabel: { fontSize: 12 } },
    series: [{
      type: 'bar', barWidth: 14,
      data: sorted.map(d => d.completionRate || 0),
      itemStyle: { color: '#2468F2', borderRadius: [0, 4, 4, 0] },
      label: { show: true, position: 'right', formatter: '{c}%' }
    }]
  })
}

const renderDeptDelayChart = () => {
  const chart = getOrCreateChart(deptDelayChartRef.value)
  if (!chart) return
  const data = deptStatsData.value.filter(d => d.taskDelayed > 0)
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
const getProgressColor = (project: any) => {
  if (project.status === 'completed') return '#00B42A'
  if (project.status !== 'completed' && project.planEndDate && new Date(project.planEndDate) < new Date() && (project.progress || 0) < 100) return '#F53F3F'
  return '#2468F2'
}

// ==================== 项目名称搜索 ====================
const handleSearch = () => {
  // 统计卡片/健康度/里程碑/进度概览是 computed 自动更新；echarts 需手动重绘
  fetchDeptStats()
  nextTick(() => renderAllCharts())
}
// 输入防抖：停止输入 300ms 后刷新图表
let searchTimer: ReturnType<typeof setTimeout> | null = null
watch(searchProjectName, () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => handleSearch(), 300)
})

// ==================== 生命周期 ====================
onMounted(async () => {
  // #9：先加载部门树（含默认选中），再加载 BI 数据
  await loadDeptTree()
  await loadData()
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
.health-matrix {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 8px;
}
.health-item {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
}
.health-item:hover {
  background: var(--el-fill-color-light);
  border-color: var(--el-color-primary-light-5);
}
.mt-16px {
  margin-top: 16px;
}
.mb-16px {
  margin-bottom: 16px;
}
.ml-auto {
  margin-left: auto;
}
.ml-8px {
  margin-left: 8px;
}
.text-gray {
  color: #86909C;
}
.text-red {
  color: #F53F3F;
}
.text-center {
  text-align: center;
}
.py-20px {
  padding-top: 20px;
  padding-bottom: 20px;
}
</style>

