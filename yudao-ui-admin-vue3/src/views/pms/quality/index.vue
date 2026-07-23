<template>
  <div class="pms-quality">
    <!-- 筛选栏 -->
    <ContentWrap>
      <el-form :model="queryParams" :inline="true" class="mb-0">
        <el-form-item label="问题编号">
          <el-input v-model="queryParams.issueNo" placeholder="请输入" clearable style="width: 140px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="项目">
          <el-select v-model="queryParams.projectId" placeholder="全部项目" clearable filterable style="width: 180px" @change="handleSearch">
            <el-option v-for="p in projectList" :key="p.projectId" :label="p.projectName" :value="p.projectId" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="严重度">
          <el-select v-model="queryParams.severity" placeholder="全部" clearable style="width: 110px">
            <el-option v-for="s in severityOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="queryParams.owner" placeholder="请输入" clearable style="width: 120px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="queryParams.dateRange" type="daterange" range-separator="至" start-placeholder="开始" end-placeholder="结束"
            value-format="YYYY-MM-DD" style="width: 240px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><Icon icon="ep:search" class="mr-5px" />查询</el-button>
          <el-button @click="handleReset"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        </el-form-item>
      </el-form>
    </ContentWrap>

    <!-- 问题列表 -->
    <ContentWrap>
      <div class="table-toolbar">
        <el-button type="primary" @click="dialogVisible = true" v-if="checkPermi(['pms:quality:create'])">
          <Icon icon="ep:plus" class="mr-5px" />新建质量问题
        </el-button>
        <el-button @click="batchClose" :disabled="!selectedRows.length" v-if="checkPermi(['pms:quality:update'])">
          <Icon icon="ep:circle-close" class="mr-5px" />批量关闭
        </el-button>
      </div>
      <el-table :data="filteredList" v-loading="loading" border stripe @selection-change="onSelectionChange" @row-click="openDetail">
        <el-table-column type="selection" width="42" />
        <el-table-column prop="issueNo" label="问题编号" width="120" />
        <el-table-column prop="title" label="问题标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="所属项目" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ getProjectName(row.projectId) }}</template>
        </el-table-column>
        <el-table-column label="严重度" width="90" align="center">
          <template #default="{ row }">
            <el-tag :color="severityMap[row.severity]?.color" effect="dark" size="small">{{ severityMap[row.severity]?.label || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :color="qualityStatusMap[row.status]?.color" effect="light" size="small"
              :style="{ color: qualityStatusMap[row.status]?.color, borderColor: qualityStatusMap[row.status]?.color }">
              {{ qualityStatusMap[row.status]?.label || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="90">
          <template #default="{ row }">{{ row.source || '-' }}</template>
        </el-table-column>
        <el-table-column prop="owner" label="负责人" width="90" />
        <el-table-column label="发现日期" width="110">
          <template #default="{ row }">{{ formatDate(row.foundDate) }}</template>
        </el-table-column>
      </el-table>
    </ContentWrap>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawerVisible" title="质量问题详情" direction="rtl" size="720px">
      <template v-if="currentIssue">
        <el-descriptions :column="2" border size="small" title="基本信息">
          <el-descriptions-item label="问题编号">{{ currentIssue.issueNo }}</el-descriptions-item>
          <el-descriptions-item label="问题标题">{{ currentIssue.title }}</el-descriptions-item>
          <el-descriptions-item label="所属项目">{{ getProjectName(currentIssue.projectId) }}</el-descriptions-item>
          <el-descriptions-item label="严重度">
            <el-tag :color="severityMap[currentIssue.severity]?.color" effect="dark" size="small">{{ severityMap[currentIssue.severity]?.label }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :style="{ color: qualityStatusMap[currentIssue.status]?.color, borderColor: qualityStatusMap[currentIssue.status]?.color }" effect="light" size="small">
              {{ qualityStatusMap[currentIssue.status]?.label }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="负责人">{{ currentIssue.owner }}</el-descriptions-item>
          <el-descriptions-item label="发现日期">{{ formatDate(currentIssue.foundDate) }}</el-descriptions-item>
          <el-descriptions-item label="关联阶段">
            <el-tag :color="phaseColorMap[currentIssue.phase]?.border" effect="plain" size="small">{{ phaseColorMap[currentIssue.phase]?.label || currentIssue.phase || '-' }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <div class="section-title">问题描述</div>
        <div class="problem-desc">{{ currentIssue.description }}</div>

        <div class="section-title">处理时间线</div>
        <el-timeline>
          <el-timeline-item v-for="(log, i) in (currentIssue.timeline || [])" :key="i" :timestamp="formatDate(log.time, 'YYYY-MM-DD HH:mm')" placement="top"
            :color="qualityStatusMap[log.status]?.color || '#2468F2'">
            <el-card shadow="never" class="timeline-card">
              <div class="timeline-header">
                <el-tag :style="{ color: qualityStatusMap[log.status]?.color, borderColor: qualityStatusMap[log.status]?.color }" effect="light" size="small">
                  {{ qualityStatusMap[log.status]?.label }}
                </el-tag>
                <span class="timeline-operator">{{ log.operator }}</span>
              </div>
              <div class="timeline-content">{{ log.content }}</div>
            </el-card>
          </el-timeline-item>
        </el-timeline>

        <!-- 处理操作表单 -->
        <div class="section-title">处理操作</div>
        <el-form :model="actionForm" label-width="80px" class="action-form">
          <el-form-item label="操作类型">
            <el-select v-model="actionForm.action" placeholder="选择操作" style="width: 100%">
              <el-option v-for="a in availableActions" :key="a.value" :label="a.label" :value="a.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="处理说明">
            <el-input v-model="actionForm.description" type="textarea" :rows="3" placeholder="请输入处理说明" />
          </el-form-item>
          <el-form-item label="附件">
            <el-upload :auto-upload="false" :limit="5" :file-list="actionForm.files">
              <el-button><Icon icon="ep:upload" class="mr-5px" />选择文件</el-button>
            </el-upload>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="submitAction" :disabled="!actionForm.action" :loading="saving">提交</el-button>
          </el-form-item>
        </el-form>
      </template>
    </el-drawer>

    <!-- 新建对话框 -->
    <el-dialog v-model="dialogVisible" title="新建质量问题" width="560px">
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="问题标题" required>
          <el-input v-model="createForm.title" placeholder="请输入问题标题" />
        </el-form-item>
        <el-form-item label="所属项目" required>
          <el-select v-model="createForm.projectId" placeholder="选择项目" filterable style="width: 100%">
            <el-option v-for="p in projectList" :key="p.projectId" :label="p.projectName" :value="p.projectId" />
          </el-select>
        </el-form-item>
        <el-form-item label="严重度" required>
          <el-radio-group v-model="createForm.severity">
            <el-radio-button v-for="s in severityOptions" :key="s.value" :value="s.value">{{ s.label }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="createForm.owner" filterable clearable placeholder="请选择负责人" style="width: 100%">
            <el-option v-for="u in userList" :key="u.id" :label="u.nickname" :value="String(u.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="问题描述" required>
          <el-input v-model="createForm.description" type="textarea" :rows="4" placeholder="请描述质量问题详情" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate" :loading="saving">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getProjectList, ProjectVO } from '@/api/pms/project'
import { getQualityIssueList, createQualityIssue, updateQualityIssue, deleteQualityIssue, QualityIssueVO } from '@/api/pms/quality'
import { formatDate, taskStatusMap, phaseColorMap } from '../pms-utils'
import { checkPermi } from '@/utils/permission'
import { useUserNames } from '@/hooks/pms/useUserNames'

defineOptions({ name: 'PmsQuality' })

const message = useMessage()
const { userList, getUserName, ensureLoaded: ensureUsersLoaded } = useUserNames()
const loading = ref(false)
const saving = ref(false)
const projectList = ref<ProjectVO[]>([])

// ==================== 常量映射 ====================
const severityMap: Record<string, { label: string; color: string }> = {
  severe: { label: '严重', color: '#F53F3F' },
  moderate: { label: '一般', color: '#FF7D00' },
  minor: { label: '轻微', color: '#FFD700' }
}
const severityOptions = Object.entries(severityMap).map(([value, { label }]) => ({ value, label }))

const qualityStatusMap: Record<string, { label: string; color: string }> = {
  new: { label: '新建', color: '#86909C' },
  assigned: { label: '已指派', color: '#2468F2' },
  processing: { label: '处理中', color: '#2468F2' },
  pending_verify: { label: '待验证', color: '#FF7D00' },
  closed: { label: '已关闭', color: '#00B42A' }
}
const statusOptions = Object.entries(qualityStatusMap).map(([value, { label }]) => ({ value, label }))

// ==================== 数据 ====================
const qualityList = ref<any[]>([])
const queryParams = reactive({ issueNo: '', projectId: '', status: '', severity: '', owner: '', dateRange: [] as string[] })

const filteredList = computed(() => qualityList.value.filter(i => {
  if (queryParams.issueNo && !(i.issueNo || '').includes(queryParams.issueNo)) return false
  if (queryParams.projectId && String(i.projectId) !== String(queryParams.projectId)) return false
  if (queryParams.status && i.status !== queryParams.status) return false
  if (queryParams.severity && i.severity !== queryParams.severity) return false
  if (queryParams.owner && !(i.owner || '').includes(queryParams.owner)) return false
  if (queryParams.dateRange?.length === 2) {
    const d = i.foundDate || ''
    if (d < queryParams.dateRange[0] || d > queryParams.dateRange[1]) return false
  }
  return true
}))

const handleSearch = () => { fetchList() }
const handleReset = () => Object.assign(queryParams, { issueNo: '', projectId: '', status: '', severity: '', owner: '', dateRange: [] })

const selectedRows = ref<any[]>([])
const onSelectionChange = (rows: any[]) => { selectedRows.value = rows }

// ==================== 数据加载 ====================
const fetchList = async () => {
  loading.value = true
  try {
    const res = await getQualityIssueList()
    qualityList.value = (res || []).map((item: any) => ({
      ...item,
      issueNo: item.issueCode || '',
      title: item.issueDescription || '',
      owner: item.responsiblePerson || (item.assigneeId ? getUserName(item.assigneeId) : (item.resolverId ? getUserName(item.resolverId) : '-')),
      foundDate: item.createTime || '',
      phase: item.rootCauseCategory || '',
      description: item.impactScope || item.rootCauseDetail || '',
      timeline: item.timeline || item.historyList || []
    }))
  } catch {
    qualityList.value = []
  } finally {
    loading.value = false
  }
}

// ==================== 批量关闭 ====================
const batchClose = async () => {
  if (!selectedRows.value.length) return
  try {
    await ElMessageBox.confirm(`确认关闭选中的 ${selectedRows.value.length} 个问题？`, '批量关闭', { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' })
  } catch { return }
  saving.value = true
  try {
    const results = await Promise.allSettled(
      selectedRows.value.map(row => updateQualityIssue({ issueId: row.issueId, status: 'closed' } as QualityIssueVO))
    )
    const succeeded = results.filter(r => r.status === 'fulfilled').length
    const failed = results.filter(r => r.status === 'rejected').length
    if (failed > 0) {
      message.warning(`已关闭 ${succeeded} 个，${failed} 个失败`)
    } else {
      message.success(`已关闭 ${succeeded} 个问题`)
    }
    await fetchList()
  } catch {
    message.error('批量关闭失败')
  } finally {
    saving.value = false
  }
}

// ==================== 详情抽屉 ====================
const drawerVisible = ref(false)
const currentIssue = ref<any>(null)

const openDetail = (row: any) => {
  currentIssue.value = row
  Object.assign(actionForm, { action: '', description: '', files: [] })
  drawerVisible.value = true
}

// 状态流转: new -> assigned -> processing -> pending_verify -> closed
const actionFlowMap: Record<string, { value: string; label: string }[]> = {
  new: [{ value: 'assigned', label: '指派负责人' }],
  assigned: [{ value: 'processing', label: '开始处理' }],
  processing: [{ value: 'pending_verify', label: '提交验证' }],
  pending_verify: [{ value: 'closed', label: '验证通过并关闭' }, { value: 'processing', label: '验证不通过，退回处理' }],
  closed: []
}
const availableActions = computed(() => currentIssue.value?.status ? (actionFlowMap[currentIssue.value.status] || []) : [])
const actionForm = reactive({ action: '', description: '', files: [] as any[] })

const submitAction = async () => {
  if (!actionForm.action || !currentIssue.value) return
  saving.value = true
  try {
    // Extract raw file objects from upload component file-list
    const attachments = (actionForm.files || []).map((f: any) => f.raw || f).filter(Boolean)
    await updateQualityIssue({
      issueId: currentIssue.value.issueId,
      status: actionForm.action,
      solution: actionForm.description,
      attachments: attachments.length > 0 ? attachments : undefined
    } as QualityIssueVO)
    message.success('操作已提交')
    Object.assign(actionForm, { action: '', description: '', files: [] })
    await fetchList()
    const updated = qualityList.value.find(q => q.issueId === currentIssue.value.issueId)
    if (updated) currentIssue.value = updated
  } catch {
    message.error('操作失败')
  } finally {
    saving.value = false
  }
}

// ==================== 新建对话框 ====================
const dialogVisible = ref(false)
const createForm = reactive({ title: '', projectId: '', severity: 'moderate', owner: '', description: '' })

const submitCreate = async () => {
  if (!createForm.title || !createForm.projectId || !createForm.description) {
    message.warning('请填写必填项'); return
  }
  saving.value = true
  try {
    await createQualityIssue({
      projectId: createForm.projectId,
      issueDescription: createForm.title,
      severity: createForm.severity,
      assigneeId: createForm.owner ? Number(createForm.owner) : undefined,
      responsiblePerson: createForm.owner || '',
      impactScope: createForm.description,
      status: 'new'
    } as QualityIssueVO)
    message.success('质量问题已创建')
    dialogVisible.value = false
    Object.assign(createForm, { title: '', projectId: '', severity: 'moderate', owner: '', description: '' })
    await fetchList()
  } catch {
    message.error('创建失败')
  } finally {
    saving.value = false
  }
}

// ==================== 删除 ====================
const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确认删除该质量问题？', '提示', { type: 'warning' })
    await deleteQualityIssue(id)
    message.success('删除成功')
    await fetchList()
  } catch {
    // cancelled or error
  }
}

const getProjectName = (projectId?: string | number) =>
  projectList.value.find(p => String(p.projectId) === String(projectId))?.projectName || '-'

// ==================== 初始化 ====================
onMounted(async () => {
  await ensureUsersLoaded()
  fetchList()
  try {
    projectList.value = (await getProjectList()) as ProjectVO[]
  } catch {
    projectList.value = []
  }
})
</script>

<style scoped lang="scss">
.pms-quality {
  .table-toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
  .section-title {
    font-size: 15px; font-weight: 600; color: #1D2129;
    margin: 20px 0 12px; padding-left: 8px;
    border-left: 3px solid #2468F2;
  }
  .problem-desc {
    background: #F7F8FA; border-radius: 6px; padding: 12px 16px;
    font-size: 14px; color: #4E5969; line-height: 1.7;
  }
  .timeline-card {
    .timeline-header { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
    .timeline-operator { font-size: 13px; color: #86909C; }
    .timeline-content { font-size: 13px; color: #4E5969; }
  }
  .action-form { margin-top: 4px; }
}
</style>
