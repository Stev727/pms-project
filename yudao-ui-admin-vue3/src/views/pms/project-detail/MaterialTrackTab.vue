<!--
  物料跟踪 Tab（项目详情页用）
  改造说明（#10 物料跟踪嵌入项目详情）：
   - 由 views/pms/material/index.vue 抽出，接收 projectId prop，移除项目选择器与"所属项目"列
   - 查询强制带 props.projectId，不再支持跨项目筛选
   - 权限叠加：菜单级 pms:material:create + 项目级 material_add（useProjectPerm/PERM）
   - 19 位雪花 ID（trackId/projectId/taskId）一律按 string 处理；4-5 位用户 ID 用 Number()
   - 全局菜单 /pms/material 仍使用原 views/pms/material/index.vue，与本 Tab 共用同一后端
-->
<template>
  <div class="pms-material-tab">
    <!-- 筛选（无项目选择器） -->
    <ContentWrap>
      <el-form :inline="true" :model="filters">
        <el-form-item label="物料名称">
          <el-input v-model="filters.name" placeholder="请输入" clearable style="width: 160px" />
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
              <Icon :icon="card.iconRef" :size="24" />
            </div>
            <div>
              <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
              <div class="stat-label">{{ card.label }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 列表（无"所属项目"列） -->
    <ContentWrap>
      <div class="list-toolbar">
        <span>共 {{ filteredData.length }} 条物料记录</span>
        <div class="toolbar-actions">
          <el-button
            size="small"
            @click="handleDownloadTemplate"
            v-if="checkPermi(['pms:material:query'])"
          >
            <el-icon><Download /></el-icon> 下载模板
          </el-button>
          <el-button
            size="small"
            @click="triggerImport"
            v-if="checkPermi(['pms:material:create']) && can(PERM.MATERIAL_ADD)"
          >
            <el-icon><Upload /></el-icon> 导入
          </el-button>
          <input
            ref="fileInputRef"
            type="file"
            accept=".xlsx,.xls"
            style="display: none"
            @change="handleImportFileChange"
          />
          <el-button
            type="primary"
            size="small"
            @click="openCreateDialog"
            v-if="checkPermi(['pms:material:create']) && can(PERM.MATERIAL_ADD)"
            class="ml-8px"
          >
            <el-icon><Plus /></el-icon> 新增物料
          </el-button>
        </div>
      </div>
      <el-table :data="filteredData" border size="small" v-loading="loading" @row-click="openDetail">
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :color="getStatusColor(row)" effect="dark" size="small">
              {{ getStatusLabel(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="materialName" label="物料名称" min-width="160" />
        <el-table-column prop="supplier" label="供应商" width="140" />
        <el-table-column label="需求日期" width="110">
          <template #default="{ row }">{{ formatDate(row.planOrderDate) }}</template>
        </el-table-column>
        <el-table-column label="承诺交期" width="110">
          <template #default="{ row }">{{ formatDate(row.planDeliveryDate) }}</template>
        </el-table-column>
        <el-table-column label="剩余天数" width="100" align="center">
          <template #default="{ row }">
            <span :style="{ color: getDaysColor(row), fontWeight: 600 }">
              {{ getRemainingDays(row) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center">
          <template #default="{ row }">
            <el-button link type="warning" size="small" @click.stop="urge(row)" v-if="row.warningStatus !== 'normal'">
              催交
            </el-button>
            <el-button link type="primary" size="small" @click.stop="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </ContentWrap>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawerVisible" size="720px" :show-close="false">
      <template #header>
        <div class="drawer-header">
          <el-tag :color="getStatusColor(selectedMaterial)" effect="dark">
            {{ getStatusLabel(selectedMaterial) }}
          </el-tag>
          <span class="material-title">{{ selectedMaterial?.materialName }}</span>
          <el-button link @click="drawerVisible = false">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
      </template>
      <template v-if="selectedMaterial">
        <el-descriptions title="物料信息" :column="2" border>
          <el-descriptions-item label="物料名称">{{ selectedMaterial.materialName }}</el-descriptions-item>
          <el-descriptions-item label="物料编码">{{ selectedMaterial.materialCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="承诺交期">{{ formatDate(selectedMaterial.planDeliveryDate) }}</el-descriptions-item>
          <el-descriptions-item label="实际交期">{{ formatDate(selectedMaterial.actualDeliveryDate) }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ selectedMaterial.supplier }}</el-descriptions-item>
          <el-descriptions-item label="采购数量">
            {{ selectedMaterial.quantity }} {{ selectedMaterial.unit }}
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>

    <!-- 新增物料弹窗（无项目选择字段） -->
    <el-dialog v-model="createDialogVisible" title="新增物料" width="560px" :close-on-click-modal="false">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="物料名称" prop="materialName">
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
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Close, Download, Upload } from '@element-plus/icons-vue'
import {
  getMaterialTrackList,
  createMaterialTrack,
  updateMaterialTrack,
  getMaterialImportTemplate,
  importMaterialTrack,
  MaterialTrackVO
} from '@/api/pms/material'
import { formatDate } from '../pms-utils'
import { checkPermi } from '@/utils/permission'
import { useProjectPerm, PERM } from '@/hooks/pms/useProjectPerm'
import download from '@/utils/download'

defineOptions({ name: 'PmsMaterialTrackTab' })

const props = defineProps<{ projectId: string | number }>()

// 项目级权限
const { can, loadPerm } = useProjectPerm()

const filters = reactive({ name: '', status: '' })
const drawerVisible = ref(false)
const selectedMaterial = ref<MaterialTrackVO | null>(null)
const loading = ref(false)
const tableData = ref<MaterialTrackVO[]>([])
const createDialogVisible = ref(false)
const submitting = ref(false)
const createFormRef = ref<any>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const createForm = reactive({
  materialName: '',
  materialCode: '',
  supplier: '',
  quantity: undefined as number | undefined,
  unit: '',
  planOrderDate: '',
  planDeliveryDate: '',
  currentStatus: 'not_ordered'
})

/** 新增物料表单校验规则 */
const createRules = {
  materialName: [{ required: true, message: '请填写物料名称', trigger: 'blur' }],
  supplier: [{ required: true, message: '请填写供应商', trigger: 'blur' }],
  quantity: [{ required: true, message: '请填写数量', trigger: 'blur' }]
}

const filteredData = computed(() => {
  let result = tableData.value
  if (filters.name) result = result.filter(m => m.materialName?.includes(filters.name))
  if (filters.status) result = result.filter(m => m.warningStatus === filters.status)
  return result
})

const statCards = computed(() => {
  const data = filteredData.value
  // 按 currentStatus 业务状态分类（与表格数据一致）
  const notOrdered = data.filter(m => !m.currentStatus || m.currentStatus === 'not_ordered').length
  const ordered = data.filter(m => m.currentStatus === 'ordered' || m.currentStatus === 'delayed').length
  const delivered = data.filter(m => m.currentStatus === 'delivered').length
  return [
    { key: 'total', label: '总物料', value: data.length, iconRef: 'ep:box', color: '#2468F2', bg: '#DCE7FF' },
    { key: 'not_ordered', label: '未下单', value: notOrdered, iconRef: 'ep:document', color: '#86909C', bg: '#F2F3F5' },
    { key: 'ordered', label: '已采购', value: ordered, iconRef: 'ep:shopping-cart', color: '#00B42A', bg: '#E8FFEA' },
    { key: 'delivered', label: '已到货', value: delivered, iconRef: 'ep:circle-check', color: '#2468F2', bg: '#DCE7FF' }
  ]
})

function getStatusColor(m: any): string {
  if (!m) return '#86909C'
  const status = calcWarningStatus(m)
  if (status === 'urgent') return '#F53F3F'
  if (status === 'warning') return '#FF7D00'
  return '#00B42A'
}

function getStatusLabel(m: any): string {
  if (!m) return '-'
  const status = calcWarningStatus(m)
  if (status === 'urgent') return '紧急(已超期)'
  if (status === 'warning') return '预警(≤7天)'
  return '正常'
}

// 自动计算预警状态：正常 / 预警(≤7天) / 紧急(已超期)
function calcWarningStatus(m: any): string {
  if (m.actualDeliveryDate) return 'normal' // 已到货
  const targetDate = m.planOrderDate || m.latestOrderDate
  if (!targetDate) return 'normal'
  const now = new Date()
  const target = new Date(targetDate)
  if (isNaN(target.getTime())) return 'normal'
  const diff = Math.floor((target.getTime() - now.getTime()) / (1000 * 60 * 60 * 24))
  if (diff < 0) return 'urgent'  // 已超期
  if (diff <= 7) return 'warning' // 7天内
  return 'normal'
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

async function urge(row: any) {
  try {
    await updateMaterialTrack({
      ...row,
      lastUrgeTime: Date.now() as any,
      urgeCount: (row.urgeCount || 0) + 1
    } as any)
    row.urgeCount = (row.urgeCount || 0) + 1
    row.lastUrgeTime = Date.now()
    ElMessage.success(`已向「${row.supplier}」发送催交通知`)
  } catch (e) {
    console.error('催交请求失败', e)
    ElMessage.error('催交通知发送失败')
  }
}

function resetFilters() {
  Object.assign(filters, { name: '', status: '' })
}

function openCreateDialog() {
  Object.assign(createForm, {
    materialName: '',
    materialCode: '',
    supplier: '',
    quantity: undefined,
    unit: '',
    planOrderDate: '',
    planDeliveryDate: '',
    currentStatus: 'not_ordered'
  })
  createFormRef.value?.resetFields()
  createDialogVisible.value = true
}

async function submitCreate() {
  try {
    await createFormRef.value?.validate()
  } catch {
    return // 校验未通过，Element Plus 自动展示错误提示
  }
  submitting.value = true
  try {
    await createMaterialTrack({
      // #10 关键：projectId 强制从 props 取（string，避免雪花ID精度丢失）
      projectId: String(props.projectId),
      materialName: createForm.materialName,
      materialCode: createForm.materialCode || undefined,
      supplier: createForm.supplier || undefined,
      quantity: createForm.quantity,
      unit: createForm.unit || undefined,
      planOrderDate: createForm.planOrderDate || undefined,
      planDeliveryDate: createForm.planDeliveryDate || undefined,
      currentStatus: createForm.currentStatus,
      warningStatus: 'normal'
    } as any)
    ElMessage.success('物料创建成功')
    createDialogVisible.value = false
    await loadData()
  } catch (e: any) {
    console.error(e)
    ElMessage.error(e?.message || '创建失败')
  } finally {
    submitting.value = false
  }
}

// ==================== 导入模板功能 ====================

async function handleDownloadTemplate() {
  try {
    // request.download 设 responseType=blob，axios 拦截器对非JSON blob 直接返回 Blob 本身
    // （见 service.ts:153-161：blob 且 type!=='application/json' 时 return response.data）
    // 所以 getMaterialImportTemplate() 的返回值就是 Blob，无需再取 .data
    const blob = (await getMaterialImportTemplate(props.projectId)) as Blob
    download.excel(blob, '物料跟踪导入模板.xlsx')
    ElMessage.success('模板下载成功')
  } catch (e: any) {
    console.error('下载模板失败', e)
    ElMessage.error(e?.message || '模板下载失败')
  }
}

function triggerImport() {
  fileInputRef.value?.click()
}

async function handleImportFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  // 重置 input 以便同一文件可重复选择
  input.value = ''

  try {
    ElMessage.info('正在导入，请稍候...')
    // postOriginal + responseType=blob：
    //   成功 → 拦截器解析 JSON blob，返回 { code:0, data:{success, successCount} }
    //   失败(校验不通过) → 拦截器透传 Excel Blob
    const result = (await importMaterialTrack(props.projectId, file)) as any

    if (result instanceof Blob) {
      // 校验失败 → 后端返回错误 Excel（blob），触发浏览器下载
      download.excel(result, '物料跟踪导入错误.xlsx')
      ElMessage.warning('导入数据校验未通过，已自动下载错误明细，请修正后重试')
    } else {
      // 成功回执：CommonResult<MaterialTrackImportRespVO>
      if (result.code !== 0) throw new Error(result.msg || '导入失败')
      if (result.data?.success) {
        ElMessage.success(`导入成功！共导入 ${result.data.successCount || 0} 条物料`)
        await loadData()
      } else {
        ElMessage.warning('导入数据校验未通过，请检查数据')
      }
    }
  } catch (e: any) {
    console.error('导入失败', e)
    ElMessage.error(e?.message || '导入失败')
  }
}

async function loadData() {
  if (!props.projectId) return
  loading.value = true
  try {
    const res = await getMaterialTrackList(String(props.projectId))
    tableData.value = (res as any[]) || []
  } catch (e) {
    console.error('加载物料数据失败', e)
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

// 项目切换时刷新数据 + 重新加载项目级权限
watch(
  () => props.projectId,
  (newId) => {
    if (newId) {
      loadPerm(String(newId))
      loadData()
    }
  },
  { immediate: true }
)

defineExpose({ refresh: loadData })
</script>

<style scoped lang="scss">
.pms-material-tab {
  .stat-card {
    .stat-content { display: flex; align-items: center; gap: 12px; }
    .stat-icon {
      width: 48px; height: 48px; border-radius: 10px;
      display: flex; align-items: center; justify-content: center; flex-shrink: 0;
    }
    .stat-value { font-size: 28px; font-weight: 700; }
    .stat-label { font-size: 14px; color: var(--el-text-color-secondary); }
  }
  .list-toolbar {
    display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;
  }
  .toolbar-actions { display: flex; gap: 8px; align-items: center; }
  .ml-8px { margin-left: 8px; }
  .drawer-header { display: flex; align-items: center; gap: 12px; width: 100%; }
  .material-title { font-size: 16px; font-weight: 600; flex: 1; }
}
.mb-16px { margin-bottom: 16px; }
.mr-8px { margin-right: 8px; }
</style>
