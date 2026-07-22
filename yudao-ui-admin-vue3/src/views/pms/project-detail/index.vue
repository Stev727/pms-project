<template>
  <div class="p-20px" v-loading="loading">
    <!-- 头部信息卡片 -->
    <ContentWrap v-if="project">
      <div class="detail-header">
        <div class="header-left">
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
        </div>
        <div class="header-right">
          <el-button @click="handleEdit" v-if="checkPermi(['pms:project:update'])">
            <Icon icon="ep:edit" class="mr-5px" />编辑
          </el-button>
          <el-dropdown trigger="click">
            <el-button>更多<Icon icon="ep:arrow-down" class="ml-5px" /></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleExport">导出项目</el-dropdown-item>
                <el-dropdown-item @click="handleArchive" v-if="project.status !== 'archived'">归档项目</el-dropdown-item>
                <el-dropdown-item divided @click="handleDelete">
                  <span style="color: #F53F3F">删除项目</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button @click="goBack"><Icon icon="ep:back" class="mr-5px" />返回</el-button>
        </div>
      </div>

      <!-- 统计卡片 -->
      <el-row :gutter="16" class="mt-16px">
        <el-col :span="6">
          <div class="stat-box">
            <div class="stat-label">总进度</div>
            <div class="stat-value">{{ project.progress || 0 }}%</div>
            <el-progress :percentage="project.progress || 0" :stroke-width="6" :show-text="false" :color="getProgressColor(project)" />
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-box">
            <div class="stat-label">任务完成</div>
            <div class="stat-value">{{ taskStats.completed }} / {{ taskStats.total }}</div>
            <div class="stat-sub" style="color: #00B42A">{{ taskStats.completionRate }}% 完成</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-box">
            <div class="stat-label">延期任务</div>
            <div class="stat-value" :class="{ 'text-danger': taskStats.delayed > 0 }">
              {{ taskStats.delayed }}
              <Icon v-if="taskStats.delayed > 0" icon="ep:warning-filled" class="ml-4px" style="color: #F53F3F" />
            </div>
            <div class="stat-sub" v-if="taskStats.delayed > 0" style="color: #F53F3F">需关注</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-box">
            <div class="stat-label">质量问题</div>
            <div class="stat-value">{{ taskStats.qualityIssues }}</div>
            <div class="stat-sub" style="color: #86909C">待关闭 {{ taskStats.pendingIssues }}</div>
          </div>
        </el-col>
      </el-row>

      <!-- 项目基本信息 -->
      <el-descriptions :column="4" border class="mt-16px" size="small">
        <el-descriptions-item label="计划周期">
          {{ formatDate(project.planStartDate) }} ~ {{ formatDate(project.planEndDate) }}
          <span style="color: #86909C; margin-left: 8px">({{ projectDuration }}天)</span>
        </el-descriptions-item>
        <el-descriptions-item label="实际开始">{{ formatDate(project.actualStartDate) }}</el-descriptions-item>
        <el-descriptions-item label="项目经理">{{ getManagerName(project) }}</el-descriptions-item>
        <el-descriptions-item label="所属部门">{{ project.deptId || '-' }}</el-descriptions-item>
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
        <!-- 任务列表 Tab -->
        <el-tab-pane label="任务列表" name="tasks">
          <template v-if="activeTab === 'tasks'">
            <TaskListTab :project-id="projectId" :tasks="projectTasks" :stages="projectStages" @task-click="openTaskDrawer" @refresh="loadProjectData" @create-task="openCreateTaskDialog" />
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

        <!-- 文档 Tab -->
        <el-tab-pane label="文档" name="documents">
          <template v-if="activeTab === 'documents'">
            <el-empty description="文档管理功能开发中" />
          </template>
        </el-tab-pane>

        <!-- 质量 Tab -->
        <el-tab-pane label="质量" name="quality">
          <template v-if="activeTab === 'quality'">
            <el-empty description="质量管理功能开发中" />
          </template>
        </el-tab-pane>

        <!-- 变更 Tab -->
        <el-tab-pane label="变更" name="changes">
          <template v-if="activeTab === 'changes'">
            <el-empty description="变更管理功能开发中" />
          </template>
        </el-tab-pane>
      </el-tabs>
    </ContentWrap>

    <!-- 任务详情抽屉 -->
    <TaskDetailDrawer ref="taskDrawerRef" @refresh="loadProjectData" />

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
                <el-option label="设计" value="design" />
                <el-option label="评审" value="review" />
                <el-option label="测试" value="testing" />
                <el-option label="采购" value="procurement" />
                <el-option label="打样" value="prototyping" />
                <el-option label="文档" value="documentation" />
                <el-option label="审批" value="approval" />
                <el-option label="供方协同" value="supplier_synergy" />
                <el-option label="其他" value="other" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级" prop="priority">
              <el-select v-model="taskForm.priority" placeholder="请选择" class="w-full">
                <el-option label="普通" value="normal" />
                <el-option label="高" value="high" />
                <el-option label="紧急" value="urgent" />
                <el-option label="低" value="low" />
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
import { getProject, ProjectVO } from '@/api/pms/project'
import { getTaskList, createTask, TaskVO } from '@/api/pms/task'
import { getStageList, StageVO } from '@/api/pms/stage'
import GanttTab from './GanttTab.vue'
import TaskListTab from './TaskListTab.vue'
import TaskDetailDrawer from './TaskDetailDrawer.vue'
import {
  projectStatusMap, phaseColorMap, priorityMap, projectTypeOptions,
  taskStatusMap, formatDate, calcDuration, calcDelayDays
} from '../pms-utils'
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'PmsProjectDetail' })

