<template>
  <div class="p-20px">
    <ContentWrap title="我的任务看板">
      <el-form :inline="true" class="mb-4">
        <el-form-item label="日期范围" required>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="~"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :shortcuts="rangeShortcuts"
          />
        </el-form-item>
        <el-form-item label="人员">
          <el-select
            v-model="selectedUsers"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="默认本人"
            style="width: 260px"
          >
            <el-option v-for="u in userList" :key="u.id" :label="u.nickname" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-switch v-model="includeSubordinates" active-text="含下属" inactive-text="仅本人" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadBoard" :loading="loading">
            <Icon icon="ep:search" class="mr-5px" />查询
          </el-button>
          <el-button @click="openCreateDaily">
            <Icon icon="ep:plus" class="mr-5px" />新建日常任务
          </el-button>
        </el-form-item>
      </el-form>
    </ContentWrap>

    <div v-loading="loading">
      <!-- 统计概览 -->
      <el-row :gutter="16" class="mb-16px">
        <el-col :xs="12" :sm="6" v-for="(s, i) in statSummary" :key="i">
          <div class="stat-mini" :style="{ borderTop: `3px solid ${s.color}` }">
            <div class="stat-mini-label">{{ s.label }}</div>
            <div class="stat-mini-value" :style="{ color: s.color }">{{ s.value }}</div>
          </div>
        </el-col>
      </el-row>
      <el-row :gutter="16" class="mb-16px">
        <el-col :xs="24" :lg="10">
          <el-card shadow="never" class="chart-card">
            <template #header><span class="chart-title">📊 任务状态分布</span></template>
            <div ref="statusChartRef" class="chart-box"></div>
          </el-card>
        </el-col>
        <el-col :xs="24" :lg="14">
          <el-card shadow="never" class="chart-card">
            <template #header><span class="chart-title">⚠️ 延期任务分布（按项目/日常）</span></template>
            <div ref="delayChartRef" class="chart-box"></div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 历史遗留区 -->
      <el-card class="board-section" shadow="never">
        <template #header>
          <div class="section-header">
            <span class="section-title">📌 历史遗留任务</span>
            <el-tag type="danger" effect="plain">{{ board.legacyTasks.length }}</el-tag>
            <span class="section-hint">计划开始早于 {{ dateRange?.[0] }} 且未完成的任务（按项目/日常分组）</span>
          </div>
        </template>
        <el-empty v-if="board.legacyTasks.length === 0" description="无历史遗留任务" :image-size="60" />
        <div v-else class="legacy-groups">
          <div v-for="group in legacyGroups" :key="group.projectId ?? 'daily'" class="legacy-group">
            <div v-if="legacyGroups.length > 1" class="legacy-group-title">
              <Icon icon="ep:folder" class="mr-5px" />
              {{ group.projectName }}
              <el-tag size="small" effect="plain" type="info" class="ml-8px">{{ group.tasks.length }}</el-tag>
            </div>
            <div class="task-list">
              <task-board-card :show-header="true" />
              <task-board-card
                v-for="t in group.tasks"
                :key="t.taskId"
                :task="t"
                :current-user-id="currentUserId"
                @detail="openDetail"
                @submit-review="handleSubmitReview"
              />
            </div>
          </div>
        </div>
      </el-card>

      <!-- 项目任务区 -->
      <el-card class="board-section" shadow="never">
        <template #header>
          <div class="section-header">
            <span class="section-title">🗂 项目任务</span>
            <el-tag type="primary" effect="plain">{{ projectTaskCount }}</el-tag>
            <span class="section-hint">时间段内、按项目分组（组内按计划开始升序）</span>
          </div>
        </template>
        <el-empty v-if="board.projectGroups.length === 0" description="该范围内无项目任务" :image-size="60" />
        <div v-for="group in board.projectGroups" :key="group.projectId" class="project-group">
