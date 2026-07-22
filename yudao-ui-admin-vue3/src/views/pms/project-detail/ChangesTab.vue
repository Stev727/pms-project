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

    <el-table :data="filteredList" border stripe v-loading="loading" @row-click="openDetail">
      <el-table-column prop="changeNumber" label="编号" width="100" />
      <el-table-column prop="title" label="变更标题" min-width="160" show-overflow-tooltip />
      <el-table-column label="类型" width="90" align="center">
        <template #default="{ row }">
          <el-tag :color="changeTypes[row.type]?.color" effect="dark" size="small">{{ changeTypes[row.type]?.label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :color="changeStatusMap[row.status]?.color" effect="dark" size="small">
            {{ changeStatusMap[row.status]?.label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="applicant" label="申请人" width="80" />
      <el-table-column label="影响" width="120">
        <template #default="{ row }">
          <template v-if="row.impacts && row.impacts.length > 0">
            <el-tag v-for="(impact, i) in row.impacts" :key="i" size="small" type="warning" effect="plain" style="margin-right: 4px">
              {{ impact }}
            </el-tag>
          </template>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="申请时间" width="110">
        <template #default="{ row }">{{ formatDate(row.applyTime, 'MM-DD HH:mm') }}</template>
      </el-table-column>
      <el-table-column label="操作" width="80" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click.stop="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

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
              <pre>{{ selected.beforeContent || '无' }}</pre>
            </div>
          </el-col>
          <el-col :span="12">
            <div class="compare-block after">
              <div class="compare-label">变更后</div>
              <pre>{{ selected.afterContent || '无' }}</pre>
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
      </template>
    </el-drawer>

    <!-- 发起变更弹窗 -->
    <el-dialog v-model="showForm" title="发起变更" width="600px">
      <el-form label-width="90px">
        <el-form-item label="变更标题" required><el-input v-model="newChange.title" placeholder="请输入变更标题" /></el-form-item>
        <el-form-item label="变更类型" required>
          <el-select v-model="newChange.type" class="w-full">
            <el-option v-for="(v, k) in changeTypes" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联任务">
          <el-select v-model="newChange.affectedTasks" filterable placeholder="选择关联任务（可选）" class="w-full">
            <el-option v-for="t in tasks" :key="t.taskId" :label="t.taskName" :value="String(t.taskId)" />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="紧急程度"><el-switch v-model="newChange.urgent" active-text="紧急" inactive-text="普通" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="成本影响(元)"><el-input-number v-model="newChange.costImpact" :min="0" class="w-full" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="变更前内容"><el-input v-model="newChange.beforeContent" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="变更后内容"><el-input v-model="newChange.afterContent" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="变更原因" required><el-input v-model="newChange.reason" type="textarea" :rows="2" placeholder="请详细说明变更原因" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showForm = false">取消</el-button><el-button type="primary" @click="submitChange" :loading="saving">提交</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getChangeRecordList, createChangeRecord, updateChangeRecord, ChangeRecordVO } from '@/api/pms/change'
import { getTaskList, TaskVO } from '@/api/pms/task'
import { formatDate } from '../pms-utils'
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'ChangesTab' })

const props = defineProps<{
  projectId: string
}>()

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
  title: '', type: 'content', urgent: false, beforeContent: '', afterContent: '',
  reason: '', affectedTasks: '', costImpact: 0, scheduleImpact: 0
})

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
        beforeContent: '',
        afterContent: '',
        reason: item.changeReason || '',
        impacts: [
          ...(item.costImpact ? [`成本: ${item.costImpact}元`] : []),
          ...(item.scheduleImpact ? [`工期: ${item.scheduleImpact}天`] : [])
        ],
        taskName: item.affectedTasks || ''
      }))
  } catch (e) { console.error(e); changeList.value = [] }
  finally { loading.value = false }
}

function openDetail(row: any) { selected.value = row; drawerVisible.value = true }

async function submitChange() {
  if (!newChange.title) { ElMessage.warning('请填写变更标题'); return }
  if (!newChange.reason) { ElMessage.warning('请填写变更原因'); return }
  saving.value = true
  try {
    await createChangeRecord({
      changeDescription: newChange.title,
      changeType: newChange.type,
      changeReason: newChange.reason,
      projectId: Number(props.projectId),
      affectedTasks: newChange.affectedTasks,
      costImpact: newChange.costImpact,
      approvalStatus: 'pending'
    } as ChangeRecordVO)
    ElMessage.success('变更已提交')
    showForm.value = false
    Object.assign(newChange, { title: '', type: 'content', urgent: false, beforeContent: '', afterContent: '', reason: '', affectedTasks: '', costImpact: 0 })
    await fetchList()
  } catch (e) { console.error(e); ElMessage.error('提交失败') }
  finally { saving.value = false }
}

async function submitApproval() {
  if (!selected.value) return
  saving.value = true
  try {
    const newStatus = approveResult.value === 'approve' ? 'approved' : approveResult.value === 'reject' ? 'rejected' : 'pending'
    await updateChangeRecord({ changeId: selected.value.changeId, approvalStatus: newStatus } as ChangeRecordVO)
    ElMessage.success('审批已提交')
    drawerVisible.value = false
    await fetchList()
  } catch (e) { console.error(e); ElMessage.error('审批失败') }
  finally { saving.value = false }
}

async function loadTasks() {
  try {
    const data = await getTaskList()
    tasks.value = ((data as TaskVO[]) || []).filter(t => String(t.projectId) === String(props.projectId))
  } catch (e) { console.error(e) }
}

onMounted(async () => {
  await loadTasks()
  await fetchList()
})

defineExpose({ refresh: fetchList })
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
</style>