const route = useRoute()
const { push, back } = useRouter()
const message = useMessage()

const loading = ref(false)
const activeTab = ref('tasks')
const project = ref<ProjectVO>()
const projectId = computed(() => route.params.id as string)
const projectTasks = ref<TaskVO[]>([])
const projectStages = ref<StageVO[]>([])
const taskDependencies = ref<any[]>([])
const taskDrawerRef = ref()

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
  isMilestone: false,
  description: '',
  outputRequirement: ''
})
const taskFormRules = {
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  stageId: [{ required: true, message: '请选择所属阶段', trigger: 'change' }],
  planStartDate: [{ required: true, message: '请选择计划开始日期', trigger: 'change' }],
  planEndDate: [{ required: true, message: '请选择计划结束日期', trigger: 'change' }]
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
    total,
    completed,
    delayed,
    inProgress: tasks.filter(t => t.completeStatus === 'in_progress').length,
    notStarted: tasks.filter(t => t.completeStatus === 'not_started').length,
    completionRate: total > 0 ? Math.round((completed / total) * 100) : 0,
    qualityIssues: 0,
    pendingIssues: 0
  }
})

const projectDuration = computed(() => {
  if (!project.value?.planStartDate || !project.value?.planEndDate) return 0
  return calcDuration(project.value.planStartDate, project.value.planEndDate)
})

// ==================== 看板 ====================
const kanbanColumns = computed(() => {
  const statuses = ['not_started', 'in_progress', 'completed', 'delayed', 'paused']
  return statuses.map(status => {
    const config = taskStatusMap[status]
    const tasks = projectTasks.value.filter(t => {
      if (status === 'delayed') {
        return calcDelayDays(t.planEndDate, t.completeStatus) > 0 && t.completeStatus !== 'completed'
      }
      if (status === 'completed' || status === 'in_progress' || status === 'not_started' || status === 'paused') {
        if (calcDelayDays(t.planEndDate, t.completeStatus) > 0 && t.completeStatus !== 'completed') return false
        return t.completeStatus === status
      }
      return false
    })
    return {
      key: status,
      title: config?.label || status,
      color: config?.borderColor || '#86909C',
      tasks
    }
  })
})

// ==================== 数据加载 ====================
const loadProjectData = async () => {
  loading.value = true
  try {
    const [proj, tasks, stages] = await Promise.all([
      getProject(Number(projectId.value)),
      getTaskList(),
      getStageList()
    ])
    project.value = proj
    projectTasks.value = (tasks || []).filter((t: TaskVO) => String(t.projectId) === String(projectId.value))
    projectStages.value = (stages || []).filter((s: StageVO) => String(s.projectId) === String(projectId.value))
  } catch (e) {
    console.error('加载项目详情失败', e)
  } finally {
    loading.value = false
  }
}

