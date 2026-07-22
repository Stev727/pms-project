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
            <el-upload action="/admin-api/infra/file/upload" :show-file-list="false" :on-success="handleUploadSuccess" v-if="checkPermi(['pms:document:create'])">
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
            <el-table-column prop="uploadBy" label="上传人" width="80" />
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

    <!-- 预览弹窗 -->
    <el-dialog v-model="previewVisible" :title="previewDocData?.fileName || '文档预览'" width="800px">
      <div v-if="previewDocData" class="preview-content">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="文档名称">{{ previewDocData.fileName }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ getCategoryLabel(previewDocData.category) }}</el-descriptions-item>
          <el-descriptions-item label="版本">v{{ previewDocData.versionNo }}</el-descriptions-item>
          <el-descriptions-item label="大小">{{ formatSize(previewDocData.fileSize) }}</el-descriptions-item>
          <el-descriptions-item label="文件类型">{{ previewDocData.fileType }}</el-descriptions-item>
          <el-descriptions-item label="上传人">{{ previewDocData.uploadBy }}</el-descriptions-item>
          <el-descriptions-item label="上传时间">{{ formatDate(previewDocData.uploadTime) }}</el-descriptions-item>
          <el-descriptions-item label="下载次数">{{ previewDocData.downloadCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="描述" :span="2">{{ previewDocData.description || '-' }}</el-descriptions-item>
          <el-descriptions-item label="标签" :span="2">{{ previewDocData.tags || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="preview-area mt-16px">
          <el-empty description="文档预览功能" />
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatDate } from '../pms-utils'
import { checkPermi } from '@/utils/permission'
import { getProjectList, ProjectVO } from '@/api/pms/project'
import { getDocumentList, createDocument, updateDocument, deleteDocument } from '@/api/pms/document'

defineOptions({ name: 'PmsDocument' })

const searchName = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const previewVisible = ref(false)
const previewDocData = ref<any>(null)
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
  previewDocData.value = row
  previewVisible.value = true
}

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
      await deleteDocument(row.documentId)
      ElMessage.success('文档已删除')
      await loadDocuments()
    } catch (e) {
      console.error(e)
    }
  }).catch(() => {})
}

async function handleUploadSuccess(response: any) {
  try {
    const fileUrl = response?.data || response
    const fileName = fileUrl?.split('/').pop() || '未命名文档'
    const ext = fileName.split('.').pop()?.toLowerCase() || ''
    await createDocument({
      fileName: fileName,
      fileType: ext,
      category: selectedCategory.value || 'project_doc',
      storagePath: fileUrl,
      versionNo: '1.0',
      fileSize: 0
    } as any)
    ElMessage.success('文档上传成功')
    await loadDocuments()
  } catch (e) {
    console.error(e)
    ElMessage.error('文档保存失败')
  }
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
  .preview-area { min-height: 300px; display: flex; align-items: center; justify-content: center; border: 1px dashed var(--el-border-color); border-radius: 4px; }
}
</style>
