<template>
  <div class="p-20px">
    <!-- 搜索栏 -->
    <ContentWrap>
      <el-form :model="queryParams" :inline="true" class="mb-0">
        <el-form-item label="项目" v-if="activeTaskTab === 'project'">
          <el-select v-model="queryParams.projectId" placeholder="全部项目" clearable filterable style="width: 200px" @change="handleSearch">
            <el-option v-for="p in projectList" :key="p.projectId" :label="p.projectName" :value="p.projectId" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务名称">
          <el-input v-model="queryParams.taskName" placeholder="请输入任务名称" clearable style="width: 180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.completeStatus" placeholder="全部" clearable style="width: 100px">
            <el-option v-for="(v, k) in taskStatusMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="queryParams.mainOwnerId" placeholder="全部" clearable filterable style="width: 140px">
            <el-option v-for="u in userList" :key="u.id" :label="`${u.nickname}`" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><Icon icon="ep:search" class="mr-5px" />查询</el-button>
          <el-button @click="handleReset"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        </el-form-item>
      </el-form>
    </ContentWrap>

    <!-- 任务分类 Tab：项目管理 / 日常任务 / 合计 -->
    <el-tabs v-model="activeTaskTab" class="task-tabs">
      <el-tab-pane label="项目管理" name="project" />
      <el-tab-pane label="日常任务" name="daily" />
      <el-tab-pane label="合计" name="summary" />
    </el-tabs>

    <!-- 表格 -->
    <ContentWrap v-if="activeTaskTab !== 'summary'">
      <div style="display: flex; justify-content: space-between; margin-bottom: 12px">
        <el-button type="primary" @click="handleCreate" v-hasPermi="['pms:task:create']" v-if="activeTaskTab === 'daily'">
          <Icon icon="ep:plus" class="mr-5px" />新建日常任务
        </el-button>
        <div style="display: flex; align-items: center; gap: 12px">
          <el-switch v-model="myTasks" active-text="我的任务" size="small" />
          <el-radio-group v-model="viewMode" size="small">
            <el-radio-button label="table">表格</el-radio-button>
            <el-radio-button label="card">卡片</el-radio-button>
          </el-radio-group>
        </div>
      </div>

      <!-- 表格视图 -->
      <el-table v-if="viewMode === 'table'" :data="pagedList" v-loading="loading" stripe border style="width: 100%" @row-click="openDetail">
        <el-table-column label="任务名称" prop="taskName" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" @click.stop="openDetail(row)">{{ row.taskName }}</el-link>
            <el-tag v-if="row.isMilestone" type="warning" size="small" effect="plain" style="margin-left: 6px">里程碑</el-tag>
            <el-tag v-if="row.isCriticalPath" type="danger" size="small" effect="plain" style="margin-left: 6px">关键路径</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="所属项目" width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ getProjectName(row.projectId) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :style="getStatusStyle(row.completeStatus)" size="small" effect="light">
              {{ getStatusLabel(row.completeStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="120">
          <template #default="{ row }">
            <el-progress :percentage="row.progress || 0" :stroke-width="8" :color="getProgressColor(row)" />
          </template>
        </el-table-column>
        <el-table-column label="负责人" width="100">
          <template #default="{ row }">{{ getOwnerName(row) }}</template>
        </el-table-column>
        <el-table-column label="优先级" width="80">
          <template #default="{ row }">
            <el-tag :color="priorityMap[row.priority || 'normal']?.color" effect="plain" size="small">
              {{ priorityMap[row.priority || 'normal']?.label || '普通' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="到期日" width="120">
          <template #default="{ row }">
            <span :style="{ color: isDelayed(row) ? '#F53F3F' : '#4E5969' }">
              {{ formatDate(row.planEndDate) }}
            </span>
            <el-tag v-if="isDelayed(row)" type="danger" size="small" effect="plain" style="margin-left: 4px">
              延{{ getDelayDays(row) }}天
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="openDetail(row)">详情</el-button>
            <el-button link type="primary" size="small" @click.stop="handleEdit(row)" v-if="checkPermi(['pms:task:update'])">编辑</el-button>
            <el-button link type="danger" size="small" @click.stop="handleDelete(row)" v-if="checkPermi(['pms:task:delete'])">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 卡片视图 -->
      <div v-else v-loading="loading">
        <el-row :gutter="12">
          <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="task in filteredList" :key="task.taskId" style="margin-bottom: 12px">
            <el-card class="task-card" shadow="hover" @click="openDetail(task)">
              <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 8px">
                <span class="card-title" :title="task.taskName">{{ task.taskName }}</span>
                <el-tag :style="getStatusStyle(task.completeStatus)" size="small" effect="light">
                  {{ getStatusLabel(task.completeStatus) }}
                </el-tag>
              </div>
              <div style="font-size: 12px; color: #86909C; margin-bottom: 8px">
                {{ getProjectName(task.projectId) }}
              </div>
              <el-progress :percentage="task.progress || 0" :stroke-width="6" :color="getProgressColor(task)" />
              <div style="display: flex; justify-content: space-between; margin-top: 8px; font-size: 12px; color: #86909C">
                <span>{{ getOwnerName(task) }}</span>
                <span :style="{ color: isDelayed(task) ? '#F53F3F' : '#86909C' }">
                  {{ formatDate(task.planEndDate) }}
                </span>
              </div>
            </el-card>
          </el-col>
        </el-row>
        <el-empty v-if="filteredList.length === 0 && !loading" description="暂无任务" />
      </div>

      <!-- 分页 -->
      <div style="display: flex; justify-content: flex-end; margin-top: 16px" v-if="filteredList.length > pageSize">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="filteredList.length"
          layout="total, prev, pager, next"
          background
        />
      </div>
    </ContentWrap>

    <!-- 合计 Tab：汇总总览 -->
    <ContentWrap v-else>
      <el-row :gutter="16">
        <el-col :span="8">
          <el-card shadow="never">
            <div style="font-size:13px;color:#86909C;margin-bottom:8px">任务总数</div>
            <div style="font-size:28px;font-weight:600;color:#1D2129">{{ stats.total }}</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never">
            <div style="font-size:13px;color:#86909C;margin-bottom:8px">项目任务</div>
            <div style="font-size:28px;font-weight:600;color:#2468F2">{{ stats.projectCount }}</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never">
            <div style="font-size:13px;color:#86909C;margin-bottom:8px">日常任务</div>
            <div style="font-size:28px;font-weight:600;color:#00B42A">{{ stats.dailyCount }}</div>
          </el-card>
        </el-col>
      </el-row>
      <el-row :gutter="16" style="margin-top:16px">
        <el-col :span="12">
          <el-card shadow="never" header="按状态分布">
            <el-table :data="Object.entries(stats.byStatus).map(([k, v]) => ({ status: k, count: v }))" size="small" border max-height="320">
              <el-table-column label="状态" prop="status" />
              <el-table-column label="数量" prop="count" width="100" align="center" />
            </el-table>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never" header="按负责人 Top8">
            <el-table :data="stats.ownerTop" size="small" border max-height="320">
              <el-table-column label="负责人" prop="name" />
              <el-table-column label="任务数" prop="cnt" width="100" align="center" />
            </el-table>
          </el-card>
        </el-col>
      </el-row>
    </ContentWrap>

    <!-- 任务详情抽屉 -->
    <TaskDetailDrawer ref="taskDrawerRef" @refresh="loadList" />

    <!-- 创建/编辑任务弹窗（仅用于创建日常任务；项目任务请在项目详情页创建） -->
    <el-dialog v-model="taskDialogVisible" :title="isEdit ? '编辑任务' : '新建日常任务'" width="680px" :close-on-click-modal="false">
      <el-alert type="info" :closable="false" class="mb-12px" show-icon>
        <template #title>
          <span>此处仅创建日常任务（由直属领导审批），项目任务请到「项目管理 → 选择项目 → 新增任务」创建</span>
        </template>
      </el-alert>
      <el-form ref="taskFormRef" :model="taskForm" :rules="taskFormRules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="任务名称" prop="taskName">
              <el-input v-model="taskForm.taskName" placeholder="请输入任务名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="任务类型" prop="taskType">
              <el-select v-model="taskForm.taskType" placeholder="请选择任务类型" class="w-full">
                <el-option v-for="opt in currentTaskTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="负责人" prop="mainOwnerId">
              <el-select v-model="taskForm.mainOwnerId" placeholder="请选择负责人" filterable class="w-full">
                <el-option v-for="u in userList" :key="u.id" :label="`${u.nickname}`" :value="u.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="协助人">
              <el-select v-model="taskForm.helperIds" multiple filterable placeholder="可选协助人" class="w-full">
                <el-option v-for="u in userList" :key="u.id" :label="`${u.nickname}`" :value="u.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="优先级" prop="priority">
              <el-select v-model="taskForm.priority" placeholder="请选择" class="w-full">
                <el-option v-for="opt in currentPriorityOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="计划开始" prop="planStartDate">
              <el-date-picker v-model="taskForm.planStartDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" class="w-full" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划结束" prop="planEndDate">
              <el-date-picker v-model="taskForm.planEndDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" class="w-full" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="工期" prop="cycle">
              <el-input-number v-model="taskForm.cycle" :min="1" :max="999" class="w-full" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="里程碑">
              <el-switch v-model="taskForm.isMilestone" active-text="是" inactive-text="否" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="任务描述">
          <el-input v-model="taskForm.description" type="textarea" :rows="2" placeholder="请输入任务描述" />
        </el-form-item>
        <el-form-item label="输出物要求">
          <el-input v-model="taskForm.outputRequirement" type="textarea" :rows="2" placeholder="请输入输出物要求" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitTask">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { getTaskList, createTask, updateTask, deleteTask, TaskVO } from '@/api/pms/task'
import { getProjectList, ProjectVO } from '@/api/pms/project'
import { getStageList, StageVO } from '@/api/pms/stage'
import TaskDetailDrawer from '../project-detail/TaskDetailDrawer.vue'
import { getSimpleDictDataList } from '@/api/system/dict/dict.data'
import {
  taskStatusMap, priorityMap, priorityOptions, taskTypeOptions, dailyTaskTypeOptions,
  formatDate, calcDelayDays
} from '../pms-utils'
import { checkPermi } from '@/utils/permission'
import { useUserNames } from '@/hooks/pms/useUserNames'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/store/modules/user'

defineOptions({ name: 'PmsTask' })

const message = useMessage()
const { userList, getUserName, ensureLoaded: ensureUsersLoaded } = useUserNames()
const userStore = useUserStore()
const currentUserId = computed(() => userStore.getUser?.id)
const loading = ref(false)
const viewMode = ref<'table' | 'card'>('table')
const taskList = ref<TaskVO[]>([])
const projectList = ref<ProjectVO[]>([])
const stageList = ref<StageVO[]>([])
const taskDrawerRef = ref()

const currentPage = ref(1)
const pageSize = 20

const queryParams = reactive({
  projectId: '',
  taskName: '',
  completeStatus: '',
  mainOwnerId: ''
})

const myTasks = ref(false)

// 【PMS】任务管理三分 Tab：项目管理 / 日常任务 / 合计
const activeTaskTab = ref<'project' | 'daily' | 'summary'>('project')
watch(activeTaskTab, () => { currentPage.value = 1 })

// 本弹窗只创建日常任务（projectId 固定为 null），故无 DAILY_PROJECT_VALUE / isDailyTask 概念
// 字典下拉选项：onMounted 中从 simple-list API 加载后填充
// 任务类型：永远从 pms_daily_task_type 数据字典取值（项目任务请到项目详情页创建）
const currentTaskTypeOptions = ref<{ value: string; label: string }[]>(dailyTaskTypeOptions)
const currentPriorityOptions = ref<{ value: string; label: string }[]>(priorityOptions)

/** 从 simple-list 全量响应中提取指定 dictType 的选项列表 */
function extractDictOptions(allData: any[], dictType: string): { value: string; label: string }[] {
  if (!Array.isArray(allData)) return []
  return allData
    .filter((d: any) => d.dictType === dictType && (d.status == null || d.status === 0))
    .sort((a: any, b: any) => (a.sort || 0) - (b.sort || 0))
    .map((d: any) => ({ value: String(d.value), label: d.label }))
}

const filteredList = computed(() => {
  let list = taskList.value
  // 按 Tab 区分：项目管理（有项目）/ 日常任务（无项目）
  if (activeTaskTab.value === 'project') {
    list = list.filter(t => t.projectId != null && t.projectId !== undefined && t.projectId !== 0 && t.projectId !== '')
  } else if (activeTaskTab.value === 'daily') {
    list = list.filter(t => t.projectId == null || t.projectId === undefined || t.projectId === 0 || t.projectId === '')
  }
  if (queryParams.projectId) {
    list = list.filter(t => String(t.projectId) === String(queryParams.projectId))
  }
  if (queryParams.taskName && queryParams.taskName.trim()) {
    const keyword = queryParams.taskName.trim().toLowerCase()
    list = list.filter(t => t.taskName && t.taskName.toLowerCase().includes(keyword))
  }
  if (queryParams.completeStatus) {
    list = list.filter(t => t.completeStatus === queryParams.completeStatus)
  }
  if (queryParams.mainOwnerId) {
    list = list.filter(t => String(t.mainOwnerId) === String(queryParams.mainOwnerId))
  }
  // “我的任务” 筛选
  if (myTasks.value && currentUserId.value) {
    const uid = String(currentUserId.value)
    list = list.filter(t => {
      if (String(t.mainOwnerId) === uid) return true
      if (t.helperIds) {
        const helpers = String(t.helperIds).split(',').map(s => s.trim())
        if (helpers.includes(uid)) return true
      }
      return false
    })
  }
  // 排除模板项目的任务；日常任务（无项目）直接保留
  return list.filter(t => {
    if (t.projectId === null || t.projectId === undefined || t.projectId === 0) return true
    const proj = projectList.value.find(p => String(p.projectId) === String(t.projectId))
    return proj && proj.projectType !== 'standard_template'
  })
})

const pagedList = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredList.value.slice(start, start + pageSize)
})

const availableProjects = computed(() => {
  return projectList.value.filter(p => p.projectType !== 'standard_template')
})

// 【PMS】合计 Tab 统计
const projectTasks = computed(() => taskList.value.filter(t => t.projectId != null && t.projectId !== undefined && t.projectId !== 0 && t.projectId !== ''))
const dailyTasks = computed(() => taskList.value.filter(t => t.projectId == null || t.projectId === undefined || t.projectId === 0 || t.projectId === ''))
const stats = computed(() => {
  const total = taskList.value.length
  const byStatus: Record<string, number> = {}
  for (const t of taskList.value) {
    const s = t.completeStatus || 'unknown'
    byStatus[s] = (byStatus[s] || 0) + 1
  }
  const byOwner: Record<string, number> = {}
  for (const t of taskList.value) {
    const o = String(t.mainOwnerId)
    byOwner[o] = (byOwner[o] || 0) + 1
  }
  const ownerTop = Object.entries(byOwner)
    .map(([uid, cnt]) => ({ uid, cnt, name: getUserName(Number(uid)) }))
    .sort((a, b) => b.cnt - a.cnt)
    .slice(0, 8)
  return {
    total,
    projectCount: projectTasks.value.length,
    dailyCount: dailyTasks.value.length,
    byStatus,
    ownerTop
  }
})

const loadList = async () => {
  loading.value = true
  try {
    const [tasks, projects, stages] = await Promise.all([
      getTaskList(),
      getProjectList(),
      getStageList().catch(() => [])
    ])
    taskList.value = tasks || []
    projectList.value = projects || []
    stageList.value = stages || []
    await ensureUsersLoaded()
  } catch (e) {
    console.error('加载任务列表失败', e)
  } finally {
    loading.value = false
  }
}

// ==================== 辅助函数 ====================
const getProjectName = (projectId?: string | number) => {
  // 日常任务（无项目）单独标识
  if (projectId === null || projectId === undefined || projectId === 0) return '日常任务'
  const p = projectList.value.find(p => String(p.projectId) === String(projectId))
  return p?.projectName || '-'
}

const getOwnerName = (task: TaskVO) => {
  return getUserName(task.mainOwnerId)
}

const getStatusStyle = (status?: string) => {
  const s = taskStatusMap[status || '']
  return s ? `color: ${s.textColor}; background: ${s.bgColor}; border-color: ${s.borderColor};` : ''
}

const getStatusLabel = (status?: string) => taskStatusMap[status || '']?.label || '-'

const getProgressColor = (task: TaskVO) => {
  if (task.completeStatus === 'completed') return '#00B42A'
  if (isDelayed(task)) return '#F53F3F'
  return '#2468F2'
}

const isDelayed = (task: TaskVO) => calcDelayDays(task.planEndDate, task.completeStatus) > 0
const getDelayDays = (task: TaskVO) => calcDelayDays(task.planEndDate, task.completeStatus)

// ==================== 操作 ====================
const handleSearch = () => { currentPage.value = 1 }
const handleReset = () => {
  queryParams.projectId = ''
  queryParams.taskName = ''
  queryParams.completeStatus = ''
  queryParams.mainOwnerId = ''
  currentPage.value = 1
}

const openDetail = (task: TaskVO) => {
  taskDrawerRef.value?.open(task)
}

// ==================== 任务表单 ====================
const taskDialogVisible = ref(false)
const taskFormRef = ref<FormInstance>()
const submitting = ref(false)
const isEdit = ref(false)

const taskForm = reactive<TaskVO & { projectId?: string | number; helperIds?: number[] }>({
  taskId: undefined,
  projectId: undefined,
  stageId: undefined,
  taskName: '',
  taskType: 'design',
  priority: 'normal',
  cycle: 1,
  planStartDate: '',
  planEndDate: '',
  mainOwnerId: undefined,
  helperIds: [] as number[],
  description: '',
  outputRequirement: '',
  isMilestone: false,
  completeStatus: 'not_started',
  progress: 0
})

const taskFormRules = reactive<FormRules>({
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  taskType: [{ required: true, message: '请选择任务类型', trigger: 'change' }],
  priority: [{ required: false }],
  planStartDate: [{ required: false }],
  planEndDate: [{ required: false }]
})

const resetTaskForm = () => {
  Object.assign(taskForm, {
    taskId: undefined,
    projectId: undefined,  // 本弹窗只创建日常任务，projectId 固定为 null（后端 createTask 中 projectId==null 走部门审核）
    stageId: undefined,
    taskName: '',
    taskType: dailyTaskTypeOptions[0]?.value || 'other',
    priority: 'normal',
    cycle: 1,
    planStartDate: '',
    planEndDate: '',
    mainOwnerId: undefined,
    helperIds: [] as number[],
    description: '',
    outputRequirement: '',
    isMilestone: false,
    completeStatus: 'not_started',
    progress: 0
  })
  isEdit.value = false
}

const handleCreate = () => {
  resetTaskForm()
  taskDialogVisible.value = true
}

const handleEdit = (task: TaskVO) => {
  resetTaskForm()
  isEdit.value = true
  Object.assign(taskForm, {
    taskId: task.taskId,
    projectId: task.projectId,
    stageId: task.stageId,
    taskName: task.taskName,
    taskType: task.taskType || 'design',
    priority: task.priority || 'normal',
    cycle: task.cycle || 1,
    planStartDate: formatDate(task.planStartDate, 'YYYY-MM-DD'),
    planEndDate: formatDate(task.planEndDate, 'YYYY-MM-DD'),
    mainOwnerId: task.mainOwnerId,
    helperIds: task.helperIds ? task.helperIds.split(',').filter(Boolean).map(Number) : [],
    description: task.description || '',
    outputRequirement: task.outputRequirement || '',
    isMilestone: task.isMilestone || false,
    completeStatus: task.completeStatus || 'not_started',
    progress: task.progress || 0
  })
  taskDialogVisible.value = true
}

const handleDelete = async (task: TaskVO) => {
  try {
    await ElMessageBox.confirm(
      `确认删除任务「${task.taskName}」？`,
      '提示',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteTask(String(task.taskId))
    ElMessage.success('任务已删除')
    await loadList()
  } catch (e) {
    if (e !== 'cancel') {
      console.error('删除任务失败', e)
    }
  }
}

const submitTask = async () => {
  const valid = await taskFormRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const data: any = { ...taskForm }
    // 序列化协助人ID为逗号分隔字符串
    if (Array.isArray(data.helperIds)) {
      // 关键修复: 空数组 join 后变空字符串, 改为 null 避免后端报错
      data.helperIds = data.helperIds.length > 0 ? data.helperIds.join(',') : null
    }
    if (isEdit.value) {
      await updateTask(data)
      message.success('任务更新成功')
    } else {
      // 本弹窗只创建日常任务：projectId/stageId 都置 null，走部门审核流程
      data.projectId = null
      data.stageId = null
      // 兜底：无计划的日常任务无法在看板按日期归类，默认填入今天，保证可见
      const today = formatDate(new Date(), 'YYYY-MM-DD')
      if (!data.planStartDate) data.planStartDate = today
      if (!data.planEndDate) data.planEndDate = data.planStartDate
      const existingDaily = taskList.value.filter(t => t.projectId == null)
      const seq = String(existingDaily.length + 1).padStart(3, '0')
      data.taskCode = `DAILY-${seq}`
      await createTask(data)
      message.success('日常任务创建成功')
    }
    taskDialogVisible.value = false
    await loadList()
  } catch (e: any) {
    console.error('任务保存失败', e)
    message.error(e?.message || '保存失败，请重试')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  // 用 simple-list（公开端点，返回全量启用字典）+ 客户端按 dictType 过滤
  // 注意：系统中的字典名是 pms_daily_task_type / task_type（不是 pms_task_type）
  try {
    const res: any = await getSimpleDictDataList()
    const dictData = Array.isArray(res) ? res : (res?.data || [])
    // 任务类型永远从 pms_daily_task_type 取（项目任务在项目详情页创建）
    const dailyOpts = extractDictOptions(dictData, 'pms_daily_task_type')
    if (dailyOpts.length > 0) currentTaskTypeOptions.value = dailyOpts
    // 优先级（系统暂未配置 pms_priority，fallback 到 hardcoded）
    const priOpts = extractDictOptions(dictData, 'pms_priority')
    if (priOpts.length > 0) currentPriorityOptions.value = priOpts
    console.log('[PMS] pms_daily_task_type:', dailyOpts.length, 'items / pms_priority:', priOpts.length, 'items')
  } catch (e) {
    console.warn('[PMS] 字典API加载失败，使用fallback', e)
  }
  loadList()
})
</script>

<style scoped>
.task-card {
  cursor: pointer;
  transition: all 0.3s ease;
}
.task-card:hover {
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}
.card-title {
  font-size: 14px;
  font-weight: 500;
  color: #1D2129;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 180px;
}
.w-full {
  width: 100%;
}
</style>
