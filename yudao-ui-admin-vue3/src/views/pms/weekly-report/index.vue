<template>
  <div class="p-20px">
    <ContentWrap title="周报看板">
      <el-form :inline="true" class="mb-4">
        <el-form-item label="基准日期" required>
          <el-date-picker
            v-model="baseDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="默认今天"
            :clearable="false"
          />
        </el-form-item>
        <el-form-item label="人员">
          <el-select
            v-model="selectedUsers"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            :placeholder="boardScope.isAdmin ? '默认本人 / 选「全公司」看全部' : '默认本人'"
            style="width: 300px"
          >
            <el-option v-if="boardScope.isAdmin" :key="ALL_SENTINEL" label="🌐 全公司（全部人员）" :value="ALL_SENTINEL" />
            <el-option v-for="u in selectableUsers" :key="u.id" :label="u.nickname" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load" :loading="loading">
            <Icon icon="ep:search" class="mr-5px" />查询
          </el-button>
        </el-form-item>
        <el-form-item>
          <el-tag type="info" effect="plain">本周 {{ report.weekStart }} ~ {{ report.weekEnd }}</el-tag>
          <el-tag type="warning" effect="plain" class="ml-8px">上周 {{ report.lastWeekStart }} ~ {{ report.lastWeekEnd }}</el-tag>
        </el-form-item>
      </el-form>
    </ContentWrap>

    <div v-loading="loading">
      <!-- A 上周完成 -->
      <el-card class="section" shadow="never">
        <template #header>
          <div class="section-header">
            <span class="section-title">✅ 上周完成</span>
            <el-tag type="success" effect="plain">{{ report.lastWeekCompleted.length }}</el-tag>
            <span class="section-hint">上周（{{ report.lastWeekEnd }} 及之前）实际完成归档的任务</span>
          </div>
        </template>
        <el-empty v-if="report.lastWeekCompleted.length === 0" description="上周无完成任务" :image-size="60" />
        <div v-else class="task-list">
          <task-board-card :show-header="true" />
          <task-board-card
            v-for="t in report.lastWeekCompleted"
            :key="t.taskId"
            :task="t"
            :current-user-id="currentUserId"
            @detail="openDetail"
          />
        </div>
      </el-card>

      <!-- B 本周计划 -->
      <el-card class="section" shadow="never">
        <template #header>
          <div class="section-header">
            <span class="section-title">📋 本周计划</span>
            <el-tag type="primary" effect="plain">{{ report.thisWeekPlan.length }}</el-tag>
            <span class="section-hint">未完成且计划窗口与本周（{{ report.weekStart }}~{{ report.weekEnd }}）重叠的任务</span>
          </div>
        </template>
        <el-empty v-if="report.thisWeekPlan.length === 0" description="本周无计划任务" :image-size="60" />
        <div v-else class="task-list">
          <task-board-card :show-header="true" />
          <task-board-card
            v-for="t in report.thisWeekPlan"
            :key="t.taskId"
            :task="t"
            :current-user-id="currentUserId"
            @detail="openDetail"
          />
        </div>
      </el-card>

      <!-- C 上周延期 -->
      <el-card class="section" shadow="never">
        <template #header>
          <div class="section-header">
            <span class="section-title">⏰ 上周延期</span>
            <el-tag type="danger" effect="plain">{{ report.lastWeekDelayed.length }}</el-tag>
            <span class="section-hint">已启动/流转过但未完成，且计划结束日期早于上周末（{{ report.lastWeekEnd }}）</span>
          </div>
        </template>
        <el-empty v-if="report.lastWeekDelayed.length === 0" description="上周无延期任务" :image-size="60" />
        <div v-else class="delay-list">
          <div v-for="d in report.lastWeekDelayed" :key="d.task.taskId" class="delay-row">
            <task-board-card :task="d.task" :current-user-id="currentUserId" @detail="openDetail" />
            <el-tag type="danger" effect="dark" class="delay-badge">
              逾期 {{ d.overdueDays }} 天（截至 {{ report.lastWeekEnd }}）
            </el-tag>
          </div>
        </div>
      </el-card>

      <!-- D 上周动态 -->
      <el-card class="section" shadow="never">
        <template #header>
          <div class="section-header">
            <span class="section-title">🔄 上周动态（状态/进度变更）</span>
            <el-tag type="info" effect="plain">{{ report.lastWeekChanges.length }}</el-tag>
            <span class="section-hint">上周内发生的状态/进度变更（方案2：精确前后值）</span>
          </div>
        </template>
        <el-empty v-if="report.lastWeekChanges.length === 0" description="上周无状态/进度变更记录" :image-size="60" />
        <div v-else class="change-list">
          <div v-for="c in report.lastWeekChanges" :key="c.taskId" class="change-group">
            <div class="change-group-title">
              <Icon icon="ep:document" class="mr-5px" />
              {{ c.taskName }}
              <el-tag v-if="c.projectName" size="small" effect="plain" type="primary" class="ml-8px">{{ c.projectName }}</el-tag>
            </div>
            <div v-for="(it, i) in (c.changes || [])" :key="i" class="change-item">
              <el-tag size="small" :type="it.operationType === 'progress_update' ? 'warning' : 'info'" effect="plain">
                {{ it.operationType === 'progress_update' ? '进度' : it.operationType === 'status_change' ? '状态' : (it.operationType || '') }}
              </el-tag>
              <span class="change-value">
                {{ formatChangeValue(it.operationType, it.beforeValue) }}
                <Icon icon="ep:right" class="mx-4px" />
                {{ formatChangeValue(it.operationType, it.afterValue) }}
              </span>
              <span class="change-time" v-if="it.operationTime">{{ formatDateTime(it.operationTime) }}</span>
              <span class="change-operator" v-if="it.operatorName">· {{ it.operatorName }}</span>
            </div>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 任务详情抽屉（复用项目详情抽屉，只读为主） -->
    <TaskDetailDrawer ref="taskDrawerRef" />
  </div>
