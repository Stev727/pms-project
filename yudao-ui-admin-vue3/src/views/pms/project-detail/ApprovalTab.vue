<template>
  <div class="approval-tab">
    <div class="tab-toolbar">
      <span class="toolbar-title">审批记录 ({{ filteredList.length }})</span>
      <div style="display: flex; gap: 8px">
        <el-select v-model="filterType" placeholder="审批类型" clearable size="small" style="width: 120px">
          <el-option label="立项审批" value="project_init" />
          <el-option label="开模审批" value="mold_open" />
          <el-option label="设计评审" value="design_review" />
          <el-option label="试产转量产" value="trial_to_mass" />
          <el-option label="变更审批" value="change" />
          <el-option label="质量审批" value="quality" />
          <el-option label="延期审批" value="delay" />
        </el-select>
        <el-select v-model="filterStatus" placeholder="状态" clearable size="small" style="width: 100px">
          <el-option label="待审批" value="pending" />
          <el-option label="已通过" value="approved" />
          <el-option label="已驳回" value="rejected" />
        </el-select>
      </div>
    </div>

    <el-table :data="filteredList" border stripe v-loading="loading">
      <el-table-column prop="approvalNo" label="审批编号" width="160" />
      <el-table-column prop="approvalTitle" label="审批事项" min-width="200" show-overflow-tooltip />
      <el-table-column label="审批类型" width="110" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="getTypeTag(row.approvalType)">{{ getTypeLabel(row.approvalType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="发起人" width="100">
        <template #default="{ row }">{{ row.initiatorName || row.initiatorId || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :color="getStatusColor(row.approvalStatus)" effect="dark">
            {{ getStatusLabel(row.approvalStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="审批人" width="100">
        <template #default="{ row }">{{ row.approverName || '-' }}</template>
      </el-table-column>
      <el-table-column label="提交时间" width="160">
        <template #default="{ row }">{{ formatDate(row.initiateTime) }}</template>
      </el-table-column>
      <el-table-column label="完成时间" width="160">
        <template #default="{ row }">{{ formatDate(row.completeTime) }}</template>
      </el-table-column>
      <el-table-column label="OA状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.oaCallbackStatus === 'success'" size="small" type="success">已回调</el-tag>
          <el-tag v-else-if="row.oaCallbackStatus === 'pending'" size="small" type="warning">等待中</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="viewDetail(row)">详情</el-button>
          <template v-if="row.approvalStatus === 'pending'">
            <el-button link type="success" size="small" @click="doApprove(row, true)" v-if="checkPermi(['pms:approval:update'])">通过</el-button>
            <el-button link type="danger" size="small" @click="doApprove(row, false)" v-if="checkPermi(['pms:approval:update'])">驳回</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && filteredList.length === 0" description="暂无审批记录" />

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="detailData?.approvalNo || '审批详情'" width="640px">
      <template v-if="detailData">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="审批编号">{{ detailData.approvalNo }}</el-descriptions-item>
          <el-descriptions-item label="审批类型">{{ getTypeLabel(detailData.approvalType) }}</el-descriptions-item>
          <el-descriptions-item label="审批事项">{{ detailData.approvalTitle || '-' }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">{{ getStatusLabel(detailData.approvalStatus) }}</el-descriptions-item>
          <el-descriptions-item label="发起人">{{ detailData.initiatorName || detailData.initiatorId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审批人">{{ detailData.approverName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ formatDate(detailData.initiateTime) }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ formatDate(detailData.completeTime) }}</el-descriptions-item>
          <el-descriptions-item label="OA回调状态">{{ detailData.oaCallbackStatus || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审批意见" :span="2">{{ detailData.approvalOpinion || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-dialog>

    <!-- 审批意见输入弹窗 -->
    <el-dialog v-model="approvalCommentVisible" :title="approvalAction ? '通过审批' : '驳回审批'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="审批意见">
          <el-input
            v-model="approvalComment"
            type="textarea"
            :rows="3"
            :placeholder="approvalAction ? '请输入通过理由（可选）' : '请输入驳回原因'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approvalCommentVisible = false">取消</el-button>
        <el-button :type="approvalAction ? 'success' : 'danger'" @click="confirmApproval">
          {{ approvalAction ? '确认通过' : '确认驳回' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getApprovalRecordList, updateApprovalRecord } from '@/api/pms/approval'
import { formatDate } from '../pms-utils'
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'ApprovalTab' })

const props = defineProps<{
  projectId: string
}>()

const loading = ref(false)
const approvalList = ref<any[]>([])
const filterType = ref('')
const filterStatus = ref('')
const detailVisible = ref(false)
const detailData = ref<any>(null)
const approvalCommentVisible = ref(false)
const approvalComment = ref('')
const approvalTarget = ref<any>(null)
const approvalAction = ref(false)

const filteredList = computed(() => {
  let list = approvalList.value
  if (filterType.value) list = list.filter(a => a.approvalType === filterType.value)
  if (filterStatus.value) list = list.filter(a => a.approvalStatus === filterStatus.value)
  return list
})

function getTypeLabel(t: string): string {
  return {
    change: '变更审批', quality: '质量审批', delay: '延期审批',
    project_init: '立项审批', mold_open: '开模审批',
    design_review: '设计评审', trial_to_mass: '试产转量产'
  }[t] || t
}

function getTypeTag(t: string): string {
  return {
    project_init: 'success', mold_open: 'primary', design_review: 'warning',
    trial_to_mass: 'danger', change: 'warning', quality: 'danger', delay: ''
  }[t] || 'info'
}

function getStatusColor(s: string): string {
  return { pending: '#FF7D00', approved: '#00B42A', rejected: '#F53F3F' }[s] || '#86909C'
}

function getStatusLabel(s: string): string {
  return { pending: '待审批', approved: '已通过', rejected: '已驳回' }[s] || s
}

function viewDetail(row: any) {
  detailData.value = row
  detailVisible.value = true
}

async function doApprove(row: any, approve: boolean) {
  try {
    await ElMessageBox.confirm(
      `确认${approve ? '通过' : '驳回'}审批「${row.approvalTitle || row.approvalNo}」？`,
      '操作确认',
      { confirmButtonText: approve ? '通过' : '驳回', cancelButtonText: '取消', type: approve ? 'success' : 'warning' }
    )
    approvalTarget.value = row
    approvalAction.value = approve
    approvalComment.value = ''
    approvalCommentVisible.value = true
  } catch (e) {
    // user cancelled
  }
}

async function confirmApproval() {
  if (!approvalTarget.value) return
  try {
    await updateApprovalRecord({
      ...approvalTarget.value,
      approvalStatus: approvalAction.value ? 'approved' : 'rejected',
      approvalOpinion: approvalComment.value
    })
    approvalCommentVisible.value = false
    ElMessage.success(`已${approvalAction.value ? '通过' : '驳回'}`)
    loadApprovals()
  } catch (e) { console.error(e); ElMessage.error('操作失败') }
}

async function loadApprovals() {
  loading.value = true
  try {
    const data = await getApprovalRecordList({ projectId: props.projectId })
    approvalList.value = ((data as any[]) || []).map(a => ({
      ...a,
      approvalTitle: a.approvalNo || `${getTypeLabel(a.approvalType)}-${a.approvalId}`
    }))
  } catch (e) { console.error(e); approvalList.value = [] }
  finally { loading.value = false }
}

onMounted(() => { loadApprovals() })

defineExpose({ refresh: loadApprovals })
</script>

<style scoped>
.approval-tab { }
.tab-toolbar {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;
}
.toolbar-title { font-size: 14px; font-weight: 600; color: #1D2129; }
</style>
