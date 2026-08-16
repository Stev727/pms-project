<template>
  <el-dialog v-model="visible" title="发起变更" width="720px" :close-on-click-modal="false" append-to-body>
    <el-form label-width="100px">
      <el-form-item label="变更标题" required><el-input v-model="newChange.title" placeholder="请输入变更标题" /></el-form-item>
      <el-form-item label="变更类型" required>
        <el-select v-model="newChange.type" class="w-full">
          <el-option v-for="(v, k) in changeTypes" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="关联任务" required>
        <el-select v-model="newChange.affectedTasks" filterable placeholder="选择关联任务（必填，自动带出旧值）" class="w-full" @change="onTaskSelect">
          <el-option v-for="t in tasks" :key="t.taskId" :label="`${t.taskName}${t.taskCode ? '(' + t.taskCode + ')' : ''}`" :value="String(t.taskId)" />
        </el-select>
      </el-form-item>

      <!-- 旧值自动带出（只读） -->
      <div v-if="newChange.affectedTasks" class="change-form-section">
        <div class="section-subtitle">旧值（任务当前值，自动带出）</div>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="任务名">
              <el-input v-model="beforeSnapshot.taskName" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="责任人">
              <el-input v-model="beforeSnapshot.mainOwnerName" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划开始">
              <el-date-picker v-model="beforeSnapshot.planStartDate" type="date" value-format="YYYY-MM-DD" disabled class="w-full" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划结束">
              <el-date-picker v-model="beforeSnapshot.planEndDate" type="date" value-format="YYYY-MM-DD" disabled class="w-full" />
            </el-form-item>
          </el-col>
        </el-row>
      </div>

      <!-- 新值（用户填写） -->
      <div v-if="newChange.affectedTasks" class="change-form-section">
        <div class="section-subtitle">新值（请填写变更后的值，未改字段留空）</div>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="新任务名">
              <el-input v-model="afterSnapshot.taskName" placeholder="留空表示不修改" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="新责任人">
              <el-select v-model="afterSnapshot.mainOwnerId" filterable clearable placeholder="留空表示不修改" class="w-full">
                <el-option v-for="u in projectMemberUsers" :key="u.id" :label="u.nickname" :value="Number(u.id)" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="新计划开始">
              <el-date-picker v-model="afterSnapshot.planStartDate" type="date" value-format="YYYY-MM-DD" placeholder="留空表示不修改" class="w-full" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="新计划结束">
              <el-date-picker v-model="afterSnapshot.planEndDate" type="date" value-format="YYYY-MM-DD" placeholder="留空表示不修改" class="w-full" />
            </el-form-item>
          </el-col>
        </el-row>
      </div>

      <el-row :gutter="16">
        <el-col :span="12"><el-form-item label="紧急程度"><el-switch v-model="newChange.urgent" active-text="紧急" inactive-text="普通" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="成本影响(元)"><el-input-number v-model="newChange.costImpact" :min="0" class="w-full" /></el-form-item></el-col>
      </el-row>
      <el-form-item label="变更原因" required><el-input v-model="newChange.reason" type="textarea" :rows="3" placeholder="请详细说明变更原因" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="submitChange" :loading="saving">提交</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createChangeRecord, ChangeRecordVO } from '@/api/pms/change'
import { TaskVO } from '@/api/pms/task'
import { useUserNames } from '@/hooks/pms/useUserNames'
import { useProjectMembers } from '@/hooks/pms/useProjectMembers'
import { useCache } from '@/hooks/web/useCache'

defineOptions({ name: 'ChangeFormDialog' })

const { getUserName } = useUserNames()
const { projectMemberUsers, loadProjectMembers } = useProjectMembers()

const props = defineProps<{
  projectId: string
  tasks?: TaskVO[]
}>()

const emit = defineEmits(['submitted'])

const visible = ref(false)
const saving = ref(false)
const tasks = ref<TaskVO[]>([])

watch(() => props.tasks, (val) => {
  tasks.value = ((val as TaskVO[]) || []).filter(t => String(t.projectId) === String(props.projectId))
}, { immediate: true })

const changeTypes: Record<string, { label: string; color: string }> = {
  requirement: { label: '需求变更', color: '#2468F2' },
  technical: { label: '技术变更', color: '#722ED1' },
  schedule: { label: '计划变更', color: '#FF7D00' },
  personnel: { label: '人员变更', color: '#0FC6C2' }
}

const newChange = reactive({
  title: '', type: 'requirement', urgent: false,
  reason: '', affectedTasks: '', costImpact: 0, scheduleImpact: 0
})

const beforeSnapshot = reactive({
  taskName: '', planStartDate: '', planEndDate: '', mainOwnerId: undefined as number | undefined, mainOwnerName: ''
})
const afterSnapshot = reactive({
  taskName: '', planStartDate: '', planEndDate: '', mainOwnerId: undefined as number | undefined
})