<div class="project-group-title" @click="toggleProject(group.projectId)" style="cursor:pointer;user-select:none">
          <Icon :icon="isProjectCollapsed(group.projectId) ? 'ep:arrow-right' : 'ep:arrow-down'" class="mr-5px" />
          {{ group.projectName }}
          <el-tag size="small" effect="plain" class="ml-8px">{{ group.tasks.length }}</el-tag>
        </div>
        <div v-show="!isProjectCollapsed(group.projectId)" class="task-list">
            <task-board-card :show-header="true" />
            <task-board-card
              v-for="t in group.tasks"
              :key="t.taskId"
              :task="t"
              :current-user-id="currentUserId"
              @detail="openDetail"
              @submit-review="handleSubmitReview"
            />
          </div>
        </div>
      </el-card>

      <!-- 日常任务区 -->
      <el-card class="board-section" shadow="never">
        <template #header>
          <div class="section-header">
            <span class="section-title">📝 日常任务</span>
            <el-tag type="warning" effect="plain">{{ board.dailyTasks.length }}</el-tag>
            <span class="section-hint">非项目任务，需责任人直属领导审核通过才算完成</span>
          </div>
        </template>
        <el-empty v-if="board.dailyTasks.length === 0" description="该范围内无日常任务" :image-size="60" />
        <div v-else class="task-list">
          <task-board-card :show-header="true" />
          <task-board-card
            v-for="t in board.dailyTasks"
            :key="t.taskId"
            :task="t"
            :current-user-id="currentUserId"
            @detail="openDetail"
            @submit-review="handleSubmitReview"
          />
        </div>
      </el-card>
    </div>

    <!-- 任务详情抽屉（复用项目详情抽屉，只读为主） -->
    <TaskDetailDrawer ref="taskDrawerRef" @refresh="loadBoard" />

    <!-- 新建日常任务弹窗 -->
    <el-dialog v-model="dailyDialogVisible" title="新建日常任务" width="640px" :close-on-click-modal="false">
      <el-form ref="dailyFormRef" :model="dailyForm" :rules="dailyRules" label-width="90px">
        <el-form-item label="任务名称" prop="taskName">
          <el-input v-model="dailyForm.taskName" placeholder="请输入任务名称" />
        </el-form-item>
        <el-form-item label="任务类型" prop="taskType">
          <el-select v-model="dailyForm.taskType" placeholder="请选择" class="w-full">
            <el-option v-for="opt in dailyTypeOpts" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人" prop="mainOwnerId">
          <el-select v-model="dailyForm.mainOwnerId" placeholder="请选择负责人" filterable class="w-full" @change="onDailyOwnerChange">
            <el-option v-for="u in userList" :key="u.id" :label="u.nickname" :value="u.id" />
          </el-select>
          <div class="reviewer-hint">
            直属领导：<span :class="{ warn: !dailyReviewerName }">{{ dailyReviewerName || '⚠️ 未抽取到该用户的直属领导，请联系管理员检查部门设置' }}</span>
          </div>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="dailyForm.priority" class="w-full">
            <el-option v-for="opt in dailyPriorityOpts" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="计划开始">
              <el-date-picker v-model="dailyForm.planStartDate" type="date" value-format="YYYY-MM-DD" class="w-full" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划结束">
              <el-date-picker v-model="dailyForm.planEndDate" type="date" value-format="YYYY-MM-DD" class="w-full" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="任务描述">
          <el-input v-model="dailyForm.description" type="textarea" :rows="2" placeholder="请输入任务描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dailyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dailySubmitting" @click="submitDaily">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { getTaskBoard, createTask, submitReview, getReviewerOf, TaskVO } from '@/api/pms/task'
import { getProjectList, ProjectVO } from '@/api/pms/project'
import { getStageList, StageVO } from '@/api/pms/stage'
import TaskDetailDrawer from '../project-detail/TaskDetailDrawer.vue'
import TaskBoardCard from './components/TaskBoardCard.vue'
import { getSimpleDictDataList } from '@/api/system/dict/dict.data'
import {
  taskStatusMap, priorityMap, priorityOptions, dailyTaskTypeOptions,
  calcDelayDays, formatDate
} from '../pms-utils'
import * as echarts from 'echarts'
import { nextTick, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useUserNames } from '@/hooks/pms/useUserNames'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/store/modules/user'

