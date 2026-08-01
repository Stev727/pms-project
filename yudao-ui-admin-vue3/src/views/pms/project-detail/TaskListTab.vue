<template>
  <div>
    <!-- ���具栏 -->
    <div class="task-toolbar">
      <div style="display: flex; gap: 8px; align-items: center">
        <el-button type="primary" size="small" @click="$emit('create-task')" v-if="checkPermi(['pms:task:create'])">
          <Icon icon="ep:plus" class="mr-4px" />新建任务
        </el-button>
        <el-button size="small" @click="showStageDialog = true" v-if="checkPermi(['pms:task:create'])">
          <Icon icon="ep:folder-add" class="mr-4px" />新建阶段
        </el-button>
        <el-input v-model="searchKeyword" placeholder="搜索任务名称" clearable size="small" style="width: 200px">
          <template #prefix><Icon icon="ep:search" /></template>
        </el-input>
        <el-select v-model="filterStage" placeholder="全部阶段" clearable size="small" style="width: 120px">
          <el-option v-for="s in stages" :key="s.stageId" :label="s.stageName" :value="s.stageId" />
        </el-select>
        <el-select v-model="filterStatus" placeholder="全部状态" clearable size="small" style="width: 120px">
          <el-option v-for="(v, k) in taskStatusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-select v-model="filterAssignee" placeholder="我的任务" clearable size="small" style="width: 120px">
          <el-option label="我负责的" value="mine" />
          <el-option label="我参与的" value="involved" />
        </el-select>
      </div>
      <div>
        <el-button size="small" @click="expandAll = !expandAll">
          <Icon :icon="expandAll ? 'ep:folder-opened' : 'ep:folder'" class="mr-4px" />
          {{ expandAll ? '折叠全部' : '展开全部' }}
        </el-button>
      </div>
    </div>

    <!-- 任务计数 + 仅看我负责的开关 -->
    <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px;">
      <span style="font-size: 13px; color: #86909c;">共 {{ filteredTaskCount }} 个任务</span>
      <el-switch v-model="onlyMyTasks" active-text="仅看我负责的" @change="handleFilterChange" />
    </div>

    <!-- 树形表格 -->
    <el-table
      :data="filteredTreeData"
      row-key="rowKey"
      :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
      :default-expand-all="expandAll"
      :expand-row-keys="expandedRowKeys"
      border stripe style="width: 100%"
    >
      <el-table-column label="任务名称" prop="taskName" min-width="250" show-overflow-tooltip>
        <template #default="{ row }">
          <div style="display: flex; align-items: center; gap: 6px">
            <el-icon v-if="row.isStageRow" style="color: #2468F2"><Icon icon="ep:folder" /></el-icon>
            <el-icon v-else-if="row.isMilestone" style="color: #FF7D00"><Icon icon="ep:star-filled" /></el-icon>
            <span :style="{ fontWeight: row.isStageRow ? '600' : 'normal', color: row.isStageRow ? '#1D2129' : '#4E5969' }">
              {{ row.taskName }}
            </span>
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
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <template v-if="!row.isStageRow">
            <!-- 状态流转按钮 -->
            <el-button
              v-if="canTransition(row, 'start')"
              link type="primary" size="small" @click.stop="handleTransition(row, 'start')"
            >开始任务</el-button>
            <el-button
              v-if="canTransition(row, 'dispatch')"
              link type="primary" size="small" @click.stop="handleTransition(row, 'dispatch')"
            >派发</el-button>
            <el-button
              v-if="canTransition(row, 'accept')"
              link type="primary" size="small" @click.stop="handleTransition(row, 'accept')"
            >接收</el-button>
            <el-button
              v-if="canTransition(row, 'reject')"
              link type="danger" size="small" @click.stop="handleTransition(row, 'reject')"
            >拒绝</el-button>
            <el-button
              v-if="canTransition(row, 'redispatch')"
              link type="primary" size="small" @click.stop="handleTransition(row, 'redispatch')"
            >重新派发</el-button>
            <el-button
              v-if="canTransition(row, 'submit')"
              link type="success" size="small" @click.stop="handleTransition(row, 'submit')"
            >提交完成</el-button>
            <el-button
              v-if="canTransition(row, 'approve')"
              link type="success" size="small" @click.stop="handleTransition(row, 'approve')"
            >审核通过</el-button>
            <el-button
              v-if="canTransition(row, 'reject_review')"
              link type="danger" size="small" @click.stop="handleTransition(row, 'reject_review')"
            >驳回</el-button>

            <el-button
              v-if="canTransition(row, 'pause')"
              link type="warning" size="small" @click.stop="handleTransition(row, 'pause')"
            >暂停</el-button>
            <el-button
              v-if="canTransition(row, 'resume')"
              link type="primary" size="small" @click.stop="handleTransition(row, 'resume')"
            >恢复</el-button>
            <!-- 已完成任务：发起变更入口 (SEVERE-8 修复) -->
            <el-button
              v-if="row.completeStatus === 'completed'"
              link type="warning" size="small" @click.stop="handleChangeRequest(row)"
            >发起变更</el-button>
            <!-- 详情 -->
            <el-button link type="primary" size="small" @click.stop="$emit('taskClick', row)">详情</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <!-- 提交完成确认弹窗（输出物校验 - SEVERE-7 修复） -->
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
    <el-dialog v-model="showStageDialog" title="新建阶段" width="480px">
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
import { ref, computed, reactive } from 'vue'
import { TaskVO } from '@/api/pms/task'
import { updateTask, dispatchTask, submitTaskCompletion } from '@/api/pms/task'
import { getDocumentList } from '@/api/pms/document'
import { StageVO, createStage } from '@/api/pms/stage'
import { taskStatusMap, formatDate, calcDelayDays } from '../pms-utils'
import { checkPermi } from '@/utils/permission'
import { useUserNames } from '@/hooks/pms/useUserNames'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useCache } from '@/hooks/web/useCache'
import { useUserStore } from '@/store/modules/user'

