<template>
  <div class="quality-tab">
    <div class="tab-toolbar">
      <span class="toolbar-title">质量问题 ({{ filteredList.length }})</span>
      <div style="display: flex; gap: 8px">
        <el-select v-model="filterSeverity" placeholder="严重程度" clearable size="small" style="width: 110px">
          <el-option v-for="opt in severityOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-select v-model="filterStatus" placeholder="状态" clearable size="small" style="width: 100px">
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
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
        <template #default="{ row }">{{ getCategoryLabel(row.rootCauseCategory) }}</template>
      </el-table-column>
      <el-table-column label="来源" width="80">
        <template #default="{ row }">{{ row.source || '-' }}</template>
      </el-table-column>
      <el-table-column label="责任人" width="90">
        <template #default="{ row }">{{ getUserName(row.responsiblePerson) || row.responsiblePerson || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag size="small" :color="getStatusColor(row.status)" effect="dark">{{ getStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="发现日期" width="100">
        <template #default="{ row }">{{ formatDate(row.foundDate) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click.stop="openDetail(row)">详情</el-button>
          <el-button link type="warning" size="small" @click.stop="editIssue(row)" v-if="row.status !== 'closed' && checkPermi(['pms:quality:update'])">编辑</el-button>
          <el-button link type="success" size="small" @click.stop="closeIssue(row)" v-if="row.status !== 'closed' && checkPermi(['pms:quality:update'])">关闭</el-button>
          <el-button link type="danger" size="small" @click.stop="deleteIssue(row)" v-if="checkPermi(['pms:quality:delete'])">删除</el-button>
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
          <el-descriptions-item label="分类">{{ getCategoryLabel(selected.rootCauseCategory) }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ selected.source || '-' }}</el-descriptions-item>
          <el-descriptions-item label="责任人">{{ getUserName(selected.responsiblePerson) || selected.responsiblePerson || '-' }}</el-descriptions-item>
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
    <el-dialog v-model="showForm" :title="editingIssue ? '编辑质量问题' : '录入质量问题'" width="560px">
      <el-form label-width="90px">
        <el-form-item label="问题描述" required><el-input v-model="newIssue.description" type="textarea" :rows="3" /></el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="严重程度"><el-select v-model="newIssue.severity" class="w-full"><el-option v-for="opt in severityOptions" :key="opt.value" :label="opt.label" :value="opt.value" /></el-select></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类"><el-select v-model="newIssue.category" class="w-full"><el-option v-for="opt in categoryOptions" :key="opt.value" :label="opt.label" :value="opt.value" /></el-select></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="责任人">
            <el-select v-model="newIssue.responsiblePerson" filterable clearable placeholder="请选择责任人" class="w-full">
              <el-option v-for="u in projectMemberUsers" :key="u.id" :label="`${u.nickname}`" :value="String(u.id)" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="来源"><el-input v-model="newIssue.source" placeholder="测试/评审/现场" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="发现日期">
            <el-date-picker v-model="newIssue.foundDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" class="w-full" />
          </el-form-item></el-col>
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
import { getQualityIssueList, createQualityIssue, updateQualityIssue, deleteQualityIssue } from '@/api/pms/quality'
import { formatDate } from '../pms-utils'
import { checkPermi } from '@/utils/permission'
import { useProjectMembers } from '@/hooks/pms/useProjectMembers'
import { useUserNames } from '@/hooks/pms/useUserNames'
import { getSimpleDictDataList } from '@/api/system/dict/dict.data'

defineOptions({ name: 'QualityTab' })

const props = defineProps<{ projectId: string }>()
const { projectMemberUsers, loadProjectMembers } = useProjectMembers()
const { getUserName, ensureLoaded: ensureUsersLoaded } = useUserNames()

const loading = ref(false)
const saving = ref(false)
const filterSeverity = ref('')
const filterStatus = ref('')
const drawerVisible = ref(false)
const selected = ref<any>(null)
const showForm = ref(false)
const editingIssue = ref<any>(null)
const issueList = ref<any[]>([])

const filteredList = computed(() => {
  let r = issueList.value
  if (filterSeverity.value) r = r.filter(i => i.severity === filterSeverity.value)
  if (filterStatus.value) r = r.filter(i => i.status === filterStatus.value)
  return r
})

const newIssue = reactive({ description: '', severity: '', category: '', responsiblePerson: '', source: '', rootCause: '', solution: '', foundDate: '' })

// ==================== 字典数据 ====================
const allDictData = ref<any[]>([])

/** 从全量字典中提取指定类型的选项列表（按 sort 排序） */
function extractDictOptions(dictType: string): { value: string; label: string }[] {
  if (!Array.isArray(allDictData.value)) return []
  return allDictData.value
    .filter((d: any) => d.dictType === dictType && (d.status == null || d.status === 0))
    .sort((a: any, b: any) => (a.sort || 0) - (b.sort || 0))
    .map((d: any) => ({ value: String(d.value), label: d.label }))
}

/** 字典选项（computed，字典加载后自动更新） */
const severityOptions = computed(() => extractDictOptions('quality_severity'))
const categoryOptions = computed(() => extractDictOptions('pms_quality_category'))
const statusOptions = computed(() => extractDictOptions('quality_status'))

// ==================== 字典值 → 标签/样式 映射（从字典数据动态查找） ====================
function findDictLabel(dictType: string, value: string): string {
  if (!value) return '-'
  const opt = extractDictOptions(dictType).find(o => o.value === String(value))
  return opt?.label || value
}

