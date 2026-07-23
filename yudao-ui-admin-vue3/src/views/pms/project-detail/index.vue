<template>
  <div class="p-20px" v-loading="loading">
    <!-- 头部信息卡片 -->
    <ContentWrap v-if="project">
      <div class="detail-header">
        <div class="header-left">
          <!-- 面包屑导航 -->
          <div class="breadcrumb">
            <el-breadcrumb separator=">">
              <el-breadcrumb-item :to="{ path: '/pms/project' }">项目管理</el-breadcrumb-item>
              <el-breadcrumb-item>{{ project.projectName }}</el-breadcrumb-item>
            </el-breadcrumb>
          </div>
          <h2 class="project-title">
            {{ project.projectName }}
            <el-tag v-if="project.isKeyProject" type="danger" size="small" effect="dark">重点</el-tag>
          </h2>
          <div class="project-sub">
            <span class="sub-item">{{ project.projectCode }}</span>
            <el-divider direction="vertical" />
            <span class="sub-item">{{ getProjectTypeLabel(project.projectType) }}</span>
            <el-divider direction="vertical" />
            <el-tag :style="getPhaseTagStyle(project.currentStage)" size="small" effect="light">
              {{ getPhaseLabel(project.currentStage) }}
            </el-tag>
            <el-divider direction="vertical" />
            <el-tag :type="getStatusType(project.status)" size="small">{{ getStatusLabel(project.status) }}</el-tag>
          </div>
          <div class="project-progress">
            <span class="mr-8px">项目完成率</span>
            <el-progress :percentage="projectCompletionRate" :stroke-width="12" :format="(p) => `${p}%`" class="flex-1 mr-16px" />
            <span class="text-gray">{{ completedTaskCount }} / {{ totalTaskCount }} 任务</span>
          </div>
          <div class="project-meta mt-8px">
            <span>已用 {{ getElapsedDays(project.actualStartDate || project.planStartDate) }} 天</span>
            <el-divider direction="vertical" />
            <span>{{ getRemainingDays(project.planEndDate) }}</span>
            <el-divider direction="vertical" />
            <el-tag v-if="projectRiskLevel" :type="projectRiskLevel.type" size="small">
              {{ projectRiskLevel.label }}
            </el-tag>
          </div>
        </div>
        <div class="header-right">
          <el-button @click="handleCopyProject" v-if="false">
            <Icon icon="ep:copy-document" class="mr-5px" />复制项目
          </el-button>
          <el-button @click="handleEdit" v-if="checkPermi(['pms:project:update'])">
            <Icon icon="ep:edit" class="mr-5px" />编辑
          </el-button>
          <el-badge :value="pendingReviewCount" :hidden="!pendingReviewCount" :max="99">
            <el-button type="warning" @click="switchToReviewCenter">
              <Icon icon="ep:circle-check" class="mr-5px" />审核中心
            </el-button>
          </el-badge>
          <el-dropdown trigger="click">
            <el-button>更多<Icon icon="ep:arrow-down" class="ml-5px" /></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleExport" v-if="false">导出项目</el-dropdown-item>
                <el-dropdown-item @click="handleShareLink">分享链接</el-dropdown-item>
                <el-dropdown-item @click="handleArchive" v-if="project.status !== 'archived' && checkPermi(['pms:project:update'])">归档项目</el-dropdown-item>
                <el-dropdown-item divided @click="handleDelete" v-if="checkPermi(['pms:project:delete'])">
                  <span style="color: #F53F3F">删除项目</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button @click="goBack"><Icon icon="ep:back" class="mr-5px" />返回</el-button>
        </div>
      </div>

      <!-- 项目基本信息 -->
      <el-descriptions :column="4" border class="mt-16px" size="small">
        <el-descriptions-item label="计划周期">
          {{ formatDate(project.planStartDate) }} ~ {{ formatDate(project.planEndDate) }}
          <span style="color: #86909C; margin-left: 8px">({{ projectDuration }}天)</span>
        </el-descriptions-item>
        <el-descriptions-item label="实际开始">{{ formatDate(project.actualStartDate) }}</el-descriptions-item>
        <el-descriptions-item label="项目经理">{{ getManagerName(project) }}</el-descriptions-item>
        <el-descriptions-item label="所属部门">{{ deptDisplayName }}</el-descriptions-item>
        <el-descriptions-item label="项目预算">{{ project.budget ? `￥${project.budget}` : '-' }}</el-descriptions-item>
        <el-descriptions-item label="优先级">
          <el-tag :color="priorityMap[project.priority || 'normal']?.color" effect="plain" size="small">
            {{ priorityMap[project.priority || 'normal']?.label || '普通' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建方式">{{ project.createMethod === 'manual' ? '手动创建' : '模板创建' }}</el-descriptions-item>
        <el-descriptions-item label="描述" :span="4">{{ project.description || '-' }}</el-descriptions-item>
      </el-descriptions>
    </ContentWrap>

    <!-- Tab 容器 -->
    <ContentWrap class="mt-16px">
      <el-tabs v-model="activeTab" type="card" @tab-change="handleTabChange">
        <!-- 概览 Tab (NEW - FATAL+SEVERE 修复) -->
        <el-tab-pane label="概览" name="overview">
          <template v-if="activeTab === 'overview'">
            <OverviewTab :tasks="projectTasks" :stages="projectStages" :project="project" />
          </template>
        </el-tab-pane>

        <!-- 审核中心 Tab (NEW - PRD-003) -->
        <el-tab-pane label="审核中心" name="review-center">
          <template v-if="activeTab === 'review-center'">
            <ReviewCenterTab :project-id="projectId" @reviewed="onTaskDataRefresh" />
          </template>
        </el-tab-pane>

        <!-- 任务列表 Tab -->
        <el-tab-pane label="任务列表" name="tasks">
          <template v-if="activeTab === 'tasks'">
            <TaskListTab :project-id="projectId" :tasks="projectTasks" :stages="projectStages"
              @task-click="openTaskDrawer" @refresh="onTaskDataRefresh" @create-task="openCreateTaskDialog"
              @start-change="handleStartChange" />
          </template>
        </el-tab-pane>

        <!-- 看板视图 Tab -->
        <el-tab-pane label="看板视图" name="kanban">
          <template v-if="activeTab === 'kanban'">
            <div class="kanban-board">
              <div v-for="col in kanbanColumns" :key="col.key" class="kanban-column">
                <div class="kanban-column-header">
                  <span class="column-title">
                    <span class="status-dot" :style="{ background: col.color }"></span>
                    {{ col.title }}
                  </span>
                  <el-tag size="small" round>{{ col.tasks.length }}</el-tag>
                </div>
                <div class="kanban-cards">
                  <div v-for="task in col.tasks" :key="task.taskId" class="kanban-card" @click="openTaskDrawer(task)">
                    <div class="card-title" :title="task.taskName">{{ task.taskName }}</div>
                    <div class="card-info">
                      <span>{{ getManagerName(task) }}</span>
                      <span v-if="task.planEndDate" style="color: #86909C">{{ formatDate(task.planEndDate, 'MM-DD') }}</span>
                    </div>
                    <el-progress v-if="task.progress > 0" :percentage="task.progress" :stroke-width="4" :show-text="false" :color="col.color" />
                  </div>
                  <div v-if="col.tasks.length === 0" class="kanban-empty">暂无任务</div>
                </div>
              </div>
            </div>
          </template>
        </el-tab-pane>

        <!-- 甘特图 Tab -->
        <el-tab-pane label="甘特图" name="gantt">
          <template v-if="activeTab === 'gantt'">
            <div style="height: calc(100vh - 420px); min-height: 400px">
              <GanttTab :project-id="projectId" :tasks="projectTasks" :stages="projectStages" :dependencies="taskDependencies" />
            </div>
          </template>
        </el-tab-pane>

        <!-- 成员 Tab (NEW - FATAL-1 修复) -->
        <el-tab-pane label="成员" name="members">
          <template v-if="activeTab === 'members'">
            <MembersTab :project-id="projectId" ref="membersTabRef" />
          </template>
        </el-tab-pane>

        <!-- 文档 Tab (NEW - FATAL-4 修复) -->
        <el-tab-pane label="文档" name="documents">
          <template v-if="activeTab === 'documents'">
            <DocumentsTab :project-id="projectId" ref="documentsTabRef" />
          </template>
        </el-tab-pane>

        <!-- 审批 Tab (NEW - FATAL-2 修复) -->
        <el-tab-pane label="审批记录" name="approvals">
          <template v-if="activeTab === 'approvals'">
            <ApprovalTab :project-id="projectId" ref="approvalTabRef" />
          </template>
        </el-tab-pane>

        <!-- 质量 Tab -->
        <el-tab-pane label="质量" name="quality">
          <template v-if="activeTab === 'quality'">
            <QualityTab :project-id="projectId" ref="qualityTabRef" />
          </template>
        </el-tab-pane>

        <!-- 变更 Tab (NEW - SEVERE-4 修复) -->
        <el-tab-pane label="变更记录" name="changes">
          <template v-if="activeTab === 'changes'">
            <ChangesTab :project-id="projectId" ref="changesTabRef" />
          </template>
        </el-tab-pane>
      </el-tabs>
    </ContentWrap>

    <!-- 任务详情抽屉 -->
    <TaskDetailDrawer ref="taskDrawerRef" @refresh="onTaskDataRefresh" />

    <!-- P0: 项目编辑弹窗 -->
    <ProjectForm ref="projectFormRef" @success="loadProjectData" />

    <!-- 创建任务弹窗 -->
    <el-dialog v-model="createTaskDialogVisible" title="新建任务" width="620px">
      <el-form ref="taskFormRef" :model="taskForm" :rules="taskFormRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="任务名称" prop="taskName">
              <el-input v-model="taskForm.taskName" placeholder="请输入任务名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属阶段" prop="stageId">
              <el-select v-model="taskForm.stageId" placeholder="请选择阶段" class="w-full">
                <el-option v-for="s in projectStages" :key="s.stageId" :label="s.stageName" :value="s.stageId" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="任务类型" prop="taskType">
              <el-select v-model="taskForm.taskType" placeholder="请选择" class="w-full">
                <el-option v-for="opt in taskTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级" prop="priority">
              <el-select v-model="taskForm.priority" placeholder="请选择" class="w-full">
                <el-option v-for="opt in priorityOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="责任人" prop="mainOwnerId">
              <el-select v-model="taskForm.mainOwnerId" filterable placeholder="请选择" class="w-full">
                <el-option v-for="u in projectMemberUsers" :key="u.id" :label="`${u.nickname} (${u.username})`" :value="String(u.id)" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="协助人">
              <el-select v-model="taskForm.helperIds" multiple filterable placeholder="可选" class="w-full">
                <el-option v-for="u in projectMemberUsers" :key="u.id" :label="`${u.nickname} (${u.username})`" :value="String(u.id)" />
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
            <el-form-item label="工期(天)" prop="cycle">
              <el-input-number v-model="taskForm.cycle" :min="1" :max="999" class="w-full" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="里程碑">
              <el-switch v-model="taskForm.isMilestone" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="任务描述">
          <el-input v-model="taskForm.description" type="textarea" :rows="3" placeholder="请输入任务描述" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="预估工时">
              <el-input-number v-model="taskForm.estimatedHours" :min="0" :precision="1" :step="0.5" class="w-full" placeholder="小时" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="完成标准">
          <el-input v-model="taskForm.completionStandard" type="textarea" :rows="2" placeholder="请输入任务完成标准" />
        </el-form-item>
        <el-form-item label="输出物要求">
          <el-input v-model="taskForm.outputRequirement" type="textarea" :rows="2" placeholder="请输入输出物要求" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createTaskDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submittingTask" @click="submitCreateTask">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { getProject, updateProject, ProjectVO } from '@/api/pms/project'
import { getTaskList, createTask, TaskVO } from '@/api/pms/task'
import { getStageList, StageVO } from '@/api/pms/stage'
import GanttTab from './GanttTab.vue'
import TaskListTab from './TaskListTab.vue'
import TaskDetailDrawer from './TaskDetailDrawer.vue'
import OverviewTab from './OverviewTab.vue'
import MembersTab from './MembersTab.vue'
import DocumentsTab from './DocumentsTab.vue'
import ApprovalTab from './ApprovalTab.vue'
import ChangesTab from './ChangesTab.vue'
import QualityTab from './QualityTab.vue'
import ReviewCenterTab from './ReviewCenterTab.vue'
import ProjectForm from '../project/ProjectForm.vue'
import {
  projectStatusMap, phaseColorMap, priorityMap, projectTypeOptions,
  taskTypeOptions, priorityOptions, taskStatusMap, formatDate, calcDuration, calcDelayDays
} from '../pms-utils'
import { checkPermi } from '@/utils/permission'
import { useUserNames } from '@/hooks/pms/useUserNames'
import { useProjectMembers } from '@/hooks/pms/useProjectMembers'

defineOptions({ name: 'PmsProjectDetail' })

const route = useRoute()
const { push, back } = useRouter()
const message = useMessage()
const { userList, getUserName, getUserNamesFromStr, ensureLoaded: ensureUsersLoaded } = useUserNames()
const { projectMemberUsers, loadProjectMembers } = useProjectMembers()

const loading = ref(false)
const activeTab = ref('overview')
const project = ref<ProjectVO>()
const projectId = computed(() => route.params.id as string)
const projectTasks = ref<TaskVO[]>([])
const projectStages = ref<StageVO[]>([])
const taskDependencies = ref<any[]>([])

// P1: 从任务列表中提取依赖关系，每次加载后自动计算
const updateTaskDependencies = () => {
  const deps: any[] = []
  projectTasks.value.forEach(t => {
    if (t.predecessorTaskIds) {
      const predIds = t.predecessorTaskIds.split(',').map(id => id.trim()).filter(Boolean)
      predIds.forEach(predId => {
        deps.push({
          from: predId,
          to: t.taskId,
          type: 'FS' // Finish-to-Start 默认类型
        })
      })
    }
    // 阶段内顺序依赖：同一 stageId 内的任务按 sortOrder 连接
    if (t.stageId && t.sortOrder != null && t.sortOrder > 0) {
      const sameStageTasks = projectTasks.value
        .filter(o => String(o.stageId) === String(t.stageId) && o.taskId !== t.taskId)
        .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
      const prevTask = sameStageTasks
        .filter(o => (o.sortOrder || 0) < (t.sortOrder || 0))
        .sort((a, b) => (b.sortOrder || 0) - (a.sortOrder || 0))[0]
      if (prevTask && !deps.some(d => d.from === prevTask.taskId && d.to === t.taskId)) {
        deps.push({ from: prevTask.taskId, to: t.taskId, type: 'FS' })
      }
    }
  })
  taskDependencies.value = deps
}
const taskDrawerRef = ref()
const membersTabRef = ref()
const documentsTabRef = ref()
const approvalTabRef = ref()
const qualityTabRef = ref()
const changesTabRef = ref()
const projectFormRef = ref()

// ==================== 任务创建弹窗 ====================
const createTaskDialogVisible = ref(false)
const taskFormRef = ref<FormInstance>()
const taskForm = reactive({
  taskName: '',
  stageId: undefined as number | undefined,
  taskType: 'design',
  priority: 'normal',
  cycle: 5,
  planStartDate: '',
  planEndDate: '',
  mainOwnerId: undefined as number | undefined,
  helperIds: [] as number[],
  isMilestone: false,
  description: '',
  outputRequirement: '',
  completionStandard: '',
  estimatedHours: undefined as number | undefined
})
const taskFormRules = {
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  stageId: [{ required: true, message: '请选择所属阶段', trigger: 'change' }],
  planStartDate: [
    { required: true, message: '请选择计划开始日期', trigger: 'change' },
    {
      validator: (_rule: any, value: string, callback: any) => {
        if (value && project.value?.planStartDate && new Date(value) < new Date(project.value.planStartDate)) {
          callback(new Error(`开始日期不能早于项目开始日期`))
        } else { callback() }
      }, trigger: 'change'
    }
  ],
  planEndDate: [
    { required: true, message: '请选择计划结束日期', trigger: 'change' },
    {
      validator: (_rule: any, value: string, callback: any) => {
        if (value && taskForm.planStartDate && new Date(value) < new Date(taskForm.planStartDate)) {
          callback(new Error('结束日期不能早于开始日期')); return
        }
        if (value && project.value?.planEndDate && new Date(value) > new Date(project.value.planEndDate)) {
          callback(new Error('结束日期不能晚于项目结束日期')); return
        }
        callback()
      }, trigger: 'change'
    }
  ],
  mainOwnerId: [{ required: true, message: '请选择责任人', trigger: 'change' }]
}
const submittingTask = ref(false)

// ==================== 统计 ====================
const taskStats = computed(() => {
  const tasks = projectTasks.value
  const total = tasks.length
  const completed = tasks.filter(t => t.completeStatus === 'completed').length
  const delayed = tasks.filter(t => {
    if (t.completeStatus === 'completed' || t.completeStatus === 'cancelled') return false
    return calcDelayDays(t.planEndDate, t.completeStatus) > 0
  }).length
  return {
    total, completed, delayed,
    inProgress: tasks.filter(t => t.completeStatus === 'in_progress').length,
    notStarted: tasks.filter(t => t.completeStatus === 'not_started').length,
    completionRate: total > 0 ? Math.round((completed / total) * 100) : 0,
    qualityIssues: 0, pendingIssues: 0
  }
})

// ==================== 审核中心 (PRD-003) ====================
const projectCompletionRate = computed(() => {
  if (!projectTasks.value.length) return 0
  return Math.round(projectTasks.value.filter(t => t.completeStatus === 'completed').length / projectTasks.value.length * 100)
})
const completedTaskCount = computed(() => projectTasks.value.filter(t => t.completeStatus === 'completed').length)
const totalTaskCount = computed(() => projectTasks.value.length)
const pendingReviewCount = computed(() => projectTasks.value.filter(t => t.completeStatus === 'pending_review').length)

const switchToReviewCenter = () => {
  activeTab.value = 'review-center'
}

const projectDuration = computed(() => {
  if (!project.value?.planStartDate || !project.value?.planEndDate) return 0
  return calcDuration(project.value.planStartDate, project.value.planEndDate)
})

// 已用天数
const getElapsedDays = (startDate: any) => {
  if (!startDate) return 0
  const start = Array.isArray(startDate)
    ? new Date(startDate[0], startDate[1] - 1, startDate[2])
    : new Date(startDate)
  return Math.floor((Date.now() - start.getTime()) / (1000 * 60 * 60 * 24))
}

// 剩余天数（文本）
const getRemainingDays = (planEndDate: any) => {
  if (!planEndDate) return '-'
  const end = Array.isArray(planEndDate)
    ? new Date(planEndDate[0], planEndDate[1] - 1, planEndDate[2])
    : new Date(planEndDate)
  const diff = Math.ceil((end.getTime() - Date.now()) / (1000 * 60 * 60 * 24))
  if (diff < 0) return `延期${Math.abs(diff)}天`
  if (diff === 0) return '今天到期'
  return `剩${diff}天`
}

// 剩余天数（数字）
const getRemainingDaysRaw = (planEndDate: any) => {
  if (!planEndDate) return 0
  const end = Array.isArray(planEndDate)
    ? new Date(planEndDate[0], planEndDate[1] - 1, planEndDate[2])
    : new Date(planEndDate)
  return Math.ceil((end.getTime() - Date.now()) / (1000 * 60 * 60 * 24))
}

// 项目风险等级
const projectRiskLevel = computed(() => {
  if (!project.value) return null
  const rate = projectCompletionRate.value
  const remaining = getRemainingDaysRaw(project.value.planEndDate)
  if (remaining < 0) return { type: 'danger', label: '已延期' }
  if (remaining < 30 && rate < 50) return { type: 'warning', label: '进度风险' }
  if (remaining < 7 && rate < 80) return { type: 'warning', label: '即将到期' }
  return null
})

// ==================== 看板 ====================
const kanbanColumns = computed(() => {
  const statuses = ['not_started', 'in_progress', 'pending_review', 'completed', 'delayed', 'paused']
  return statuses.map(status => {
    const config = taskStatusMap[status]
    const tasks = projectTasks.value.filter(t => {
      const isDelayed = calcDelayDays(t.planEndDate, t.completeStatus) > 0 && t.completeStatus !== 'completed'
      if (status === 'delayed') {
        // 已延期的：排除已完成和已取消的
        return isDelayed
      }
      if (status === 'completed' || status === 'in_progress' || status === 'not_started' || status === 'paused' || status === 'pending_review') {
        // 如果任务已延期，不应出现在正常状态列中
        if (isDelayed) return false
        return t.completeStatus === status
      }
      return false
    })
    return { key: status, title: config?.label || status, color: config?.borderColor || '#86909C', tasks }
  })
})

// ==================== 数据加载 ====================
const loadProjectData = async () => {
  loading.value = true
  try {
    const [proj, tasks, stages] = await Promise.all([
      getProject(projectId.value),
      getTaskList(),
      getStageList()
    ])
    project.value = proj
    // P0-04: 确保按 projectId 过滤任务和阶段，使用字符串比较避免雪花ID精度丢失
    projectTasks.value = (tasks || []).filter((t: TaskVO) =>
      String(t.projectId) === String(projectId.value)
    )
    projectStages.value = (stages || []).filter((s: StageVO) =>
      String(s.projectId) === String(projectId.value)
    )
    // P0-04: 调试日志 - 确认数据加载情况
    console.log('[PMS] loadProjectData:', {
      projectId: projectId.value,
      taskCount: projectTasks.value.length,
      stageCount: projectStages.value.length
    })
    // P1: 每次加载数据后更新任务依赖关系
    updateTaskDependencies()
  } catch (e) {
    console.error('加载项目详情失败', e)
  } finally {
    loading.value = false
  }
}

// P1-9: 任务状态变更后检查项目是否需要自动启动
const checkProjectAutoStart = async () => {
  if (project.value?.status === 'initiating' || project.value?.status === 'planning') {
    const hasInProgress = projectTasks.value.some(t =>
      t.completeStatus === 'in_progress' || t.completeStatus === 'completed'
    )
    if (hasInProgress) {
      try {
        await updateProject({
          ...project.value,
          status: 'in_progress',
          actualStartDate: new Date().toISOString().split('T')[0]
        } as any)
        await loadProjectData()
      } catch (e) { console.error('自动启动项目失败', e) }
    }
  }
}

// 任务数据刷新包装器：重新加载数据后检查项目自动启动
const onTaskDataRefresh = async () => {
  await loadProjectData()
  await checkProjectAutoStart()
}

const handleTabChange = (tab: string) => {
  // 懒加载 — 首次切换时刷新子组件数据
  if (tab === 'members') { nextTick(() => membersTabRef.value?.refresh?.()) }
  if (tab === 'documents') { nextTick(() => documentsTabRef.value?.refresh?.()) }
  if (tab === 'approvals') { nextTick(() => approvalTabRef.value?.refresh?.()) }
  if (tab === 'quality') { nextTick(() => qualityTabRef.value?.refresh?.()) }
  if (tab === 'changes') { nextTick(() => changesTabRef.value?.refresh?.()) }
}

// ==================== 辅助函数 ====================
const getPhaseLabel = (stage?: string) => phaseColorMap[stage || '']?.label || '未开始'
const getPhaseTagStyle = (stage?: string) => {
  const p = phaseColorMap[stage || '']
  return p ? `color: ${p.color}; background: ${p.bg}; border-color: ${p.border};` : ''
}
const getStatusType = (status: string) => projectStatusMap[status]?.type || 'info'
const getStatusLabel = (status: string) => projectStatusMap[status]?.label || status
const getProjectTypeLabel = (type?: string) => projectTypeOptions.find(o => o.value === type)?.label || type || '-'
const getManagerName = (item: any) => {
  if (item?.projectManagerId) return getUserName(item.projectManagerId)
  if (item?.mainOwnerId) return getUserName(item.mainOwnerId)
  return '未分配'
}
const getProgressColor = (proj: ProjectVO) => {
  if (proj.status === 'completed') return '#00B42A'
  if (proj.status === 'delayed') return '#F53F3F'
  return '#2468F2'
}

// ==================== 操作 ====================
const openTaskDrawer = (task: TaskVO) => {
  taskDrawerRef.value?.open(task)
}

const openCreateTaskDialog = () => {
  taskFormRef.value?.resetFields?.()
  Object.assign(taskForm, {
    taskName: '', stageId: projectStages.value[0]?.stageId, taskType: 'design',
    priority: 'normal', cycle: 5, planStartDate: '', planEndDate: '',
    mainOwnerId: undefined, helperIds: [], isMilestone: false, completionStandard: '', estimatedHours: undefined,
    description: '', outputRequirement: ''
  })
  // 加载当前项目成员列表作为责任人/协助人下拉数据源
  loadProjectMembers(projectId.value)
  createTaskDialogVisible.value = true
}

const submitCreateTask = async () => {
  if (!taskFormRef.value) return
  const valid = await taskFormRef.value.validate().catch(() => false)
  if (!valid) return
  submittingTask.value = true
  try {
    // 生成任务编号: TASK-阶段缩写-3位序号
    const stageCode = projectStages.value.find(s => s.stageId === taskForm.stageId)?.stageCode || 'GEN'
    const existingTasks = projectTasks.value.filter(t => String(t.stageId) === String(taskForm.stageId))
    const seq = String(existingTasks.length + 1).padStart(3, '0')
    const taskCode = `TASK-${stageCode}-${seq}`
    // 关键修复: helperIds 后端期望 String 或 null, 空数组导致 500
    const submitData = {
      ...taskForm,
      projectId: projectId.value,
      completeStatus: 'not_started',
      progress: 0,
      taskCode: taskCode
    }
    if (Array.isArray(submitData.helperIds)) {
      submitData.helperIds = submitData.helperIds.length > 0
        ? submitData.helperIds.join(',')
        : null
    }
    await createTask(submitData)
    message.success('任务创建成功')
    createTaskDialogVisible.value = false
    await loadProjectData()
    await checkProjectAutoStart()
  } catch (e: any) {
    message.error(e?.message || '任务创建失败')
  } finally { submittingTask.value = false }
}

const handleEdit = () => {
  // P0: 在当前页面弹窗编辑，不再跳转到 project/index 带 edit 参数
  if (project.value) {
    projectFormRef.value?.open('update', { ...project.value })
  }
}

const handleStartChange = () => {
  activeTab.value = 'changes'
  nextTick(() => changesTabRef.value?.refresh?.())
}

const handleCopyProject = () => {
  message.info('复制项目功能开发中')
  // TODO: 跳转创建向导并预填数据
}

const handleExport = () => message.info('导出功能开发中')

const handleShareLink = () => {
  const url = `${window.location.origin}/pms/project-detail/${projectId.value}`
  navigator.clipboard.writeText(url).then(() => {
    message.success('项目链接已复制到剪贴板')
  }).catch(() => {
    message.info(`项目链接: ${url}`)
  })
}

const handleArchive = () => {
  message.confirm(`确认归档项目「${project.value?.projectName}」？`).then(async () => {
    try {
      await import('@/api/pms/project').then(m => m.updateProject({ ...project.value!, archived: true, status: 'archived' } as any))
      message.success('归档成功')
      loadProjectData()
    } catch (e) { message.error('归档失败') }
  }).catch(() => {})
}

const handleDelete = () => {
  message.delConfirm(`确认删除项目「${project.value?.projectName}」？此操作不可恢复！`).then(async () => {
    try {
      await import('@/api/pms/project').then(m => m.deleteProject(project.value!.projectId))
      message.success('删除成功')
      goBack()
    } catch (e) { message.error('删除失败') }
  }).catch(() => {})
}

// P1-05 修复: 返回项目列表而非 router.back()，避免回到创建页触发草稿恢复
const goBack = () => { push('/pms/project') }

// 部门名称解析 — P1: 改为 computed 确保 deptList 加载后自动刷新
const deptList = ref<any[]>([])
const getDeptName = (deptId?: number) => {
  if (!deptId) return '-'
  const dept = deptList.value.find(d => d.id === deptId)
  return dept?.name || `部门${deptId}`
}

// P1: 为模板中直接使用的 deptName 提供 computed 响应式版本
const deptDisplayName = computed(() => {
  if (!project.value?.deptId) return '-'
  const dept = deptList.value.find(d => d.id === project.value.deptId)
  return dept?.name || `部门${project.value.deptId}`
})

onMounted(async () => {
  ensureUsersLoaded()
  try {
    const { getSimpleDeptList } = await import('@/api/system/dept')
    const depts = await getSimpleDeptList()
    deptList.value = (depts as any[]) || []
  } catch { /* ignore */ }
  loadProjectData()
})
</script>

<style scoped>
.detail-header {
  display: flex; justify-content: space-between; align-items: flex-start;
}
.breadcrumb { margin-bottom: 8px; }
.project-title {
  font-size: 20px; font-weight: 600; color: #1D2129; margin: 0 0 8px 0;
  display: flex; align-items: center; gap: 8px;
}
.project-sub {
  display: flex; align-items: center; font-size: 13px; color: #4E5969;
}
.sub-item { font-size: 13px; }
.header-right {
  display: flex; gap: 8px; align-items: center;
}
.mt-16px { margin-top: 16px; }

/* 看板 */
.kanban-board {
  display: flex; gap: 12px; overflow-x: auto; padding-bottom: 8px; min-height: 400px;
}
.kanban-column {
  flex: 0 0 260px; background: #F7F8FA; border-radius: 6px; padding: 12px;
  display: flex; flex-direction: column;
}
.kanban-column-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 12px; padding-bottom: 8px; border-bottom: 1px solid #E5E6EB;
}
.column-title {
  font-size: 14px; font-weight: 600; color: #1D2129;
  display: flex; align-items: center; gap: 6px;
}
.status-dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
.kanban-cards { flex: 1; overflow-y: auto; }
.kanban-card {
  background: #FFF; border-radius: 4px; padding: 12px; margin-bottom: 8px;
  cursor: pointer; border: 1px solid #E5E6EB; transition: all 0.2s;
}
.kanban-card:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.1); border-color: #2468F2; }
.kanban-card .card-title {
  font-size: 14px; font-weight: 500; color: #1D2129; margin-bottom: 6px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.kanban-card .card-info {
  display: flex; justify-content: space-between; font-size: 12px; color: #86909C; margin-bottom: 4px;
}
.kanban-empty { text-align: center; color: #C9CDD4; font-size: 13px; padding: 40px 0; }

.project-progress {
  display: flex; align-items: center; margin-top: 12px; font-size: 13px; color: #4E5969;
}
.project-progress .flex-1 { flex: 1; }
.project-progress .mr-8px { margin-right: 8px; }
.project-progress .mr-16px { margin-right: 16px; }
.project-progress .text-gray { color: #86909C; }
.project-meta {
  display: flex; align-items: center; font-size: 13px; color: #4E5969; margin-top: 8px;
}
.mt-8px { margin-top: 8px; }
</style>
