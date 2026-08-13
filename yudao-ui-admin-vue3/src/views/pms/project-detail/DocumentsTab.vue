<!--
  项目文档 Tab（#6/#7 改造）
  改造点：
  1. 列表加载改用 getDocumentListByProject（后端按权限过滤）
  2. 预览改用 PreviewDialog 组件（支持 PDF/图片/文本/Office 转 PDF）
  3. 下载改用 downloadDocument（带权限校验）
  4. 上传弹窗新增：可见范围选择器 + 角色多选（visibility=role）+ 允许下载开关
  5. 文档列表新增「可见范围」列
-->
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
        <el-button type="primary" size="small" @click="openUploadDialog" v-if="canUpload">
          <Icon icon="ep:upload" class="mr-4px" />上传文档
        </el-button>
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
          <el-table-column label="可见范围" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="getVisibilityTagType(row.visibility)" size="small">
                {{ getVisibilityLabel(row.visibility) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="上传人" width="80">
            <template #default="{ row }">{{ row.uploadByName || row.uploadBy || '-' }}</template>
          </el-table-column>
          <el-table-column label="上传时间" width="110">
            <template #default="{ row }">{{ formatDate(row.uploadTime, 'YYYY-MM-DD HH:mm') }}</template>
          </el-table-column>
          <el-table-column label="下载" width="70" align="center">
            <template #default="{ row }">{{ row.downloadCount || 0 }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="previewDoc(row)">预览</el-button>
              <el-button link type="primary" size="small" @click="downloadDoc(row)" :disabled="!row.allowDownload && row.allowDownload !== null && row.allowDownload !== undefined">
                下载
              </el-button>
              <el-button link type="danger" size="small" @click="removeDoc(row)" v-if="canDelete">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-else class="group-empty">暂无文档</div>
      </div>
    </div>

    <el-empty v-if="!loading && filteredDocs.length === 0" description="暂无项目文档" />

    <!-- 预览弹窗（#6） -->
    <PreviewDialog
      v-model="previewVisible"
      :doc-id="previewDocId"
      :file-name="previewDocName"
      @downloaded="onPreviewDownloaded"
    />

    <!-- 上传弹窗（#7 加权限字段） -->
    <el-dialog v-model="uploadVisible" title="上传项目文档" width="560px">
      <el-form label-width="90px">
        <el-form-item label="文档分类">
          <el-select v-model="uploadCategory" placeholder="选择分类" class="w-full">
            <el-option label="技术文档" value="tech_doc" />
            <el-option label="管理文档" value="mgmt_doc" />
            <el-option label="项目文档" value="project_doc" />
            <el-option label="输出物" value="deliverable" />
            <el-option label="图纸" value="drawing" />
            <el-option label="报告" value="report" />
            <el-option label="标准文件" value="standard" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联任务">
          <el-select v-model="uploadTaskId" filterable clearable placeholder="选择关联任务（可选）" class="w-full">
            <el-option v-for="t in tasks" :key="t.taskId" :label="t.taskName" :value="String(t.taskId)" />
          </el-select>
        </el-form-item>

        <!-- #7：可见范围 -->
        <el-form-item label="可见范围">
          <el-radio-group v-model="uploadVisibility">
            <el-radio value="public">项目全员</el-radio>
            <el-radio value="role">指定角色</el-radio>
            <el-radio value="private">仅上传人和项目经理</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- #7：角色多选（visibility=role 时显示） -->
        <el-form-item label="可查看角色" v-if="uploadVisibility === 'role'">
          <el-select
            v-model="uploadAllowedRoleIds"
            multiple
            filterable
            placeholder="选择可查看的角色"
            class="w-full"
          >
            <el-option
              v-for="r in projectRoles"
              :key="r.roleId"
              :label="r.roleName"
              :value="String(r.roleId)"
            />
          </el-select>
          <div class="form-tip">未选择任何角色时，role 模式下文档将无人可见（上传人除外）</div>
        </el-form-item>

        <!-- #7：允许下载 -->
        <el-form-item label="允许下载">
          <el-switch v-model="uploadAllowDownload" />
          <span class="form-tip ml-8px">{{ uploadAllowDownload ? '允许有权限的用户下载' : '禁止下载（仅可预览）' }}</span>
        </el-form-item>
      </el-form>

      <el-upload
        action="/admin-api/infra/file/upload"
        :headers="uploadHeaders"
        :show-file-list="true"
        :on-success="handleUploadSuccess"
        :on-error="handleUploadError"
        :on-remove="handleUploadRemove"
        drag
        multiple
      >
        <el-icon class="el-icon--upload"><Icon icon="ep:upload-filled" /></el-icon>
        <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">支持任意格式文件，单个文件不超过 50MB</div>
        </template>
      </el-upload>

      <template #footer>
        <el-button @click="uploadVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getDocumentListByProject,
  createDocument,
  deleteDocument,
  downloadDocument,
  getVisibilityLabel,
  type DocumentVO
} from '@/api/pms/document'
import { getProjectRoleList, type ProjectRoleVO } from '@/api/pms/permission'
import { getTaskList, TaskVO } from '@/api/pms/task'
import { getStageList, StageVO } from '@/api/pms/stage'
import { formatDate, phaseColorMap } from '../pms-utils'
import { checkPermi } from '@/utils/permission'
import { getAccessToken, getTenantId } from '@/utils/auth'
import { useProjectPerm } from '@/hooks/pms/useProjectPerm'
import PreviewDialog from '../document/PreviewDialog.vue'

defineOptions({ name: 'DocumentsTab' })

const props = defineProps<{
  projectId: string
}>()

const { loadPerm } = useProjectPerm()

const uploadHeaders = computed(() => ({
  Authorization: 'Bearer ' + getAccessToken(),
  'tenant-id': String(getTenantId() || '')
}))
const loading = ref(false)
const searchName = ref('')
const filterCategory = ref('')
const documentList = ref<any[]>([])
const tasks = ref<TaskVO[]>([])
const stages = ref<StageVO[]>([])
const projectRoles = ref<ProjectRoleVO[]>([])
const selectedRows = ref<any[]>([])
const groupExpanded = ref<Record<string, boolean>>({})

// 预览弹窗状态
const previewVisible = ref(false)
const previewDocId = ref<string>('')
const previewDocName = ref<string>('')

// 上传弹窗状态
const uploadVisible = ref(false)
const uploadCategory = ref('project_doc')
const uploadTaskId = ref<string | undefined>(undefined)
const uploadVisibility = ref<string>('public')
const uploadAllowedRoleIds = ref<string[]>([])
const uploadAllowDownload = ref<boolean>(true)

// 项目级权限
// 降级策略：菜单级 checkPermi 通过即放行；项目级 can() 作为额外控制，
// 矩阵未加载完成（permLoaded=false）时降级放行（与任务模块一致）
const canUpload = computed(() => checkPermi(['pms:document:create']))
const canDelete = computed(() => checkPermi(['pms:document:delete']))

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
function getVisibilityTagType(visibility?: string): 'info' | 'success' | 'warning' {
  if (visibility === 'private') return 'warning'
  if (visibility === 'role') return 'info'
  return 'success'
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
    expanded: groupExpanded.value[label] !== false
  }))
})

