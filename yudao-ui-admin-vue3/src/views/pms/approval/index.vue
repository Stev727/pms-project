<template>
  <div class="pms-approval">
    <ContentWrap>
      <el-form :inline="true" :model="filters">
        <el-form-item label="项目">
          <el-select v-model="filters.projectId" clearable style="width: 160px">
            <el-option v-for="p in projects" :key="p.projectId" :label="p.projectName" :value="p.projectId" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filters.type" clearable style="width: 120px">
            <el-option label="立项审批" value="project_init" />
            <el-option label="开模审批" value="mold_open" />
            <el-option label="设计评审" value="design_review" />
            <el-option label="试产转量产" value="trial_to_mass" />
            <el-option label="变更审批" value="change" />
            <el-option label="质量审批" value="quality" />
            <el-option label="延期审批" value="delay" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable style="width: 100px">
            <el-option label="待审批" value="pending" /><el-option label="已通过" value="approved" /><el-option label="已驳回" value="rejected" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="loadData">查询</el-button>
          <el-button type="primary" @click="showCreateDialog = true" v-if="checkPermi(['pms:approval:create'])">
            <Icon icon="ep:plus" /> 发起审批
          </el-button>
        </el-form-item>
      </el-form>

      <el-table :data="filteredList" border size="small" v-loading="loading">
        <el-table-column prop="approvalNo" label="审批事项" min-width="200" />
        <el-table-column label="项目" width="100">
          <template #default="{ row }">{{ getProjectName(row.projectId) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }"><el-tag size="small" :type="getTypeTag(row.approvalType)">{{ getTypeLabel(row.approvalType) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="申请人" width="80">
          <template #default="{ row }">{{ getUserName(row.initiatorId) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }"><el-tag size="small" :color="getStatusColor(row.approvalStatus)" effect="dark">{{ getStatusLabel(row.approvalStatus) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="approverName" label="审批人" width="80" />
        <el-table-column label="提交时间" width="110"><template #default="{ row }">{{ formatDate(row.initiateTime) }}</template></el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button v-if="row.approvalStatus === 'pending' && checkPermi(['pms:approval:update'])" link type="success" size="small" @click="doApprove(row, true)">通过</el-button>
            <el-button v-if="row.approvalStatus === 'pending' && checkPermi(['pms:approval:update'])" link type="danger" size="small" @click="doApprove(row, false)">驳回</el-button>
            <el-button v-else link type="primary" size="small" @click="viewDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </ContentWrap>

    <!-- 发起审批弹窗 -->
    <el-dialog v-model="showCreateDialog" title="发起审批" width="560px">
      <el-form label-width="100px">
        <el-form-item label="审批类型" required>
          <el-select v-model="createForm.approvalType" placeholder="请选择" class="w-full">
            <el-option label="立项审批" value="project_init" />
            <el-option label="开模审批" value="mold_open" />
            <el-option label="设计评审" value="design_review" />
            <el-option label="试产转量产" value="trial_to_mass" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联项目" required>
          <el-select v-model="createForm.projectId" filterable placeholder="选择项目" class="w-full">
            <el-option v-for="p in projects" :key="p.projectId" :label="p.projectName" :value="p.projectId" />
          </el-select>
        </el-form-item>
        <el-form-item label="审批事项" required>
          <el-input v-model="createForm.approvalTitle" placeholder="请输入审批事项" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="submitCreateApproval" :loading="creating">提交审批</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getProjectList, ProjectVO } from '@/api/pms/project'
import { getApprovalRecordList, createApprovalRecord, updateApprovalRecord, deleteApprovalRecord } from '@/api/pms/approval'
import { formatDate } from '../pms-utils'
import { checkPermi } from '@/utils/permission'
import { useUserNames } from '@/hooks/pms/useUserNames'

defineOptions({ name: 'PmsApproval' })

