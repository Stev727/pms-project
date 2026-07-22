<template>
  <div class="documents-tab">
    <div class="tab-toolbar">
      <span class="toolbar-title">项目文档 ({{ filteredDocs.length }})</span>
      <div style="display: flex; gap: 8px">
        <el-input v-model="searchName" placeholder="搜索文档名" clearable size="small" style="width: 180px" />
        <el-select v-model="filterCategory" placeholder="文档分类" clearable size="small" style="width: 120px">
          <el-option label="技术文档" value="tech_doc" />
          <el-option label="管理文档" value="mgmt_doc" />
          <el-option label="项目文档" value="project_doc" />
          <el-option label="输出物" value="deliverable" />
          <el-option label="图纸" value="drawing" />
          <el-option label="报告" value="report" />
          <el-option label="标准文件" value="standard" />
        </el-select>
        <el-upload
          action="/admin-api/infra/file/upload"
          :show-file-list="false"
          :on-success="handleUploadSuccess"
          v-if="checkPermi(['pms:document:create'])"
        >
          <el-button type="primary" size="small"><Icon icon="ep:upload" class="mr-4px" />上传文档</el-button>
        </el-upload>
        <el-button size="small" @click="handleBatchDownload" :disabled="selectedRows.length === 0">
          <Icon icon="ep:download" class="mr-4px" />批量下载
        </el-button>
      </div>
    </div>

    <!-- 按阶段分组 -->
    <div v-for="group in groupedDocs" :key="group.label" class="doc-group">
      <div class="group-header" @click="toggleGroup(group.label)">
        <Icon :icon="group.expanded ? 'ep:arrow-down' : 'ep:arrow-right'" class="mr-4px" />
        <Icon icon="ep:folder" :color="getPhaseColor(group.label)" class="mr-4px" />
        <span class="group-label">{{ group.label }}</span>
        <el-tag size="small" round>{{ group.docs.length }}</el-tag>
      </div>
      <div v-show="group.expanded">
        <el-table :data="group.docs" border stripe size="small" v-if="group.docs.length > 0"
          @selection-change="(rows: any) => handleSelectionChange(rows)">
          <el-table-column type="selection" width="40" />
          <el-table-column label="文档名称" min-width="220">
            <template #default="{ row }">
              <Icon icon="ep:document" class="mr-4px" />
              <el-link type="primary" @click="previewDoc(row)">{{ row.fileName }}</el-link>
              <el-tag v-if="row.isNewVersion" size="small" type="success" effect="plain" style="margin-left: 6px">新版本</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="分类" width="100">
            <template #default="{ row }">{{ getCategoryLabel(row.category) }}</template>
          </el-table-column>
          <el-table-column label="关联任务" width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.taskName || getTaskName(row.taskId) || '-' }}</template>
          </el-table-column>
          <el-table-column label="版本" width="70" align="center">
            <template #default="{ row }">
              <el-tag size="small">v{{ row.versionNo }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="大小" width="80" align="center">
            <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column label="类型" width="70" align="center" prop="fileType" />
          <el-table-column label="上传人" width="80">
            <template #default="{ row }">{{ row.uploadByName || row.uploadBy || '-' }}</template>
          </el-table-column>
          <el-table-column label="上传时间" width="110">
            <template #default="{ row }">{{ formatDate(row.uploadTime, 'MM-DD HH:mm') }}</template>
          </el-table-column>
          <el-table-column label="下载" width="70" align="center">
            <template #default="{ row }">{{ row.downloadCount || 0 }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="previewDoc(row)">预览</el-button>
              <el-button link type="primary" size="small" @click="downloadDoc(row)">下载</el-button>
              <el-button link type="danger" size="small" @click="removeDoc(row)" v-if="checkPermi(['pms:document:delete'])">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-else class="group-empty">暂无文档</div>
      </div>
    </div>

    <el-empty v-if="!loading && filteredDocs.length === 0" description="暂无项目文档" />

    <!-- 预览弹窗 -->
    <el-dialog v-model="previewVisible" :title="previewDocData?.fileName || '文档预览'" width="800px">
      <template v-if="previewDocData">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="文档名称">{{ previewDocData.fileName }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ getCategoryLabel(previewDocData.category) }}</el-descriptions-item>
          <el-descriptions-item label="版本">v{{ previewDocData.versionNo }}</el-descriptions-item>
          <el-descriptions-item label="大小">{{ formatSize(previewDocData.fileSize) }}</el-descriptions-item>
          <el-descriptions-item label="文件类型">{{ previewDocData.fileType }}</el-descriptions-item>
          <el-descriptions-item label="上传人">{{ previewDocData.uploadByName || previewDocData.uploadBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="上传时间">{{ formatDate(previewDocData.uploadTime) }}</el-descriptions-item>
          <el-descriptions-item label="下载次数">{{ previewDocData.downloadCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="描述" :span="2">{{ previewDocData.description || '-' }}</el-descriptions-item>
          <el-descriptions-item label="标签" :span="2">{{ previewDocData.tags || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="preview-area mt-16px">
          <el-empty description="文档内容预览区域" />
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDocumentList, createDocument, deleteDocument, DocumentVO } from '@/api/pms/document'
import { getTaskList, TaskVO } from '@/api/pms/task'
import { getStageList, StageVO } from '@/api/pms/stage'
import { formatDate, phaseColorMap } from '../pms-utils'
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'DocumentsTab' })

const props = defineProps<{
  projectId: string
}>()

const loading = ref(false)
const searchName = ref('')
const filterCategory = ref('')
const documentList = ref<any[]>([])
const tasks = ref<TaskVO[]>([])
const stages = ref<StageVO[]>([])
const previewVisible = ref(false)
const previewDocData = ref<any>(null)
const selectedRows = ref<any[]>([])
const groupExpanded = ref<Record<string, boolean>>({})

const categoryLabelMap: Record<string, string> = {
  tech_doc: '技术文档', mgmt_doc: '管理文档', project_doc: '项目文档',
  deliverable: '输出物', drawing: '图纸', report: '报告', standard: '标准文件'
}

function getCategoryLabel(code: string): string { return categoryLabelMap[code] || code || '-' }
function getTaskName(taskId?: number): string {
  if (!taskId) return ''
  return tasks.value.find(t => String(t.taskId) === String(taskId))?.taskName || ''
}
function getPhaseColor(stageName: string): string {
  return phaseColorMap[stageName]?.color || '#2468F2'
}

const filteredDocs = computed(() => {
  let list = documentList.value
  if (searchName.value) list = list.filter(d => d.fileName?.includes(searchName.value))
  if (filterCategory.value) list = list.filter(d => d.category === filterCategory.value)
  return list
})

const groupedDocs = computed(() => {
  const groups: Record<string, any[]> = {}
  for (const doc of filteredDocs.value) {
    const key = doc.stageName || doc.category || '未分类'
    if (!groups[key]) groups[key] = []
    groups[key].push(doc)
  }
  return Object.entries(groups).map(([label, docs]) => ({
    label,
    docs,
    expanded: groupExpanded.value[label] !== false // default expanded
  }))
})

function toggleGroup(label: string) {
  groupExpanded.value[label] = !(groupExpanded.value[label] !== false)
}

function handleSelectionChange(rows: any[]) { selectedRows.value = rows }

function previewDoc(row: any) { previewDocData.value = row; previewVisible.value = true }

function downloadDoc(row: any) {
  if (row.storagePath) {
    window.open(row.storagePath, '_blank')
    ElMessage.success(`正在下载：${row.fileName}`)
  } else {
    ElMessage.warning('该文档没有可用的下载路径')
  }
}

function removeDoc(row: any) {
  ElMessageBox.confirm(`确认删除文档「${row.fileName}」？`, '提示', { type: 'warning' }).then(async () => {
    try {
      await deleteDocument(row.documentId as number)
      ElMessage.success('文档已删除')
      await loadDocuments()
    } catch (e) { console.error(e) }
  }).catch(() => {})
}

function handleBatchDownload() {
  if (selectedRows.value.length === 0) return
  ElMessage.info(`准备下载 ${selectedRows.value.length} 个文档（批量下载功能开发中）`)
}

async function handleUploadSuccess(response: any) {
  try {
    const fileUrl = response?.data || response
    const fileName = fileUrl?.split('/').pop() || '未命名文档'
    const ext = fileName.split('.').pop()?.toLowerCase() || ''
    await createDocument({
      fileName,
      fileType: ext,
      category: filterCategory.value || 'project_doc',
      projectId: Number(props.projectId),
      storagePath: fileUrl,
      versionNo: '1.0',
      fileSize: 0
    } as DocumentVO)
    ElMessage.success('文档上传成功')
    await loadDocuments()
  } catch (e) { console.error(e); ElMessage.error('文档保存失败') }
}

async function loadDocuments() {
  loading.value = true
  try {
    const data = await getDocumentList()
    const allDocs = (data as any[]) || []
    // 关联阶段信息
    const stageList = await getStageList()
    stages.value = (stageList as StageVO[]).filter(s => String(s.projectId) === String(props.projectId))
    documentList.value = allDocs
      .filter(d => String(d.projectId) === String(props.projectId))
      .map(d => {
        const task = tasks.value.find(t => String(t.taskId) === String(d.taskId))
        const stage = task ? stages.value.find(s => String(s.stageId) === String(task.stageId)) : undefined
        return { ...d, taskName: task?.taskName, stageName: stage?.stageName }
      })
  } catch (e) { console.error(e); documentList.value = [] }
  finally { loading.value = false }
}

async function loadTasks() {
  try {
    const data = await getTaskList()
    tasks.value = ((data as TaskVO[]) || []).filter(t => String(t.projectId) === String(props.projectId))
  } catch (e) { console.error(e) }
}

function formatSize(bytes: number): string {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

onMounted(async () => {
  await loadTasks()
  await loadDocuments()
})

defineExpose({ refresh: loadDocuments })
</script>

<style scoped>
.documents-tab { }
.tab-toolbar {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; flex-wrap: wrap; gap: 8px;
}
.toolbar-title { font-size: 14px; font-weight: 600; color: #1D2129; }
.doc-group { margin-bottom: 16px; }
.group-header {
  display: flex; align-items: center; padding: 8px 12px; background: #F7F8FA; border-radius: 4px;
  cursor: pointer; margin-bottom: 4px; transition: background 0.2s;
}
.group-header:hover { background: #EDEFF2; }
.group-label { font-size: 14px; font-weight: 600; flex: 1; }
.group-empty { text-align: center; padding: 20px; color: #C9CDD4; font-size: 13px; }
.preview-area { min-height: 200px; display: flex; align-items: center; justify-content: center; border: 1px dashed var(--el-border-color); border-radius: 4px; }
</style>