const handleTabChange = (tab: string) => {
  // 懒加载，首次切换时触发渲染
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
const getManagerName = (item: any) => item?.projectManagerId ? `用户${item.projectManagerId}` : (item?.mainOwnerId ? `用户${item.mainOwnerId}` : '未分配')
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
    taskName: '',
    stageId: projectStages.value[0]?.stageId,
    taskType: 'design',
    priority: 'normal',
    cycle: 5,
    planStartDate: '',
    planEndDate: '',
    mainOwnerId: undefined,
    isMilestone: false,
    description: '',
    outputRequirement: ''
  })
  createTaskDialogVisible.value = true
}

const submitCreateTask = async () => {
  if (!taskFormRef.value) return
  const valid = await taskFormRef.value.validate().catch(() => false)
  if (!valid) return
  submittingTask.value = true
  try {
    await createTask({
      ...taskForm,
      projectId: projectId.value,
      completeStatus: 'not_started',
      progress: 0
    })
    message.success('任务创建成功')
    createTaskDialogVisible.value = false
    await loadProjectData()
  } catch (e: any) {
    message.error(e?.message || '任务创建失败')
  } finally {
    submittingTask.value = false
  }
}

const handleEdit = () => {
  push({ name: 'PmsProject', query: { edit: projectId.value } })
}

const handleExport = () => message.info('导出功能开发中')
const handleArchive = () => {
  message.confirm(`确认归档项目「${project.value?.projectName}」？`).then(() => {
    message.success('归档成功')
  }).catch(() => {})
}
const handleDelete = () => {
  message.delConfirm(`确认删除项目「${project.value?.projectName}」？此操作不可恢复！`).then(() => {
    message.success('删除成功')
    goBack()
  }).catch(() => {})
}

const goBack = () => {
  back()
}

onMounted(() => {
  loadProjectData()
})
</script>

<style scoped>
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.project-title {
  font-size: 20px;
  font-weight: 600;
  color: #1D2129;
  margin: 0 0 8px 0;
  display: flex;
  align-items: center;
  gap: 8px;
}
.project-sub {
  display: flex;
  align-items: center;
  font-size: 13px;
  color: #4E5969;
}
.sub-item {
  font-size: 13px;
}
.header-right {
  display: flex;
  gap: 8px;
  align-items: center;
}
.stat-box {
  background: #F7F8FA;
  border-radius: 6px;
  padding: 16px;
  text-align: center;
}
.stat-label {
  font-size: 13px;
  color: #86909C;
  margin-bottom: 8px;
}
.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #1D2129;
  margin-bottom: 4px;
}
.stat-sub {
  font-size: 12px;
  color: #86909C;
}
.text-danger {
  color: #F53F3F !important;
}
.mt-16px {
  margin-top: 16px;
}

/* 看板 */
.kanban-board {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 8px;
  min-height: 400px;
}
.kanban-column {
  flex: 0 0 260px;
  background: #F7F8FA;
  border-radius: 6px;
  padding: 12px;
  display: flex;
  flex-direction: column;
}
.kanban-column-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #E5E6EB;
}
.column-title {
  font-size: 14px;
  font-weight: 600;
  color: #1D2129;
  display: flex;
  align-items: center;
  gap: 6px;
}
.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}
.kanban-cards {
  flex: 1;
  overflow-y: auto;
}
.kanban-card {
  background: #FFF;
  border-radius: 4px;
  padding: 12px;
  margin-bottom: 8px;
  cursor: pointer;
  border: 1px solid #E5E6EB;
  transition: all 0.2s;
}
.kanban-card:hover {
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  border-color: #2468F2;
}
.kanban-card .card-title {
  font-size: 14px;
  font-weight: 500;
  color: #1D2129;
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kanban-card .card-info {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #86909C;
  margin-bottom: 4px;
}
.kanban-empty {
  text-align: center;
  color: #C9CDD4;
  font-size: 13px;
  padding: 40px 0;
}
</style>
