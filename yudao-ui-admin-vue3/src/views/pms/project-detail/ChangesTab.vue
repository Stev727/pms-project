<template>
  <div class="changes-tab">
    <div class="tab-toolbar">
      <span class="toolbar-title">变更记录 ({{ filteredList.length }})</span>
      <div style="display: flex; gap: 8px">
        <el-select v-model="filterType" placeholder="变更类型" clearable size="small" style="width: 110px">
          <el-option v-for="(v, k) in changeTypes" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-select v-model="filterStatus" placeholder="状态" clearable size="small" style="width: 100px">
          <el-option v-for="(v, k) in changeStatusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-button type="primary" size="small" @click="showForm = true" v-if="checkPermi(['pms:change:create'])">
          <Icon icon="ep:plus" class="mr-4px" />发起变更
        </el-button>
      </div>
    </div>

    <div v-loading="loading" class="change-timeline">
      <el-timeline v-if="filteredList.length > 0">
        <el-timeline-item
          v-for="item in filteredList"
          :key="item.id"
          :timestamp="formatDate(item.applyTime, 'YYYY-MM-DD HH:mm')"
          :type="getTimelineType(item.status)"
          placement="top"
          @click="openDetail(item)"
        >
          <el-card shadow="hover" class="change-card" @click="openDetail(item)">
            <div class="change-card-header">
              <span class="change-card-number">{{ item.changeNumber }}</span>
              <el-tag :color="changeTypes[item.type]?.color" effect="dark" size="small">{{ changeTypes[item.type]?.label }}</el-tag>
              <el-tag :color="changeStatusMap[item.status]?.color" effect="dark" size="small">{{ changeStatusMap[item.status]?.label }}</el-tag>
              <el-tag v-if="item.urgent" type="danger" effect="dark" size="small">紧急</el-tag>
            </div>
            <div class="change-card-title">{{ item.title }}</div>

            <!-- P1: 变更前/变更后直接展示，无需点入详情 -->
            <div class="change-card-compare" v-if="item.beforeContent || item.afterContent">
              <div class="compare-cell before">
                <div class="compare-label">变更前</div>
                <div class="compare-text">{{ item.beforeContent || '--' }}</div>
              </div>
              <div class="compare-arrow">
                <Icon icon="ep:right" />
              </div>
              <div class="compare-cell after">
                <div class="compare-label">变更后</div>
                <div class="compare-text">{{ item.afterContent || '--' }}</div>
              </div>
            </div>

            <div class="change-card-meta">
              <span v-if="item.applicant"><Icon icon="ep:user" class="mr-2px" />{{ item.applicant }}</span>
              <span v-if="item.taskName" class="meta-task"><Icon icon="ep:list" class="mr-2px" />{{ item.taskName }}</span>
              <span v-if="item.reason" class="meta-reason" :title="item.reason">
                <Icon icon="ep:chat-line-square" class="mr-2px" />{{ item.reason.length > 30 ? item.reason.slice(0, 30) + '...' : item.reason }}
              </span>
              <template v-if="item.impacts && item.impacts.length > 0">
                <el-tag v-for="(impact, i) in item.impacts" :key="i" size="small" type="warning" effect="plain" style="margin-right: 4px">
                  {{ impact }}
                </el-tag>
              </template>
            </div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
    </div>

    <el-empty v-if="!loading && filteredList.length === 0" description="暂无变更记录" />

    <!-- 变更详情抽屉 -->
    <el-drawer v-model="drawerVisible" size="720px" :show-close="false">
      <template #header>
        <div class="drawer-header">
          <el-tag :color="changeTypes[selected?.type]?.color" effect="dark">{{ changeTypes[selected?.type]?.label }}</el-tag>
          <el-tag :color="changeStatusMap[selected?.status]?.color" effect="dark">{{ changeStatusMap[selected?.status]?.label }}</el-tag>
          <span class="title">{{ selected?.title }}</span>
          <el-button link @click="drawerVisible = false"><Icon icon="ep:close" /></el-button>
        </div>
      </template>
      <template v-if="selected">
        <el-descriptions title="变更信息" :column="2" border size="small">
          <el-descriptions-item label="变更编号">{{ selected.changeNumber }}</el-descriptions-item>
          <el-descriptions-item label="变更类型">{{ changeTypes[selected.type]?.label }}</el-descriptions-item>
          <el-descriptions-item label="关联任务">{{ selected.taskName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ selected.applicant }}</el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ formatDate(selected.applyTime) }}</el-descriptions-item>
          <el-descriptions-item label="审批人">{{ selected.approver || '待指派' }}</el-descriptions-item>
          <el-descriptions-item label="紧急程度">
            <el-tag :color="selected.urgent ? '#FF7D00' : '#86909C'" effect="dark" size="small">
              {{ selected.urgent ? '紧急' : '普通' }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <div class="section-title">变更内容</div>
        <el-row :gutter="16">
          <el-col :span="12">
            <div class="compare-block before">
              <div class="compare-label">变更前</div>
              <pre>{{ selected.beforeContent || '--' }}</pre>
            </div>
          </el-col>
          <el-col :span="12">
            <div class="compare-block after">
              <div class="compare-label">变更后</div>
              <pre>{{ selected.afterContent || '--' }}</pre>
            </div>
          </el-col>
        </el-row>

        <div class="section-title">变更原因</div>
        <p class="reason-text">{{ selected.reason || '-' }}</p>

        <div class="section-title">影响分析</div>
        <ul class="impact-list">
          <li v-for="(impact, i) in (selected.impacts || [])" :key="i">{{ impact }}</li>
          <li v-if="!selected.impacts || selected.impacts.length === 0">无</li>
        </ul>

        <div class="section-title">审批流程</div>
        <el-timeline>
          <el-timeline-item timestamp="申请" type="success">{{ selected.applicant }} 提交变更申请</el-timeline-item>
          <el-timeline-item v-if="selected.status !== 'draft'" timestamp="审批"
            :type="selected.status === 'approved' ? 'success' : selected.status === 'rejected' ? 'danger' : 'primary'">
            {{ selected.approver || '审批人' }} {{ changeStatusMap[selected.status]?.label }}
          </el-timeline-item>
          <el-timeline-item v-if="selected.status === 'approved'" timestamp="执行" type="info">系统自动执行变更</el-timeline-item>
        </el-timeline>

        <template v-if="selected.status === 'pending' || selected.status === 'approving'">
          <div class="section-title">审批操作</div>
          <el-form label-width="80px">
            <el-form-item label="审批结果">
              <el-radio-group v-model="approveResult">
                <el-radio value="approve">通过</el-radio>
                <el-radio value="reject">驳回</el-radio>
                <el-radio value="modify">需修改</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="审批意见">
              <el-input v-model="approveOpinion" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item>
              <el-checkbox v-model="autoAdjust">通过后自动调整项目计划</el-checkbox>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="submitApproval" :loading="saving">提交审批</el-button>
            </el-form-item>
          </el-form>
        </template>
        <div v-if="selected.status === 'approved'" class="section-title">执行操作</div>
      </template>
    </el-drawer>

    <!-- 发起变更弹窗 - 重新设计: 关联任务自动带出旧值 -->
    <el-dialog v-model="showForm" title="发起变更" width="720px" :close-on-click-modal="false">
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
      <template #footer><el-button @click="showForm = false">取消</el-button><el-button type="primary" @click="submitChange" :loading="saving">提交</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getChangeRecordList, createChangeRecord, reviewChangeRecord, ChangeRecordVO } from '@/api/pms/change'
import { getTaskList, TaskVO } from '@/api/pms/task'
import { formatDate } from '../pms-utils'
import { checkPermi } from '@/utils/permission'
import { useUserNames } from '@/hooks/pms/useUserNames'
import { useProjectMembers } from '@/hooks/pms/useProjectMembers' 
import { useCache } from '@/hooks/web/useCache'

defineOptions({ name: 'ChangesTab' })

const { getUserName } = useUserNames()

const props = defineProps<{
  projectId: string
  tasks?: TaskVO[]  // P1: 由父组件传入任务列表（避免非PM看不到任务的问题）
}>()
const { projectMemberUsers, loadProjectMembers } = useProjectMembers()

const loading = ref(false)
const saving = ref(false)
const filterType = ref('')
const filterStatus = ref('')
const drawerVisible = ref(false)
const selected = ref<any>(null)
const showForm = ref(false)
const approveResult = ref('approve')
const approveOpinion = ref('')
const autoAdjust = ref(true)
const tasks = ref<TaskVO[]>([])
// P1: 监听 props.tasks 变化同步到本地（避免非PM用户调用 getTaskList 拿不到数据）
watch(() => props.tasks, (val) => {
  tasks.value = ((val as TaskVO[]) || []).filter(t => String(t.projectId) === String(props.projectId))
}, { immediate: true })

const changeTypes: Record<string, { label: string; color: string }> = {
  requirement: { label: '需求变更', color: '#2468F2' },
  technical: { label: '技术变更', color: '#722ED1' },
  schedule: { label: '计划变更', color: '#FF7D00' },
  personnel: { label: '人员变更', color: '#0FC6C2' }
}

const changeStatusMap: Record<string, { label: string; color: string }> = {
  draft: { label: '草稿', color: '#86909C' },
  pending: { label: '待审批', color: '#FF7D00' },
  approving: { label: '审批中', color: '#2468F2' },
  approved: { label: '已通过', color: '#00B42A' },
  rejected: { label: '已驳回', color: '#F53F3F' },
  revoked: { label: '已撤销', color: '#86909C' }
}

const changeList = ref<any[]>([])

const filteredList = computed(() => {
  let r = changeList.value
  if (filterType.value) r = r.filter(c => c.type === filterType.value)
  if (filterStatus.value) r = r.filter(c => c.status === filterStatus.value)
  return r
})

const newChange = reactive({
  title: '', type: 'requirement', urgent: false,
  reason: '', affectedTasks: '', costImpact: 0, scheduleImpact: 0
})

// P1: 变更前快照（自动从任务带出）
const beforeSnapshot = reactive({
  taskName: '', planStartDate: '', planEndDate: '', mainOwnerId: undefined as number | undefined, mainOwnerName: ''
})
// P1: 变更后快照（用户填写）
const afterSnapshot = reactive({
  taskName: '', planStartDate: '', planEndDate: '', mainOwnerId: undefined as number | undefined
})

// 关联任务选择后自动带出旧值
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
  // 清空新值
  Object.assign(afterSnapshot, { taskName: '', planStartDate: '', planEndDate: '', mainOwnerId: undefined })
}

