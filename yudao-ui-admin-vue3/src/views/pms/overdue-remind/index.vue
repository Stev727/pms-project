<template>
  <div class="pms-overdue-remind">
    <!-- 统计与操作栏 -->
    <ContentWrap>
      <div class="toolbar">
        <div class="stats">
          <span class="stat-item danger">超期任务 <b>{{ stats.total }}</b> 个</span>
          <el-divider direction="vertical" />
          <span class="stat-item">涉及人员 <b>{{ stats.ownerCount }}</b> 人</span>
          <el-divider direction="vertical" />
          <span class="stat-item">涉及项目 <b>{{ stats.projectCount }}</b> 个</span>
        </div>
        <div>
          <el-button size="small" :loading="loading" @click="loadData">
            <Icon icon="ep:refresh" class="mr-4px" />刷新
          </el-button>
          <el-button type="primary" size="small" @click="openReceiverDialog" v-if="canUpdate">
            <Icon icon="ep:bell" class="mr-4px" />提醒设置
          </el-button>
        </div>
      </div>

      <!-- 监控推送配置 -->
      <div class="receiver-bar">
        <span class="receiver-label">
          <Icon icon="ep:bell" class="mr-4px" />监控推送：
        </span>
        <template v-if="monitorSummary.length">
          <div class="receiver-lines">
            <div v-for="(line, idx) in monitorSummary" :key="idx" class="receiver-line">
              <el-tag size="small" type="warning" effect="dark" class="mr-4px">{{ line.notifyName }}</el-tag>
              <span class="arrow">监控</span>
              <el-tag v-for="o in line.ownerNames" :key="o" size="small" effect="plain" class="mr-4px">{{ o }}</el-tag>
              <span class="form-tip">每日 09:00 推送其超期任务（钉钉+站内信）</span>
            </div>
          </div>
        </template>
        <template v-else>
          <span class="form-tip">未配置，暂不推送</span>
        </template>
      </div>

      <!-- 筛选区 -->
      <el-form :inline="true" class="mt-8px">
        <el-form-item label="责任人部门">
          <el-tree-select
            v-model="queryDeptId"
            :data="deptTree"
            :props="{ value: 'id', label: 'name', children: 'children' }"
            check-strictly
            clearable
            filterable
            placeholder="全部部门"
            class="w-180px"
          />
        </el-form-item>
        <el-form-item label="责任人">
          <el-select v-model="queryUserId" filterable clearable placeholder="全部人员" class="w-160px">
            <el-option v-for="u in filteredUserList" :key="u.id" :label="u.nickname" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目">
          <el-select v-model="queryProjectId" filterable clearable placeholder="全部项目" class="w-220px">
            <el-option v-for="p in projectList" :key="p.projectId" :label="p.projectName" :value="p.projectId" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <Icon icon="ep:search" class="mr-4px" />查询
          </el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </ContentWrap>

    <!-- 超期任务列表 -->
    <ContentWrap>
      <el-table :data="list" border stripe v-loading="loading">
        <el-table-column label="任务名称" min-width="220">
          <template #default="{ row }">
            <el-link v-if="row.projectId" type="primary" @click="gotoTask(row)">
              {{ row.taskName || '-' }}
            </el-link>
            <span v-else>{{ row.taskName || '-' }}</span>
            <el-tag v-if="!row.projectId" size="small" type="info" class="ml-4px">日常</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="projectName" label="所属项目" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.projectName || '日常任务' }}</template>
        </el-table-column>
        <el-table-column prop="mainOwnerName" label="责任人" width="110">
          <template #default="{ row }">{{ row.mainOwnerName || '未分配' }}</template>
        </el-table-column>
        <el-table-column prop="deptName" label="责任部门" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.deptName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="planEndDate" label="计划结束日期" width="120" align="center">
          <template #default="{ row }">{{ fmtDate(row.planEndDate) }}</template>
        </el-table-column>
        <el-table-column label="已超期" width="90" align="center">
          <template #default="{ row }">
            <el-tag type="danger" effect="dark">{{ row.overdueDays }} 天</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="completeStatus" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="warning">{{ statusText(row.completeStatus) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && list.length === 0" description="当前没有超期任务 🎉" :image-size="80" />
      <!-- 分页 -->
      <div class="mt-12px" v-if="total > 0">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadData"
          @size-change="loadData"
        />
      </div>
    </ContentWrap>

    <!-- 提醒设置弹窗（监控配置） -->
    <el-dialog v-model="receiverDialogVisible" title="提醒设置（监控配置）" width="760px">
      <el-alert
        title="每日 09:00，系统按以下配置分别推送：每个通知人只会收到「其监控的责任人」的超期任务汇总（钉钉 + 站内消息）"
        type="info"
        :closable="false"
        show-icon
        class="mb-12px"
      />
      <el-table :data="monitorConfigs" border>
        <el-table-column label="通知人（接收推送）" width="200">
          <template #default="{ row }">
            <el-select v-model="row.notifyUserId" filterable placeholder="选择通知人" class="w-full">
              <el-option v-for="u in userList" :key="u.id" :label="u.nickname" :value="u.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="监控的责任人（超期了才推）">
          <template #default="{ row }">
            <el-select v-model="row.ownerIds" multiple filterable placeholder="选择被监控的责任人（可多选）" class="w-full">
              <el-option v-for="u in userList" :key="u.id" :label="u.nickname" :value="u.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" @click="monitorConfigs.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button class="mt-8px" @click="addMonitorRow">
        <Icon icon="ep:plus" class="mr-4px" />添加一条监控配置
      </el-button>
      <template #footer>
        <el-button @click="receiverDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveMonitorConfigs">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getOverdueTaskPage, getOverdueMonitorConfigs, updateOverdueMonitorConfigs, OverdueMonitorConfig } from '@/api/pms/overdueRemind'
import { getProjectList, ProjectVO } from '@/api/pms/project'
import { getSimpleDeptList } from '@/api/system/dept'
import { getSimpleUserList } from '@/api/system/user'
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'PmsOverdueRemind' })

