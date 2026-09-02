<template>
  <div>
    <!-- 方案A sticky group：tabs(40px) 下方整组冻结（筛选行 + 操作行/提示行） -->
    <div class="sticky-toolbar-group">
    <!-- 上层工具栏：新建/搜索/筛选 + 导出/折叠（始终显示） -->
    <div class="task-toolbar task-toolbar-top">
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <el-button type="primary" size="small" @click="$emit('create-task')" v-if="checkPermi(['pms:task:create']) && canProject(PERM.TASK_CREATE)">
          <Icon icon="ep:plus" class="mr-4px" />新建任务
        </el-button>
        <el-button size="small" @click="openStageDialog" v-if="checkPermi(['pms:task:create'])">
          <Icon icon="ep:folder-add" class="mr-4px" />新建阶段
        </el-button>
        <el-input v-model="searchKeyword" placeholder="搜索任务名称" clearable size="small" style="width: 200px">
          <template #prefix><Icon icon="ep:search" /></template>
        </el-input>
        <el-select filterable v-model="filterStage" placeholder="全部阶段" clearable size="small" style="width: 120px">
          <el-option v-for="s in stages" :key="s.stageId" :label="s.stageName" :value="s.stageId" />
        </el-select>
        <el-select filterable v-model="filterStatus" placeholder="全部状态" clearable size="small" style="width: 120px">
          <el-option v-for="(v, k) in taskStatusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-select filterable v-model="filterAssignee" placeholder="我的任务" clearable size="small" style="width: 120px">
          <el-option label="我负责的" value="mine" />
          <el-option label="我参与的" value="involved" />
        </el-select>
      </div>
      <div>
        <el-button size="small" :loading="exporting" @click="handleExport" v-if="checkPermi(['pms:task:query'])">
          <Icon icon="ep:download" class="mr-4px" />导出
        </el-button>
        <el-button size="small" @click="expandAll = !expandAll">
          <Icon :icon="expandAll ? 'ep:folder-opened' : 'ep:folder'" class="mr-4px" />
          {{ expandAll ? '折叠全部' : '展开全部' }}
        </el-button>
      </div>
    </div>

    <!-- 下层工具栏：选中任务后的操作按钮（独立 sticky 行，滚动时始终可见） -->
    <div v-if="selectedTask && !selectedTask.isStageRow" class="task-toolbar task-toolbar-action"
         :style="{ left: taskActionBarLeft }">
      <div class="task-op-actions">
        <span class="task-op-label" :title="selectedTask.taskName">
          <Icon icon="ep:select" class="mr-4px" />已选：{{ selectedTask.taskName }}
        </span>
        <el-button v-if="canTransition(selectedTask, 'start')" size="small" type="primary" @click.stop="handleTransition(selectedTask, 'start')">开始任务</el-button>
        <el-button v-if="canTransition(selectedTask, 'dispatch')" size="small" type="primary" @click.stop="handleTransition(selectedTask, 'dispatch')">派发</el-button>
        <el-button v-if="canTransition(selectedTask, 'accept')" size="small" type="primary" @click.stop="handleTransition(selectedTask, 'accept')">接收</el-button>
        <el-button v-if="canTransition(selectedTask, 'reject')" size="small" type="danger" @click.stop="handleTransition(selectedTask, 'reject')">拒绝</el-button>
        <el-button v-if="canTransition(selectedTask, 'redispatch')" size="small" type="primary" @click.stop="handleTransition(selectedTask, 'redispatch')">重新派发</el-button>
        <el-button v-if="canTransition(selectedTask, 'submit')" size="small" type="success" @click.stop="handleTransition(selectedTask, 'submit')">提交完成</el-button>
        <el-button v-if="canTransition(selectedTask, 'approve')" size="small" type="success" @click.stop="handleTransition(selectedTask, 'approve')">审核通过</el-button>
        <el-button v-if="canTransition(selectedTask, 'reject_review')" size="small" type="danger" @click.stop="handleTransition(selectedTask, 'reject_review')">驳回</el-button>
        <el-button v-if="canTransition(selectedTask, 'pause')" size="small" type="warning" @click.stop="handleTransition(selectedTask, 'pause')">暂停</el-button>
        <el-button v-if="canTransition(selectedTask, 'resume')" size="small" type="primary" @click.stop="handleTransition(selectedTask, 'resume')">恢复</el-button>
        <el-button v-if="canReportProgress(selectedTask)" size="small" type="primary" @click.stop="$emit('taskClick', selectedTask)">进度填报</el-button>
        <el-button v-if="canAddSubtask(selectedTask)" size="small" type="primary" @click.stop="$emit('add-subtask', selectedTask)">添加子任务</el-button>
        <el-button v-if="ALLOW_CHANGE_STATUSES.includes(selectedTask.completeStatus)" size="small" type="warning" @click.stop="handleChangeRequest(selectedTask)">发起变更</el-button>
        <el-button v-if="checkPermi(['pms:task:delete']) && (isPM || String(selectedTask.mainOwnerId) === currentUserId)" size="small" type="danger" @click.stop="handleDeleteTask(selectedTask)">删除</el-button>
        <el-button size="small" text @click.stop="clearSelection">
          <Icon icon="ep:close" class="mr-4px" />取消选择
        </el-button>
      </div>
    </div>
    <div v-else class="task-toolbar task-toolbar-hint">
      <span class="task-op-hint">
        <Icon icon="ep:info-filled" class="mr-4px" />点击任务行后可在此操作（开始/派发/提交/审核/进度填报/变更 等）
      </span>
    </div>
    </div><!-- /sticky-toolbar-group -->

    <!-- 任务计数 + 仅看我负责的开关 -->
    <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px;">
      <span style="font-size: 13px; color: #86909c;">共 {{ filteredTaskCount }} 个任务</span>
      <el-switch v-model="onlyMyTasks" active-text="仅看我负责的" @change="handleFilterChange" />
    </div>

    <!-- 树形表格（阶段 → 父任务 → 子任务，三级） -->
    <el-table
      :data="filteredTreeData"
      row-key="rowKey"
      :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
      :default-expand-all="expandAll"
      :expand-row-keys="expandedRowKeys"
      border stripe style="width: 100%"
      highlight-current-row
      :current-row-key="selectedRowKey"
      @row-click="handleRowClick"
    >
      <el-table-column label="任务名称" prop="taskName" min-width="250" show-overflow-tooltip>
        <template #default="{ row }">
          <div style="display: flex; align-items: center; gap: 6px">
            <el-icon v-if="row.isStageRow" style="color: #2468F2"><Icon icon="ep:folder" /></el-icon>
            <el-icon v-else-if="row.isMilestone" style="color: #FF7D00"><Icon icon="ep:star-filled" /></el-icon>
            <!-- 子任务层级标识 -->
            <el-tag v-if="!row.isStageRow && (row.level || 1) > 1" type="info" size="small" effect="plain">
              {{ row.parentTaskId ? '子任务' : '' }}
            </el-tag>
            <span :style="{ fontWeight: row.isStageRow ? '600' : 'normal', color: row.isStageRow ? '#1D2129' : '#4E5969' }">
              {{ row.taskName }}
            </span>
            <el-tag v-if="row.isStageRow" type="primary" size="small" effect="plain">排序 {{ row.sortOrder ?? 0 }}</el-tag>
            <el-tag v-if="row.isCriticalPath && !row.isStageRow" type="danger" size="small" effect="plain">关键路径</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="负责人" width="100">
        <template #default="{ row }">
          <span v-if="row.isStageRow">-</span>
          <span v-else>{{ getOwnerName(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <template v-if="!row.isStageRow">
            <el-tag :style="getTaskStatusStyle(row.completeStatus)" size="small" effect="light">
              {{ getTaskStatusLabel(row.completeStatus) }}
            </el-tag>
          </template>
        </template>
      </el-table-column>
      <!-- #3 派发审核：审核状态列 -->
      <el-table-column label="审核" width="100">
        <template #default="{ row }">
          <template v-if="!row.isStageRow">
            <el-tag :style="getReviewStatusStyle(row.reviewStatus)" size="small" effect="light">
              {{ getReviewStatusLabel(row.reviewStatus) }}
            </el-tag>
          </template>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <!-- #1 子任务层级：层级列 -->
      <el-table-column label="层级" width="70">
        <template #default="{ row }">
          <span v-if="!row.isStageRow">{{ row.level || 1 }}级</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="进度" width="120">
        <template #default="{ row }">
          <template v-if="!row.isStageRow">
            <el-input-number v-model="row.progress" :min="0" :max="100" :step="5" size="small" controls-position="right" style="width: 100px" @change="(val) => handleProgressChange(row, val)" />
          </template>
        </template>
      </el-table-column>
      <el-table-column label="计划开始" width="110">
        <template #default="{ row }">
          <span v-if="!row.isStageRow" style="font-size: 13px">{{ formatDate(row.planStartDate) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="到期日" width="120">
        <template #default="{ row }">
          <template v-if="!row.isStageRow">
            <span :style="{ color: isDelayed(row) ? '#F53F3F' : '#4E5969' }">
              {{ formatDate(row.planEndDate) }}
            </span>
            <el-tag v-if="isDelayed(row)" type="danger" size="small" effect="plain" style="margin-left: 4px">
              延期{{ getDelayDays(row) }}天
            </el-tag>
          </template>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <template v-if="!row.isStageRow">
            <!-- 父任务支持同阶段内上下移动（子任务保持层级关系不参与排序） -->
            <template v-if="!row.parentTaskId && (row.level || 1) === 1">
              <el-button link type="default" size="small" :disabled="isFirstInStage(row)" @click.stop="moveTaskInStage(row, -1)">
                <Icon icon="ep:top" />上移
              </el-button>
              <el-button link type="default" size="small" :disabled="isLastInStage(row)" @click.stop="moveTaskInStage(row, 1)">
                <Icon icon="ep:bottom" />下移
              </el-button>
            </template>
            <!-- 方案 A：操作列只保留「详情」，其余操作在工具条按选中行展示 -->
            <el-button link type="primary" size="small" @click.stop="$emit('taskClick', row)">详情</el-button>
          </template>
          <!-- 阶段行操作：编辑/删除（权限可配置） -->
          <template v-if="row.isStageRow">
            <el-button link type="primary" size="small" @click.stop="handleEditStage(row)" v-if="checkPermi(['pms:task:update'])">编辑</el-button>
            <el-button link type="danger" size="small" @click.stop="handleDeleteStage(row)" v-if="checkPermi(['pms:task:delete'])">删除</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <!-- 提交完成确认弹窗（输出物校验） -->
    <el-dialog v-model="submitConfirmVisible" title="提交完成确认" width="480px">
      <div class="submit-confirm-content">
        <el-alert
          v-if="submitTarget?.isOutputRequired && !hasDeliverable"
          title="此任务需要输出物，请先上传"
          type="warning"
          :closable="false"
          show-icon
          description="根据流程要求，提交完成前必须至少关联一个输出物文件。"
          class="mb-16px"
        />
        <el-form label-width="90px">
          <el-form-item label="实际完成日期">
            <el-date-picker v-model="submitForm.actualCompleteDate" type="date" value-format="YYYY-MM-DD" class="w-full" />
          </el-form-item>
          <el-form-item label="完成说明">
            <el-input v-model="submitForm.completionNote" type="textarea" :rows="3" placeholder="请描述完成情况" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="submitConfirmVisible = false">取消</el-button>
        <el-button type="primary" :disabled="submitTarget?.isOutputRequired && !hasDeliverable" @click="confirmSubmit">确认提交</el-button>
      </template>
    </el-dialog>

    <!-- 新建阶段弹窗 -->
    <el-dialog v-model="showStageDialog" :title="editingStageId ? '编辑阶段' : '新建阶段'" width="480px">
      <el-form label-width="80px">
        <el-form-item label="阶段名称" required>
          <el-input v-model="stageForm.stageName" placeholder="如：需求分析、方案设计" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="stageForm.sortOrder" :min="0" :step="1" />
        </el-form-item>
        <el-form-item label="里程碑">
          <el-switch v-model="stageForm.isMilestone" />
        </el-form-item>
        <el-form-item label="计划开始">
          <el-date-picker v-model="stageForm.planStartDate" type="date" value-format="YYYY-MM-DD" class="w-full" :disabled-date="disabledStageStartDate" />
        </el-form-item>
        <el-form-item label="计划结束">
          <el-date-picker v-model="stageForm.planEndDate" type="date" value-format="YYYY-MM-DD" class="w-full" :disabled-date="(date: Date) => disabledStageEndDate(date)" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showStageDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmCreateStage" :loading="stageSaving">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import { TaskVO } from '@/api/pms/task'
import { updateTask, dispatchTask, submitTaskCompletion, deleteTask, updateTaskProgress, exportTask } from '@/api/pms/task'
import download from '@/utils/download'
import { getDocumentList } from '@/api/pms/document'
import { StageVO, createStage, updateStage, deleteStage } from '@/api/pms/stage'
import { taskStatusMap, formatDate, calcDelayDays, getReviewStatusLabel, getReviewStatusStyle } from '../pms-utils'
import { checkPermi } from '@/utils/permission'
import { useUserNames } from '@/hooks/pms/useUserNames'
import { useProjectPerm, PERM } from '@/hooks/pms/useProjectPerm'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { useAppStore } from '@/store/modules/app'

defineOptions({ name: 'TaskListTab' })

const props = defineProps<{
  projectId: string
  project?: any  // 项目整体（含 planStartDate/planEndDate），用于日期范围校验
  tasks: TaskVO[]
  stages: StageVO[]
}>()

// ==================== 项目级权限（降级放行） ====================
// 权限矩阵未初始化（空集合）或尚未加载完成 → 降级放行，避免存量项目误隐藏按钮
const { loadPerm, can, permLoaded, permKeys } = useProjectPerm()
const canProject = (permKey: string): boolean => {
  if (!permLoaded.value) return true
  if (permKeys.value.size === 0) return true
  return can(permKey)
}

// 新建阶段
const showStageDialog = ref(false)
const openStageDialog = () => {
  editingStageId.value = null
  // 默认排序 = 当前项目阶段最大排序 + 1，新建阶段自然追加到末尾且可手动调整
  const maxSort = props.stages.reduce((m, s) => Math.max(m, Number(s.sortOrder) || 0), 0)
  Object.assign(stageForm, { stageName: '', sortOrder: maxSort + 1, isMilestone: false, planStartDate: '', planEndDate: '' })
  showStageDialog.value = true
}
const stageSaving = ref(false)

// 限制阶段开始日期必须在项目周期内
const disabledStageStartDate = (date: Date) => {
  if (!props.project?.planStartDate) return false
  const projStart = new Date(props.project.planStartDate + ' 00:00:00').getTime()
  if (date.getTime() < projStart) return true
  if (props.project?.planEndDate) {
    const projEnd = new Date(props.project.planEndDate + ' 23:59:59').getTime()
    if (date.getTime() > projEnd) return true
  }
  return false
}
// 限制阶段结束日期不能早于开始日期，也不能超出项目周期
const disabledStageEndDate = (date: Date) => {
  if (stageForm.planStartDate && date.getTime() < new Date(stageForm.planStartDate + ' 00:00:00').getTime()) return true
  if (props.project?.planEndDate && date.getTime() > new Date(props.project.planEndDate + ' 23:59:59').getTime()) return true
  return false
}
const stageForm = reactive({
  stageName: '',
  sortOrder: 0,
  isMilestone: false,
  planStartDate: '',
  planEndDate: ''
})

async function confirmCreateStage() {
  if (!stageForm.stageName?.trim()) {
    ElMessage.warning('请输入阶段名称')
    return
  }
  stageSaving.value = true
  try {
    if (editingStageId.value) {
      // 编辑模式
      await updateStage({
        stageId: editingStageId.value,
        projectId: props.projectId,
        stageName: stageForm.stageName,
        sortOrder: stageForm.sortOrder || 0,
        isMilestone: stageForm.isMilestone,
        planStartDate: stageForm.planStartDate || undefined,
        planEndDate: stageForm.planEndDate || undefined
      } as any)
      ElMessage.success('阶段更新成功')
    } else {
      // 创建模式
      await createStage({
      projectId: props.projectId,
      stageName: stageForm.stageName,
      sortOrder: stageForm.sortOrder || 0,
      isMilestone: stageForm.isMilestone,
      planStartDate: stageForm.planStartDate || undefined,
      planEndDate: stageForm.planEndDate || undefined
    } as any)
      ElMessage.success('阶段创建成功')
    }
    showStageDialog.value = false
    editingStageId.value = null
    Object.assign(stageForm, { stageName: '', sortOrder: 0, isMilestone: false, planStartDate: '', planEndDate: '' })
    emit('refresh')
  } catch (e) {
    console.error('创建阶段失败:', e)
    ElMessage.error('创建阶段失败')
  } finally {
    stageSaving.value = false
  }
}

// 编辑阶段
const editingStageId = ref<string | null>(null)
const handleEditStage = (row: TreeRow) => {
  editingStageId.value = String(row.stageId)
  Object.assign(stageForm, {
    stageName: row.stageName || '',
    sortOrder: row.sortOrder || 0,
    isMilestone: row.isMilestone || false,
    planStartDate: row.planStartDate || '',
    planEndDate: row.planEndDate || ''
  })
  showStageDialog.value = true
}

// 删除阶段：有子任务则弹框确认（含级联删除警告），无子任务直接删除
const handleDeleteStage = async (row: TreeRow) => {
  const hasChildren = (props.tasks || []).some(t => String(t.stageId) === String(row.stageId))
  if (hasChildren) {
    try {
      await ElMessageBox.confirm(
        `阶段「${row.stageName}」下存在任务，删除后将一并删除，此操作不可恢复！`,
        '危险操作',
        { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' }
      )
    } catch { return }
  }
  try {
    await deleteStage(String(row.stageId))
    ElMessage.success('阶段已删除')
    emit('refresh')
  } catch (e: any) {
    ElMessage.error(e?.message || '删除阶段失败')
  }
}

// 删除任务（#1：后端已校验有无子任务，有子任务则拒绝）
const handleDeleteTask = async (row: TreeRow) => {
  try {
    await ElMessageBox.confirm(
      `确定删除任务「${row.taskName}」吗？此操作不可恢复！`,
      '危险操作',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch { return }
  try {
    await deleteTask(String(row.taskId))
    ElMessage.success('任务已删除')
    emit('refresh')
  } catch (e: any) {
    ElMessage.error(e?.message || '删除任务失败')
  }
}

const emit = defineEmits<{
  taskClick: [task: TaskVO]
  refresh: []
  'create-task': []
  'add-subtask': [parent: TaskVO]
  'start-change': [task: TaskVO]
}>()

const { getUserName, ensureLoaded: ensureUsersLoaded } = useUserNames()
const searchKeyword = ref('')
const filterStage = ref<string | undefined>()
const filterStatus = ref('')
const filterAssignee = ref('')
const expandAll = ref(true)
const exporting = ref(false)
const onlyMyTasks = ref(false)


const expandedRowKeys = ref<string[]>([])

// 提交完成
const submitConfirmVisible = ref(false)
const submitTarget = ref<any>(null)
const hasDeliverable = ref(false)
const submitForm = reactive({ actualCompleteDate: '', completionNote: '' })

interface TreeRow extends TaskVO {
  rowKey: string
  isStageRow?: boolean
  children?: TreeRow[]
  hasChildren?: boolean
}

// 将任务列表按 parentTaskId 组装成层级树（仅在同一阶段分组内嵌套）
// 先按 sortOrder 升序排序（null/0 视为同一档放末尾），保证根任务顺序与持久化一致
function buildHierarchy(list: TaskVO[]): TreeRow[] {
  const sortedList = [...list].sort((a, b) => {
    const sa = Number(a.sortOrder) || 999999
    const sb = Number(b.sortOrder) || 999999
    if (sa !== sb) return sa - sb
    return String(a.taskId).localeCompare(String(b.taskId))
  })
  const map = new Map<string, TreeRow>()
  sortedList.forEach(t => {
    map.set(String(t.taskId), { ...t, rowKey: `task_${t.taskId}`, children: [], hasChildren: false })
  })
  const roots: TreeRow[] = []
  map.forEach(node => {
    const pid = node.parentTaskId
    if (pid && map.has(String(pid))) {
      const parent = map.get(String(pid))!
      // 子任务也按 sortOrder 排序插入
      let i = 0
      while (i < parent.children!.length) {
        const csa = Number(parent.children![i].sortOrder) || 999999
        const nsa = Number(node.sortOrder) || 999999
        if (csa > nsa || (csa === nsa && String(parent.children![i].taskId).localeCompare(String(node.taskId)) > 0)) break
        i++
      }
      parent.children!.splice(i, 0, node)
      parent.hasChildren = true
    } else {
      roots.push(node)
    }
  })
  return roots
}

const filteredTreeData = computed<TreeRow[]>(() => {
  let tasks = props.tasks

  // 搜索过滤（大小写不敏感 + 自动 trim）
  if (searchKeyword.value && searchKeyword.value.trim()) {
    const keyword = searchKeyword.value.trim().toLowerCase()
    tasks = tasks.filter(t => t.taskName && t.taskName.toLowerCase().includes(keyword))
  }
  if (filterStage.value) {
    tasks = tasks.filter(t => String(t.stageId) === String(filterStage.value))
  }
  if (filterStatus.value) {
    tasks = tasks.filter(t => t.completeStatus === filterStatus.value)
  }
  // 我的任务筛选（仅责任人）
  if (filterAssignee.value === 'mine') {
    const uid = currentUserId.value
    if (uid) {
      tasks = tasks.filter(t => String(t.mainOwnerId) === String(uid))
    }
  }
  if (filterAssignee.value === 'involved') {
    const uid = currentUserId.value
    if (uid) {
      tasks = tasks.filter(t => {
        const isOwner = String(t.mainOwnerId) === String(uid)
        const isHelper = t.helperIds ? t.helperIds.split(',').some((id: string) => String(id.trim()) === String(uid)) : false
        return isOwner || isHelper
      })
    }
  }
  // 仅看我负责的开关过滤（责任人 = 登录人）
  if (onlyMyTasks.value) {
    const uid = currentUserId.value
    if (uid) {
      tasks = tasks.filter(t => String(t.mainOwnerId) === String(uid))
    }
  }

  // 按阶段分组
  const tree: TreeRow[] = []
  const stageMap = new Map<string, TreeRow>()
  const tasksByStage = new Map<string, TaskVO[]>()
  for (const t of tasks) {
    const key = String(t.stageId || '')
    if (!tasksByStage.has(key)) tasksByStage.set(key, [])
    tasksByStage.get(key)!.push(t)
  }

  const sortedStages = [...props.stages].sort((a, b) => (Number(a.sortOrder) || 0) - (Number(b.sortOrder) || 0))
  for (const stage of sortedStages) {
    const stageRow: TreeRow = {
      ...stage,
      taskName: stage.stageName,
      rowKey: `stage_${stage.stageId}`,
      isStageRow: true,
      children: [],
      hasChildren: true
    }
    // 阶段内任务按 parentTaskId 组装成层级树
    stageRow.children = buildHierarchy(tasksByStage.get(String(stage.stageId)) || [])
    stageMap.set(String(stage.stageId), stageRow)
    tree.push(stageRow)
  }

  // 无阶段归属的任务，统一挂在表格末尾（同样支持父子嵌套）
  const noStageRoots = buildHierarchy(tasksByStage.get('') || [])
  noStageRoots.forEach(r => tree.push(r))

  return tree  // 显示所有阶段（含空阶段），解决新建阶段后不显示的问题
})

// 当前登录用户ID
const currentUserId = computed(() => {
  const userStore = useUserStore()
  return String(userStore.getUser?.id || '')
})

// 底部操作条联动侧边栏折叠：展开 200px / 折叠 64px，避免 fixed 条遮挡左侧菜单
const appStore = useAppStore()
const taskActionBarLeft = computed(() => {
  return appStore.getCollapse
    ? 'calc(64px + 20px)'
    : 'calc(200px + 20px)'
})

// 当前用户是否是项目经理（或超管）
const isPM = computed(() => {
  if (!props.project?.projectManagerId) return false
  if (String(props.project.projectManagerId) === currentUserId.value) return true
  const userStore = useUserStore()
  const roles = userStore.getUser?.roles || []
  return Array.isArray(roles) && roles.includes('super_admin')
})

// #1 是否可添加子任务：层级未满 3 级
const canAddSubtask = (row: TreeRow): boolean => {
  if (!checkPermi(['pms:task:create'])) return false
  if (!canProject(PERM.TASK_CREATE)) return false
  const level = row.level || 1
  return level < 3
}

// ==================== 任务上下移动（同阶段内父任务排序） ====================
// 同阶段父任务列表（按 sortOrder 排序）；子任务不参与跨任务排序
const getStageParentTasks = (row: TreeRow): TaskVO[] => {
  return props.tasks
    .filter(t => String(t.stageId) === String(row.stageId) && !t.parentTaskId)
    .sort((a, b) => (Number(a.sortOrder) || 0) - (Number(b.sortOrder) || 0))
}

const isFirstInStage = (row: TreeRow): boolean => {
  if (row.isStageRow || row.parentTaskId) return true
  const list = getStageParentTasks(row)
  return list[0]?.taskId === row.taskId
}

const isLastInStage = (row: TreeRow): boolean => {
  if (row.isStageRow || row.parentTaskId) return true
  const list = getStageParentTasks(row)
  return list[list.length - 1]?.taskId === row.taskId
}

const moveTaskInStage = async (row: TreeRow, dir: -1 | 1) => {
  if (row.isStageRow || row.parentTaskId) return
  const list = getStageParentTasks(row)  // 已按 sortOrder 升序
  const idx = list.findIndex(t => t.taskId === row.taskId)
  const targetIdx = idx + dir
  if (targetIdx < 0 || targetIdx >= list.length) return
  // 拍快照用于失败回滚
  const snapshot = list.map(t => ({ taskId: t.taskId, sortOrder: t.sortOrder }))
  // 在副本里重排并归一化 1..n
  const reordered = [...list]
  const [moved] = reordered.splice(idx, 1)
  reordered.splice(targetIdx, 0, moved)
  reordered.forEach((t, i) => { t.sortOrder = i + 1 })
  // 计算真正变化的行（避免无变更的 updateTask 噪声）
  const changed = reordered.filter(t => {
    const orig = snapshot.find(o => o.taskId === t.taskId)
    return !orig || Number(orig.sortOrder) !== Number(t.sortOrder) || orig.sortOrder == null
  })
  if (!changed.length) {
    ElMessage.warning('无需移动')
    return
  }
  try {
    // 并行更新；sortOrder 用 Number 包一层，防 Number 与 String 类型不通
    await Promise.all(
      changed.map(t => updateTask({ taskId: t.taskId, sortOrder: Number(t.sortOrder) } as any))
    )
    ElMessage.success('任务顺序已更新')
    // 通知父组件重新拉数据 → projectTasks.value 重置 → props 整体刷新
    emit('refresh')
  } catch (e: any) {
    console.error('移动任务失败', e)
    ElMessage.error(e?.message || '移动任务失败')
    // 失败回滚到原值（只回滚变化过的行）
    snapshot.forEach(o => {
      const t = list.find(x => x.taskId === o.taskId)
      if (t) t.sortOrder = o.sortOrder
    })
  }
}

// 过滤后的任务总数（不含阶段行）
const filteredTaskCount = computed(() => {
  let count = 0
  for (const node of filteredTreeData.value) {
    if (node.isStageRow) {
      count += node.children?.length || 0
    } else {
      count += 1
    }
  }
  return count
})

// 仅看我负责的开关变化处理（仅用于触发重新计算，过滤逻辑在 filteredTreeData 内）
const handleFilterChange = () => {
  // 切换 onlyMyTasks 时，自动清空 filterAssignee 避免冲突
  if (onlyMyTasks.value) {
    filterAssignee.value = ''
  }
}

// ==================== 状态流转逻辑 ====================
const transitionRules: Record<string, { from: string[]; to: string; label: string; roles: string[] }> = {
  start: { from: ['not_started'], to: 'in_progress', label: '开始任务', roles: ['main_owner', 'pm'] },
  dispatch: { from: ['not_started'], to: 'pending_accept', label: '派发任务', roles: ['pm'] },
  accept: { from: ['pending_accept'], to: 'in_progress', label: '接收任务', roles: ['assignee'] },
  reject: { from: ['pending_accept'], to: 'rejected', label: '拒绝任务', roles: ['assignee'] },
  redispatch: { from: ['rejected'], to: 'pending_accept', label: '重新派发', roles: ['pm'] },
  submit: { from: ['in_progress', 'delayed'], to: 'completion_pending_review', label: '提交完成', roles: ['assignee'] },
  approve: { from: ['completion_pending_review'], to: 'completed', label: '审核通过', roles: ['pm', 'reviewer'] },
  reject_review: { from: ['completion_pending_review'], to: 'in_progress', label: '驳回', roles: ['pm', 'reviewer'] },
  pause: { from: ['in_progress'], to: 'paused', label: '暂停', roles: ['assignee', 'pm'] },
  resume: { from: ['delayed', 'paused'], to: 'in_progress', label: '恢复', roles: ['assignee', 'pm'] }
}

// 允许发起变更的任务状态：任务创建后即可发起变更（走审核流程），不限于已完成
const ALLOW_CHANGE_STATUSES = ['not_started', 'pending_accept', 'in_progress', 'delayed', 'paused', 'rejected', 'completion_pending_review', 'completed']

function canTransition(row: TreeRow, action: string): boolean {
  if (row.isStageRow) return false
  const rule = transitionRules[action]
  if (!rule) return false
  // 仅检查状态流转条件（按钮权限由 v-if checkPermi 控制，角色校验由后端保障）
  return rule.from.includes(row.completeStatus || '')
}

async function handleTransition(row: TreeRow, action: string) {
  const rule = transitionRules[action]
  if (!rule) return

  if (action === 'submit') {
    // 输出物校验
    submitTarget.value = row
    try {
      const docs = await getDocumentList()
      const taskDocs = ((docs as any[]) || []).filter(d => String(d.taskId) === String(row.taskId))
      hasDeliverable.value = taskDocs.length > 0
    } catch { hasDeliverable.value = false }
    submitForm.actualCompleteDate = new Date().toISOString().split('T')[0]
    submitForm.completionNote = ''
    submitConfirmVisible.value = true
    return
  }


  try {
    if (action === 'dispatch' || action === 'redispatch') {
      await dispatchTask(row.taskId)
      ElMessage.success('任务已派发，钉钉通知发送成功')
      emit('refresh')
      return
    }
    await updateTask({
      taskId: row.taskId,
      completeStatus: rule.to
    } as TaskVO)
    ElMessage.success(`任务已${rule.label}`)
    emit('refresh')
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}


async function confirmSubmit() {
  if (!submitTarget.value) return
  try {
    await submitTaskCompletion(submitTarget.value.taskId, submitForm.actualCompleteDate, submitForm.completionNote)
    ElMessage.success('任务已提交审核')
    submitConfirmVisible.value = false
    emit('refresh')
  } catch (e: any) {
    ElMessage.error(e?.message || '提交失败')
  }
}

// #1 进度填报（走聚合接口，父任务进度自动汇总）
async function handleProgressChange(row: TreeRow, val: number) {
  const value = Number(val) || 0
  try {
    await updateTaskProgress(row.taskId, value)
    ElMessage.success('进度已更新，父任务进度已自动汇总')
    emit('refresh')
  } catch (e: any) {
    ElMessage.error(e?.message || '进度更新失败')
    emit('refresh') // 失败则重新拉取，还原显示
  }
}

function handleChangeRequest(row: TreeRow) {
  emit('start-change', row)
}

// ==================== 辅助函数 ====================
const getOwnerName = (task: TaskVO) => {
  return getUserName(task.mainOwnerId)
}
const getTaskStatusStyle = (status?: string) => {
  const s = taskStatusMap[status || '']
  return s ? `color: ${s.textColor}; background: ${s.bgColor}; border-color: ${s.borderColor};` : ''
}
const getTaskStatusLabel = (status?: string) => taskStatusMap[status || '']?.label || status || '-'
const getProgressColor = (task: TaskVO) => {
  if (task.completeStatus === 'completed') return '#00B42A'
  if (isDelayed(task)) return '#F53F3F'
  return '#2468F2'
}
const isDelayed = (task: TaskVO) => calcDelayDays(task.planEndDate, task.completeStatus) > 0
const getDelayDays = (task: TaskVO) => calcDelayDays(task.planEndDate, task.completeStatus)

// ==================== 导出任务（新增） ====================
async function handleExport() {
  if (!props.projectId) return
  exporting.value = true
  try {
    const res = await exportTask(props.projectId)
    // request.download 返回 axios Response，文件本体在 res.data(Blob)
    const blob = res && (res as any).data ? (res as any).data : res
    download.excel(blob as Blob, '项目任务.xlsx')
    ElMessage.success('导出成功，文件已开始下载')
  } catch (e: any) {
    ElMessage.error(e?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

// ==================== 方案 A：选中行 + 顶部任务操作区 ====================
const selectedTask = ref<any>(null)
const selectedRowKey = ref<string | null>(null)
const handleRowClick = (row: any) => {
  if (row.isStageRow) {
    // 阶段行不参与任务操作；点阶段行会清除之前的选中
    if (selectedRowKey.value) {
      selectedTask.value = null
      selectedRowKey.value = null
    }
    return
  }
  // 再次点击同一行 → 取消选中
  if (selectedRowKey.value === row.rowKey) {
    clearSelection()
    return
  }
  selectedTask.value = row
  selectedRowKey.value = row.rowKey
}
const clearSelection = () => {
  selectedTask.value = null
  selectedRowKey.value = null
}
// 进度填报可见性（与 TaskDetailDrawer 中的 canReportProgress 语义保持一致）
const canReportProgress = (row: any): boolean => {
  if (!row) return false
  if (!checkPermi(['pms:task:update'])) return false
  const s = row.completeStatus
  if (s === 'completed' || s === 'paused') return false
  return true
}

onMounted(async () => {
  ensureUsersLoaded()
  // 加载项目级权限矩阵（未初始化时 useProjectPerm 内部按无权限处理，本组件 canProject 会降级放行）
  if (props.projectId) {
    try {
      await loadPerm(props.projectId)
    } catch { /* 权限模块未部署则忽略，按钮按降级策略显示 */ }
  }
})
</script>

<style scoped>
/* 工具栏拆双层：上层（筛选+导出）普通布局；操作行（选中后）独立 sticky */
.task-toolbar { margin-bottom: 8px; }

/* 方案A 二次加固：与 el-tabs header 同方案（fixed + var），
   top = navbar(50) + tags view(35) + tabs header(40) = 125px */
/* 终极方案：sticky 与 .sticky-tabs-area 同滚动上下文。
   top: 40px = el-tabs header 高度，sticky 后正好在 tabs 下方连续两段冻结 */
.sticky-toolbar-group {
  position: sticky;
  top: 40px;
  z-index: 997;
  background: var(--el-bg-color, #fff);
  padding: 4px 16px 0;
}

/* 上层：搜索/筛选/新建/导出/折叠。允许多行换行 */
.task-toolbar-top {
  display: flex; justify-content: space-between; align-items: center;
  flex-wrap: wrap; gap: 8px;
  padding: 6px 12px; background: #fff;
  border-bottom: 1px solid #ebeef5;
}

/* 操作行（选中任务后）：fixed 钉视口底部 + 阴影悬浮
   一次性修复：sticky 链路在 el-tabs/el-tab-pane 内不可靠，
   改用 fixed 永远可见。bottom 避开 el-backtop（右下角 50px 处的回顶按钮）。
   不与顶部 sticky tabs/toolbar 冲突（顶部 sticky，底部 fixed，分两个区）。 */
.task-toolbar-action {
  position: fixed;
  bottom: 16px;
  /* +20px 对齐项目详情页 .p-20px 内边距，让 action bar 左右与表格内容完全对齐 */
  left: calc(var(--left-menu-min-width, 64px) + 20px);
  right: 20px;
  z-index: 999;
  background: linear-gradient(135deg, #ecf5ff 0%, #f0f9ff 100%);
  border: 1px solid #b3d8ff;
  border-radius: 8px;
  padding: 10px 16px;
  box-shadow: 0 8px 24px rgba(36, 104, 242, 0.25);
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
}
.task-toolbar-action .task-op-actions {
  display: flex; flex-wrap: wrap; gap: 6px; align-items: center;
}
.task-toolbar-action .task-op-label {
  font-weight: 600; color: #2468F2;
  margin-right: 4px;
  padding: 0 8px;
  border-right: 1px solid #b3d8ff;
  max-width: 260px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

/* 提示行（未选中）：轻量提示 */
.task-toolbar-hint {
  padding: 6px 12px; background: #fafafa;
  border: 1px dashed #e4e7ed;
  border-radius: 4px;
  margin-bottom: 12px;
}
.task-toolbar-hint .task-op-hint {
  color: #909399; font-size: 12px;
}

/* 兼容旧类名（导出按钮/折叠按钮容器内还在用） */
.task-op-divider {
  display: inline-block; width: 1px; height: 20px; background: #ebeef5;
  margin: 0 6px; vertical-align: middle;
}
.mb-16px { margin-bottom: 16px; }
.submit-confirm-content { }
</style>

