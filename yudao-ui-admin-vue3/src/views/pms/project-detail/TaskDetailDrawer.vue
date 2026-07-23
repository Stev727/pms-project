<template>
  <el-drawer v-model="drawerVisible" :size="720" :with-header="false" :close-on-click-modal="false">
    <div class="task-drawer">
      <!-- 顶部标题栏 -->
      <div class="drawer-header">
        <div class="header-content">
          <div class="header-top">
            <h3 class="task-name">{{ task?.taskName }}</h3>
            <div class="header-actions">
              <el-button text @click="drawerVisible = false"><Icon icon="ep:close" size="20" /></el-button>
            </div>
          </div>
          <div class="header-meta">
            <el-tag :style="getStatusStyle(task?.completeStatus)" size="small" effect="light">
              {{ getStatusLabel(task?.completeStatus) }}
            </el-tag>
            <span class="progress-text">进度 {{ task?.progress || 0 }}%</span>
            <el-progress :percentage="task?.progress || 0" :stroke-width="4" :show-text="false" style="width: 100px" />
            <el-button link type="primary" size="small" @click="handleEdit" v-if="checkPermi(['pms:task:update'])">编辑</el-button>
          </div>
        </div>
      </div>

      <!-- Tab 内容 -->
      <div class="drawer-body">
        <el-tabs v-model="activeTab">
          <!-- 基本信息 -->
          <el-tab-pane label="基本信息" name="info">
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="任务名称" :span="2">{{ task?.taskName }}</el-descriptions-item>
              <el-descriptions-item label="任务编号">{{ task?.taskCode || '-' }}</el-descriptions-item>
              <el-descriptions-item label="任务类型">{{ getTaskTypeLabel(task?.taskType) }}</el-descriptions-item>
              <el-descriptions-item label="优先级">
                <el-tag :color="priorityMap[task?.priority || 'normal']?.color" effect="plain" size="small">
                  {{ priorityMap[task?.priority || 'normal']?.label || '普通' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="里程碑">
                <el-tag v-if="task?.isMilestone" type="warning" size="small">是</el-tag>
                <span v-else>否</span>
              </el-descriptions-item>
              <el-descriptions-item label="负责人">{{ getOwnerName(task) }}</el-descriptions-item>
              <el-descriptions-item label="参与人">{{ getHelperNames(task) }}</el-descriptions-item>
              <el-descriptions-item label="计划开始">{{ formatDate(task?.planStartDate) }}</el-descriptions-item>
              <el-descriptions-item label="计划结束">
                <span :style="{ color: isDelayed ? '#F53F3F' : '' }">
                  {{ formatDate(task?.planEndDate) }}
                  <el-tag v-if="isDelayed" type="danger" size="small" effect="plain" style="margin-left: 4px">
                    延期{{ delayDays }}天
                  </el-tag>
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="实际完成">{{ formatDate(task?.actualCompleteDate) }}</el-descriptions-item>
              <el-descriptions-item label="工期">{{ task?.cycle || '-' }}天</el-descriptions-item>
              <el-descriptions-item label="预估工时">{{ task?.estimatedHours || '-' }}h</el-descriptions-item>
              <el-descriptions-item label="实际工时">{{ task?.actualHours || '-' }}h</el-descriptions-item>
              <el-descriptions-item label="关键路径">
                <el-tag v-if="task?.isCriticalPath" type="danger" size="small" effect="plain">是</el-tag>
                <span v-else>否</span>
              </el-descriptions-item>
              <el-descriptions-item label="描述" :span="2">{{ task?.description || '-' }}</el-descriptions-item>
              <el-descriptions-item label="输出物要求" :span="2">{{ task?.outputRequirement || '-' }}</el-descriptions-item>
              <el-descriptions-item label="完成标准" :span="2">{{ task?.completionStandard || '-' }}</el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>

          <!-- 进度填报 -->
          <el-tab-pane label="进度填报" name="progress">
            <div style="margin-bottom: 12px">
              <el-button type="primary" size="small" @click="showProgressDialog = true" v-if="canReportProgress && task?.completeStatus !== 'completed' && task?.completeStatus !== 'paused'">
                <Icon icon="ep:edit" class="mr-4px" />进度填报
              </el-button>
            </div>
            <el-table :data="progressList" stripe size="small" style="width: 100%">
              <el-table-column label="时间" width="150">
                <template #default="{ row }">{{ formatDate(row.createTime, 'MM-DD HH:mm') }}</template>
              </el-table-column>
              <el-table-column label="进度" width="80">
                <template #default="{ row }">
                  <el-progress :percentage="row.progress" :stroke-width="6" />
                </template>
              </el-table-column>
              <el-table-column label="填报人" width="100">
                <template #default="{ row }">{{ row.reporter || '-' }}</template>
              </el-table-column>
              <el-table-column label="备注" prop="remark" show-overflow-tooltip />
            </el-table>
            <el-empty v-if="progressList.length === 0" description="暂无进度记录" :image-size="60" />
          </el-tab-pane>

          <!-- 输出物 -->
          <el-tab-pane label="输出物" name="outputs">
            <div style="margin-bottom: 12px">
              <el-button type="primary" size="small" @click="handleUpload" v-if="checkPermi(['pms:task:update'])">
                <Icon icon="ep:upload" class="mr-4px" />上传文件
              </el-button>
            </div>
            <el-table :data="outputList" stripe size="small" style="width: 100%">
              <el-table-column label="文件名" prop="fileName" show-overflow-tooltip>
                <template #default="{ row }">
                  <Icon icon="ep:document" class="mr-4px" />{{ row.fileName }}
                </template>
              </el-table-column>
              <el-table-column label="版本" width="80" prop="version" />
              <el-table-column label="大小" width="80">
                <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
              </el-table-column>
              <el-table-column label="上传时间" width="120">
                <template #default="{ row }">{{ formatDate(row.createTime, 'MM-DD') }}</template>
              </el-table-column>
              <el-table-column label="操作" width="80">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" @click="handleViewOutput(row)">查看</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="outputList.length === 0" description="暂无输出物" :image-size="60" />
          </el-tab-pane>

          <!-- 变更记录 -->
          <el-tab-pane label="变更记录" name="changes">
            <el-table :data="changeList" stripe size="small" style="width: 100%">
              <el-table-column label="变更编号" prop="changeCode" width="140" />
              <el-table-column label="类型" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.changeType === 'schedule' ? 'warning' : 'info'" size="small">{{ getChangeTypeLabel(row.changeType) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="80">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'approved' ? 'success' : 'warning'" size="small">{{ getChangeStatusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="变更说明" prop="changeReason" show-overflow-tooltip />
            </el-table>
            <el-empty v-if="changeList.length === 0" description="暂无变更记录" :image-size="60" />
          </el-tab-pane>

          <!-- 审核 -->
          <el-tab-pane label="审核" name="review">
            <div v-if="canReview" class="review-form">
              <el-form label-width="80px">
                <el-form-item label="审核结果">
                  <el-radio-group v-model="reviewForm.result">
                  <el-radio label="approved">通过</el-radio>
                  <el-radio label="rejected">驳回</el-radio>
                  </el-radio-group>
                </el-form-item>
                <el-form-item label="审核意见">
                  <el-input v-model="reviewForm.opinion" type="textarea" :rows="3" placeholder="请输入审核意见" />
                </el-form-item>
                <el-form-item>
                  <el-button @click="drawerVisible = false">取消</el-button>
                  <el-button type="primary" @click="handleSubmitReview">提交审核</el-button>
                </el-form-item>
              </el-form>
            </div>
            <el-empty v-else description="您不是此任务的审核人" :image-size="60" />
          </el-tab-pane>
        </el-tabs>
      </div>

      <!-- 底部操作栏 -->
      <div class="drawer-footer">
        <template v-if="task?.completeStatus !== 'completed'">
          <el-button @click="handleReportProgress" v-if="canReportProgress">
            <Icon icon="ep:edit" class="mr-4px" />进度填报
          </el-button>
          <el-button @click="handlePause" v-if="task?.completeStatus === 'in_progress' && checkPermi(['pms:task:update'])">
            <Icon icon="ep:video-pause" class="mr-4px" />暂停任务
          </el-button>
          <el-button type="warning" @click="handleResume" v-if="task?.completeStatus === 'paused' && checkPermi(['pms:task:update'])">
            <Icon icon="ep:video-play" class="mr-4px" />恢复任务
          </el-button>
          <el-button type="success" @click="handleSubmitComplete" v-if="task?.completeStatus === 'in_progress' || task?.completeStatus === 'delayed'">
            <Icon icon="ep:circle-check" class="mr-4px" />提交完成
          </el-button>
        </template>
        <el-button @click="handleCreateChange" v-if="task?.completeStatus === 'completed'">
          <Icon icon="ep:edit-pen" class="mr-4px" />发起变更
        </el-button>
      </div>
    </div>

    <!-- 进度填报弹窗 -->
    <el-dialog v-model="showProgressDialog" title="进度填报" width="480px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="当前进度">{{ task?.progress || 0 }}%</el-form-item>
        <el-form-item label="填报进度">
          <el-slider v-model="progressForm.progress" :min="task?.progress || 0" :max="100" show-input />
        </el-form-item>
        <el-form-item label="剩余工期">
          <el-input-number v-model="progressForm.remainingDays" :min="0" />天
        </el-form-item>
        <el-form-item label="填报说明">
          <el-input v-model="progressForm.remark" type="textarea" :rows="3" placeholder="请输入填报说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showProgressDialog = false">取消</el-button>
        <el-button type="primary" @click="submitProgress">提交填报</el-button>
      </template>
    </el-dialog>
  </el-drawer>

  <!-- 提交完成确认弹窗 -->
  <el-dialog v-model="showSubmitDialog" title="提交完成确认" width="480px" append-to-body>
    <el-alert
      v-if="task?.isOutputRequired && !hasDeliverable"
      title="此任务需要输出物，请先上传"
      type="warning"
      :closable="false"
      show-icon
      description="根据流程要求，提交完成前必须至少关联一个输出物文件。"
      class="mb-16px"
    />
    <el-form label-width="90px">
      <el-form-item label="完成说明">
        <el-input v-model="submitForm.completionNote" type="textarea" :rows="3" placeholder="请描述完成情况" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showSubmitDialog = false">取消</el-button>
      <el-button type="primary" :disabled="task?.isOutputRequired && !hasDeliverable" @click="confirmSubmitComplete">确认提交</el-button>
    </template>
  </el-dialog>

  <!-- 隐藏的文件上传 input -->
  <input
    ref="fileInputRef"
    type="file"
    style="display: none"
    @change="handleFileSelect"
  />
</template>

<script setup lang="ts">
import { TaskVO, updateTask, getTask } from '@/api/pms/task'
import { getDocumentList } from '@/api/pms/document'
import { getChangeRecordList } from '@/api/pms/change'
import {
  taskStatusMap, priorityMap, taskTypeOptions,
  formatDate, calcDelayDays
} from '../pms-utils'
import { checkPermi } from '@/utils/permission'
import { getAccessToken } from '@/utils/auth'
import { useUserNames } from '@/hooks/pms/useUserNames'

defineOptions({ name: 'TaskDetailDrawer' })

const emit = defineEmits<{
  (e: 'refresh'): void
  (e: 'edit', task: TaskVO): void
  (e: 'upload', task: TaskVO): void
  (e: 'create-change', task: TaskVO): void
}>()
const message = useMessage()
const { getUserName, getUserNamesFromStr } = useUserNames()

const drawerVisible = ref(false)
const activeTab = ref('info')
const task = ref<TaskVO | null>(null)
const showProgressDialog = ref(false)

// 列表数据
const progressList = ref<any[]>([])
const outputList = ref<any[]>([])
const changeList = ref<any[]>([])

const progressForm = reactive({
  progress: 0,
  remainingDays: 0,
  remark: ''
})

const reviewForm = reactive({
  result: '',
  opinion: ''
})

// ==================== 权限 ====================
const canReportProgress = computed(() => checkPermi(['pms:task:update']))
const canReview = computed(() => checkPermi(['pms:task:update']))

// ==================== 计算属性 ====================
const isDelayed = computed(() => {
  if (!task.value) return false
  return calcDelayDays(task.value.planEndDate, task.value.completeStatus) > 0
})

const delayDays = computed(() => {
  if (!task.value) return 0
  return calcDelayDays(task.value.planEndDate, task.value.completeStatus)
})

// ==================== 方法 ====================
const open = async (taskData: TaskVO) => {
  drawerVisible.value = true
  // 重新获取最新任务数据，避免使用旧对象 (P0 修复: 列表与详情状态不一致)
  try {
    const freshTask = await getTask(String(taskData.taskId))
    task.value = freshTask || taskData
  } catch {
    task.value = taskData
  }
  activeTab.value = 'info'
  progressForm.progress = task.value?.progress || 0

  // 优先从 task 数据加载
  progressList.value = task.value?.progressHistory || []
  outputList.value = task.value?.outputList || []
  changeList.value = task.value?.changeList || []

  // 加载进度记录、输出物、变更记录
  await loadProgressList()
  await loadOutputList()
  await loadChangeList()
}

const loadProgressList = async () => {
  if (!task.value) return
  try {
    // 进度记录通过 task 的 progress 字段构建单条记录
    if (task.value.progress !== undefined && task.value.progress > 0) {
      progressList.value = [{
        progress: task.value.progress,
        reporter: '',
        remark: '当前进度',
        createTime: task.value.createTime || new Date().toISOString()
      }]
    } else {
      progressList.value = []
    }
  } catch {
    progressList.value = []
  }
}

const loadOutputList = async () => {
  if (!task.value) return
  try {
    const res = await getDocumentList()
    const docs = ((res as any)?.data || (res as any[]) || []).filter(
      (d: any) => String(d.taskId) === String(task.value!.taskId)
    )
    outputList.value = docs
  } catch {
    outputList.value = []
  }
}

const loadChangeList = async () => {
  if (!task.value) return
  try {
    const res = await getChangeRecordList()
    const allChanges = ((res as any)?.data || (res as any[]) || [])
    // P0-03: 变更记录按当前 taskId 隔离过滤
    changeList.value = allChanges.filter(c => String(c.taskId) === String(task.value!.taskId))
  } catch {
    changeList.value = []
  }
}

const getStatusStyle = (status?: string) => {
  const s = taskStatusMap[status || '']
  return s ? `color: ${s.textColor}; background: ${s.bgColor}; border-color: ${s.borderColor};` : ''
}

const getStatusLabel = (status?: string) => taskStatusMap[status || '']?.label || '-'
const getOwnerName = (t?: TaskVO) => getUserName(t?.mainOwnerId)
const getHelperNames = (t?: TaskVO) => getUserNamesFromStr(t?.helperIds)
const getTaskTypeLabel = (type?: string) => taskTypeOptions.find(o => o.value === type)?.label || type || '-'
const getChangeTypeLabel = (type: string) => ({ schedule: '工期变更', scope: '范围变更', cost: '成本变更' }[type] || type)
const getChangeStatusLabel = (status: string) => ({ pending: '待审批', approved: '已通过', rejected: '已驳回' }[status] || status)

const formatFileSize = (bytes?: number) => {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + 'KB'
  return (bytes / 1048576).toFixed(1) + 'MB'
}

// ==================== 操作 ====================
const handleEdit = () => {
  if (task.value) {
    emit('edit', task.value)
    drawerVisible.value = false
  }
}

const handleReportProgress = () => {
  progressForm.progress = task.value?.progress || 0
  showProgressDialog.value = true
}

const submitProgress = async () => {
  if (!task.value) return
  // 进度=100 时不自动改状态，提示用户走提交完成流程
  if (progressForm.progress >= 100) {
    message.warning('进度达到100%，请点击"提交完成"按钮进入审核流程')
    return
  }
  try {
    const updateData: any = {
      taskId: task.value.taskId,
      progress: progressForm.progress
    }
    // 自动状态流转: not_started → in_progress
    if (progressForm.progress > 0 && task.value.completeStatus === 'not_started') {
      updateData.completeStatus = 'in_progress'
    }
    await updateTask(updateData)
    message.success('进度填报成功')
    showProgressDialog.value = false
    // 重新获取任务数据，确保状态同步
    try {
      const refreshed = await getTask(String(task.value.taskId))
      if (refreshed) task.value = refreshed
    } catch { /* ignore */ }
    emit('refresh')
  } catch (e) {
    console.error('进度填报失败', e)
    message.error('进度填报失败')
  }
}

const handleCreateChange = () => {
  if (task.value) {
    emit('create-change', task.value)
    drawerVisible.value = false
  }
}

const handlePause = () => {
  message.confirm('确认暂停此任务？').then(async () => {
    if (!task.value) return
    await updateTask({ taskId: task.value.taskId, completeStatus: 'paused' })
    message.success('任务已暂停')
    task.value.completeStatus = 'paused'
    emit('refresh')
  }).catch(() => {})
}

const handleResume = () => {
  message.confirm('确认恢复此任务？').then(async () => {
    if (!task.value) return
    await updateTask({ taskId: task.value.taskId, completeStatus: 'in_progress' })
    message.success('任务已恢复')
    task.value.completeStatus = 'in_progress'
    emit('refresh')
  }).catch(() => {})
}

// 提交完成 — 走待审核流程，需校验输出物
const showSubmitDialog = ref(false)
const submitForm = reactive({ completionNote: '' })
const hasDeliverable = ref(false)

const handleSubmitComplete = async () => {
  if (!task.value) return
  // 校验输出物
  try {
    const docs = await getDocumentList()
    const taskDocs = ((docs as any[]) || []).filter(d => String(d.taskId) === String(task.value!.taskId))
    hasDeliverable.value = taskDocs.length > 0
  } catch { hasDeliverable.value = false }
  submitForm.completionNote = ''
  showSubmitDialog.value = true
}

const confirmSubmitComplete = async () => {
  if (!task.value) return
  // 输出物不强制：仅当任务标记为需要输出物时才校验
  if (task.value?.isOutputRequired && !hasDeliverable.value) {
    message.warning('此任务需要输出物，请先上传')
    return
  }
  try {
    await updateTask({
      taskId: task.value.taskId,
      completeStatus: 'pending_review'
      // P0-05: 不强制设 progress=100，保留当前进度值，避免列表与详情不一致
    })
    message.success('已提交完成，等待项目经理审核')
    showSubmitDialog.value = false
    // P0-05: 重新获取最新任务数据，避免使用旧对象
    try {
      const fresh = await getTask(String(task.value.taskId))
      if (fresh) task.value = fresh
    } catch { /* ignore */ }
    emit('refresh')
  } catch (e) {
    console.error('提交完成失败', e)
    message.error('提交失败')
  }
}

const handleUpload = () => {
  fileInputRef.value?.click()
}

const handleViewOutput = (item: any) => {
  if (item?.storagePath) {
    window.open(item.storagePath, '_blank')
  } else if (item?.fileName) {
    message.info('文档: ' + item.fileName)
  } else {
    message.info('暂无可查看的内容')
  }
}

const fileInputRef = ref<HTMLInputElement | null>(null)

const handleFileSelect = async (event: Event) => {
  const target = event.target as HTMLInputElement
  if (!target.files || !target.files.length) return
  const file = target.files[0]
  if (file.size > 50 * 1024 * 1024) {
    message.error('文件不能超过 50MB')
    return
  }
  message.info('正在上传...')
  const formData = new FormData()
  formData.append('file', file)
  try {
    const token = getAccessToken()
    const res = await fetch('/admin-api/infra/file/upload', {
      method: 'POST',
      headers: { 'Authorization': 'Bearer ' + token, 'tenant-id': '1' },
      body: formData
    })
    if (!res.ok) {
      message.error(`上传失败: HTTP ${res.status}`)
      return
    }
    const text = await res.text()
    let fileUrl = ''
    const fileName = file.name
    // 尝试 JSON 解析
    try {
      const data = JSON.parse(text)
      if (data.code === 0) {
        fileUrl = data.data
      } else {
        message.error('上传失败: ' + (data.msg || '未知错误'))
        return
      }
    } catch {
      // 不是 JSON，可能是纯文本 URL
      if (text && text.startsWith('http')) {
        fileUrl = text
      } else {
        message.error('上传失败: 服务端返回格式异常')
        console.error('Upload response:', text)
        return
      }
    }
    // 上传成功，添加到输出物列表
    outputList.value.push({
      fileName: fileName,
      fileType: fileName.split('.').pop() || 'unknown',
      storagePath: fileUrl,
      fileSize: file.size,
      version: 'v1',
      createTime: new Date().toISOString(),
      uploadTime: new Date().toISOString()
    })
    message.success('文件上传成功')
  } catch (e) {
    message.error('文件上传失败: ' + (e instanceof Error ? e.message : '网络错误'))
    console.error('Upload error:', e)
  }
  target.value = ''
}

const handleSubmitReview = async () => {
  if (!reviewForm.result) {
    message.warning('请选择审核结果')
    return
  }
  if (!task.value) return
  try {
    const statusMap: Record<string, string> = {
      approved: 'completed',
      rejected: 'in_progress'
    }
    const updateData: any = {
      taskId: task.value.taskId,
      completeStatus: statusMap[reviewForm.result] || task.value.completeStatus,
      opinion: reviewForm.opinion,
      reviewOpinion: reviewForm.opinion || ''
    }
    // 审核通过时设置实际完成日期为当前时间（PM审核通过时间）
    if (reviewForm.result === 'approved') {
      updateData.actualCompleteDate = new Date().toISOString().split('T')[0]
      updateData.progress = 100
    }
    await updateTask(updateData)
    message.success(`审核已${reviewForm.result === 'approved' ? '通过' : '驳回'}`)
    reviewForm.result = ''
    reviewForm.opinion = ''
    emit('refresh')
  } catch (e) {
    console.error('审核提交失败', e)
    message.error('审核提交失败')
  }
}

defineExpose({ open })
</script>

<style scoped>
.task-drawer {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.drawer-header {
  padding: 16px 20px;
  border-bottom: 1px solid #E5E6EB;
  background: #FFF;
}
.header-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.task-name {
  font-size: 18px;
  font-weight: 600;
  color: #1D2129;
  margin: 0 0 8px 0;
}
.header-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}
.progress-text {
  font-size: 13px;
  color: #4E5969;
}
.drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
}
.drawer-footer {
  padding: 12px 20px;
  border-top: 1px solid #E5E6EB;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  background: #FFF;
}
.review-form {
  padding: 16px 0;
}
</style>
