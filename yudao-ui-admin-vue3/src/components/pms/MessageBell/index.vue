<script setup lang="ts">
/**
 * PMS 站内消息铃铛（#4 站内消息中心）
 *
 * 功能：
 *   - 顶栏铃铛图标 + 未读红点（轮询 countUnread，30s 一次）
 *   - 点击铃铛弹出未读消息列表（el-popover）
 *   - 列表项点击 → 标记已读 + 跳转对应业务页
 *   - 「全部已读」按钮
 *   - 「查看全部」按钮 → 跳转消息中心页
 *
 * 部署：在 layout/components/ToolHeader.vue 末尾挂载，v-if 控制显示
 *      （需 checkPermi(['pms:message:list'])）
 */
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getUnreadCount,
  getUnreadList,
  markRead,
  markAllRead,
  PmsMessageVO
} from '@/api/pms/message'
import { getTask } from '@/api/pms/task'
import { formatDate } from '@/utils/formatTime'

const router = useRouter()

const unreadCount = ref(0)
const list = ref<PmsMessageVO[]>([])
const loading = ref(false)
let unreadCountTimer: ReturnType<typeof setInterval> | undefined

// 轮询间隔：30s（产品需求规定）
const POLL_INTERVAL = 30 * 1000

/** 拉取未读数（轮询用，不弹错误） */
const refreshUnreadCount = async () => {
  try {
    const res = await getUnreadCount()
    unreadCount.value = (res as any)?.count || 0
  } catch (e) {
    // 静默失败：轮询失败不弹窗
    console.warn('[PmsMessageBell] 拉取未读数失败', e)
  }
}

/** 拉取未读列表（点开铃铛时调用） */
const refreshList = async () => {
  loading.value = true
  try {
    const res = await getUnreadList()
    list.value = (res as PmsMessageVO[]) || []
  } catch (e) {
    console.warn('[PmsMessageBell] 拉取未读列表失败', e)
  } finally {
    loading.value = false
  }
}

/** 标记单条已读 + 跳转业务页 */
const handleMessageClick = async (msg: PmsMessageVO) => {
  // 先标记已读
  try {
    await markRead([msg.messageId])
    // 本地立刻更新：把这条从列表移除
    list.value = list.value.filter((m) => m.messageId !== msg.messageId)
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  } catch (e) {
    // 失败不阻塞跳转
    console.warn('[PmsMessageBell] 标记已读失败', e)
  }
  // 跳转业务页
  navigateToBiz(msg)
}

/** 全部已读 */
const handleMarkAllRead = async () => {
  try {
    await markAllRead()
    list.value = []
    unreadCount.value = 0
    ElMessage.success('已全部标记为已读')
  } catch (e) {
    ElMessage.error('操作失败，请稍后重试')
  }
}

/** 跳转消息中心页 */
const goMessageCenter = () => {
  // 跳转到消息中心路由（菜单需在 SQL 中插入）
  router.push({ name: 'PmsMessageCenter' })
}

/**
 * 跳转业务页。根据 bizType 决定：
 *   task    → 先调 getTask 拿 projectId，再跳 /pms/project-detail/<projectId>
 *   project → 直接 /pms/project-detail/<bizId>
 *   其它    → 跳 /pms/dashboard（兜底）
 */
const navigateToBiz = async (msg: PmsMessageVO) => {
  const bizType = msg.bizType
  const bizId = msg.bizId
  if (!bizId) {
    router.push('/pms/dashboard')
    return
  }
  if (bizType === 'project') {
    router.push(`/pms/project-detail/${bizId}`)
    return
  }
  if (bizType === 'task') {
    try {
      const task = (await getTask(bizId)) as any
      if (task && task.projectId) {
        router.push(`/pms/project-detail/${task.projectId}`)
      } else {
        router.push('/pms/dashboard')
      }
    } catch (e) {
      // 查询任务失败兜底
      router.push('/pms/dashboard')
    }
    return
  }
  // 其它 bizType 兜底
  router.push('/pms/dashboard')
}

onMounted(() => {
  // 首次加载红点
  refreshUnreadCount()
  // 30s 轮询
  unreadCountTimer = setInterval(refreshUnreadCount, POLL_INTERVAL)
})

onBeforeUnmount(() => {
  if (unreadCountTimer) {
    clearInterval(unreadCountTimer)
    unreadCountTimer = undefined
  }
})

// 暴露给父组件调用的方法（如消息发送后立刻刷新红点）
defineExpose({
  refreshUnreadCount
})
</script>

<template>
  <div class="pms-message-bell">
    <ElPopover :width="400" placement="bottom" trigger="click" @show="refreshList">
      <template #reference>
        <ElBadge :is-dot="unreadCount > 0" class="item">
          <Icon :size="18" class="cursor-pointer" icon="ep:bell" />
        </ElBadge>
      </template>
      <div class="bell-header">
        <span class="bell-title">PMS 站内消息</span>
        <el-button
          v-if="unreadCount > 0"
          type="primary"
          link
          size="small"
          @click="handleMarkAllRead"
        >
          全部已读
        </el-button>
      </div>
      <el-scrollbar class="message-list" v-loading="loading">
        <div v-if="list.length === 0" class="message-empty">暂无未读消息</div>
        <div
          v-for="item in list"
          :key="item.messageId"
          class="message-item"
          @click="handleMessageClick(item)"
        >
          <div class="message-dot" />
          <div class="message-content">
            <div class="message-title">{{ item.title }}</div>
            <div class="message-text">{{ item.content }}</div>
            <div class="message-date">{{ formatDate(item.createTime) }}</div>
          </div>
        </div>
      </el-scrollbar>
      <div class="bell-footer">
        <el-button type="primary" size="small" @click="goMessageCenter">
          <Icon icon="ep:view" class="mr-1px" /> 查看全部
        </el-button>
      </div>
    </ElPopover>
  </div>
</template>

<style lang="scss" scoped>
// 与系统级 Message 组件对齐风格，避免视觉割裂
.pms-message-bell {
  display: flex;
  align-items: center;
  height: 100%;

  :deep(.el-badge) {
    display: flex;
    align-items: center;
  }
}

.bell-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);

  .bell-title {
    font-weight: 600;
    color: var(--el-text-color-primary);
  }
}

.message-list {
  display: flex;
  height: 360px;
  flex-direction: column;
}

.message-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: var(--el-text-color-secondary);
}

.message-item {
  display: flex;
  align-items: flex-start;
  padding: 12px 8px;
  border-bottom: 1px solid var(--el-border-color-light);
  cursor: pointer;
  transition: background 0.15s;

  &:hover {
    background: var(--el-fill-color-light);
  }

  &:last-child {
    border: none;
  }

  .message-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--el-color-primary);
    margin-top: 6px;
    margin-right: 8px;
    flex-shrink: 0;
  }

  .message-content {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;

    .message-title {
      font-weight: 500;
      color: var(--el-text-color-primary);
      margin-bottom: 4px;
      white-space: nowrap;
      text-overflow: ellipsis;
      overflow: hidden;
    }

    .message-text {
      font-size: 13px;
      color: var(--el-text-color-regular);
      margin-bottom: 4px;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }

    .message-date {
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }
  }
}

.bell-footer {
  margin-top: 10px;
  text-align: right;
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 8px;
}
</style>

