<template>
  <div class="p-20px">
    <ContentWrap title="周报报表">
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
        <el-form-item label="部门">
          <el-select
            v-model="selectedDept"
            filterable
            clearable
            :loading="deptLoading"
            :placeholder="deptLoading ? '加载中…' : (deptList.length ? '搜索或选择部门（含下级）' : '无可选部门')"
            style="width: 260px"
          >
            <el-option
              v-for="d in deptList"
              :key="d.id"
              :label="d.name"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="人员">
          <el-select
            v-model="selectedUser"
            filterable
            clearable
            :loading="userLoading"
            :placeholder="userList.length ? '搜索人员（可空）' : '加载中…'"
            :disabled="!!selectedDept"
            style="width: 240px"
          >
            <el-option
              v-for="u in userList"
              :key="u.id"
              :label="u.nickname"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onQuery" :loading="loading">
            <Icon icon="ep:search" class="mr-5px" />查询
          </el-button>
        </el-form-item>
        <el-form-item>
          <el-tooltip
            :content="exportTip"
            placement="top"
            :disabled="!exportTip"
          >
            <el-button
              type="success"
              :loading="exporting"
              :disabled="!canExport"
              @click="onExport"
            >
              <Icon icon="ep:download" class="mr-5px" />导出报表
            </el-button>
          </el-tooltip>
        </el-form-item>
      </el-form>
    </ContentWrap>

    <ContentWrap>
      <el-table
        v-loading="loading"
        :data="pagedRows"
        border
        stripe
        :default-sort="{ prop: 'category', order: 'ascending' }"
        height="600"
        :empty-text="hasQueried ? '当前筛选条件下无数据' : '请选择条件后点击查询'"
      >
        <el-table-column prop="category" label="分类" width="120" sortable />
        <el-table-column prop="projectName" label="项目" width="140" show-overflow-tooltip />
        <el-table-column prop="taskName" label="任务名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="mainOwnerName" label="责任人" width="100" />
        <el-table-column prop="planStartDate" label="计划开始" width="110" />
        <el-table-column prop="planEndDate" label="计划结束" width="110" />
        <el-table-column prop="actualCompleteDate" label="实际完成" width="110" />
        <el-table-column prop="completeStatusLabel" label="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.completeStatusLabel" size="small" effect="plain">{{ row.completeStatusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="progress" label="进度" width="90" sortable>
          <template #default="{ row }">{{ row.progress != null ? row.progress + '%' : '' }}</template>
        </el-table-column>
        <el-table-column prop="overdueDays" label="逾期(天)" width="90" sortable>
          <template #default="{ row }">
            <el-tag v-if="row.overdueDays" type="danger" effect="dark" size="small">{{ row.overdueDays }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operationTypeLabel" label="操作" width="100" />
        <el-table-column prop="beforeValue" label="变更前" width="120" show-overflow-tooltip />
        <el-table-column prop="afterValue" label="变更后" width="120" show-overflow-tooltip />
        <el-table-column prop="operationTime" label="操作时间" width="140" />
        <el-table-column prop="operatorName" label="操作人" width="100" />
      </el-table>

      <div class="flex justify-end mt-12px">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="allRows.length"
          :page-sizes="[20, 50, 100, 200]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="onSizeChange"
          @current-change="onPageChange"
        />
      </div>
    </ContentWrap>
  </div>
</template>

<script setup lang="ts">
import { getWeeklyReportPage, exportWeeklyReportXls, getWeeklyReportDepts, WeeklyReportRowVO } from '@/api/pms/report'
import download from '@/utils/download'
import { useUserNames } from '@/hooks/pms/useUserNames'
import { formatDate } from '../../pms-utils'
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

defineOptions({ name: 'PmsWeeklyReportXls' })

const { userList, ensureLoaded: ensureUsersLoaded } = useUserNames()

const loading = ref(false)
const exporting = ref(false)
const baseDate = ref<string>(formatDate(new Date(), 'YYYY-MM-DD'))

const deptList = ref<PmsDeptVO[]>([])
const deptLoading = ref(false)
const selectedDept = ref<number | null>(null)

const userLoading = ref(false)
const selectedUser = ref<number | null>(null)

// 数据
const allRows = ref<WeeklyReportRowVO[]>([])
const hasQueried = ref(false)

// 分页
const pageNo = ref(1)
const pageSize = ref(20)
const pagedRows = computed(() => {
  const from = (pageNo.value - 1) * pageSize.value
  return allRows.value.slice(from, from + pageSize.value)
})

// 导出可用性：有部门或具体人员才可导出（全公司/本人也允许）
const canExport = computed(() => {
  return allRows.value.length > 0 || selectedDept.value || selectedUser.value
})
const exportTip = computed(() => {
  return ''  // 永远允许，按钮始终可点；空数据时导出按钮在 loading 状态下给出"无数据"提示
})

const loadDepts = async () => {
  deptLoading.value = true
  try {
    const res: any = await getWeeklyReportDepts()
    const payload = res?.data || res || {}
    const list = Array.isArray(payload.depts) ? payload.depts : []
    // 注意 yudao CommonStatusEnum：0=开启，1=停用 → 保留 0/null
    deptList.value = list.filter((d: any) => d && (d.status == null || d.status === 0))
    // 默认选中登录人所在部门（可修改）
    if (payload.myDeptId && deptList.value.some((d: any) => Number(d.id) === Number(payload.myDeptId))) {
      selectedDept.value = Number(payload.myDeptId)
    }
  } catch (e) {
    console.warn('[PMS-WeeklyReport] 加载部门列表失败', e)
    deptList.value = []
  } finally {
    deptLoading.value = false
  }
}

const onQuery = async () => {
  if (!baseDate.value) {
    ElMessage.warning('请选择基准日期')
    return
  }
  loading.value = true
  hasQueried.value = true
  pageNo.value = 1
  try {
    await ensureUsersLoaded()
    const params: { date: string; deptId?: number | string; userId?: number | string; pageNo?: number; pageSize?: number } = {
      date: baseDate.value,
      pageNo: 1,
      pageSize: 500
      // 拉全量（后端上限 500）本地分页——避免「区块顺序+小分页」导致首页只见第一个分类
    }
    if (selectedDept.value) {
      params.deptId = Number(selectedDept.value)
    } else if (selectedUser.value) {
      params.userId = Number(selectedUser.value)
    }
    const data: any = await getWeeklyReportPage(params)
    allRows.value = Array.isArray(data) ? data : (data?.data || [])
    if (allRows.value.length === 0) {
      ElMessage.info('当前筛选条件下无数据')
    }
  } catch (e) {
    console.error('[PMS-WeeklyReport] 查询失败', e)
    allRows.value = []
  } finally {
    loading.value = false
  }
}

const onExport = async () => {
  if (!baseDate.value) {
    ElMessage.warning('请选择基准日期')
    return
  }
  exporting.value = true
  try {
    const params: { date: string; deptId?: number | string; userId?: number | string } = { date: baseDate.value }
    if (selectedDept.value) {
      params.deptId = Number(selectedDept.value)
    } else if (selectedUser.value) {
      params.userId = Number(selectedUser.value)
    }
    const res: any = await exportWeeklyReportXls(params)
    // request.download 返回 axios Response，文件本体在 res.data(Blob)——同 TaskListTab 导出写法
    const blob = res && res.data ? res.data : res
    // 文件名与后端规则一致：周报报表_{部门名}_{基准日期}.xlsx
    let deptLabel = '个人'
    if (selectedDept.value) {
      const d = deptList.value.find((x: any) => Number(x.id) === Number(selectedDept.value))
      if (d && d.name) deptLabel = d.name
    }
    download.excel(blob as Blob, `周报报表_${deptLabel}_${baseDate.value}.xlsx`)
    ElMessage.success('导出成功，文件已开始下载')
  } catch (e) {
    console.error('[PMS-WeeklyReport] 导出失败', e)
    ElMessage.error('导出失败，请稍后重试')
  } finally {
    exporting.value = false
  }
}

const onPageChange = () => {
  // 翻页无需重新请求（数据已在 allRows.value 中）
}
const onSizeChange = () => {
  pageNo.value = 1
}

onMounted(async () => {
  await ensureUsersLoaded()
  await loadDepts()
})
</script>

<style scoped>
.flex { display: flex; }
.justify-end { justify-content: flex-end; }
.mt-12px { margin-top: 12px; }
.mr-5px { margin-right: 5px; }
</style>
