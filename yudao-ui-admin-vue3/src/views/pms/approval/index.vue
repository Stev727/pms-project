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
            <el-option label="变更审批" value="change" /><el-option label="质量审批" value="quality" /><el-option label="延期审批" value="delay" /><el-option label="立项审批" value="project_init" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable style="width: 100px">
            <el-option label="待审批" value="pending" /><el-option label="已通过" value="approved" /><el-option label="已驳回" value="rejected" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button @click="loadData">查询</el-button></el-form-item>
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
          <template #default="{ row }">{{ row.initiatorId || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }"><el-tag size="small" :color="getStatusColor(row.approvalStatus)" effect="dark">{{ getStatusLabel(row.approvalStatus) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="approverName" label="审批人" width="80" />
        <el-table-column label="提交时间" width="110"><template #default="{ row }">{{ formatDate(row.initiateTime) }}</template></el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button v-if="row.approvalStatus === 'pending'" link type="success" size="small" @click="doApprove(row, true)">通过</el-button>
            <el-button v-if="row.approvalStatus === 'pending'" link type="danger" size="small" @click="doApprove(row, false)">驳回</el-button>
            <el-button v-else link type="primary" size="small" @click="viewDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </ContentWrap>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getProjectList, ProjectVO } from '@/api/pms/project'
import { getApprovalRecordList, createApprovalRecord, updateApprovalRecord, deleteApprovalRecord } from '@/api/pms/approval'
import { formatDate } from '../pms-utils'
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'PmsApproval' })

const filters = reactive({ projectId: '' as '' | number, type: '', status: '' })
const projects = ref<ProjectVO[]>([])
const loading = ref(false)
const tableData = ref<any[]>([])

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

function getTypeLabel(t: string): string { return { change: '变更', quality: '质量', delay: '延期', project_init: '立项' }[t] || t }
function getTypeTag(t: string): string { return { change: 'warning', quality: 'danger', delay: '', project_init: 'success' }[t] || 'info' }
function getStatusColor(s: string): string { return { pending: '#FF7D00', approved: '#00B42A', rejected: '#F53F3F' }[s] || '#86909C' }
function getStatusLabel(s: string): string { return { pending: '待审批', approved: '已通过', rejected: '已驳回' }[s] || s }

async function doApprove(row: any, approve: boolean) {
  try {
    await updateApprovalRecord({
      ...row,
      approvalStatus: approve ? 'approved' : 'rejected'
    })
    row.approvalStatus = approve ? 'approved' : 'rejected'
    ElMessage.success(`已${approve ? '通过' : '驳回'}`)
  } catch (e) {
    console.error(e)
    ElMessage.error('操作失败')
  }
}

function viewDetail(row: any) { ElMessage.info(`查看审批详情：${row.approvalNo}`) }

async function loadData() {
  loading.value = true
  try {
    const [projectRes, approvalRes] = await Promise.all([
      getProjectList(),
      getApprovalRecordList()
    ])
    projects.value = projectRes as ProjectVO[]
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