const getTaskCode = (taskId: any) => {
  if (!taskId) return ''
  const task = tasks.value.find(t => String(t.taskId) === String(taskId))
  return task ? (task.taskCode || task.taskName || '') : String(taskId)
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getChangeRecordList()
    changeList.value = ((res as any[]) || [])
      .filter(item => String(item.projectId) === String(props.projectId))
      .map(item => ({
        ...item,
        changeNumber: item.changeCode || '',
        title: item.changeDescription || '',
        type: item.changeType || '',
        status: item.approvalStatus || '',
        applicant: item.initiatorId ? getUserName(item.initiatorId) : '',
        approver: item.approverId ? getUserName(item.approverId) : '',
        applyTime: item.createTime || '',
        urgent: item.scheduleImpact ? Number(item.scheduleImpact) > 0 : false,
        beforeContent: item.beforeContent || '',
        afterContent: item.afterContent || '',
        reason: item.changeReason || '',
        impacts: [
          ...(item.costImpact ? [`成本: ${item.costImpact}元`] : []),
          ...(item.scheduleImpact ? [`工期: ${item.scheduleImpact}天`] : [])
        ],
        taskName: getTaskCode(item.taskId || item.affectedTasks)
      }))
  } catch (e) { console.error(e); changeList.value = [] }
  finally { loading.value = false }
}