defineOptions({ name: 'TaskListTab' })

const props = defineProps<{
  projectId: string
  project?: any  // 项目整体（含 planStartDate/planEndDate），用于日期范围校验
  tasks: TaskVO[]
  stages: StageVO[]
}>()

// 新建阶段
const showStageDialog = ref(false)
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
    await createStage({
      projectId: props.projectId,
      stageName: stageForm.stageName,
      sortOrder: stageForm.sortOrder || 0,
      isMilestone: stageForm.isMilestone,
      planStartDate: stageForm.planStartDate || undefined,
      planEndDate: stageForm.planEndDate || undefined
    } as any)
    ElMessage.success('阶段创建成功')
    showStageDialog.value = false
    Object.assign(stageForm, { stageName: '', sortOrder: 0, isMilestone: false, planStartDate: '', planEndDate: '' })
    emit('refresh')
  } catch (e) {
    console.error('创建阶段失败:', e)
    ElMessage.error('创建阶段失败')
  } finally {
    stageSaving.value = false
  }
}

const emit = defineEmits<{
  taskClick: [task: TaskVO]
  refresh: []
  'create-task': []
  'start-change': [task: TaskVO]
}>()

const { getUserName, ensureLoaded: ensureUsersLoaded } = useUserNames()
const searchKeyword = ref('')
const filterStage = ref<string | undefined>()
const filterStatus = ref('')
const filterAssignee = ref('')
const expandAll = ref(true)
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
  // 我的任务筛选 (MINOR-5 修复)
  if (filterAssignee.value === 'mine') {
    const userInfo = useCache().wsCache.get('userInfo')
    const currentUserId = userInfo?.id
    if (currentUserId) {
      tasks = tasks.filter(t => String(t.mainOwnerId) === String(currentUserId))
    }
  }
  if (filterAssignee.value === 'involved') {
    const userInfo = useCache().wsCache.get('userInfo')
    const currentUserId = userInfo?.id
    if (currentUserId) {
      tasks = tasks.filter(t => {
        const isOwner = String(t.mainOwnerId) === String(currentUserId)
        const isHelper = t.helperIds ? t.helperIds.split(',').some((id: string) => String(id.trim()) === String(currentUserId)) : false
        return isOwner || isHelper
      })
    }
  }
  // 仅看我负责的开关过滤
  if (onlyMyTasks.value) {
    const userInfo = useCache().wsCache.get('userInfo')
    const uid = userInfo?.id
    if (uid) {
      tasks = tasks.filter(t => String(t.mainOwnerId) === String(uid))
    }
  }

  // 按阶段分组
  const tree: TreeRow[] = []
  const stageMap = new Map<string, TreeRow>()

  for (const stage of props.stages) {
    const stageRow: TreeRow = {
      ...stage,
      taskName: stage.stageName,
      rowKey: `stage_${stage.stageId}`,
      isStageRow: true,
      children: [],
      hasChildren: true
    }
    stageMap.set(String(stage.stageId), stageRow)
    tree.push(stageRow)
  }

  for (const task of tasks) {
    const stageKey = String(task.stageId || '')
    const stageNode = stageMap.get(stageKey)
    const taskRow: TreeRow = { ...task, rowKey: `task_${task.taskId}` }
    if (stageNode) {
      stageNode.children!.push(taskRow)
    } else {
      tree.push(taskRow)
    }
  }

  return tree  // 显示所有阶段（含空阶段），解决新建阶段后不显示的问题
})

// 当前登录用户ID
const currentUserId = computed(() => {
  const userStore = useUserStore()
  return String(userStore.getUser?.id || '')
})

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

function canTransition(row: TreeRow, action: string): boolean {
  if (row.isStageRow) return false
  const rule = transitionRules[action]
  if (!rule) return false
  // 仅检查状态流转条件（按钮权限由 v-if checkPermi 控制，角色校验由后端保障）
  // P1-01 修复：移除 roles 检查，避免 currentUserRole 为空导致按钮不显示
  return rule.from.includes(row.completeStatus || '')
}

async function handleTransition(row: TreeRow, action: string) {
  const rule = transitionRules[action]
  if (!rule) return

  if (action === 'submit') {
    // 输出物校验 (SEVERE-7 修复)
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
</script>

<style scoped>
.task-toolbar {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; flex-wrap: wrap; gap: 8px;
}
.mb-16px { margin-bottom: 16px; }
.submit-confirm-content { }
</style>
