<template>
  <div class="social-login-buttons" v-if="showDingTalk">
    <el-divider content-position="center">
      <span class="text-gray-400 text-sm">其他登录方式</span>
    </el-divider>
    <div class="flex justify-center gap-3">
      <el-button
        circle
        size="large"
        @click="handleDingTalkLogin"
        :loading="dingTalkLoading"
        title="钉钉登录"
      >
        <svg viewBox="0 0 1024 1024" width="24" height="24" style="fill: #1677ff;">
          <path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zM384 640h-64v-256h64v256z m320 0H448v-64h256v64z m0-128H448v-64h256v64z m0-128H448v-64h256v64z"/>
        </svg>
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import * as LoginApi from '@/api/login'

const showDingTalk = ref(false)
const dingTalkLoading = ref(false)

onMounted(async () => {
  // 检查是否已配置钉钉社交客户端
  try {
    const res = await LoginApi.getSocialAuthRedirect(20, window.location.origin + '/login?type=20&redirect=/')
    if (res) {
      showDingTalk.value = true
    }
  } catch (e) {
    // 钉钉未配置，不显示按钮
  }
})

const handleDingTalkLogin = async () => {
  dingTalkLoading.value = true
  try {
    const redirectUri = window.location.origin + '/login?type=20&redirect=/'
    const authUrl = await LoginApi.getSocialAuthRedirect(20, redirectUri)
    if (authUrl) {
      window.location.href = authUrl
    }
  } catch (e) {
    console.error('钉钉登录跳转失败', e)
  } finally {
    dingTalkLoading.value = false
  }
}
</script>
