<script setup lang="ts">
/**
 * PMS 消息中心（#4 站内消息中心）
 *
 * 功能：
 *   - 分页查询当前用户消息（支持按未读/已读筛选）
 *   - 单条标记已读 / 全部已读
 *   - 点击消息跳转对应业务页
 */
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getMessagePage,
  markRead,
  markAllRead,
  PmsMessageVO
} from '@/api/pms/message'
import { getTask } from '@/api/pms/task'
import { formatDate } from '@/utils/formatTime'

defineOptions({ name: 'PmsMessageCenter' })

const router = useRouter()

const loading = ref(false)
const tableData = ref<PmsMessageVO[]>([])
const total = ref(0)
const queryParams = reactive({
  readStatus: undefined as number | undefined,
  pageNo: 1,
  pageSize: 20
})

const getList = async () => {
  loading.value = true
  try {
    const res = await getMessagePage(queryParams)
    tableData.value = (res?.list as PmsMessageVO[]) || []
    total.value = res?.total || 0
  } catch (e) {
    console.error('[PmsMessageCenter] 加载失败', e)
  } finally {
    loading.value = false
  }
}

/** 切换"未读/已读/全部" */
const handleTabChange = (val: string) => {
  queryParams.readStatus = val === 'all' ? undefined : Number(val)
  queryParams.pageNo = 1
  getList()
}

/** 单条点击：标已读 + 跳转业务页 */
const handleMessageClick = async (row: PmsMessageVO) => {
  if (row.readStatus === 0) {
    try {
      await markRead([row.messageId])
      // 本地立刻更新
      row.readStatus = 1
    } catch (e) {
      // 失败不阻塞跳转
      console.warn('[PmsMessageCenter] 标记已读失败', e)
    }
  }
  navigateToBiz(row)
}

/** 全部已读 */
const handleMarkAllRead = async () => {
  try {
    await ElMessageBox.confirm('确定将所有未读消息标记为已读？', '提示', {
      type: 'warning'
    })
    const n = await markAllRead()
    ElMessage.success(`已标记 ${n || 0} 条消息为已读`)
    getList()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('操作失败，请稍后重试')
    }
  }
}

/** 跳转业务页：bizType=task/project 跳项目详情，其它兜底 dashboard */
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
      router.push('/pms/dashboard')
    }
    return
  }
  router.push('/pms/dashboard')
}

onMounted(() => {
  getList()
})
</script>

<template>
  <div class="pms-message-center">
    <ContentWrap>
      <div class="header-bar">
        <el-radio-group :model-value="queryParams.readStatus ?? 'all'" @update:model-value="handleTabChange">
          <el-radio-button value="all">全部</el-radio-button>
          <el-radio-button :value="0">未读</el-radio-button>
          <el-radio-button :value="1">已读</el-radio-button>
        </el-radio-group>
        <el-button type="primary" @click="handleMarkAllRead">
          <Icon icon="ep:check" class="mr-5px" />全部已读
        </el-button>
      </div>
    </ContentWrap>

    <ContentWrap>
      <el-table
        v-loading="loading"
        :data="tableData"
        stripe
        @row-click="handleMessageClick"
      >
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag
              :type="row.readStatus === 0 ? 'danger' : 'info'"
              size="small"
              effect="light"
            >
              {{ row.readStatus === 0 ? '未读' : '已读' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标题" prop="title" min-width="200" show-overflow-tooltip />
        <el-table-column label="内容" prop="content" min-width="300" show-overflow-tooltip />
        <el-table-column label="业务类型" prop="bizType" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.bizType || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="接收时间" width="170">
          <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="阅读时间" width="170">
          <template #default="{ row }">
            {{ row.readTime ? formatDate(row.readTime) : '-' }}
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="queryParams.pageNo"
          v-model:page-size="queryParams.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="getList"
          @current-change="getList"
        />
      </div>
    </ContentWrap>
  </div>
</template>

<style lang="scss" scoped>
.pms-message-center {
  .header-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .pagination-bar {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }
}
</style>