</template>

<script setup lang="ts">
import { getWeeklyReport, getBoardScope, TaskVO, WeeklyReportVO } from '@/api/pms/task'
import TaskDetailDrawer from '../project-detail/TaskDetailDrawer.vue'
import TaskBoardCard from '../my-task-board/components/TaskBoardCard.vue'
import { taskStatusMap, formatDate } from '../pms-utils'
import { useUserNames } from '@/hooks/pms/useUserNames'
import { useUserStore } from '@/store/modules/user'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

defineOptions({ name: 'PmsWeeklyReport' })

const { userList, ensureLoaded: ensureUsersLoaded } = useUserNames()
const userStore = useUserStore()
const currentUserId = computed(() => userStore.getUser?.id)

const loading = ref(false)
const baseDate = ref<string>(formatDate(new Date(), 'YYYY-MM-DD'))
const selectedUsers = ref<(string | number)[]>([])
const ALL_SENTINEL = '__ALL__'

// 看板人员范围（权限判定）：管理员允许全部、领导=本人+下属、非领导=本人
const boardScope = ref<{
  isAdmin?: boolean
  isLeader?: boolean
  loginUserId?: number
  allowedUserIds?: number[] | null
}>({})

const selectableUsers = computed(() => {
  const al = boardScope.value.allowedUserIds
  if (al == null) return userList.value
  const set = new Set(al.map((id) => String(id)))
  return userList.value.filter((u: any) => set.has(String(u.id)))
})

const report = reactive<WeeklyReportVO>({
  weekStart: '',
  weekEnd: '',
  lastWeekStart: '',
  lastWeekEnd: '',
  targetUserId: undefined,
  isAdmin: false,
  isLeader: false,
  lastWeekCompleted: [],
  thisWeekPlan: [],
  lastWeekDelayed: [],
  lastWeekChanges: []
})

const taskDrawerRef = ref()
const openDetail = (task: TaskVO) => taskDrawerRef.value?.open(task)

// 方案2：精确前后值格式化（状态→中文标签；进度→百分比）
const formatChangeValue = (op?: string, v?: string) => {
  if (v == null || v === '') return '—'
  if (op === 'progress_update') return v + '%'
  if (op === 'status_change') return taskStatusMap[v]?.label || v
  return v
}
const formatDateTime = (s?: string) => (s ? String(s).replace('T', ' ').slice(0, 16) : '')

const load = async () => {
  if (!baseDate.value) {
    ElMessage.warning('请选择基准日期')
    return
  }
  loading.value = true
  try {
    await ensureUsersLoaded()
    const sel = selectedUsers.value
    const params: { date: string; userId?: number | string } = { date: baseDate.value }
    // 全公司（管理员查看全部）哨兵
    if (sel.includes(ALL_SENTINEL)) {
      params.userId = 0
    } else if (sel.length) {
      params.userId = Number(sel[0])
    }
    // 空选择：管理员按「全公司」、其余按「本人」——后端 resolveReportOwners 已处理
    const data = await getWeeklyReport(params)
    Object.assign(report, data || {})
  } catch (e) {
    console.error('加载周报失败', e)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await ensureUsersLoaded()
  // 获取看板人员范围（权限判定）
  try {
    const scopeRes: any = await getBoardScope()
    boardScope.value = scopeRes?.data || {}
  } catch (e) {
    console.warn('[PMS-Weekly] 获取人员范围失败，按本人视图兜底', e)
    boardScope.value = {}
  }
  // 默认选中本人（管理员可清空或选「全公司」查看全部）
  const defaultUid = boardScope.value.loginUserId ?? currentUserId.value
  if (defaultUid != null) selectedUsers.value = [defaultUid]
  await load()
})
</script>

<style scoped>
.section {
  margin-bottom: 16px;
}
.section-header {
  display: flex;
  align-items: center;
  gap: 10px;
}
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #1d2129;
}
.section-hint {
  font-size: 12px;
  color: #86909c;
}
.task-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.task-list > :nth-child(even) {
  background: #fafbfc;
}
.delay-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.delay-row {
  position: relative;
}
.delay-badge {
  position: absolute;
  top: 8px;
  right: 12px;
  font-weight: 600;
}
.change-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.change-group {
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  padding: 12px 16px;
  background: #fff;
}
.change-group-title {
  font-size: 14px;
  font-weight: 600;
  color: #1d2129;
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}
.change-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: #4e5969;
  padding: 4px 0;
}
.change-value {
  font-weight: 600;
  color: #1d2129;
}
.change-time {
  color: #86909c;
}
.change-operator {
  color: #86909c;
}
.ml-8px {
  margin-left: 8px;
}
.mx-4px {
  margin-left: 4px;
  margin-right: 4px;
}
</style>
