<!--
  文档管理页面（#6/#7 改造）
  改造点：
  1. 预览改用 PreviewDialog 组件（替换原空壳 el-empty）
  2. 下载改用 downloadDocument（带权限校验）
  3. 删除原内嵌预览弹窗，引入 PreviewDialog

  注：此页面为全局文档管理，文档权限字段（visibility/allowedRoleIds/allowDownload）
  的编辑在「项目详情 → 文档 Tab」的上传弹窗中完成（依赖项目角色列表）。
-->
<template>
  <div class="pms-document">
    <ContentWrap>
      <el-row :gutter="16">
        <!-- 左侧分类树 -->
        <el-col :span="5">
          <div class="tree-header">文档分类</div>
          <el-tree
            :data="categoryTree" :props="{ label: 'name', children: 'children' }"
            highlight-current default-expand-all @node-click="handleCategoryClick"
          />
        </el-col>

        <!-- 右侧文档列表 -->
        <el-col :span="19">
          <div class="list-header">
            <div class="filters">
              <el-input v-model="searchName" placeholder="搜索文档名" clearable style="width: 180px" />
              <el-button @click="searchDocs">查询</el-button>
            </div>
            <el-upload action="/admin-api/infra/file/upload" :headers="uploadHeaders" :show-file-list="false" :on-success="handleUploadSuccess" :on-error="handleUploadError" v-if="checkPermi(['pms:document:create'])">
              <el-button type="primary" size="small"><el-icon><Upload /></el-icon> 上传文档</el-button>
            </el-upload>
          </div>

          <el-table :data="filteredDocs" border size="small" v-loading="loading">
            <el-table-column prop="fileName" label="文档名称" min-width="200">
              <template #default="{ row }">
                <el-icon class="mr-4px"><Document /></el-icon>
                <el-link type="primary" @click="previewDoc(row)">{{ row.fileName }}</el-link>
              </template>
            </el-table-column>
            <el-table-column prop="category" label="分类" width="100">
              <template #default="{ row }">{{ getCategoryLabel(row.category) }}</template>
            </el-table-column>
            <el-table-column label="所属项目" width="120">
              <template #default="{ row }">{{ getProjectName(row.projectId) }}</template>
            </el-table-column>
            <el-table-column prop="versionNo" label="版本" width="70" align="center">
              <template #default="{ row }">
                <el-tag size="small">v{{ row.versionNo }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="fileSize" label="大小" width="80" align="center">
              <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
            </el-table-column>
            <el-table-column prop="fileType" label="类型" width="70" align="center" />
            <el-table-column label="可见范围" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="getVisibilityTagType(row.visibility)" size="small">
                  {{ getVisibilityLabel(row.visibility) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="上传人" width="80">
              <template #default="{ row }">{{ getUserName(row.uploadBy) }}</template>
            </el-table-column>
            <el-table-column label="上传时间" width="120">
              <template #default="{ row }">{{ formatDate(row.uploadTime) }}</template>
            </el-table-column>
            <el-table-column label="下载次数" width="80" align="center">
              <template #default="{ row }">{{ row.downloadCount || 0 }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150" align="center">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="previewDoc(row)">预览</el-button>
                <el-button link type="primary" size="small" @click="downloadDoc(row)">下载</el-button>
                <el-button link type="danger" size="small" @click="removeDoc(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="currentPage" v-model:page-size="pageSize"
            :total="filteredDocs.length" layout="total, prev, pager, next"
            class="mt-12px" style="justify-content: flex-end"
          />
        </el-col>
      </el-row>
    </ContentWrap>

    <!-- #6 预览弹窗 -->
    <PreviewDialog
      v-model="previewVisible"
      :doc-id="previewDocId"
      :file-name="previewDocName"
      @downloaded="loadDocuments"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Document } from '@element-plus/icons-vue'
import { formatDate } from '../pms-utils'
import { checkPermi } from '@/utils/permission'
import { getAccessToken, getTenantId } from '@/utils/auth'
import { getProjectList, ProjectVO } from '@/api/pms/project'
import {
  getDocumentList,
  createDocument,
  deleteDocument,
  downloadDocument,
  getVisibilityLabel
} from '@/api/pms/document'
import { useUserNames } from '@/hooks/pms/useUserNames'
import PreviewDialog from './PreviewDialog.vue'

defineOptions({ name: 'PmsDocument' })

const { getUserName, ensureLoaded: ensureUsersLoaded } = useUserNames()

const uploadHeaders = computed(() => ({
  Authorization: 'Bearer ' + getAccessToken(),
  'tenant-id': String(getTenantId() || '')
}))

const searchName = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const previewVisible = ref(false)
const previewDocId = ref<string>('')
const previewDocName = ref<string>('')
const selectedCategory = ref('')
const loading = ref(false)
const projects = ref<ProjectVO[]>([])

const categoryTree = ref([
  { name: '全部文档', id: '' },
  { name: '技术文档', id: 'tech_doc' },
  { name: '管理文档', id: 'mgmt_doc' },
  { name: '项目文档', id: 'project_doc' }
])

const categoryLabelMap: Record<string, string> = {
  tech_doc: '技术文档',
  mgmt_doc: '管理文档',
  project_doc: '项目文档'
}

function getCategoryLabel(code: string): string {
  return categoryLabelMap[code] || code || '-'
}

function getProjectName(projectId: any): string {
  if (!projectId) return '-'
  const p = projects.value.find(p => String(p.projectId) === String(projectId))
  return p?.projectName || '-'
}

function getVisibilityTagType(visibility?: string): 'info' | 'success' | 'warning' {
  if (visibility === 'private') return 'warning'
  if (visibility === 'role') return 'info'
  return 'success'
}

const documentList = ref<any[]>([])

const filteredDocs = computed(() => {
  let result = documentList.value
  if (searchName.value) result = result.filter(d => d.fileName && d.fileName.includes(searchName.value))
  if (selectedCategory.value) result = result.filter(d => d.category === selectedCategory.value)
  return result
})

function handleCategoryClick(node: any) {
  selectedCategory.value = node.id || ''
}

function searchDocs() { currentPage.value = 1 }

function previewDoc(row: any) {
  previewDocId.value = String(row.documentId)
  previewDocName.value = row.fileName || ''
  previewVisible.value = true
}

async function downloadDoc(row: any) {
  try {
    await downloadDocument(row.documentId, row.fileName)
    ElMessage.success(`正在下载：${row.fileName}`)
    await loadDocuments()
  } catch (e) {
    console.error(e)
  }
}

function removeDoc(row: any) {
  ElMessageBox.confirm(`确认删除文档「${row.fileName}」？`, '提示', { type: 'warning' }).then(async () => {
    try {
      await deleteDocument(row.documentId)
      ElMessage.success('文档已删除')
      await loadDocuments()
    } catch (e) {
      console.error(e)
    }
  }).catch(() => {})
}

async function handleUploadSuccess(response: any, uploadFile?: any) {
  try {
    if (response && typeof response === 'object' && response.code !== 0) {
      ElMessage.error(response.msg || '文件上传失败')
      return
    }
    const fileUrl = response?.data || response
    const fileName = uploadFile?.name || fileUrl?.split('/').pop() || '未命名文档'
    const ext = fileName.split('.').pop()?.toLowerCase() || ''
    const fileSize = uploadFile?.size || 0
    await createDocument({
      fileName: fileName,
      fileType: ext,
      category: selectedCategory.value || 'project_doc',
      storagePath: fileUrl,
      versionNo: '1.0',
      fileSize: fileSize,
      visibility: 'public',
      allowDownload: true
    } as any)
    ElMessage.success('文档上传成功')
    await loadDocuments()
  } catch (e) {
    console.error(e)
    ElMessage.error('文档保存失败')
  }
}

function handleUploadError() {
  ElMessage.error('文件上传失败，请检查网络或文件大小')
}

async function loadDocuments() {
  loading.value = true
  try {
    const data = await getDocumentList()
    documentList.value = (data as any[]) || []
  } catch (e) {
    console.error(e)
    documentList.value = []
  } finally {
    loading.value = false
  }
}

async function loadData() {
  try {
    projects.value = (await getProjectList()) as ProjectVO[]
    await ensureUsersLoaded()
  } catch (e) {
    console.error(e)
  }
  await loadDocuments()
}

function formatSize(bytes: number): string {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

onMounted(() => { loadData() })
</script>

<style scoped lang="scss">
.pms-document {
  .tree-header { font-size: 14px; font-weight: 600; margin-bottom: 12px; }
  .list-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
  .filters { display: flex; gap: 8px; }
}
</style>
