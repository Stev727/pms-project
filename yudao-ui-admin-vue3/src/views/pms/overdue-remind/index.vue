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
            <el-option v-for="u in userList" :key="u.id" :label="u.nickname" :value="u.id" />
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
        <el-table-column prop="planEndDate" label="计划结束日期" width="120" align="center" />
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

    <!-- 提醒设置弹窗 -->
    <el-dialog v-model="receiverDialogVisible" title="提醒设置" width="560px">
      <el-alert
        title="任务超期后，每日 09:00 将超期清单汇总推送给以下人员（钉钉 + 站内消息）"
        type="info"
        :closable="false"
        show-icon
        class="mb-12px"
      />
      <el-form label-width="100px">
        <el-form-item label="监控人/通知人">
          <el-select
            v-model="receiverIds"
            multiple
            filterable
            placeholder="选择需要接收超期提醒的人员"
            class="w-full"
          >
            <el-option v-for="u in userList" :key="u.id" :label="u.nickname" :value="u.id" />
          </el-select>
          <div class="form-tip">不选则不推送；接收人将收到全部超期任务的每日汇总</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="receiverDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveReceivers">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getOverdueTaskPage, getOverdueReceivers, updateOverdueReceivers } from '@/api/pms/overdueRemind'
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
const queryProjectId = ref<number | undefined>(undefined)
const deptTree = ref<any[]>([])
const userList = ref<any[]>([])
const projectList = ref<ProjectVO[]>([])

// 提醒设置
const receiverDialogVisible = ref(false)
const receiverIds = ref<number[]>([])
const saving = ref(false)
const canUpdate = computed(() => checkPermi(['pms:overdue-remind:update']))

function statusText(status: string): string {
  const map: Record<string, string> = {
    not_started: '未开始',
    in_progress: '进行中',
    under_review: '审核中'
  }
  return map[status] || status || '-'
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

async function openReceiverDialog() {
  try {
    receiverIds.value = ((await getOverdueReceivers()) as number[]) || []
  } catch {
    receiverIds.value = []
  }
  receiverDialogVisible.value = true
}

async function saveReceivers() {
  saving.value = true
  try {
    await updateOverdueReceivers(receiverIds.value)
    ElMessage.success(receiverIds.value.length ? `已保存，每日将推送至 ${receiverIds.value.length} 位接收人` : '已清空接收人，将不再推送')
    receiverDialogVisible.value = false
  } catch (e) {
    console.error('保存接收人失败', e)
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
