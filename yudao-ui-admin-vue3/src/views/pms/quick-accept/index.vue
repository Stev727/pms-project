<template>
  <div class="quick-accept-page">
    <div v-if="status === 'loading'" class="qa-container">
      <div class="qa-card">
        <div class="qa-spinner"></div>
        <p class="qa-text">正在接收任务...</p>
      </div>
    </div>
    <div v-else-if="status === 'success'" class="qa-container">
      <div class="qa-card qa-success">
        <div class="qa-icon qa-icon-success">✅</div>
        <h2 class="qa-title">任务已接收</h2>
        <p class="qa-desc">任务「{{ taskName }}」已成功接收，状态变更为<span class="qa-highlight">进行中</span>。</p>
        <div class="qa-actions">
          <el-button type="primary" @click="viewDetail">查看任务详情</el-button>
        </div>
      </div>
    </div>
    <div v-else-if="status === 'error'" class="qa-container">
      <div class="qa-card qa-error">
        <div class="qa-icon qa-icon-error">❌</div>
        <h2 class="qa-title">接收失败</h2>
        <p class="qa-desc">{{ errorMsg }}</p>
        <div class="qa-actions">
          <el-button @click="retry">重试</el-button>
          <el-button type="primary" plain @click="viewDetail">查看任务详情</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { acceptTask, getTask } from '@/api/pms/task'

const route = useRoute()
const router = useRouter()

const status = ref<'loading' | 'success' | 'error'>('loading')
const taskName = ref('')
const errorMsg = ref('')
const taskId = route.query.taskId as string

const doAccept = async () => {
  if (!taskId) {
    status.value = 'error'
    errorMsg.value = '缺少任务ID参数'
    return
  }
  try {
    await acceptTask(taskId)
    try { const t = await getTask(taskId); taskName.value = t?.taskName || taskId } catch {}
    status.value = 'success'
  } catch (e: any) {
    console.error('接收任务失败', e)
    status.value = 'error'
    errorMsg.value = e?.msg || e?.message || '未知错误，请稍后重试'
  }
}

const retry = () => { status.value = 'loading'; doAccept() }

const viewDetail = () => { router.push('/') }

onMounted(() => { doAccept() })
</script>

<style scoped>
.quick-accept-page {
  min-height: 100vh; display: flex; align-items: center; justify-content: center;
  background: #f5f7fa;
}
.qa-container { width: 100%; max-width: 420px; padding: 20px; }
.qa-card {
  background: #fff; border-radius: 12px; padding: 40px 30px; text-align: center;
  box-shadow: 0 4px 20px rgba(0,0,0,0.08);
}
.qa-icon { font-size: 48px; margin-bottom: 16px; }
.qa-title { font-size: 20px; font-weight: 600; color: #303133; margin: 0 0 12px; }
.qa-desc { font-size: 14px; color: #606266; line-height: 1.6; margin: 0 0 24px; }
.qa-highlight { color: #409eff; font-weight: 500; }
.qa-actions { display: flex; gap: 12px; justify-content: center; }
.qa-spinner {
  width: 40px; height: 40px; border: 3px solid #e4e7ed; border-top-color: #409eff;
  border-radius: 50%; animation: spin 0.8s linear infinite; margin: 0 auto 16px;
}
@keyframes spin { to { transform: rotate(360deg); } }
.qa-text { color: #909399; margin: 0; }
</style>