const router = useRouter()
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)
const stats = reactive({ total: 0, ownerCount: 0, projectCount: 0 })

// 筛选
const queryDeptId = ref<number | undefined>(undefined)
const queryUserId = ref<number | undefined>(undefined)
const queryProjectId = ref<string | undefined>(undefined)
const deptTree = ref<any[]>([])
const userList = ref<any[]>([])
const projectList = ref<ProjectVO[]>([])

// 提醒设置（监控配置：通知人 ↔ 被监控责任人）
const receiverDialogVisible = ref(false)
const monitorConfigs = ref<OverdueMonitorConfig[]>([]) // 弹窗编辑副本
const currentMonitorConfigs = ref<OverdueMonitorConfig[]>([]) // 主界面展示
const saving = ref(false)
const canUpdate = computed(() => checkPermi(['pms:overdue-remind:update']))

// userId → 姓名
function userName(id: number): string {
  const u: any = userList.value.find((x: any) => x.id === id)
  return u?.nickname || `用户${id}`
}

// 主界面监控配置摘要
const monitorSummary = computed(() =>
  currentMonitorConfigs.value.map((c: any) => ({
    notifyName: c.notifyUserId ? userName(c.notifyUserId) : '-',
    ownerNames: (c.ownerIds || []).map((id: number) => userName(id))
  }))
)

// 收集部门及其全部子部门 id（树形联动用）
function collectDeptIds(rootId: number, nodes: any[] = deptTree.value): Set<number> {
  const result = new Set<number>()
  const walk = (node: any) => {
    result.add(node.id)
    ;(node.children || []).forEach(walk)
  }
  const find = (list: any[]): boolean =>
    list.some((n) => {
      if (n.id === rootId) {
        walk(n)
        return true
      }
      return n.children?.length ? find(n.children) : false
    })
  find(nodes)
  return result
}

// 责任人下拉：选了部门后只显示该部门（含子部门）人员
const filteredUserList = computed(() => {
  if (!queryDeptId.value) return userList.value
  const deptIds = collectDeptIds(queryDeptId.value)
  return userList.value.filter((u: any) => deptIds.has(u.deptId))
})

// 切换部门时，清掉不在该部门范围内的责任人选择
watch(queryDeptId, () => {
  if (queryUserId.value && !filteredUserList.value.some((u: any) => u.id === queryUserId.value)) {
    queryUserId.value = undefined
  }
})

function statusText(status: string): string {
  const map: Record<string, string> = {
    not_started: '未开始',
    pending_accept: '待验收',
    in_progress: '进行中',
    delayed: '已延期',
    paused: '已暂停',
    rejected: '已驳回',
    under_review: '审核中',
    completion_pending_review: '完成待审核',
    completed: '已完成'
  }
  return map[status] || status || '-'
}

