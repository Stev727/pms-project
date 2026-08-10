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
      <!-- 历史遗留区 -->
      <el-card class="board-section" shadow="never">
        <template #header>
          <div class="section-header">
            <span class="section-title">📌 历史遗留任务</span>
            <el-tag type="danger" effect="plain">{{ board.legacyTasks.length }}</el-tag>
            <span class="section-hint">计划开始早于 {{ dateRange?.[0] }} 且未完成的任务（含项目与日常）</span>
          </div>
        </template>
        <el-empty v-if="board.legacyTasks.length === 0" description="无历史遗留任务" :image-size="60" />
        <div v-else class="task-grid">
          <task-board-card
            v-for="t in board.legacyTasks"
            :key="t.taskId"
            :task="t"
            :current-user-id="currentUserId"
            @detail="openDetail"
            @submit-review="handleSubmitReview"
          />
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
          <div class="project-group-title">
            <Icon icon="ep:folder" class="mr-5px" />
            {{ group.projectName }}
            <el-tag size="small" effect="plain" class="ml-8px">{{ group.tasks.length }}</el-tag>
          </div>
          <div class="task-grid">
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
        <div v-else class="task-grid">
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
            <el-option v-for="opt in dailyTaskTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人" prop="mainOwnerId">
          <el-select v-model="dailyForm.mainOwnerId" placeholder="请选择负责人" filterable class="w-full">
            <el-option v-for="u in userList" :key="u.id" :label="u.nickname" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="dailyForm.priority" class="w-full">
            <el-option v-for="opt in priorityOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
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
import { getTaskBoard, createTask, submitReview, TaskVO } from '@/api/pms/task'
import { getProjectList, ProjectVO } from '@/api/pms/project'
import { getStageList, StageVO } from '@/api/pms/stage'
import TaskDetailDrawer from '../project-detail/TaskDetailDrawer.vue'
import TaskBoardCard from './components/TaskBoardCard.vue'
import {
  taskStatusMap, priorityMap, priorityOptions, dailyTaskTypeOptions, formatDate
} from '../pms-utils'
import { useUserNames } from '@/hooks/pms/useUserNames'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/store/modules/user'

defineOptions({ name: 'PmsMyTaskBoard' })

const message = useMessage()
const { userList, ensureLoaded: ensureUsersLoaded } = useUserNames()
const userStore = useUserStore()
const currentUserId = computed(() => userStore.getUser?.id)

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
  // 默认范围：本月
  const n = new Date()
  dateRange.value = [
    formatDate(new Date(n.getFullYear(), n.getMonth(), 1), 'YYYY-MM-DD'),
    formatDate(n, 'YYYY-MM-DD')
  ]
  await ensureUsersLoaded()
  await loadBoard()
})
</script>

<style scoped>
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
.task-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}
.ml-8px {
  margin-left: 8px;
}
.w-full {
  width: 100%;
}
</style>
