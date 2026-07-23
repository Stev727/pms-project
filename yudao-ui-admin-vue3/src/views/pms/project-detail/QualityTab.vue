<template>
  <div class="quality-tab">
    <div class="tab-toolbar">
      <span class="toolbar-title">质量问题 ({{ filteredList.length }})</span>
      <div style="display: flex; gap: 8px">
        <el-select v-model="filterSeverity" placeholder="严重程度" clearable size="small" style="width: 110px">
          <el-option label="致命(9-10)" value="fatal" />
          <el-option label="严重(6-8)" value="severe" />
          <el-option label="一般(3-5)" value="minor" />
          <el-option label="建议(1-2)" value="suggestion" />
        </el-select>
        <el-select v-model="filterStatus" placeholder="状态" clearable size="small" style="width: 100px">
          <el-option label="新建" value="new" />
          <el-option label="已指派" value="assigned" />
          <el-option label="处理中" value="processing" />
          <el-option label="待验证" value="pending_verify" />
          <el-option label="已关闭" value="closed" />
        </el-select>
        <el-button type="primary" size="small" @click="showForm = true" v-if="checkPermi(['pms:quality:create'])">
          <Icon icon="ep:plus" class="mr-4px" />录入问题
        </el-button>
      </div>
    </div>

    <el-table :data="filteredList" border stripe v-loading="loading" @row-click="openDetail">
      <el-table-column prop="issueCode" label="编号" width="100" />
      <el-table-column prop="issueDescription" label="问题描述" min-width="200" show-overflow-tooltip />
      <el-table-column label="严重程度" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="getSeverityType(row.severity)">{{ getSeverityLabel(row.severity) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="分类" width="100">
        <template #default="{ row }">{{ getCategoryLabel(row.category) }}</template>
      </el-table-column>
      <el-table-column label="来源" width="80">
        <template #default="{ row }">{{ row.source || '-' }}</template>
      </el-table-column>
      <el-table-column label="责任人" width="90">
        <template #default="{ row }">{{ row.responsiblePerson || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag size="small" :color="getStatusColor(row.status)" effect="dark">{{ getStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="发现日期" width="100">
        <template #default="{ row }">{{ formatDate(row.foundDate, 'MM-DD') }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click.stop="openDetail(row)">详情</el-button>
          <el-button v-if="row.status !== 'closed' && checkPermi(['pms:quality:update'])" link type="success" size="small" @click.stop="closeIssue(row)">关闭</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && filteredList.length === 0" description="暂无质量问题" />

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawerVisible" size="640px" :show-close="false">
      <template #header>
        <div class="drawer-header">
          <el-tag size="small" :type="getSeverityType(selected?.severity)">{{ getSeverityLabel(selected?.severity) }}</el-tag>
          <span class="title">{{ selected?.description }}</span>
          <el-button link @click="drawerVisible = false"><Icon icon="ep:close" /></el-button>
        </div>
      </template>
      <template v-if="selected">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="问题编号">{{ selected.issueCode }}</el-descriptions-item>
          <el-descriptions-item label="严重程度">{{ getSeverityLabel(selected.severity) }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ getCategoryLabel(selected.category) }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ selected.source || '-' }}</el-descriptions-item>
          <el-descriptions-item label="责任人">{{ selected.responsiblePerson || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ getStatusLabel(selected.status) }}</el-descriptions-item>
          <el-descriptions-item label="发现日期">{{ formatDate(selected.foundDate) }}</el-descriptions-item>
          <el-descriptions-item label="关闭日期">{{ formatDate(selected.closeTime) }}</el-descriptions-item>
          <el-descriptions-item label="问题描述" :span="2">{{ selected.issueDescription || '-' }}</el-descriptions-item>
          <el-descriptions-item label="根因分析" :span="2">{{ selected.rootCauseDetail || '-' }}</el-descriptions-item>
          <el-descriptions-item label="解决方案" :span="2">{{ selected.solution || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>

    <!-- 录入弹窗 -->
    <el-dialog v-model="showForm" title="录入质量问题" width="560px">
      <el-form label-width="90px">
        <el-form-item label="问题描述" required><el-input v-model="newIssue.description" type="textarea" :rows="3" /></el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="严重程度"><el-select v-model="newIssue.severity" class="w-full"><el-option label="致命" value="fatal" /><el-option label="严重" value="severe" /><el-option label="一般" value="minor" /><el-option label="建议" value="suggestion" /></el-select></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类"><el-select v-model="newIssue.category" class="w-full"><el-option label="设计问题" value="design" /><el-option label="工艺问题" value="process" /><el-option label="物料问题" value="material" /><el-option label="测试问题" value="testing" /><el-option label="其他" value="other" /></el-select></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="责任人"><el-input v-model="newIssue.responsiblePerson" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="来源"><el-input v-model="newIssue.source" placeholder="测试/评审/现场" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="根因分析"><el-input v-model="newIssue.rootCause" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="解决方案"><el-input v-model="newIssue.solution" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showForm = false">取消</el-button><el-button type="primary" @click="submitIssue" :loading="saving">提交</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getQualityIssueList, createQualityIssue, updateQualityIssue } from '@/api/pms/quality'
import { formatDate } from '../pms-utils'
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'QualityTab' })