defineOptions({ name: 'PmsMyTaskBoard' })

const message = useMessage()
const { userList, ensureLoaded: ensureUsersLoaded, getUserName } = useUserNames()
const userStore = useUserStore()
const currentUserId = computed(() => userStore.getUser?.id)
const route = useRoute()

const loading = ref(false)
const dateRange = ref<[string, string]>([])
const selectedUsers = ref<(string | number)[]>([])
const includeSubordinates = ref(true)
const projectList = ref<ProjectVO[]>([])
const stageList = ref<StageVO[]>([])

const board = reactive<{
  legacyTasks: TaskVO[]
  projectGroups: { projectId: number; projectName: string; tasks: TaskVO[] }[]
  dailyTasks: TaskVO[]
}>({
  legacyTasks: [],
  projectGroups: [],
  dailyTasks: []
})

const projectTaskCount = computed(() =>
  board.projectGroups.reduce((sum, g) => sum + g.tasks.length, 0)
)

// 日常任务类型下拉（动态字典，fallback 到硬编码）
// 日常任务类型下拉 + 优先级下拉：先设 fallback，onMounted 直接调 API 覆盖
const dailyTypeOpts = ref<{ value: string; label: string }[]>(dailyTaskTypeOptions)
const dailyPriorityOpts = ref<{ value: string; label: string }[]>(priorityOptions)

/** 从后端全量字典响应中提取指定 dictType 的选项列表 */
function extractDictOpts(allData: any[], dictType: string): { value: string; label: string }[] {
  return allData
    .filter((d: any) => d.dictType === dictType && (d.status == null || d.status === 0))
    .sort((a: any, b: any) => (a.sort || 0) - (b.sort || 0))
    .map((d: any) => ({ value: String(d.value), label: d.label }))
}

// 项目分组折叠/展开状态（reactive 对象属性触发响应式）
const collapsedFlag = reactive<Record<number, boolean>>({})
const isProjectCollapsed = (id: number) => !!collapsedFlag[id]
const toggleProject = (id: number) => { collapsedFlag[id] = !collapsedFlag[id] }

// 日常任务直属领导预校验（新建弹窗选择负责人后实时查询）
const dailyReviewerName = ref('')
const onDailyOwnerChange = async (userId: number | string | undefined) => {
  if (!userId) { dailyReviewerName.value = ''; return }
  try {
    const r: any = await getReviewerOf(userId)
    dailyReviewerName.value = (r?.code === 0 && r.data) ? getUserName(r.data) : ''
  } catch { dailyReviewerName.value = '' }
}

// 全部任务汇总（用于统计概览与图表）
const allTasks = computed<TaskVO[]>(() => {
  const list: TaskVO[] = [...board.legacyTasks, ...board.dailyTasks]
  board.projectGroups.forEach(g => list.push(...g.tasks))
  return list
})

// 历史遗留任务按项目/日常分组（复用 projectGroups 已有的项目名映射）
const legacyGroups = computed(() => {
  const projectNameMap = new Map<number, string>()
  board.projectGroups.forEach(g => projectNameMap.set(g.projectId, g.projectName))
  const groups: { projectId: number | null; projectName: string; tasks: TaskVO[] }[] = []
  const idxMap = new Map<number | null, number>()
  board.legacyTasks.forEach(t => {
    const hasPid = t.projectId != null && t.projectId !== 0
    const key: number | null = hasPid ? Number(t.projectId) : null
    let idx = idxMap.get(key)
    if (idx === undefined) {
      const name = key !== null
        ? (projectNameMap.get(key) || `项目 ${key}`)
        : '日常任务'
      idx = groups.length
      groups.push({ projectId: key, projectName: name, tasks: [] })
      idxMap.set(key, idx)
    }
    groups[idx].tasks.push(t)
  })
  return groups
})

