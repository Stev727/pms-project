<template>
  <div class="app-container">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- 配置管理 -->
      <el-tab-pane label="钉钉配置" name="config">
        <el-form :model="config" label-width="140px" style="max-width: 600px">
          <el-form-item label="AppKey">
            <el-input v-model="config.appKey" placeholder="钉钉应用 AppKey" />
          </el-form-item>
          <el-form-item label="AppSecret">
            <el-input v-model="config.appSecret" type="password" show-password placeholder="钉钉应用 AppSecret" />
          </el-form-item>
          <el-form-item label="AgentId">
            <el-input-number v-model="config.agentId" :min="0" controls-position="right" placeholder="工作通知 AgentId" />
          </el-form-item>
          <el-form-item label="CorpId">
            <el-input v-model="config.corpId" placeholder="企业 CorpId（可选）" />
          </el-form-item>
          <el-form-item label="功能开关">
            <div style="display: flex; flex-direction: column; gap: 10px">
              <el-switch v-model="config.ssoEnabled" active-text="免登（SSO）" />
              <el-switch v-model="config.notifyEnabled" active-text="消息通知" />
              <el-switch v-model="config.syncEnabled" active-text="组织架构同步" />
            </div>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="saveConfig">保存配置</el-button>
            <el-button @click="testConnection">测试连接</el-button>
          </el-form-item>
        </el-form>
        <el-alert v-if="testResult" :title="testResult" :type="testResult.startsWith('OK') ? 'success' : 'error'"
          closable style="max-width: 600px; margin-top: 10px" />
      </el-tab-pane>

      <!-- 组织架构同步 -->
      <el-tab-pane label="组织架构同步" name="sync">
        <div style="margin-bottom: 20px">
          <el-button type="primary" @click="handleSyncAll" :loading="syncLoading">全量同步</el-button>
          <el-button @click="handleSyncDepts" :loading="deptLoading">仅同步部门</el-button>
          <el-button @click="handleSyncUsers" :loading="userLoading">仅同步用户</el-button>
          <el-button @click="loadSyncStatus">刷新状态</el-button>
        </div>

        <el-descriptions :column="2" border>
          <el-descriptions-item label="同步状态">
            <el-tag :type="syncStatus.syncEnabled ? 'success' : 'info'">
              {{ syncStatus.syncEnabled ? '已启用' : '未启用' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="配置状态">
            <el-tag>{{ syncStatus.configStatus || '-' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="已同步部门数">{{ syncStatus.totalSyncedDepts || 0 }}</el-descriptions-item>
          <el-descriptions-item label="已同步用户数">{{ syncStatus.totalSyncedUsers || 0 }}</el-descriptions-item>
          <el-descriptions-item label="最后部门同步时间">{{ syncStatus.lastDeptSync || '未同步' }}</el-descriptions-item>
          <el-descriptions-item label="最后用户同步时间">{{ syncStatus.lastUserSync || '未同步' }}</el-descriptions-item>
        </el-descriptions>

        <el-alert v-if="syncResult" :title="JSON.stringify(syncResult)" type="info" closable
          style="margin-top: 15px" />
      </el-tab-pane>

      <!-- 通知测试 -->
      <el-tab-pane label="通知测试" name="notify">
        <el-form :model="notifyForm" label-width="100px" style="max-width: 600px">
          <el-form-item label="接收人ID">
            <el-input-number v-model="notifyForm.userId" :min="1" controls-position="right" placeholder="系统用户ID" />
          </el-form-item>
          <el-form-item label="消息标题">
            <el-input v-model="notifyForm.title" placeholder="通知标题" />
          </el-form-item>
          <el-form-item label="消息内容">
            <el-input v-model="notifyForm.content" type="textarea" :rows="4" placeholder="通知内容" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="sendTest" :loading="notifyLoading">发送测试通知</el-button>
            <el-button @click="triggerCheck">手动触发通知检查</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as DingTalkApi from '@/api/dingtalk'

defineOptions({ name: 'PmsDingTalk' })

const activeTab = ref('config')
const testResult = ref('')
const syncLoading = ref(false)
const deptLoading = ref(false)
const userLoading = ref(false)
const notifyLoading = ref(false)
const syncResult = ref<any>(null)

const config = reactive({
  configId: undefined,
  appKey: '',
  appSecret: '',
  agentId: 0,
  corpId: '',
  ssoEnabled: true,
  notifyEnabled: true,
  syncEnabled: true,
  status: 'enabled',
  remark: ''
})

const syncStatus = reactive<any>({
  syncEnabled: false,
  configStatus: '',
  totalSyncedDepts: 0,
  totalSyncedUsers: 0,
  lastDeptSync: '',
  lastUserSync: ''
})

const notifyForm = reactive({
  userId: 1,
  title: '【PMS测试】钉钉通知测试',
  content: '这是一条来自PMS研发项目管理系统的测试通知。'
})

onMounted(async () => {
  await Promise.all([loadConfig(), loadSyncStatus()])
})

const loadConfig = async () => {
  try {
    const res = await DingTalkApi.getDingTalkConfig()
    Object.assign(config, res)
  } catch (e) {
    // 首次加载，使用默认值
  }
}

const saveConfig = async () => {
  try {
    await DingTalkApi.updateDingTalkConfig(config)
    ElMessage.success('配置保存成功')
  } catch (e) {
    ElMessage.error('配置保存失败')
  }
}

const testConnection = async () => {
  try {
    const res = await DingTalkApi.testDingTalkConnection()
    testResult.value = res
  } catch (e) {
    testResult.value = 'FAIL: 请求失败'
  }
}

const loadSyncStatus = async () => {
  try {
    const res = await DingTalkApi.getSyncStatus()
    Object.assign(syncStatus, res)
  } catch (e) {
    // ignore
  }
}

const handleSyncAll = async () => {
  syncLoading.value = true
  try {
    const res = await DingTalkApi.syncAll()
    syncResult.value = res
    ElMessage.success('全量同步完成')
    await loadSyncStatus()
  } catch (e) {
    ElMessage.error('同步失败')
  } finally {
    syncLoading.value = false
  }
}

const handleSyncDepts = async () => {
  deptLoading.value = true
  try {
    const res = await DingTalkApi.syncDepartments()
    syncResult.value = res
    ElMessage.success('部门同步完成')
    await loadSyncStatus()
  } catch (e) {
    ElMessage.error('部门同步失败')
  } finally {
    deptLoading.value = false
  }
}

const handleSyncUsers = async () => {
  userLoading.value = true
  try {
    const res = await DingTalkApi.syncUsers()
    syncResult.value = res
    ElMessage.success('用户同步完成')
    await loadSyncStatus()
  } catch (e) {
    ElMessage.error('用户同步失败')
  } finally {
    userLoading.value = false
  }
}

const sendTest = async () => {
  notifyLoading.value = true
  try {
    await DingTalkApi.sendTestNotify(notifyForm)
    ElMessage.success('测试通知已发送')
  } catch (e) {
    ElMessage.error('通知发送失败')
  } finally {
    notifyLoading.value = false
  }
}

const triggerCheck = async () => {
  try {
    await DingTalkApi.triggerNotifyCheck()
    ElMessage.success('通知检查已触发，请查看日志')
  } catch (e) {
    ElMessage.error('触发失败')
  }
}
</script>
