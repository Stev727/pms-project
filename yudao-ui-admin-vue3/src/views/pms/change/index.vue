<template>
  <div class="pms-change">
    <ContentWrap>
      <el-form :inline="true" :model="filters">
        <el-form-item label="变更编号"><el-input v-model="filters.number" clearable style="width: 120px" /></el-form-item>
        <el-form-item label="项目">
          <el-select v-model="filters.projectId" clearable style="width: 140px">
            <el-option v-for="p in projects" :key="p.projectId" :label="p.projectName" :value="p.projectId" />
          </el-select>
        </el-form-item>
        <el-form-item label="变更类型">
          <el-select v-model="filters.type" clearable style="width: 120px">
            <el-option v-for="(v, k) in changeTypes" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable style="width: 100px">
            <el-option v-for="(v, k) in changeStatusMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="fetchList">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
          <el-button type="primary" @click="showForm = true" v-if="checkPermi(['pms:change:create'])"><el-icon><Plus /></el-icon> 发起变更</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="filteredList" v-loading="loading" border size="small" @row-click="openDetail">
        <el-table-column prop="changeNumber" label="编号" width="100" />
        <el-table-column prop="title" label="变更标题" min-width="160" />
        <el-table-column prop="projectName" label="项目" width="100" />
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :color="changeTypes[row.type]?.color" effect="dark" size="small">{{ changeTypes[row.type]?.label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :color="changeStatusMap[row.status]?.color" effect="dark" size="small">{{ changeStatusMap[row.status]?.label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="applicant" label="申请人" width="80" />
        <el-table-column prop="approver" label="审批人" width="80" />
        <el-table-column label="申请时间" width="100">
          <template #default="{ row }">{{ formatDate(row.applyTime) }}</template>
        </el-table-column>
      </el-table>
    </ContentWrap>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawerVisible" size="720px" :show-close="false">
      <template #header>
        <div class="drawer-header">
          <el-tag :color="changeTypes[selected?.type]?.color" effect="dark">{{ changeTypes[selected?.type]?.label }}</el-tag>
          <el-tag :color="changeStatusMap[selected?.status]?.color" effect="dark">{{ changeStatusMap[selected?.status]?.label }}</el-tag>
          <span class="title">{{ selected?.title }}</span>
          <el-button link @click="drawerVisible = false"><el-icon><Close /></el-icon></el-button>
        </div>
      </template>
      <template v-if="selected">
        <el-descriptions title="变更信息" :column="2" border>
          <el-descriptions-item label="变更编号">{{ selected.changeNumber }}</el-descriptions-item>
          <el-descriptions-item label="变更类型">{{ changeTypes[selected.type]?.label }}</el-descriptions-item>
          <el-descriptions-item label="所属项目">{{ selected.projectName }}</el-descriptions-item>
          <el-descriptions-item label="关联任务">{{ selected.taskName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ selected.applicant }}</el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ formatDate(selected.applyTime) }}</el-descriptions-item>
          <el-descriptions-item label="审批人">{{ selected.approver || '待指派' }}</el-descriptions-item>
          <el-descriptions-item label="紧急程度">
            <el-tag :color="selected.urgent ? '#FF7D00' : '#86909C'" effect="dark" size="small">{{ selected.urgent ? '紧急' : '普通' }}</el-tag>
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
        </ul>

        <div class="section-title">审批流程</div>
        <el-timeline>
          <el-timeline-item timestamp="申请" :type="'success'">{{ selected.applicant }} 提交变更申请</el-timeline-item>
          <el-timeline-item v-if="selected.status !== 'draft'" timestamp="审批" :type="selected.status === 'approved' ? 'success' : selected.status === 'rejected' ? 'danger' : 'primary'">
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
        <el-form-item label="变更标题" required><el-input v-model="newChange.title" /></el-form-item>
        <el-form-item label="所属项目" required>
          <el-select v-model="newChange.projectId" placeholder="选择项目" filterable style="width: 100%">
            <el-option v-for="p in projects" :key="p.projectId" :label="p.projectName" :value="p.projectId" />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="变更类型"><el-select v-model="newChange.type" class="w-full"><el-option v-for="(v,k) in changeTypes" :key="k" :label="v.label" :value="k" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="紧急程度"><el-switch v-model="newChange.urgent" active-text="紧急" inactive-text="普通" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="变更前内容"><el-input v-model="newChange.beforeContent" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="变更后内容"><el-input v-model="newChange.afterContent" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="变更原因"><el-input v-model="newChange.reason" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showForm = false">取消</el-button><el-button type="primary" @click="submitChange" :loading="saving">提交</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Close } from '@element-plus/icons-vue'
import { getProjectList, ProjectVO } from '@/api/pms/project'
import { getChangeRecordList, createChangeRecord, updateChangeRecord, deleteChangeRecord, ChangeRecordVO } from '@/api/pms/change'
import { formatDate } from '../pms-utils'
import { checkPermi } from '@/utils/permission'
import { useUserNames } from '@/hooks/pms/useUserNames'

defineOptions({ name: 'PmsChange' })

const { getUserName, ensureLoaded: ensureUsersLoaded } = useUserNames()
const loading = ref(false)
const saving = ref(false)
const filters = reactive({ number: '', projectId: '', type: '', status: '' })
const projects = ref<ProjectVO[]>([])
const drawerVisible = ref(false)
const selected = ref<any>(null)
const showForm = ref(false)
const approveResult = ref('approve')
const approveOpinion = ref('')
const autoAdjust = ref(true)

const changeTypes: Record<string, { label: string; color: string }> = {
  schedule: { label: '工期变更', color: '#2468F2' },
  content: { label: '内容变更', color: '#722ED1' },
  material: { label: '物料变更', color: '#FF7D00' },
  personnel: { label: '人员变更', color: '#0FC6C2' },
  cost: { label: '成本变更', color: '#00B42A' }
}

const changeStatusMap: Record<string, { label: string; color: string }> = {
  draft: { label: '草稿', color: '#86909C' },
  pending: { label: '待审批', color: '#FF7D00' },
  approving: { label: '审批中', color: '#2468F2' },
  approved: { label: '已通过', color: '#00B42A' },
  rejected: { label: '已驳回', color: '#F53F3F' },
  revoked: { label: '已撤销', color: '#86909C' }
}

// ==================== 数据 ====================
const changeList = ref<any[]>([])

const getProjectName = (projectId?: string | number) =>
  projects.value.find(p => String(p.projectId) === String(projectId))?.projectName || '-'

const filteredList = computed(() => {
  let r = changeList.value
  if (filters.number) r = r.filter(c => (c.changeNumber || '').includes(filters.number))
  if (filters.projectId) r = r.filter(c => String(c.projectId) === String(filters.projectId))
  if (filters.type) r = r.filter(c => c.type === filters.type)
  if (filters.status) r = r.filter(c => c.status === filters.status)
  return r
})

const newChange = reactive({ title: '', type: 'schedule', urgent: false, beforeContent: '', afterContent: '', reason: '', projectId: '' })

// ==================== 数据加载 ====================
const fetchList = async () => {
  loading.value = true
  try {
    const res = await getChangeRecordList()
    changeList.value = (res || []).map((item: any) => ({
      ...item,
      changeNumber: item.changeCode || '',
      title: item.changeDescription || '',
      projectName: getProjectName(item.projectId),
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
        ...(item.costImpact ? [`成本影响: ${item.costImpact}元`] : []),
        ...(item.scheduleImpact ? [`工期影响: ${item.scheduleImpact}天`] : [])
      ],
      taskName: item.affectedTasks || ''
    }))
  } catch {
    changeList.value = []
  } finally {
    loading.value = false
  }
}

// ==================== 操作函数 ====================
function openDetail(row: any) { selected.value = row; drawerVisible.value = true }
function resetFilters() { Object.assign(filters, { number: '', projectId: '', type: '', status: '' }) }

async function submitChange() {
  if (!newChange.title) { ElMessage.warning('请填写变更标题'); return }
  if (!newChange.projectId) { ElMessage.warning('请选择所属项目'); return }
  saving.value = true
  try {
    await createChangeRecord({
      changeDescription: newChange.title,
      changeType: newChange.type,
      changeReason: newChange.reason,
      projectId: String(newChange.projectId),
      beforeContent: newChange.beforeContent,
      afterContent: newChange.afterContent,
      urgent: newChange.urgent,
      scheduleImpact: newChange.scheduleImpact,
      changeCode: `CR-${Date.now().toString().slice(-6)}`,
      approvalStatus: 'pending'
    } as ChangeRecordVO)
    ElMessage.success('变更已提交')
    showForm.value = false
    Object.assign(newChange, { title: '', type: 'schedule', urgent: false, beforeContent: '', afterContent: '', reason: '', projectId: '' })
    await fetchList()
  } catch {
    ElMessage.error('提交失败')
  } finally {
    saving.value = false
  }
}

async function submitApproval() {
  if (!selected.value) return
  try {
    await ElMessageBox.confirm('确认提交审批结果？', '提示', { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' })
  } catch { return }
  saving.value = true
  try {
    const newStatus = approveResult.value === 'approve' ? 'approved' : approveResult.value === 'reject' ? 'rejected' : 'pending'
    await updateChangeRecord({
      changeId: selected.value.changeId,
      approvalStatus: newStatus,
      approveOpinion: approveOpinion.value,
      autoAdjust: autoAdjust.value
    } as ChangeRecordVO)
    ElMessage.success('审批已提交')
    drawerVisible.value = false
    await fetchList()
  } catch {
    ElMessage.error('审批失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(id: number) {
  try {
    await ElMessageBox.confirm('确认删除该变更记录？', '提示', { type: 'warning' })
    await deleteChangeRecord(id)
    ElMessage.success('删除成功')
    await fetchList()
  } catch {
    // cancelled or error
  }
}

// ==================== 初始化 ====================
async function loadData() {
  try {
    projects.value = (await getProjectList()) as ProjectVO[]
  } catch (e) { console.error(e) }
  await ensureUsersLoaded()
  await fetchList()
}
onMounted(() => { loadData() })
</script>

<style scoped lang="scss">
.pms-change {
  .drawer-header { display: flex; align-items: center; gap: 8px; width: 100%; .title { font-size: 16px; font-weight: 600; flex: 1; } }
  .section-title { font-size: 14px; font-weight: 600; margin: 20px 0 10px; padding-left: 8px; border-left: 3px solid var(--el-color-primary); }
  .compare-block { border: 1px solid var(--el-border-color); border-radius: 4px; padding: 12px; min-height: 120px;
    &.before { background: #FFF7E8; .compare-label { color: #FF7D00; } }
    &.after { background: #E8FFEA; .compare-label { color: #00B42A; } }
    .compare-label { font-weight: 600; margin-bottom: 8px; }
    pre { white-space: pre-wrap; word-break: break-all; margin: 0; font-size: 13px; }
  }
  .reason-text { font-size: 14px; line-height: 1.6; margin: 0; }
  .impact-list { padding-left: 20px; li { font-size: 14px; line-height: 1.8; } }
}
</style>