function getTimelineType(status: string): string {
  const map: Record<string, string> = {
    draft: 'info',
    pending: 'warning',
    approving: 'primary',
    approved: 'success',
    rejected: 'danger',
    executed: 'success'
  }
  return map[status] || 'info'
}

function openDetail(row: any) { selected.value = row; drawerVisible.value = true }

// P1: 生成结构化变更文本（仅展示被修改的字段）
const buildChangeText = (snapshot: any, isBefore: boolean) => {
  const parts: string[] = []
  if (isBefore) {
    parts.push('任务名: ' + (beforeSnapshot.taskName || '-'))
    parts.push('责任人: ' + (beforeSnapshot.mainOwnerName || '-'))
    parts.push('计划开始: ' + (beforeSnapshot.planStartDate || '-'))
    parts.push('计划结束: ' + (beforeSnapshot.planEndDate || '-'))
  } else {
    // 新值只展示用户修改过的字段
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
    // 自动生成 beforeContent / afterContent
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
    showForm.value = false
    Object.assign(newChange, { title: '', type: 'requirement', urgent: false, reason: '', affectedTasks: '', costImpact: 0, scheduleImpact: 0 })
    Object.assign(beforeSnapshot, { taskName: '', planStartDate: '', planEndDate: '', mainOwnerId: undefined, mainOwnerName: '' })
    Object.assign(afterSnapshot, { taskName: '', planStartDate: '', planEndDate: '', mainOwnerId: undefined })
    await fetchList()
  } catch (e) { console.error(e); ElMessage.error('提交失败') }
  finally { saving.value = false }
}