// 毫秒时间戳 → YYYY-MM-DD
function fmtDate(ts: any): string {
  if (!ts) return '-'
  const d = new Date(Number(ts))
  if (isNaN(d.getTime())) return String(ts)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getOverdueTaskPage({
      deptId: queryDeptId.value,
      userId: queryUserId.value,
      projectId: queryProjectId.value,
      pageNo: pageNo.value,
      pageSize: pageSize.value
    })
    list.value = res?.list || []
    total.value = Number(res?.total || 0)
    if (res?.stats) {
      stats.total = Number(res.stats.total || 0)
      stats.ownerCount = Number(res.stats.ownerCount || 0)
      stats.projectCount = Number(res.stats.projectCount || 0)
    }
  } catch (e) {
    console.error('加载超期任务失败', e)
    ElMessage.error('加载超期任务失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNo.value = 1
  loadData()
}

function handleReset() {
  queryDeptId.value = undefined
  queryUserId.value = undefined
  queryProjectId.value = undefined
  pageNo.value = 1
  loadData()
}

function gotoTask(row: any) {
  router.push(`/pms/project-detail/${row.projectId}?taskId=${row.taskId}`)
}

function openReceiverDialog() {
  monitorConfigs.value = currentMonitorConfigs.value.map((c: any) => ({
    notifyUserId: c.notifyUserId,
    ownerIds: [...(c.ownerIds || [])]
  }))
  receiverDialogVisible.value = true
}

function addMonitorRow() {
  monitorConfigs.value.push({ notifyUserId: undefined, ownerIds: [] })
}

async function saveMonitorConfigs() {
  const invalid = monitorConfigs.value.some(
    (c: any) => !c.notifyUserId || !(c.ownerIds || []).length
  )
  if (invalid) {
    ElMessage.warning('每条配置需选择通知人，且至少监控一个责任人')
    return
  }
  saving.value = true
  try {
    await updateOverdueMonitorConfigs(monitorConfigs.value)
    currentMonitorConfigs.value = monitorConfigs.value.map((c: any) => ({
      notifyUserId: c.notifyUserId,
      ownerIds: [...(c.ownerIds || [])]
    }))
    ElMessage.success(
      monitorConfigs.value.length
        ? `已保存 ${monitorConfigs.value.length} 条监控配置，每日 09:00 按配置定向推送`
        : '已清空监控配置，将不再推送'
    )
    receiverDialogVisible.value = false
  } catch (e) {
    console.error('保存监控配置失败', e)
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  loadData()
  try {
    deptTree.value = (await getSimpleDeptList()) || []
  } catch (e) { console.error(e) }
  try {
    userList.value = (await getSimpleUserList()) || []
  } catch (e) { console.error(e) }
  try {
    currentMonitorConfigs.value = ((await getOverdueMonitorConfigs()) as OverdueMonitorConfig[]) || []
  } catch (e) { console.error(e) }
  try {
    const projects = await getProjectList()
    projectList.value = ((projects as ProjectVO[]) || []).filter(
      (p) => p.projectType !== 'standard_template'
    )
  } catch (e) { console.error(e) }
})
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.stats {
  font-size: 14px;
  color: #4e5969;
}
.stats b {
  font-size: 16px;
  color: #1d2129;
  margin: 0 2px;
}
.stat-item.danger b {
  color: #f53f3f;
}
.mt-8px { margin-top: 8px; }
.mt-12px { margin-top: 12px; }
.ml-4px { margin-left: 4px; }
.mb-12px { margin-bottom: 12px; }
.receiver-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 0;
  margin-top: 10px;
  padding: 8px 12px;
  background: #fdf6ec;
  border: 1px solid #faecd8;
  border-radius: 4px;
  font-size: 13px;
}
.receiver-label {
  color: #b88230;
  display: inline-flex;
  align-items: center;
  margin-right: 4px;
  white-space: nowrap;
}
.receiver-lines {
  display: inline-flex;
  flex-direction: column;
  gap: 4px;
}
.receiver-line {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
}
.receiver-line .arrow {
  margin: 0 6px;
  color: #b88230;
  font-size: 12px;
}
.receiver-line .form-tip {
  margin-left: 8px;
}
.w-full { width: 100%; }
.w-160px { width: 160px; }
.w-180px { width: 180px; }
.w-220px { width: 220px; }
.form-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
  margin-top: 4px;
}
</style>
