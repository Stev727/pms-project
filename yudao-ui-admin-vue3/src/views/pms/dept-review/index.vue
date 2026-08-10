<template>
  <div class="p-20px">
    <ContentWrap title="部门审核中心">
      <template #header>
        <div class="flex justify-between items-center w-full">
          <span class="text-14px text-gray-500">
            待您（责任人的直属领导）审核的日常任务，审核通过才算完成
          </span>
          <el-button @click="loadList" :loading="loading">
            <Icon icon="ep:refresh" class="mr-5px" />刷新
          </el-button>
        </div>
      </template>

      <el-table :data="reviewList" v-loading="loading" stripe border style="width: 100%">
        <el-table-column label="任务名称" prop="taskName" min-width="200" show-overflow-tooltip />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">{{ dailyTaskTypeName(row.taskType) }}</template>
        </el-table-column>
        <el-table-column label="责任人" width="110">
          <template #default="{ row }">{{ getUserName(row.mainOwnerId) }}</template>
        </el-table-column>
        <el-table-column label="计划周期" width="200">
          <template #default="{ row }">{{ formatDate(row.planStartDate) }} ~ {{ formatDate(row.planEndDate) }}</template>
        </el-table-column>
        <el-table-column label="优先级" width="90">
          <template #default="{ row }">
            <el-tag :color="priorityMap[row.priority || 'normal']?.color" effect="plain" size="small">
              {{ priorityMap[row.priority || 'normal']?.label || '普通' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交说明" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.completionNote || row.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button link type="success" size="small" @click="handleApprove(row)">通过</el-button>
            <el-button link type="danger" size="small" @click="handleReject(row)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="reviewList.length === 0 && !loading" description="暂无待审日常任务" />
    </ContentWrap>

    <!-- 驳回原因弹窗 -->
    <el-dialog v-model="rejectVisible" title="驳回任务" width="420px">
      <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请填写驳回原因（必填）" />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="acting" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <TaskDetailDrawer ref="taskDrawerRef" @refresh="loadList" />
  </div>
</template>

<script setup lang="ts">
import { getDeptReviewList, approveReview, rejectReview, TaskVO } from '@/api/pms/task'
import TaskDetailDrawer from '../project-detail/TaskDetailDrawer.vue'
import { dailyTaskTypeOptions, priorityMap, formatDate } from '../pms-utils'
import { useUserNames } from '@/hooks/pms/useUserNames'
import { ElMessage, ElMessageBox } from 'element-plus'

defineOptions({ name: 'PmsDeptReview' })

const message = useMessage()
const { userList, getUserName, ensureLoaded: ensureUsersLoaded } = useUserNames()

const loading = ref(false)
const reviewList = ref<TaskVO[]>([])
const taskDrawerRef = ref()

const dailyTaskTypeName = (type?: string) => {
  const opt = dailyTaskTypeOptions.find(o => o.value === type)
  return opt?.label || '其他'
}

const openDetail = (task: TaskVO) => taskDrawerRef.value?.open(task)

const loadList = async () => {
  loading.value = true
  try {
    await ensureUsersLoaded()
    reviewList.value = await getDeptReviewList() || []
  } catch (e) {
    console.error('加载部门审核列表失败', e)
  } finally {
    loading.value = false
  }
}

const handleApprove = async (task: TaskVO) => {
  try {
    await ElMessageBox.confirm(`确认通过任务「${task.taskName}」？`, '审核通过', {
      confirmButtonText: '通过', cancelButtonText: '取消', type: 'success'
    })
    await approveReview(String(task.taskId), '部门审核通过')
    ElMessage.success('已审核通过')
    await loadList()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const rejectVisible = ref(false)
const rejectReason = ref('')
const actingTask = ref<TaskVO | null>(null)
const acting = ref(false)

const handleReject = (task: TaskVO) => {
  actingTask.value = task
  rejectReason.value = ''
  rejectVisible.value = true
}

const confirmReject = async () => {
  if (!rejectReason.value.trim()) {
    message.warning('请填写驳回原因')
    return
  }
  if (!actingTask.value) return
  acting.value = true
  try {
    await rejectReview(String(actingTask.value.taskId), rejectReason.value.trim())
    ElMessage.success('已驳回')
    rejectVisible.value = false
    await loadList()
  } catch (e: any) {
    console.error(e)
    message.error(e?.message || '驳回失败')
  } finally {
    acting.value = false
  }
}

onMounted(loadList)
</script>

<style scoped>
.flex { display: flex; }
.justify-between { justify-content: space-between; }
.items-center { align-items: center; }
.w-full { width: 100%; }
</style>