function getSeverityLabel(s: string): string { return findDictLabel('quality_severity', s) }
function getCategoryLabel(c: string): string { return findDictLabel('pms_quality_category', c) }
function getStatusLabel(s: string): string { return findDictLabel('quality_status', s) }

/** 严重程度 tag 类型：按字典 sort 值映射（sort 越大越严重） */
function getSeverityType(s: string): string {
  const opts = extractDictOptions('quality_severity')
  const idx = opts.findIndex(o => o.value === String(s))
  if (idx === -1) return 'info'
  // 前2项(最严重)→danger/warning，中间→primary，末尾→info/success
  if (idx <= 1) return idx === 0 ? 'danger' : 'warning'
  if (idx >= opts.length - 2) return idx === opts.length - 1 ? 'info' : 'success'
  return 'primary'
}

/** 状态颜色 */
function getStatusColor(s: string): string {
  // 从字典数据取 colorType 或按默认规则
  const colorMap: Record<string, string> = {
    unassigned: '#86909C', open: '#F53F3F', assigned: '#2468F2',
    improving: '#FF7D00', processing: '#FF7D00', in_progress: '#FF7D00',
    to_verify: '#FF7D00', pending_verify: '#FF7D00',
    verified: '#00B42A', resolved: '#00B42A', closed: '#00B42A',
    new: '#86909C'
  }
  return colorMap[s] || '#86909C'
}

function openDetail(row: any) { selected.value = row; drawerVisible.value = true }

async function closeIssue(row: any) {
  // P1-02 修复：关闭前强制确认已填写根因分析和解决方案
  try {
    await ElMessageBox.confirm(
      '关闭前请确认已填写根因分析和解决方案。确认关闭此质量问题？',
      '关闭确认',
      { confirmButtonText: '确认关闭', cancelButtonText: '取消', type: 'warning' }
    )
    await updateQualityIssue({ issueId: row.issueId, status: 'closed', closeTime: new Date().toISOString().split('T')[0] } as any)
    await fetchList()
    ElMessage.success('问题已关闭')
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

async function submitIssue() {
  if (!newIssue.description) { ElMessage.warning('请填写问题描述'); return }
  saving.value = true
  try {
    if (editingIssue.value) {
      // 编辑模式
      await updateQualityIssue({
        issueId: editingIssue.value.issueId,
        issueDescription: newIssue.description,
        severity: newIssue.severity,
        rootCauseCategory: newIssue.category,
        responsiblePerson: newIssue.responsiblePerson,
        rootCauseDetail: newIssue.rootCause,
        solution: newIssue.solution,
        source: newIssue.source,
        foundDate: newIssue.foundDate || undefined
      } as any)
      ElMessage.success('问题已更新')
    } else {
      // 新建模式
      await createQualityIssue({
        issueCode: `QI-${Date.now().toString().slice(-6)}`,
        issueDescription: newIssue.description,
        severity: newIssue.severity,
        rootCauseCategory: newIssue.category,
        responsiblePerson: newIssue.responsiblePerson,
        rootCauseDetail: newIssue.rootCause,
        solution: newIssue.solution,
        source: newIssue.source,
        impactScope: '',
        projectId: props.projectId,
        status: 'open',
        foundDate: new Date().toISOString().split('T')[0]
      } as any)
      ElMessage.success('问题已录入')
    }
    showForm.value = false
    editingIssue.value = null
    const defaultSeverity = severityOptions.value[0]?.value || ''
    const defaultCategory = categoryOptions.value[0]?.value || ''
    Object.assign(newIssue, { description: '', severity: defaultSeverity, category: defaultCategory, responsiblePerson: '', source: '', foundDate: '', rootCause: '', solution: '' })
    await fetchList()
  } catch (e) { console.error(e) }
  finally { saving.value = false }
}

function editIssue(row: any) {
  editingIssue.value = row
  const defaultSeverity = severityOptions.value[0]?.value || ''
  const defaultCategory = categoryOptions.value[0]?.value || ''
  Object.assign(newIssue, {
    description: row.issueDescription || '',
    severity: row.severity || defaultSeverity,
    category: row.rootCauseCategory || defaultCategory,
    responsiblePerson: row.responsiblePerson || '',
    source: row.source || '',
    foundDate: row.foundDate || '',
    rootCause: row.rootCauseDetail || '',
    solution: row.solution || ''
  })
  showForm.value = true
}

async function deleteIssue(row: any) {
  try {
    await ElMessageBox.confirm('确认删除此质量问题？删除后不可恢复。', '删除确认', {
      confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning'
    })
    await deleteQualityIssue(row.issueId)
    ElMessage.success('问题已删除')
    await fetchList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

async function fetchList() {
  loading.value = true
  try {
    const data = await getQualityIssueList()
    issueList.value = ((data as any[]) || []).filter(i => String(i.projectId) === String(props.projectId))
  } catch (e) { console.error(e); issueList.value = [] }
  finally { loading.value = false }
}

onMounted(async () => {
  ensureUsersLoaded()
  loadProjectMembers(props.projectId)
  // 加载字典数据（严重程度/分类/状态）
  try {
    const res: any = await getSimpleDictDataList()
    allDictData.value = Array.isArray(res) ? res : (res?.data || [])
  } catch (e) {
    console.warn('[QualityTab] 字典加载失败，使用空字典', e)
  }
  fetchList()
})
defineExpose({ refresh: fetchList })
</script>

<style scoped>
.quality-tab { }
.tab-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; flex-wrap: wrap; gap: 8px; }
.toolbar-title { font-size: 14px; font-weight: 600; color: #1D2129; }
.drawer-header { display: flex; align-items: center; gap: 8px; width: 100%; .title { font-size: 16px; font-weight: 600; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; } }
</style>
