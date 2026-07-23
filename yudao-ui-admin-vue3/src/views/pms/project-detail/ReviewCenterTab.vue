<template>
  <div class="review-center">
    <div class="flex items-center justify-between mb-16px">
      <h3>待审核任务</h3>
      <div class="flex items-center gap-8px">
        <el-tag type="warning">待审核 {{ pendingTasks.length }}</el-tag>
        <el-button type="success" :disabled="!selectedIds.length" @click="handleBatchApprove">批量通过</el-button>
        <el-button type="danger" :disabled="!selectedIds.length" @click="handleBatchReject">批量驳回</el-button>
      </div>
    </div>

    <el-table :data="pendingTasks" @selection-change="handleSelection" v-loading="loading" empty-text="暂无待审核任务">
      <el-table-column type="selection" width="55" />
      <el-table-column prop="taskCode" label="编号" width="120" />
      <el-table-column prop="taskName" label="任务名称" min-width="200" show-overflow-tooltip />
      <el-table-column prop="stageName" label="阶段" width="100" />
      <el-table-column prop="mainOwnerName" label="责任人" width="100" />
      <el-table-column label="计划工期" width="200">
        <template #default="{ row }">
          {{ row.planStartDate }} ~ {{ row.planEndDate }}
        </template>
      </el-table-column>
      <el-table-column prop="progress" label="进度" width="80">
        <template #default="{ row }">
          <el-progress :percentage="row.progress" :stroke-width="8" />
        </template>
      </el-table-column>
      <el-table-column prop="outputRequirement" label="输出物要求" min-width="150" show-overflow-tooltip />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openReviewDialog(row, 'approve')">通过</el-button>
          <el-button link type="danger" @click="openReviewDialog(row, 'reject')">驳回</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 审核对话框 -->
    <el-dialog v-model="reviewDialogVisible" :title="reviewDialogTitle" width="500px">
      <el-form :model="reviewForm" label-width="80px">
        <el-form-item label="任务">
          <span>{{ currentTask?.taskName }} ({{ currentTask?.taskCode }})</span>
        </el-form-item>
        <el-form-item label="审核结果">
          <el-tag :type="reviewForm.result === 'approve' ? 'success' : 'danger'">
            {{ reviewForm.result === 'approve' ? '通过' : '驳回' }}
          </el-tag>
        </el-form-item>
        <el-form-item label="审核意见" required>
          <el-input v-model="reviewForm.opinion" type="textarea" :rows="4"
            :placeholder="reviewForm.result === 'reject' ? '请说明驳回原因（必填）' : '请输入审核意见（可选）'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReview">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { updateTask, getTaskList, TaskVO } from '@/api/pms/task'
import { getStageList, StageVO } from '@/api/pms/stage'
import { useUserNames } from '@/hooks/pms/useUserNames'

defineOptions({ name: 'ReviewCenterTab' })

const props = defineProps<{ projectId: string }>()
const emit = defineEmits<{ (e: 'reviewed'): void }>()

const { getUserName } = useUserNames()

const loading = ref(false)
const submitting = ref(false)
const allTasks = ref<TaskVO[]>([])
const allStages = ref<StageVO[]>([])
const selectedIds = ref<string[]>([])
const reviewDialogVisible = ref(false)
const currentTask = ref<TaskVO | null>(null)
const reviewForm = reactive({ result: 'approve' as 'approve' | 'reject', opinion: '' })

const pendingTasks = computed(() => {
  const stageMap = new Map(allStages.value.map(s => [String(s.stageId), s.stageName]))
  return allTasks.value
    .filter(t => String(t.projectId) === String(props.projectId) && t.completeStatus === 'pending_review')
    .map(t => ({
      ...t,
      mainOwnerName: t.mainOwnerId ? getUserName(t.mainOwnerId) : '-',
      stageName: (t.stageId && stageMap.get(String(t.stageId))) || t.stageId || '-'
    }))
})

const reviewDialogTitle = computed(() => {
  return reviewForm.result === 'approve' ? '审核通过' : '审核驳回'
})

const loadTasks = async () => {
  loading.value = true
  try {
    const [tasks, stages] = await Promise.all([getTaskList(), getStageList()])
    allTasks.value = (tasks as TaskVO[]) || []
    allStages.value = (stages as StageVO[]) || []
  } finally {
    loading.value = false
  }
}

const handleSelection = (selection: TaskVO[]) => {
  selectedIds.value = selection.map(t => String(t.taskId))
}

const openReviewDialog = (task: TaskVO, result: 'approve' | 'reject') => {
  currentTask.value = task
  reviewForm.result = result
  reviewForm.opinion = ''
  reviewDialogVisible.value = true
}

const submitReview = async () => {
  if (!currentTask.value) return
  if (reviewForm.result === 'reject' && !reviewForm.opinion.trim()) {
    ElMessage.warning('驳回必须填写意见')
    return
  }
  if (reviewForm.opinion.length > 500) {
    ElMessage.warning('审核意见不能超过 500 字')
    return
  }
  submitting.value = true
  try {
    const newStatus = reviewForm.result === 'approve' ? 'completed' : 'in_progress'
    await updateTask({
      ...currentTask.value,
      completeStatus: newStatus,
      reviewOpinion: reviewForm.opinion,
      actualCompleteDate: reviewForm.result === 'approve' ? new Date().toISOString() : undefined
    } as TaskVO)
    ElMessage.success(reviewForm.result === 'approve' ? '已通过审核' : '已驳回')
    reviewDialogVisible.value = false
    await loadTasks()
    emit('reviewed')
  } catch (e) {
    ElMessage.error('审核失败')
  } finally {
    submitting.value = false
  }
}

const handleBatchApprove = async () => {
  if (!selectedIds.value.length) return
  try {
    await ElMessageBox.confirm(`确认批量通过 ${selectedIds.value.length} 个任务？`, '批量审核', { type: 'warning' })
    let success = 0
    for (const id of selectedIds.value) {
      const task = allTasks.value.find(t => String(t.taskId) === id)
      if (task) {
        await updateTask({ ...task, completeStatus: 'completed', reviewOpinion: '批量通过', actualCompleteDate: new Date().toISOString() } as TaskVO)
        success++
      }
    }
    ElMessage.success(`已批量通过 ${success} 个任务`)
    selectedIds.value = []
    await loadTasks()
    emit('reviewed')
  } catch {}
}

const handleBatchReject = async () => {
  if (!selectedIds.value.length) return
  try {
    const { value: opinion } = await ElMessageBox.prompt('请输入批量驳回意见', '批量驳回', {
      inputType: 'textarea',
      inputValidator: (v) => (v && v.trim() ? true : '驳回意见必填')
    })
    let success = 0
    for (const id of selectedIds.value) {
      const task = allTasks.value.find(t => String(t.taskId) === id)
      if (task) {
        await updateTask({ ...task, completeStatus: 'in_progress', reviewOpinion: opinion } as TaskVO)
        success++
      }
    }
    ElMessage.success(`已批量驳回 ${success} 个任务`)
    selectedIds.value = []
    await loadTasks()
    emit('reviewed')
  } catch {}
}

watch(() => props.projectId, () => loadTasks(), { immediate: true })
</script>

<style scoped>
.review-center h3 {
  font-size: 16px;
  font-weight: 600;
  color: #1D2129;
  margin: 0;
}
.flex {
  display: flex;
}
.items-center {
  align-items: center;
}
.justify-between {
  justify-content: space-between;
}
.mb-16px {
  margin-bottom: 16px;
}
.gap-8px {
  gap: 8px;
}
</style>