const props = defineProps<{ projectId: string }>()

const loading = ref(false)
const saving = ref(false)
const filterSeverity = ref('')
const filterStatus = ref('')
const drawerVisible = ref(false)
const selected = ref<any>(null)
const showForm = ref(false)
const issueList = ref<any[]>([])

const filteredList = computed(() => {
  let r = issueList.value
  if (filterSeverity.value) r = r.filter(i => i.severity === filterSeverity.value)
  if (filterStatus.value) r = r.filter(i => i.status === filterStatus.value)
  return r
})

const newIssue = reactive({ description: '', severity: 'minor', category: 'design', responsiblePerson: '', source: '', rootCause: '', solution: '' })

function getSeverityLabel(s: string): string {
  return { fatal: '致命', severe: '严重', minor: '一般', suggestion: '建议' }[s] || s
}
function getSeverityType(s: string): string {
  return { fatal: 'danger', severe: 'warning', minor: 'primary', suggestion: 'info' }[s] || 'info'
}
function getCategoryLabel(c: string): string {
  return { design: '设计问题', process: '工艺问题', material: '物料问题', testing: '测试问题', other: '其他' }[c] || c
}
function getStatusLabel(s: string): string {
  return { open: '待处理', assigned: '已指派', in_progress: '处理中', processing: '处理中', pending_verify: '待验证', closed: '已关闭' }[s] || s
}
function getStatusColor(s: string): string {
  return { open: '#F53F3F', assigned: '#2468F2', in_progress: '#FF7D00', processing: '#FF7D00', pending_verify: '#FF7D00', closed: '#00B42A' }[s] || '#86909C'
}

function openDetail(row: any) { selected.value = row; drawerVisible.value = true }

async function closeIssue(row: any) {
  try {
    await ElMessageBox.confirm('确认关闭该质量问题？', '提示', { confirmButtonText: '确认关闭', type: 'warning' })
    await updateQualityIssue({ issueId: row.issueId, status: 'closed', closeTime: new Date().toISOString().split('T')[0] } as any)
    await fetchList()
    ElMessage.success('问题已关闭')
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

async function submitIssue() {
  if (!newIssue.description) { ElMessage.warning('请填写问题描述'); return }
  saving.value = true
  try {
    await createQualityIssue({
      issueDescription: newIssue.description,
      severity: newIssue.severity,
      rootCauseCategory: newIssue.category,
      responsiblePerson: newIssue.responsiblePerson,
      rootCauseDetail: newIssue.rootCause,
      solution: newIssue.solution,
      source: newIssue.source,
      impactScope: newIssue.source,
      projectId: props.projectId,
      status: 'open'
    } as any)
    ElMessage.success('问题已录入')
    showForm.value = false
    Object.assign(newIssue, { description: '', severity: 'minor', category: 'design', responsiblePerson: '', source: '', rootCause: '', solution: '' })
    await fetchList()
  } catch (e) { console.error(e) }
  finally { saving.value = false }
}

async function fetchList() {
  loading.value = true
  try {
    const data = await getQualityIssueList()
    issueList.value = ((data as any[]) || []).filter(i => String(i.projectId) === String(props.projectId))
  } catch (e) { console.error(e); issueList.value = [] }
  finally { loading.value = false }
}

onMounted(() => { fetchList() })
defineExpose({ refresh: fetchList })
</script>

<style scoped>
.quality-tab { }
.tab-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; flex-wrap: wrap; gap: 8px; }
.toolbar-title { font-size: 14px; font-weight: 600; color: #1D2129; }
.drawer-header { display: flex; align-items: center; gap: 8px; width: 100%; .title { font-size: 16px; font-weight: 600; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; } }
</style>