// 统计概览卡片
const statSummary = computed(() => {
  const all = allTasks.value
  const delayed = all.filter(t => calcDelayDays(t.planEndDate, t.completeStatus) > 0).length
  const inProgress = all.filter(t => t.completeStatus === 'in_progress').length
  const pendingReview = all.filter(t =>
    t.reviewStatus === 'submitted' ||
    t.completeStatus === 'pending_review' ||
    t.completeStatus === 'completion_pending_review'
  ).length
  return [
    { label: '任务总数', value: all.length, color: '#2468F2' },
    { label: '进行中', value: inProgress, color: '#1A56DB' },
    { label: '已延期', value: delayed, color: '#F53F3F' },
    { label: '待审核', value: pendingReview, color: '#722ED1' }
  ]
})

// ==================== 图表（echarts） ====================
const chartInstances: echarts.ECharts[] = []
const statusChartRef = ref<HTMLElement>()
const delayChartRef = ref<HTMLElement>()

const getOrCreateChart = (el: HTMLElement | undefined): echarts.ECharts | null => {
  if (!el) return null
  const existing = chartInstances.find(c => c.getDom() === el)
  if (existing) { existing.clear(); return existing }
  const chart = echarts.init(el)
  chartInstances.push(chart)
  return chart
}

const renderStatusChart = () => {
  const chart = getOrCreateChart(statusChartRef.value)
  if (!chart) return
  const count: Record<string, number> = {}
  allTasks.value.forEach(t => {
    const s = t.completeStatus || 'not_started'
    count[s] = (count[s] || 0) + 1
  })
  const data = Object.entries(count).map(([k, v]) => ({
    name: taskStatusMap[k]?.label || k,
    value: v,
    itemStyle: { color: taskStatusMap[k]?.borderColor || '#86909C' }
  }))
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, icon: 'circle', type: 'scroll' },
    series: [{
      type: 'pie', radius: ['38%', '66%'], center: ['50%', '44%'],
      label: { show: true, formatter: '{b}\n{c}个', fontSize: 12 },
      data
    }]
  })
}

const renderDelayChart = () => {
  const chart = getOrCreateChart(delayChartRef.value)
  if (!chart) return
  // 按项目/日常/历史遗留汇总延期任务数
  const groupDelay: Record<string, number> = {}
  board.projectGroups.forEach(g => {
    const c = g.tasks.filter(t => calcDelayDays(t.planEndDate, t.completeStatus) > 0).length
    if (c > 0) groupDelay[g.projectName || `项目${g.projectId}`] = c
  })
  const dailyDelay = board.dailyTasks.filter(t => calcDelayDays(t.planEndDate, t.completeStatus) > 0).length
  if (dailyDelay > 0) groupDelay['日常任务'] = dailyDelay
  const legacyDelay = board.legacyTasks.filter(t => calcDelayDays(t.planEndDate, t.completeStatus) > 0).length
  if (legacyDelay > 0) groupDelay['历史遗留'] = legacyDelay

  let sorted = Object.entries(groupDelay).sort((a, b) => b[1] - a[1]).slice(0, 10)
  let fallback = false
  // 若无延期数据，降级展示「各项目任务数」，避免空白
  if (sorted.length === 0) {
    fallback = true
    const cnt: Record<string, number> = {}
    board.projectGroups.forEach(g => { cnt[g.projectName || `项目${g.projectId}`] = g.tasks.length })
    if (board.dailyTasks.length) cnt['日常任务'] = board.dailyTasks.length
    sorted = Object.entries(cnt).sort((a, b) => b[1] - a[1]).slice(0, 10)
  }
  chart.setOption({
    title: fallback ? {
      text: '当前无延期任务，已展示各项目任务数',
      left: 'center', top: 4,
      textStyle: { fontSize: 12, color: '#86909C' }
    } : undefined,
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '10%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: {
      type: 'category',
      data: sorted.map(e => e[0]).reverse(),
      axisLabel: { fontSize: 12, width: 120, overflow: 'truncate' }
    },
    series: [{
      type: 'bar', barWidth: 16,
      data: sorted.map(e => e[1]).reverse(),
      itemStyle: {
        color: fallback
          ? new echarts.graphic.LinearGradient(0, 0, 1, 0, [
              { offset: 0, color: '#2468F2' }, { offset: 1, color: '#0FC6C2' }
            ])
          : new echarts.graphic.LinearGradient(0, 0, 1, 0, [
              { offset: 0, color: '#FF7D00' }, { offset: 1, color: '#F53F3F' }
            ]),
        borderRadius: [0, 4, 4, 0]
      },
      label: { show: true, position: 'right', formatter: '{c}' }
    }]
  })
}