const { getUserName, ensureLoaded: ensureUsersLoaded } = useUserNames()
const filters = reactive({ projectId: '' as '' | number, type: '', status: '' })
const projects = ref<ProjectVO[]>([])
const loading = ref(false)
const tableData = ref<any[]>([])

// 发起审批
const showCreateDialog = ref(false)
const creating = ref(false)
const createForm = reactive({
  approvalType: 'project_init',
  projectId: '' as string | number,
  approvalTitle: '',
  remark: ''
})

const submitCreateApproval = async () => {
  if (!createForm.approvalType) { ElMessage.warning('请选择审批类型'); return }
  if (!createForm.projectId) { ElMessage.warning('请选择关联项目'); return }
  if (!createForm.approvalTitle) { ElMessage.warning('请输入审批事项'); return }
  creating.value = true
  try {
    await createApprovalRecord({
      approvalType: createForm.approvalType,
      projectId: String(createForm.projectId),
      approvalNo: createForm.approvalTitle,
      remark: createForm.remark,
      approvalStatus: 'pending'
    } as any)
    ElMessage.success('审批已发起，将推送至OA系统')
    showCreateDialog.value = false
    Object.assign(createForm, { approvalType: 'project_init', projectId: '', approvalTitle: '', remark: '' })
    await loadData()
  } catch (e) {
    ElMessage.error('发起审批失败')
  } finally {
    creating.value = false
  }
}

const filteredList = computed(() => {
  let r = tableData.value
  if (filters.projectId) r = r.filter(a => a.projectId === filters.projectId)
  if (filters.type) r = r.filter(a => a.approvalType === filters.type)
  if (filters.status) r = r.filter(a => a.approvalStatus === filters.status)
  return r
})

function getProjectName(projectId?: number): string {
  if (!projectId) return '-'
  const p = projects.value.find(p => p.projectId === projectId)
  return p?.projectName || '-'
}

function getTypeLabel(t: string): string { return { change: '变更', quality: '质量', delay: '延期', project_init: '立项', mold_open: '开模', design_review: '设计评审', trial_to_mass: '试产转量产' }[t] || t }
function getTypeTag(t: string): string { return { change: 'warning', quality: 'danger', delay: '', project_init: 'success', mold_open: 'info', design_review: 'primary', trial_to_mass: 'success' }[t] || 'info' }
function getStatusColor(s: string): string { return { pending: '#FF7D00', approved: '#00B42A', rejected: '#F53F3F' }[s] || '#86909C' }
function getStatusLabel(s: string): string { return { pending: '待审批', approved: '已通过', rejected: '已驳回' }[s] || s }

async function doApprove(row: any, approve: boolean) {
  try {
    await ElMessageBox.confirm(
      `确认${approve ? '通过' : '驳回'}该审批？`,
      '提示',
      { confirmButtonText: `确认${approve ? '通过' : '驳回'}`, cancelButtonText: '取消', type: 'warning' }
    )
    await updateApprovalRecord({
      ...row,
      approvalStatus: approve ? 'approved' : 'rejected'
    })
    row.approvalStatus = approve ? 'approved' : 'rejected'
    ElMessage.success(`已${approve ? '通过' : '驳回'}`)
  } catch (e) {
    if (e !== 'cancel') {
      console.error(e)
      ElMessage.error('操作失败')
    }
  }
}

function viewDetail(row: any) {
  ElMessage.info(`审批编号：${row.approvalNo || '-'}\n类型：${getTypeLabel(row.approvalType)}\n状态：${getStatusLabel(row.approvalStatus)}\n审批人：${row.approverName || '-'}`)
}

async function loadData() {
  loading.value = true
  try {
    const [projectRes, approvalRes] = await Promise.all([
      getProjectList(),
      getApprovalRecordList()
    ])
    projects.value = projectRes as ProjectVO[]
    await ensureUsersLoaded()
    tableData.value = (approvalRes as any[]) || []
  } catch (e) {
    console.error(e)
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => { loadData() })
</script>

<style scoped lang="scss">.pms-approval {}</style>
