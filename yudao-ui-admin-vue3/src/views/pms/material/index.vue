<template>
  <div class="pms-material">
    <!-- 筛选 -->
    <ContentWrap>
      <el-form :inline="true" :model="filters">
        <el-form-item label="物料名称">
          <el-input v-model="filters.name" placeholder="请输入" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="项目">
          <el-select v-model="filters.projectId" clearable placeholder="全部" style="width: 140px">
            <el-option v-for="p in projects" :key="p.projectId" :label="p.projectName" :value="p.projectId" />
          </el-select>
        </el-form-item>
        <el-form-item label="预警状态">
          <el-select v-model="filters.status" clearable placeholder="全部" style="width: 120px">
            <el-option label="正常" value="normal" />
            <el-option label="预警中" value="warning" />
            <el-option label="紧急" value="urgent" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="loadData">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </ContentWrap>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="mb-16px">
      <el-col :span="6" v-for="card in statCards" :key="card.key">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" :style="{ background: card.bg, color: card.color }">
              <el-icon :size="24"><component :is="card.icon" /></el-icon>
            </div>
            <div>
              <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
              <div class="stat-label">{{ card.label }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 列表 -->
    <ContentWrap>
      <div class="list-toolbar">
        <span>共 {{ filteredData.length }} 条物料记录</span>
        <div>
          <el-button type="primary" size="small" @click="openCreateDialog" v-if="checkPermi(['pms:material:create'])" class="mr-8px"><el-icon><Plus /></el-icon> 新增物料</el-button>
          <el-button type="primary" size="small" @click="exportData" plain>导出</el-button>
        </div>
      </div>
      <el-table :data="filteredData" border size="small" v-loading="loading" @row-click="openDetail">
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :color="getStatusColor(row)" effect="dark" size="small">{{ getStatusLabel(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="materialName" label="物料名称" min-width="150" />
        <el-table-column label="项目" width="100">
          <template #default="{ row }">{{ getProjectName(row.projectId) }}</template>
        </el-table-column>
        <el-table-column prop="supplier" label="供应商" width="120" />
        <el-table-column label="需求日期" width="110">
          <template #default="{ row }">{{ formatDate(row.planOrderDate) }}</template>
        </el-table-column>
        <el-table-column label="承诺交期" width="110">
          <template #default="{ row }">{{ formatDate(row.planDeliveryDate) }}</template>
        </el-table-column>
        <el-table-column label="剩余天数" width="90" align="center">
          <template #default="{ row }">
            <span :style="{ color: getDaysColor(row), fontWeight: 600 }">{{ getRemainingDays(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button link type="warning" size="small" @click.stop="urge(row)" v-if="row.warningStatus !== 'normal'">催交</el-button>
            <el-button link type="primary" size="small" @click.stop="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </ContentWrap>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawerVisible" size="720px" :show-close="false">
      <template #header>
        <div class="drawer-header">
          <el-tag :color="getStatusColor(selectedMaterial)" effect="dark">{{ getStatusLabel(selectedMaterial) }}</el-tag>
          <span class="material-title">{{ selectedMaterial?.materialName }}</span>
          <el-button link @click="drawerVisible = false"><el-icon><Close /></el-icon></el-button>
        </div>
      </template>
      <template v-if="selectedMaterial">
        <el-descriptions title="物料信息" :column="2" border>
          <el-descriptions-item label="物料名称">{{ selectedMaterial.materialName }}</el-descriptions-item>
          <el-descriptions-item label="物料编码">{{ selectedMaterial.materialCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="所属项目">{{ getProjectName(selectedMaterial.projectId) }}</el-descriptions-item>
          <el-descriptions-item label="关联任务">{{ selectedMaterial.taskId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="需求日期">{{ formatDate(selectedMaterial.planOrderDate) }}</el-descriptions-item>
          <el-descriptions-item label="承诺交期">{{ formatDate(selectedMaterial.planDeliveryDate) }}</el-descriptions-item>
          <el-descriptions-item label="实际交期">{{ formatDate(selectedMaterial.actualDeliveryDate) }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ selectedMaterial.supplier }}</el-descriptions-item>
          <el-descriptions-item label="采购数量">{{ selectedMaterial.quantity }} {{ selectedMaterial.unit }}</el-descriptions-item>
        </el-descriptions>

        <div class="section-title mt-20px">交期跟踪记录</div>
        <el-timeline>
          <el-timeline-item v-for="(log, i) in (selectedMaterial.logs || [])" :key="i" :timestamp="log.date" :type="log.type" placement="top">
            <div>{{ log.content }}</div>
            <div class="log-remark" v-if="log.remark">备注：{{ log.remark }}</div>
          </el-timeline-item>
        </el-timeline>

        <div class="section-title">催交记录</div>
        <el-table :data="selectedMaterial.urgeLogs || []" border size="small">
          <el-table-column prop="date" label="催交时间" width="150" />
          <el-table-column prop="operator" label="操作人" width="80" />
          <el-table-column prop="result" label="催交结果" />
        </el-table>
      </template>
    </el-drawer>

    <!-- 新增物料弹窗 -->
    <el-dialog v-model="createDialogVisible" title="新增物料" width="560px" :close-on-click-modal="false">
      <el-form :model="createForm" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="所属项目" required>
              <el-select v-model="createForm.projectId" placeholder="请选择项目" filterable style="width: 100%">
                <el-option v-for="p in projects" :key="p.projectId" :label="p.projectName" :value="p.projectId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料名称" required>
              <el-input v-model="createForm.materialName" placeholder="请输入物料名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="物料编码">
              <el-input v-model="createForm.materialCode" placeholder="请输入物料编码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商">
              <el-input v-model="createForm.supplier" placeholder="请输入供应商" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="数量">
              <el-input-number v-model="createForm.quantity" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位">
              <el-input v-model="createForm.unit" placeholder="如：个/套/件" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="需求日期">
              <el-date-picker v-model="createForm.planOrderDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="承诺交期">
              <el-date-picker v-model="createForm.planDeliveryDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="当前状态">
          <el-select v-model="createForm.currentStatus" placeholder="请选择" style="width: 100%">
            <el-option label="未下单" value="not_ordered" />
            <el-option label="已下单" value="ordered" />
            <el-option label="已到货" value="delivered" />
            <el-option label="延期" value="delayed" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getProjectList, ProjectVO } from '@/api/pms/project'
import { getMaterialTrackList, createMaterialTrack, updateMaterialTrack, deleteMaterialTrack } from '@/api/pms/material'
import { formatDate } from '../pms-utils'
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'PmsMaterial' })

const filters = reactive({ name: '', projectId: '' as '' | number, status: '' })
const projects = ref<ProjectVO[]>([])
const drawerVisible = ref(false)
const selectedMaterial = ref<any>(null)
const loading = ref(false)
const tableData = ref<any[]>([])
const createDialogVisible = ref(false)
const submitting = ref(false)
const createForm = reactive({
  projectId: undefined as number | undefined,
  materialName: '',
  materialCode: '',
  supplier: '',
  quantity: undefined as number | undefined,
  unit: '',
  planOrderDate: '',
  planDeliveryDate: '',
  currentStatus: 'not_ordered'
})

const filteredData = computed(() => {
  let result = tableData.value
  if (filters.name) result = result.filter(m => m.materialName?.includes(filters.name))
  if (filters.projectId) result = result.filter(m => m.projectId === filters.projectId)
  if (filters.status) result = result.filter(m => m.warningStatus === filters.status)
  return result
})

const statCards = computed(() => {
  const data = tableData.value
  return [
    { key: 'total', label: '总物料数', value: data.length, icon: 'Box', color: '#2468F2', bg: '#DCE7FF' },
    { key: 'normal', label: '正常', value: data.filter(m => m.warningStatus === 'normal').length, icon: 'CircleCheck', color: '#00B42A', bg: '#E8FFEA' },
    { key: 'warning', label: '预警中', value: data.filter(m => m.warningStatus === 'warning').length, icon: 'Warning', color: '#FF7D00', bg: '#FFF7E8' },
    { key: 'urgent', label: '紧急', value: data.filter(m => m.warningStatus === 'urgent').length, icon: 'CircleClose', color: '#F53F3F', bg: '#FFECE8' }
  ]
})

function getProjectName(projectId?: number): string {
  if (!projectId) return '-'
  const p = projects.value.find(p => p.projectId === projectId)
  return p?.projectName || '-'
}

function getStatusColor(m: any): string {
  if (!m) return '#86909C'
  if (m.warningStatus === 'urgent') return '#F53F3F'
  if (m.warningStatus === 'warning') return '#FF7D00'
  return '#00B42A'
}

function getStatusLabel(m: any): string {
  if (!m) return '-'
  if (m.warningStatus === 'urgent') return '紧急'
  if (m.warningStatus === 'warning') return '预警中'
  return '正常'
}

function getRemainingDays(m: any): string {
  if (m.actualDeliveryDate) return '已到货'
  const now = new Date()
  const required = new Date(m.planOrderDate)
  if (isNaN(required.getTime())) return '-'
  const diff = Math.floor((required.getTime() - now.getTime()) / (1000 * 60 * 60 * 24))
  return diff > 0 ? `${diff} 天` : diff === 0 ? '今天' : `超${Math.abs(diff)}天`
}

function getDaysColor(m: any): string {
  if (m.actualDeliveryDate) return '#00B42A'
  const days = parseInt(getRemainingDays(m))
  if (isNaN(days)) return '#86909C'
  if (days < 0) return '#F53F3F'
  if (days <= 3) return '#FF7D00'
  return '#4E5969'
}

function openDetail(row: any) {
  selectedMaterial.value = row
  drawerVisible.value = true
}

function urge(row: any) {
  ElMessage.success(`已向「${row.supplier}」发送催交通知`)
  if (!row.urgeLogs) row.urgeLogs = []
  row.urgeLogs.push({ date: new Date().toLocaleString(), operator: '当前用户', result: '催交通知已发送' })
}

function exportData() { console.log('导出物料列表') }
function resetFilters() { Object.assign(filters, { name: '', projectId: '', status: '' }) }

function openCreateDialog() {
  Object.assign(createForm, {
    projectId: undefined,
    materialName: '',
    materialCode: '',
    supplier: '',
    quantity: undefined,
    unit: '',
    planOrderDate: '',
    planDeliveryDate: '',
    currentStatus: 'not_ordered'
  })
  createDialogVisible.value = true
}

async function submitCreate() {
  if (!createForm.projectId || !createForm.materialName) {
    ElMessage.warning('请填写必填项')
    return
  }
  submitting.value = true
  try {
    await createMaterialTrack({
      projectId: createForm.projectId,
      materialName: createForm.materialName,
      materialCode: createForm.materialCode || undefined,
      supplier: createForm.supplier || undefined,
      quantity: createForm.quantity,
      unit: createForm.unit || undefined,
      planOrderDate: createForm.planOrderDate || undefined,
      planDeliveryDate: createForm.planDeliveryDate || undefined,
      currentStatus: createForm.currentStatus,
      warningStatus: 'normal'
    })
    ElMessage.success('物料创建成功')
    createDialogVisible.value = false
    await loadData()
  } catch (e) {
    console.error(e)
    ElMessage.error('创建失败')
  } finally {
    submitting.value = false
  }
}

async function loadData() {
  loading.value = true
  try {
    const [projectRes, materialRes] = await Promise.all([
      getProjectList(),
      getMaterialTrackList()
    ])
    projects.value = projectRes as ProjectVO[]
    tableData.value = (materialRes as any[]) || []
  } catch (e) {
    console.error(e)
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => { loadData() })
</script>

<style scoped lang="scss">
.pms-material {
  .stat-card {
    .stat-content { display: flex; align-items: center; gap: 12px; }
    .stat-icon { width: 48px; height: 48px; border-radius: 10px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
    .stat-value { font-size: 28px; font-weight: 700; }
    .stat-label { font-size: 14px; color: var(--el-text-color-secondary); }
  }
  .list-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
  .drawer-header { display: flex; align-items: center; gap: 12px; width: 100%; }
  .material-title { font-size: 16px; font-weight: 600; flex: 1; }
  .section-title { font-size: 14px; font-weight: 600; margin: 20px 0 12px; }
  .log-remark { font-size: 12px; color: var(--el-text-color-secondary); margin-top: 4px; }
}
</style>