async function submitApproval() {
  if (!selected.value) return
  try {
    await ElMessageBox.confirm('确认提交审批结果？', '提示', { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' })
  } catch { return }
  saving.value = true
  try {
    if (approveResult.value === 'modify') {
      ElMessage.warning('请选择通过或驳回')
      return
    }
    await reviewChangeRecord(selected.value.changeId, approveResult.value === 'approve')
    ElMessage.success('审批已提交')
    drawerVisible.value = false
    await fetchList()
  } catch (e) { console.error(e); ElMessage.error('审批失败') }
  finally { saving.value = false }
}

// tasks 不再从本地加载，使用 props.tasks 同步赋值
// 由 watch 监听 props.tasks 变化时更新


onMounted(async () => {
  // 不再 loadTasks：非PM用户调用 getTaskList 会被后端过滤为空
  // 直接用 props.projectTasks（来自父组件，已包含当前项目所有任务）
  await loadProjectMembers(props.projectId)
  await fetchList()
})

// 暴露给父组件调用：从任务列表点"发起变更"时自动打开对话框并预填任务
const openChangeForm = (taskId?: string) => {
  if (taskId) {
    newChange.affectedTasks = String(taskId)
    onTaskSelect(String(taskId))
  }
  showForm.value = true
}

defineExpose({ refresh: fetchList, openChangeForm })
</script>

<style scoped>
.changes-tab { }
.tab-toolbar {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; flex-wrap: wrap; gap: 8px;
}
.toolbar-title { font-size: 14px; font-weight: 600; color: #1D2129; }
.drawer-header { display: flex; align-items: center; gap: 8px; width: 100%; .title { font-size: 16px; font-weight: 600; flex: 1; } }
.section-title { font-size: 14px; font-weight: 600; margin: 20px 0 10px; padding-left: 8px; border-left: 3px solid var(--el-color-primary); }
.compare-block {
  border: 1px solid var(--el-border-color); border-radius: 4px; padding: 12px; min-height: 100px;
  &.before { background: #FFF7E8; .compare-label { color: #FF7D00; } }
  &.after { background: #E8FFEA; .compare-label { color: #00B42A; } }
  .compare-label { font-weight: 600; margin-bottom: 8px; }
  pre { white-space: pre-wrap; word-break: break-all; margin: 0; font-size: 13px; }
}
.reason-text { font-size: 14px; line-height: 1.6; margin: 0; }
.impact-list { padding-left: 20px; li { font-size: 14px; line-height: 1.8; } }
.change-timeline { padding: 8px 0; }
.change-card { cursor: pointer; transition: all 0.2s; }
.change-card:hover { box-shadow: 0 2px 12px rgba(0,0,0,0.12); }
.change-card-header { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; }
.change-card-number { font-size: 13px; color: #86909C; font-weight: 600; }
.change-card-title { font-size: 14px; font-weight: 500; color: #1D2129; margin-bottom: 6px; }
.change-card-meta { display: flex; align-items: center; gap: 12px; font-size: 12px; color: #86909C; flex-wrap: wrap; margin-top: 8px; }
.change-card-meta .meta-task { color: #2468F2; }
.change-card-meta .meta-reason { color: #FF7D00; }
/* 发起变更表单分组 */
.change-form-section {
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
  padding: 12px;
  margin-bottom: 12px;
  background: #FAFBFC;
}
.section-subtitle {
  font-size: 13px;
  font-weight: 600;
  color: #1D2129;
  margin-bottom: 12px;
  padding-left: 6px;
  border-left: 3px solid var(--el-color-primary);
}
/* 变更前/变更后对比 */
.change-card-compare {
  display: flex;
  align-items: stretch;
  gap: 6px;
  margin: 8px 0 4px;
  font-size: 13px;
}
.change-card-compare .compare-cell {
  flex: 1;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  padding: 6px 10px;
  min-height: 36px;
}
.change-card-compare .compare-cell.before { background: #FFF7E8; border-color: #FFD79C; }
.change-card-compare .compare-cell.after { background: #E8FFEA; border-color: #A8E5B5; }
.change-card-compare .compare-label {
  font-size: 11px;
  font-weight: 600;
  margin-bottom: 2px;
}
.change-card-compare .compare-cell.before .compare-label { color: #FF7D00; }
.change-card-compare .compare-cell.after .compare-label { color: #00B42A; }
.change-card-compare .compare-text {
  white-space: pre-wrap;
  word-break: break-all;
  color: #1D2129;
  line-height: 1.5;
}
.change-card-compare .compare-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #86909C;
  font-size: 16px;
}
</style>