const onTaskSelect = (taskId: string) => {
  const task = tasks.value.find(t => String(t.taskId) === String(taskId))
  if (!task) {
    Object.assign(beforeSnapshot, { taskName: '', planStartDate: '', planEndDate: '', mainOwnerId: undefined, mainOwnerName: '' })
    Object.assign(afterSnapshot, { taskName: '', planStartDate: '', planEndDate: '', mainOwnerId: undefined })
    return
  }
  beforeSnapshot.taskName = task.taskName || ''
  beforeSnapshot.planStartDate = task.planStartDate || ''
  beforeSnapshot.planEndDate = task.planEndDate || ''
  beforeSnapshot.mainOwnerId = task.mainOwnerId
  beforeSnapshot.mainOwnerName = task.mainOwnerId ? getUserName(task.mainOwnerId) : ''
  Object.assign(afterSnapshot, { taskName: '', planStartDate: '', planEndDate: '', mainOwnerId: undefined })
}

const buildChangeText = (snapshot: any, isBefore: boolean) => {
  const parts: string[] = []
  if (isBefore) {
    parts.push('任务名: ' + (beforeSnapshot.taskName || '-'))
    parts.push('责任人: ' + (beforeSnapshot.mainOwnerName || '-'))
    parts.push('计划开始: ' + (beforeSnapshot.planStartDate || '-'))
    parts.push('计划结束: ' + (beforeSnapshot.planEndDate || '-'))
  } else {
    if (afterSnapshot.taskName) parts.push('任务名: ' + afterSnapshot.taskName)
    if (afterSnapshot.mainOwnerId) {
      const uname = projectMemberUsers.value.find(u => Number(u.id) === Number(afterSnapshot.mainOwnerId))?.nickname || ''
      if (uname) parts.push('责任人: ' + uname)
    }
    if (afterSnapshot.planStartDate) parts.push('计划开始: ' + afterSnapshot.planStartDate)
    if (afterSnapshot.planEndDate) parts.push('计划结束: ' + afterSnapshot.planEndDate)
    if (parts.length === 0) parts.push('（未修改任何字段）')
  }
  return parts.join('; ')
}

async function submitChange() {
  if (!newChange.title) { ElMessage.warning('请填写变更标题'); return }
  if (!newChange.reason) { ElMessage.warning('请填写变更原因'); return }
  if (!newChange.affectedTasks) { ElMessage.warning('请选择关联任务'); return }
  saving.value = true
  try {
    const userInfo = useCache().wsCache.get('userInfo')
    const currentUserId = userInfo?.id
    const beforeContent = buildChangeText(beforeSnapshot, true)
    const afterContent = buildChangeText(afterSnapshot, false)
    const submitData: any = {
      changeDescription: newChange.title,
      changeType: newChange.type,
      changeReason: newChange.reason,
      projectId: props.projectId,
      affectedTasks: newChange.affectedTasks,
      taskId: newChange.affectedTasks,
      beforeContent,
      afterContent,
      afterState: JSON.stringify({
        taskName: afterSnapshot.taskName || null,
        mainOwnerId: afterSnapshot.mainOwnerId || null,
        planStartDate: afterSnapshot.planStartDate || null,
        planEndDate: afterSnapshot.planEndDate || null
      }),
      urgent: newChange.urgent,
      costImpact: newChange.costImpact ?? 0,
      scheduleImpact: newChange.scheduleImpact ?? 0,
      changeCode: `CR-${Date.now().toString().slice(-6)}`,
      approvalStatus: 'pending'
    }
    if (currentUserId) {
      submitData.initiatorId = String(currentUserId)
    }
    await createChangeRecord(submitData as unknown as ChangeRecordVO)
    ElMessage.success('变更已提交')
    visible.value = false
    Object.assign(newChange, { title: '', type: 'requirement', urgent: false, reason: '', affectedTasks: '', costImpact: 0, scheduleImpact: 0 })
    Object.assign(beforeSnapshot, { taskName: '', planStartDate: '', planEndDate: '', mainOwnerId: undefined, mainOwnerName: '' })
    Object.assign(afterSnapshot, { taskName: '', planStartDate: '', planEndDate: '', mainOwnerId: undefined })
    emit('submitted')
  } catch (e) { console.error(e); ElMessage.error('提交失败') }
  finally { saving.value = false }
}

/** 对外暴露：打开发起变更对话框，可预填任务ID */
const open = (taskId?: string) => {
  if (taskId) {
    newChange.affectedTasks = String(taskId)
    onTaskSelect(String(taskId))
  }
  visible.value = true
}

onMounted(() => {
  loadProjectMembers?.(props.projectId)
})

defineExpose({ open })
</script>

<style scoped>
.change-form-section {
  background: #f7f8fa;
  border-radius: 4px;
  padding: 12px;
  margin-bottom: 16px;
}
.section-subtitle {
  font-size: 13px;
  font-weight: 600;
  color: #4e5969;
  margin-bottom: 8px;
}
.w-full { width: 100%; }
</style>