function toggleGroup(label: string) {
  groupExpanded.value[label] = !(groupExpanded.value[label] !== false)
}

function handleSelectionChange(rows: any[]) { selectedRows.value = rows }

// ========== #6 预览 ==========

function previewDoc(row: any) {
  previewDocId.value = String(row.documentId)
  previewDocName.value = row.fileName || ''
  previewVisible.value = true
}

function onPreviewDownloaded() {
  // 预览弹窗内触发的下载，刷新下载计数
  loadDocuments()
}

// ========== 下载（#7 带权限校验） ==========

async function downloadDoc(row: any) {
  try {
    await downloadDocument(row.documentId, row.fileName)
    ElMessage.success(`正在下载：${row.fileName}`)
    row.downloadCount = (row.downloadCount || 0) + 1
  } catch (e: any) {
    console.error('[downloadDoc] 下载失败', e)
    ElMessage.error(e?.message || '下载失败，请检查文件是否存在或联系管理员')
  }
}

function handleBatchDownload() {
  if (selectedRows.value.length === 0) return
  let count = 0
  for (const doc of selectedRows.value) {
    if (doc.allowDownload === false) continue
    downloadDocument(doc.documentId, doc.fileName)
      .then(() => { count++ })
      .catch((e) => console.error('[handleBatchDownload] 下载失败', e))
  }
  ElMessage.success(`已开始下载 ${selectedRows.value.length} 个文档`)
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

// ========== 上传弹窗（#7 加权限字段） ==========

function openUploadDialog() {
  uploadCategory.value = 'project_doc'
  uploadTaskId.value = undefined
  uploadVisibility.value = 'public'
  uploadAllowedRoleIds.value = []
  uploadAllowDownload.value = true
  uploadVisible.value = true
}

async function handleUploadSuccess(response: any, uploadFile: any) {
  try {
    if (response && typeof response === 'object' && response.code !== 0) {
      ElMessage.error(response.msg || '文件上传失败')
      return
    }
    const fileUrl = response?.data || response
    const fileName = uploadFile?.name || fileUrl?.split('/').pop() || '未命名文档'
    const ext = fileName.split('.').pop()?.toLowerCase() || ''
    const fileSize = uploadFile?.size || 0

    // 同名文档版本递增
    const existing = documentList.value.find(d => d.fileName === fileName)
    let versionNo = '1'
    if (existing) {
      const prevVersion = parseInt(existing.versionNo || '1', 10)
      versionNo = String(prevVersion + 1)
    }

    // #7：构造权限字段
    const docData: DocumentVO = {
      fileName,
      fileType: ext,
      category: uploadCategory.value || 'project_doc',
      projectId: props.projectId,
      taskId: uploadTaskId.value || undefined,
      storagePath: fileUrl,
      versionNo,
      fileSize,
      visibility: uploadVisibility.value,
      allowDownload: uploadAllowDownload.value
    }
    // role 模式下传 allowedRoleIds（JSON 数组字符串）
    if (uploadVisibility.value === 'role' && uploadAllowedRoleIds.value.length > 0) {
      docData.allowedRoleIds = JSON.stringify(uploadAllowedRoleIds.value)
    }

    await createDocument(docData)
    ElMessage.success(`文档上传成功（版本 v${versionNo}）`)
    await loadDocuments()
  } catch (e) { console.error(e); ElMessage.error('文档保存失败') }
}

function handleUploadError() {
  ElMessage.error('文件上传失败，请检查网络或文件大小')
}

function handleUploadRemove() {
  ElMessage.info('文件已从列表中移除，如需删除文档请在文档列表中操作')
}

// ========== 数据加载 ==========

async function loadDocuments() {
  loading.value = true
  try {
    // #7：改用按项目 + 权限过滤的列表接口
    const data = await getDocumentListByProject(props.projectId)
    const allDocs = (data as any[]) || []
    // 关联阶段信息
    const stageList = await getStageList()
    stages.value = (stageList as StageVO[]).filter(s => String(s.projectId) === String(props.projectId))
    documentList.value = allDocs.map(d => {
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

async function loadProjectRoles() {
  try {
    const data = await getProjectRoleList(props.projectId)
    projectRoles.value = ((data as ProjectRoleVO[]) || [])
  } catch (e) {
    console.error('[loadProjectRoles] 加载项目角色失败', e)
    projectRoles.value = []
  }
}

function formatSize(bytes: number): string {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

onMounted(async () => {
  await loadPerm(props.projectId)
  await loadTasks()
  await loadProjectRoles()
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
.form-tip { font-size: 12px; color: #86909C; margin-top: 4px; }
</style>
