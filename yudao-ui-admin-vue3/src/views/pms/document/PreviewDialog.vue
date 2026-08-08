<!--
  文档在线预览弹窗（#6 文件预览）
  - PDF：iframe 加载预览文件 Blob URL
  - 图片：img 标签加载预览文件 Blob URL
  - 文本：<pre> 渲染文本内容
  - 不支持的类型：显示下载按钮
  - 权限不足：后端返回错误，axios 拦截器统一提示
-->
<template>
  <el-dialog
    v-model="visible"
    :title="title || '文档预览'"
    :width="dialogWidth"
    :close-on-click-modal="false"
    destroy-on-close
    @closed="handleClosed"
  >
    <div v-loading="loading" class="preview-dialog-body">
      <!-- 元信息 -->
      <el-descriptions v-if="meta" :column="3" border size="small" class="mb-16px">
        <el-descriptions-item label="文件名">{{ meta.fileName }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ meta.fileType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="大小">{{ formatSize(meta.fileSize) }}</el-descriptions-item>
      </el-descriptions>

      <!-- 预览区域 -->
      <div class="preview-area">
        <!-- PDF -->
        <iframe
          v-if="previewType === 'pdf' && blobUrl"
          :src="blobUrl"
          class="preview-iframe"
          frameborder="0"
        />

        <!-- 图片 -->
        <div v-else-if="previewType === 'image' && blobUrl" class="image-wrap">
          <img :src="blobUrl" :alt="meta?.fileName" class="preview-image" />
        </div>

        <!-- 文本 -->
        <pre v-else-if="previewType === 'text'" class="preview-text">{{ textContent }}</pre>

        <!-- 不支持 -->
        <div v-else-if="previewType === 'unsupported'" class="unsupported-wrap">
          <el-icon :size="48" color="#C9CDD4"><Document /></el-icon>
          <p class="unsupported-tip">该文件类型不支持在线预览</p>
          <el-button type="primary" @click="handleDownload">
            <Icon icon="ep:download" class="mr-4px" />下载查看
          </el-button>
        </div>

        <!-- 加载中/空 -->
        <el-empty v-else-if="!loading" description="暂无预览内容" />
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button
        v-if="previewType !== 'unsupported'"
        type="primary"
        @click="handleDownload"
      >
        <Icon icon="ep:download" class="mr-4px" />下载
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Document } from '@element-plus/icons-vue'
import {
  previewDocument,
  fetchPreviewFileBlob,
  downloadDocument,
  type PreviewResultVO
} from '@/api/pms/document'

defineOptions({ name: 'PreviewDialog' })

const props = defineProps<{
  /** 弹窗显隐 */
  modelValue: boolean
  /** 文档ID（string，雪花ID） */
  docId?: string | number
  /** 文档名（用于标题） */
  fileName?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'downloaded'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const title = computed(() => props.fileName || '文档预览')
const dialogWidth = computed(() => {
  if (previewType.value === 'text') return '700px'
  return '900px'
})

const loading = ref(false)
const meta = ref<PreviewResultVO | null>(null)
const previewType = ref<string>('')
const blobUrl = ref<string>('')
const textContent = ref<string>('')

watch(
  () => [props.modelValue, props.docId],
  async ([val, docId]) => {
    if (val && docId) {
      await loadPreview(String(docId))
    }
  },
  { immediate: false }
)

async function loadPreview(docId: string) {
  loading.value = true
  // 重置
  previewType.value = ''
  blobUrl.value = ''
  textContent.value = ''
  meta.value = null

  try {
    // 1. 获取预览元信息
    const res = (await previewDocument(docId)) as PreviewResultVO
    meta.value = res
    previewType.value = res.previewType

    // 2. 按类型加载内容
    if (res.previewType === 'pdf' || res.previewType === 'image') {
      // 请求字节流，生成 Blob URL
      const blob = await fetchPreviewFileBlob(docId)
      blobUrl.value = window.URL.createObjectURL(blob)
    } else if (res.previewType === 'text') {
      textContent.value = res.textContent || ''
    }
    // unsupported: 无需额外加载
  } catch (e: any) {
    console.error('[PreviewDialog] 预览加载失败', e)
    // 错误提示由 axios 拦截器统一处理
  } finally {
    loading.value = false
  }
}

function handleDownload() {
  if (!props.docId) return
  downloadDocument(props.docId, props.fileName)
    .then(() => {
      ElMessage.success('下载已开始')
      emit('downloaded')
    })
    .catch((e) => {
      console.error('[PreviewDialog] 下载失败', e)
    })
}

function handleClosed() {
  // 释放 Blob URL，避免内存泄漏
  if (blobUrl.value) {
    window.URL.revokeObjectURL(blobUrl.value)
    blobUrl.value = ''
  }
  previewType.value = ''
  textContent.value = ''
  meta.value = null
}

function formatSize(bytes?: number): string {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}
</script>

<style scoped lang="scss">
.preview-dialog-body {
  min-height: 400px;
}

.preview-area {
  width: 100%;
  min-height: 500px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  overflow: hidden;
  background: #fafafa;
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-iframe {
  width: 100%;
  height: 600px;
  border: none;
}

.image-wrap {
  max-width: 100%;
  max-height: 600px;
  overflow: auto;
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-image {
  max-width: 100%;
  max-height: 600px;
  object-fit: contain;
}

.preview-text {
  width: 100%;
  max-height: 600px;
  overflow: auto;
  margin: 0;
  padding: 12px;
  font-family: 'Courier New', Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  background: #fff;
}

.unsupported-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 40px;
}

.unsupported-tip {
  font-size: 14px;
  color: #86909c;
  margin: 0;
}
</style>