const renderCharts = () => {
  renderStatusChart()
  renderDelayChart()
}

const handleResize = () => chartInstances.forEach(c => c.resize())

const rangeShortcuts = [
  { text: '最近一周', value: () => [formatDate(new Date(Date.now() - 6 * 86400000), 'YYYY-MM-DD'), formatDate(new Date(), 'YYYY-MM-DD')] },
  { text: '本月', value: () => { const n = new Date(); const f = new Date(n.getFullYear(), n.getMonth(), 1); return [formatDate(f, 'YYYY-MM-DD'), formatDate(n, 'YYYY-MM-DD')] } }
]

const taskDrawerRef = ref()

const openDetail = (task: TaskVO) => {
  taskDrawerRef.value?.open(task)
}

const handleSubmitReview = async (task: TaskVO) => {
  try {
    await ElMessageBox.confirm(`确认提交任务「${task.taskName}」完成审核？`, '提示', {
      confirmButtonText: '提交审核', cancelButtonText: '取消', type: 'warning'
    })
    await submitReview(String(task.taskId))
    ElMessage.success('已提交审核，等待直属领导审核')
    await loadBoard()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const loadBoard = async () => {
  if (!dateRange.value || !dateRange.value[0] || !dateRange.value[1]) {
    message.warning('请选择日期范围')
    return
  }
  loading.value = true
  try {
    const [projects, stages] = await Promise.all([
      getProjectList().catch(() => []),
      getStageList().catch(() => [])
    ])
    projectList.value = projects || []
    stageList.value = stages || []
    await ensureUsersLoaded()
    const data = await getTaskBoard({
      userIds: selectedUsers.value.length ? selectedUsers.value : undefined,
      dateFrom: dateRange.value[0],
      dateTo: dateRange.value[1],
      includeSubordinates: includeSubordinates.value
    })
    board.legacyTasks = data.legacyTasks || []
    board.projectGroups = data.projectGroups || []
    board.dailyTasks = data.dailyTasks || []
    nextTick(() => renderCharts())
  } catch (e) {
    console.error('加载看板失败', e)
  } finally {
    loading.value = false
  }
}

// ==================== 新建日常任务 ====================
const dailyDialogVisible = ref(false)
const dailyFormRef = ref<FormInstance>()
const dailySubmitting = ref(false)
const dailyForm = reactive({
  taskName: '',
  taskType: 'other',
  mainOwnerId: undefined as number | undefined,
  priority: 'normal',
  planStartDate: '',
  planEndDate: '',
  description: ''
})
const dailyRules = reactive<FormRules>({
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  taskType: [{ required: true, message: '请选择任务类型', trigger: 'change' }],
  mainOwnerId: [{ required: true, message: '请选择负责人', trigger: 'change' }]
})

const openCreateDaily = () => {
  Object.assign(dailyForm, {
    taskName: '', taskType: 'other', mainOwnerId: undefined,
    priority: 'normal', planStartDate: '', planEndDate: '', description: ''
  })
  dailyReviewerName.value = ''
  dailyDialogVisible.value = true
}

const submitDaily = async () => {
  const valid = await dailyFormRef.value?.validate().catch(() => false)
  if (!valid) return
  dailySubmitting.value = true
  try {
    const today = formatDate(new Date(), 'YYYY-MM-DD')
    const data: any = {
      ...dailyForm,
      projectId: null,
      stageId: null,
      cycle: 1,
      completeStatus: 'in_progress',
      progress: 0,
      isMilestone: false,
      planStartDate: dailyForm.planStartDate || today,
      planEndDate: dailyForm.planEndDate || dailyForm.planStartDate || today
    }
    await createTask(data)
    ElMessage.success('日常任务创建成功')
    dailyDialogVisible.value = false
    await loadBoard()
  } catch (e: any) {
    console.error(e)
    message.error(e?.message || '创建失败')
  } finally {
    dailySubmitting.value = false
  }
}

onMounted(async () => {
  // 直接调后端字典 API 获取全量字典数据（绕开 dictStore 异步竞态问题）
  try {
    const res: any = await getSimpleDictDataList()
    const dictData = Array.isArray(res) ? res : (res?.data || [])
    const dtOpts = extractDictOpts(dictData, 'pms_daily_task_type')
    const dpOpts = extractDictOpts(dictData, 'pms_priority')
    dailyTypeOpts.value = dtOpts.length > 0 ? dtOpts : dailyTaskTypeOptions
    dailyPriorityOpts.value = dpOpts.length > 0 ? dpOpts : priorityOptions
  } catch (e) {
    console.warn('[PMS] 字典API加载失败，使用fallback', e)
  }
  window.addEventListener('resize', handleResize)
  // 默认范围：本月
  const n = new Date()
  dateRange.value = [
    formatDate(new Date(n.getFullYear(), n.getMonth(), 1), 'YYYY-MM-DD'),
    formatDate(n, 'YYYY-MM-DD')
  ]
  await ensureUsersLoaded()
  await loadBoard()

  // 处理深链 taskId（钉钉驳回跳转场景）：自动定位并打开任务详情
  const tid = route.query.taskId as string | undefined
  if (tid) {
    const all = [...board.legacyTasks, ...board.dailyTasks]
    board.projectGroups.forEach(g => all.push(...g.tasks))
    const t = all.find(x => String(x.taskId) === tid)
    if (t) {
      nextTick(() => taskDrawerRef.value?.open(t))
    } else {
      message.warning(`任务 #${tid} 不在当前看板范围内，请调整日期/人员筛选`)
    }
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartInstances.forEach(c => c.dispose())
  chartInstances.length = 0
})
</script>

<style scoped>
.stat-mini {
  background: #fff;
  border-radius: 8px;
  padding: 14px 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}
.stat-mini-label {
  font-size: 13px;
  color: #86909c;
  margin-bottom: 6px;
}
.stat-mini-value {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.1;
}
.chart-card {
  margin-bottom: 16px;
}
.chart-title {
  font-size: 14px;
  font-weight: 600;
  color: #1d2129;
}
.chart-box {
  width: 100%;
  height: 280px;
}
.board-section {
  margin-bottom: 16px;
}
.section-header {
  display: flex;
  align-items: center;
  gap: 10px;
}
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #1d2129;
}
.section-hint {
  font-size: 12px;
  color: #86909c;
}
.project-group {
  margin-bottom: 16px;
}
.project-group-title {
  font-size: 14px;
  font-weight: 600;
  color: #2468f2;
  margin: 4px 0 12px;
  display: flex;
  align-items: center;
}
.task-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
/* 斑马纹：偶数行浅灰底（scoped 样式下用 :deep 或直接子选择器；TaskBoardCard 的 .task-row 是子元素） */
.task-list > :nth-child(even) {
  background: #fafbfc;
}
/* 延期任务左侧红色边条（用 :global 选择内部 .task-row 类的元素） */
.task-list > :global(.task-row):has(.delay-tag) {
  border-left: 3px solid #F53F3F;
}
.legacy-groups {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.legacy-group {
  margin-bottom: 4px;
}
.legacy-group-title {
  font-size: 13px;
  font-weight: 500;
  color: #4e5969;
  margin: 4px 0 8px;
  display: flex;
  align-items: center;
  padding-left: 4px;
}
.reviewer-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #86909c;
  line-height: 1.4;
}
.reviewer-hint .warn {
  color: #F53F3F;
  font-weight: 500;
}
.ml-8px {
  margin-left: 8px;
}
.w-full {
  width: 100%;
}
</style>
