<template>
  <div class="dingtalk-login-container">
    <div class="dingtalk-login-card">
      <!-- Loading state -->
      <div v-if="status === 'loading'" class="dingtalk-login-content">
        <el-icon class="is-loading dingtalk-spinner" :size="48">
          <Loading />
        </el-icon>
        <p class="dingtalk-status-text">{{ statusText }}</p>
      </div>

      <!-- Success state (brief flash before redirect) -->
      <div v-else-if="status === 'success'" class="dingtalk-login-content">
        <el-icon :size="48" color="#67c23a">
          <CircleCheckFilled />
        </el-icon>
        <p class="dingtalk-status-text">登录成功，正在跳转...</p>
      </div>

      <!-- Error state -->
      <div v-else-if="status === 'error'" class="dingtalk-login-content">
        <el-icon :size="48" color="#f56c6c">
          <CircleCloseFilled />
        </el-icon>
        <p class="dingtalk-status-text">{{ statusText }}</p>
        <el-button type="primary" class="dingtalk-retry-btn" @click="goLogin">
          前往登录页
        </el-button>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Loading, CircleCheckFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import * as LoginApi from '@/api/login'
import * as authUtil from '@/utils/auth'

defineOptions({ name: 'DingTalkLogin' })

const router = useRouter()
const status = ref<'loading' | 'success' | 'error'>('loading')
const statusText = ref('正在初始化钉钉登录...')

/** 动态加载钉钉 JS SDK */
function loadDingTalkScript(): Promise<void> {
  return new Promise((resolve, reject) => {
    if (window.dd) {
      resolve()
      return
    }
    const script = document.createElement('script')
    script.src = 'https://g.alicdn.com/dingding/dingtalk-jsapi/3.0.25/dingtalk.open.js'
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('钉钉 SDK 加载失败'))
    document.head.appendChild(script)
  })
}

/** 通过钉钉 JSAPI 获取免登码 authCode */
function getAuthCode(corpId: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const dd = (window as any).dd
    if (!dd || !dd.runtime || !dd.runtime.permission) {
      reject(new Error('钉钉 SDK 未正确加载，请在钉钉客户端中打开'))
      return
    }
    dd.runtime.permission.requestAuthCode({
      corpId: corpId,
      onSuccess: (result: { code: string }) => {
        resolve(result.code)
      },
      onFail: (err: any) => {
        reject(new Error('获取免登码失败: ' + JSON.stringify(err)))
      }
    })
  })
}

/** 跳转到登录页 */
function goLogin() {
  router.push('/login')
}

/** 主流程 */
async function dingTalkLogin() {
  try {
    // 1. 获取钉钉配置（corpId）
    statusText.value = '正在获取钉钉配置...'
    const corpId = await LoginApi.getDingTalkConfig()
    if (!corpId) {
      status.value = 'error'
      statusText.value = '钉钉企业 CorpId 未配置，请联系管理员在后台设置'
      return
    }

    // 2. 加载钉钉 JS SDK
    statusText.value = '正在加载钉钉 SDK...'
    await loadDingTalkScript()

    // 3. 获取免登码
    statusText.value = '正在获取钉钉授权...'
    const authCode = await getAuthCode(corpId)

    // 4. 调用后端免登接口
    statusText.value = '正在登录系统...'
    const res = await LoginApi.dingtalkLogin(authCode)
    if (!res) {
      status.value = 'error'
      statusText.value = '登录返回为空，请重试'
      return
    }

    // 5. 存储 token，跳转首页
    status.value = 'success'
    statusText.value = '登录成功，正在跳转...'
    authUtil.setToken(res)
    setTimeout(() => {
      router.push('/')
    }, 500)

  } catch (err: any) {
    status.value = 'error'
    // 判断是否为未绑定错误
    const errMsg = (typeof err === 'string' ? err : (err?.msg || err?.message)) || '未知错误'
    if (errMsg.includes('未绑定')) {
      statusText.value = '钉钉账号未绑定系统用户，请先登录系统并绑定钉钉账号'
    } else {
      statusText.value = '钉钉登录失败: ' + errMsg
    }
    console.error('[DingTalkLogin] error:', err)
  }
}

onMounted(() => {
  dingTalkLogin()
})
</script>

<style lang="scss" scoped>
.dingtalk-login-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #e8f0fe 0%, #f5f7fa 100%);
}

.dingtalk-login-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 80px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  min-width: 400px;
}

.dingtalk-login-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

.dingtalk-spinner {
  color: #409eff;
}

.dingtalk-status-text {
  font-size: 16px;
  color: #606266;
  text-align: center;
  margin: 0;
  line-height: 1.6;
}

.dingtalk-retry-btn {
  margin-top: 10px;
}
</style>
