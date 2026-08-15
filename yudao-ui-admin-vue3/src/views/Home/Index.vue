<template>
  <div>
    <!-- 欢迎 + 统计卡 -->
    <el-card shadow="never">
      <el-skeleton :loading="loading" animated>
        <el-row :gutter="16" justify="space-between" class="items-center">
          <el-col :xl="13" :lg="13" :md="13" :sm="24" :xs="24">
            <div class="flex items-center">
              <el-avatar :src="avatar" :size="62" class="mr-16px">
                <img src="@/assets/imgs/avatar.gif" alt="" />
              </el-avatar>
              <div>
                <div class="text-20px font-bold">{{ username }}，欢迎回来 👋</div>
                <div class="mt-8px text-14px text-gray-500">
                  {{ todayText }} · 您有
                  <span class="text-red-500 font-bold">{{ stats.todo }}</span> 项待办、
                  <span class="text-orange-500 font-bold">{{ stats.overdue }}</span> 项已逾期
                </div>
              </div>
            </div>
          </el-col>
          <el-col :xl="11" :lg="11" :md="11" :sm="24" :xs="24">
            <div class="flex h-70px items-center justify-end lt-sm:mt-10px lt-sm:justify-start">
              <div class="px-12px text-center">
                <div class="mb-6px text-13px text-gray-400">我的待办</div>
                <CountTo class="text-22px font-bold text-blue-500" :start-val="0" :end-val="stats.todo" :duration="1500" />
              </div>
              <el-divider direction="vertical" />
              <div class="px-12px text-center">
                <div class="mb-6px text-13px text-gray-400">进行中项目</div>
                <CountTo class="text-22px font-bold text-green-500" :start-val="0" :end-val="stats.project" :duration="1500" />
              </div>
              <el-divider direction="vertical" />
              <div class="px-12px text-center">
                <div class="mb-6px text-13px text-gray-400">本周到期</div>
                <CountTo class="text-22px font-bold text-orange-500" :start-val="0" :end-val="stats.dueWeek" :duration="1500" />
              </div>
              <el-divider direction="vertical" />
              <div class="px-12px text-center">
                <div class="mb-6px text-13px text-gray-400">已逾期</div>
                <CountTo class="text-22px font-bold text-red-500" :start-val="0" :end-val="stats.overdue" :duration="1500" />
              </div>
            </div>
          </el-col>
        </el-row>
      </el-skeleton>
    </el-card>

    <el-row class="mt-8px" :gutter="8">
      <!-- 左侧：我的待办 + 我的项目 -->
      <el-col :xl="16" :lg="16" :md="24" :sm="24" :xs="24" class="mb-8px">
        <el-card shadow="never">
          <template #header>
            <div class="flex justify-between items-center">
              <span class="font-bold">我的待办任务</span>
              <el-link type="primary" :underline="false" @click="goPath('/pms/my-task-board')">全部 →</el-link>
            </div>
          </template>
          <el-table :data="todoList" size="small" stripe style="width: 100%" @row-click="onTaskRow" class="cursor-pointer">
            <el-table-column label="任务" prop="taskName" min-width="170" show-overflow-tooltip />
            <el-table-column label="所属" min-width="110" show-overflow-tooltip>
              <template #default="{ row }">{{ projectNameOf(row.projectId) }}</template>
            </el-table-column>
            <el-table-column label="优先级" width="78">
              <template #default="{ row }">
                <el-tag :type="priorityTag(row.priority)" size="small">{{ priorityLabel(row.priority) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="截止" width="100">
              <template #default="{ row }">
                <span :class="dueClass(row)">{{ row.planEndDate ? formatDate(row.planEndDate, 'MM-DD') : '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="88">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.completeStatus)" size="small">{{ statusLabel(row.completeStatus) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="todoList.length === 0 && !loading" description="太棒了，暂无待办任务 🎉" :image-size="80" />
        </el-card>

        <el-card shadow="never" class="mt-8px">
          <template #header>
            <div class="flex justify-between items-center">
              <span class="font-bold">我的项目</span>
              <el-link type="primary" :underline="false" @click="goPath('/pms/project')">全部 →</el-link>
            </div>
          </template>
          <el-table :data="projectList" size="small" stripe style="width: 100%" @row-click="onProjectRow" class="cursor-pointer">
            <el-table-column label="项目名称" prop="projectName" min-width="150" show-overflow-tooltip />
            <el-table-column label="状态" width="96">
              <template #default="{ row }">
                <el-tag :type="projStatusTag(row.status)" size="small">{{ projStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="进度" width="160">
              <template #default="{ row }">
                <el-progress :percentage="row.progress || 0" :stroke-width="10" :color="progressColor(row)" />
              </template>
            </el-table-column>
            <el-table-column label="计划结束" width="100">
              <template #default="{ row }">{{ row.planEndDate ? formatDate(row.planEndDate, 'MM-DD') : '-' }}</template>
            </el-table-column>
            <el-table-column label="剩余" width="92">
              <template #default="{ row }">
                <span :class="row.planEndDate && remainDays(row.planEndDate) < 0 ? 'text-red-500 font-bold' : 'text-gray-500'">
                  {{ row.planEndDate ? remainText(row.planEndDate) : '-' }}
                </span>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="projectList.length === 0 && !loading" description="暂无项目" :image-size="80" />
        </el-card>
      </el-col>

      <!-- 右侧：快捷入口 + 通知 -->
      <el-col :xl="8" :lg="8" :md="24" :sm="24" :xs="24" class="mb-8px">
        <el-card shadow="never">
          <template #header><span class="font-bold">快捷入口</span></template>
          <el-row :gutter="8">
            <el-col :span="8" v-for="item in shortcuts" :key="item.path" class="mb-14px text-center cursor-pointer" @click="goPath(item.path)">
              <div class="flex flex-col items-center hover:opacity-80">
                <Icon :icon="item.icon" :size="26" :style="{ color: item.color }" />
                <span class="mt-6px text-13px">{{ item.name }}</span>
              </div>
            </el-col>
          </el-row>
        </el-card>

        <el-card shadow="never" class="mt-8px">
          <template #header>
            <div class="flex justify-between items-center">
              <span class="font-bold">通知公告</span>
              <el-link type="primary" :underline="false" @click="goPath('/pms/message')">全部 →</el-link>
            </div>
          </template>
          <div v-if="noticeList.length === 0 && !loading" class="text-center text-gray-400 py-16px">暂无新通知</div>
          <div
            v-for="(n, i) in noticeList"
            :key="i"
            class="py-8px border-b border-gray-100 last:border-0"
          >
            <div class="text-14px truncate" :title="n.templateNickname">{{ n.templateNickname || '系统通知' }}</div>
            <div class="mt-4px text-12px text-gray-400 truncate" :title="stripHtml(n.templateContent)">{{ stripHtml(n.templateContent) }}</div>
            <div class="mt-2px text-12px text-gray-300">{{ n.createTime ? formatDate(n.createTime, 'MM-DD HH:mm') : '' }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script lang="ts" setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { formatTime } from '@/utils'
import { useUserStore } from '@/store/modules/user'
import { useUserNames } from '@/hooks/pms/useUserNames'
import { getTaskList } from '@/api/pms/task'
import { getProjectList } from '@/api/pms/project'
import { getUnreadNotifyMessageList } from '@/api/system/notify/message'

defineOptions({ name: 'Index' })

const router = useRouter()
const userStore = useUserStore()
const userNames = useUserNames()

const avatar = userStore.getUser.avatar
const username = userStore.getUser.nickname || userStore.getUser.username || '用户'
const todayText = formatTime(new Date(), 'yyyy年M月d日')

const loading = ref(true)
const myTasks = ref<any[]>([])
const projectList = ref<any[]>([])
const noticeList = ref<any[]>([])
const projectMap = reactive<Record<string, string>>({})

const stats = reactive({ todo: 0, project: 0, dueWeek: 0, overdue: 0 })

const todoList = computed(() => myTasks.value.filter((t) => t.completeStatus !== 'completed').slice(0, 8))

const shortcuts = [
  { name: '新建项目', path: '/pms/project-create', icon: 'ep:plus', color: '#409EFF' },
  { name: '我的看板', path: '/pms/my-task-board', icon: 'ep:grid', color: '#67C23A' },
  { name: '项目列表', path: '/pms/project', icon: 'ep:folder', color: '#E6A23C' },
  { name: '待我审核', path: '/pms/dept-review', icon: 'ep:check', color: '#F56C6C' },
  { name: '通知中心', path: '/pms/message', icon: 'ep:bell', color: '#909399' },
  { name: '延期分析', path: '/pms/delay-analysis', icon: 'ep:warning', color: '#FF6B6B' }
]

const loadTasks = async () => {
  try {
    const uid = userStore.getUser?.id
    const data = await getTaskList({ mainOwnerId: uid })
    myTasks.value = ((data as any[]) || []).filter((t) => String(t.mainOwnerId) === String(uid))
  } catch (e) {
    console.error('加载我的任务失败', e)
  }
}

const loadProjects = async () => {
  try {
    const data = (await getProjectList()) as any[]
    projectList.value = (data || []).slice(0, 8)
    ;(data || []).forEach((p) => {
      projectMap[String(p.id)] = p.projectName
    })
  } catch (e) {
    console.error('加载项目失败', e)
  }
}

const loadNotices = async () => {
  try {
    const data = (await getUnreadNotifyMessageList()) as any[]
    noticeList.value = (data || []).slice(0, 6)
  } catch (e) {
    console.error('加载通知失败', e)
  }
}

const computeStats = () => {
  const undone = myTasks.value.filter((t) => t.completeStatus !== 'completed')
  stats.todo = undone.length
  stats.project = projectList.value.length
  const now = new Date()
  now.setHours(0, 0, 0, 0)
  const weekEnd = new Date(now)
  weekEnd.setDate(weekEnd.getDate() + 7)
  let dueWeek = 0
  let overdue = 0
  undone.forEach((t) => {
    if (!t.planEndDate) return
    const d = new Date(t.planEndDate)
    if (d < now) overdue++
    else if (d <= weekEnd) dueWeek++
  })
  stats.dueWeek = dueWeek
  stats.overdue = overdue
}

onMounted(async () => {
  await userNames.ensureLoaded()
  await Promise.all([loadTasks(), loadProjects(), loadNotices()])
  computeStats()
  loading.value = false
})

// ============ 交互 ============
const goPath = (p: string) => router.push(p)
const onTaskRow = (row: any) => {
  if (row.projectId) router.push({ path: '/pms/project-detail', query: { id: row.projectId } })
  else router.push('/pms/my-task-board')
}
const onProjectRow = (row: any) => router.push({ path: '/pms/project-detail', query: { id: row.id } })

// ============ 工具 ============
const formatDate = (v: any, fmt: string) => formatTime(v, fmt)
const projectNameOf = (pid?: string | number) => {
  if (!pid) return '日常任务'
  return projectMap[String(pid)] || '项目'
}
const remainDays = (end: string) => {
  const d = new Date(end)
  const now = new Date()
  return Math.ceil((d.setHours(0, 0, 0, 0) - now.setHours(0, 0, 0, 0)) / 86400000)
}
const remainText = (end: string) => {
  const n = remainDays(end)
  return n >= 0 ? '剩' + n + '天' : '逾期' + -n + '天'
}
const dueClass = (row: any) => {
  if (!row.planEndDate || row.completeStatus === 'completed') return 'text-gray-400'
  const n = remainDays(row.planEndDate)
  if (n < 0) return 'text-red-500 font-bold'
  if (n <= 7) return 'text-orange-500'
  return 'text-gray-500'
}

const priorityMap: Record<string, { l: string; t: any }> = {
  high: { l: '高', t: 'danger' },
  medium: { l: '中', t: 'warning' },
  low: { l: '低', t: 'info' },
  urgent: { l: '紧急', t: 'danger' }
}
const priorityLabel = (p?: string) => priorityMap[p || '']?.l || p || '未设'
const priorityTag = (p?: string) => priorityMap[p || '']?.t || 'info'

const statusMap: Record<string, { l: string; t: any }> = {
  none: { l: '未开始', t: 'info' },
  in_progress: { l: '进行中', t: 'warning' },
  completed: { l: '已完成', t: 'success' },
  pending: { l: '待开始', t: 'info' }
}
const statusLabel = (s?: string) => statusMap[s || '']?.l || s || '未知'
const statusTag = (s?: string) => statusMap[s || '']?.t || 'info'

const projStatusMap: Record<string, { l: string; t: any }> = {
  initiation: { l: '启动', t: 'info' },
  in_progress: { l: '进行中', t: 'primary' },
  completed: { l: '已完成', t: 'success' },
  paused: { l: '暂停', t: 'warning' },
  cancelled: { l: '已取消', t: 'danger' }
}
const projStatusLabel = (s?: string) => projStatusMap[s || '']?.l || s || '未知'
const projStatusTag = (s?: string) => projStatusMap[s || '']?.t || 'info'

const progressColor = (row: any) => {
  const p = row.progress || 0
  if (p >= 100) return '#67C23A'
  if (row.planEndDate && remainDays(row.planEndDate) < 0) return '#F56C6C'
  return '#409EFF'
}

const stripHtml = (html?: string) => {
  if (!html) return ''
  return String(html)
    .replace(/<[^>]+>/g, '')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 60)
}
</script>
