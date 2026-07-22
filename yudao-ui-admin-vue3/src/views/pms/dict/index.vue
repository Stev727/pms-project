<template>
  <div class="pms-dict">
    <ContentWrap>
      <el-tabs v-model="activeType" type="card">
        <el-tab-pane v-for="group in dictGroups" :key="group.key" :label="group.label" :name="group.key">
          <div class="dict-toolbar">
            <span class="toolbar-title">{{ group.label }} ({{ getGroupItems(group.key).length }})</span>
            <el-button type="primary" size="small" @click="handleAdd(group.key)">
              <Icon icon="ep:plus" class="mr-4px" />添加选项
            </el-button>
          </div>

          <el-table :data="getGroupItems(group.key)" border stripe size="small">
            <el-table-column prop="label" label="显示名称" min-width="150" />
            <el-table-column prop="value" label="字典值" width="180" />
            <el-table-column label="颜色标识" width="100" align="center">
              <template #default="{ row }">
                <span v-if="row.color" class="color-dot" :style="{ background: row.color }"></span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="sort" label="排序" width="80" align="center" />
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 0 ? 'success' : 'info'">
                  {{ row.status === 0 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" align="center">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleEdit(group.key, row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDelete(group.key, row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </ContentWrap>

    <!-- 添加/编辑弹窗 -->
    <el-dialog v-model="showForm" :title="editing ? '编辑选项' : '添加选项'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="字典分组">
          <el-input :value="currentGroupLabel" disabled />
        </el-form-item>
        <el-form-item label="显示名称" required>
          <el-input v-model="form.label" placeholder="如：设计任务" />
        </el-form-item>
        <el-form-item label="字典值" required>
          <el-input v-model="form.value" placeholder="如：design" />
        </el-form-item>
        <el-form-item label="颜色标识">
          <el-color-picker v-model="form.color" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.statusBool" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showForm = false">取消</el-button>
        <el-button type="primary" @click="saveItem">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

defineOptions({ name: 'PmsDict' })

// 字典分组定义 (SEVERE-6 修复: 所有硬编码下拉值都从此处维护)
const dictGroups = [
  { key: 'pms_task_type', label: '任务类型' },
  { key: 'pms_project_type', label: '项目类型' },
  { key: 'pms_quality_category', label: '质量问题分类' },
  { key: 'pms_change_type', label: '变更类型' },
  { key: 'pms_document_category', label: '文档分类' },
  { key: 'pms_approval_type', label: '审批类型' },
  { key: 'pms_member_role', label: '项目成员角色' },
  { key: 'pms_material_type', label: '物料类型' },
  { key: 'pms_template_type', label: '模板类型' },
  { key: 'pms_priority', label: '优先级' }
]

const activeType = ref('pms_task_type')
const showForm = ref(false)
const editing = ref<any>(null)
const currentGroupKey = ref('')

const form = reactive({
  label: '',
  value: '',
  color: '',
  sort: 0,
  statusBool: true
})

const currentGroupLabel = ref('')

// 默认字典数据（从现有硬编码中迁移）
const dictData = ref<Record<string, any[]>>({
  pms_task_type: [
    { label: '设计任务', value: 'design', color: '#2468F2', sort: 1, status: 0 },
    { label: '评审任务', value: 'review', color: '#722ED1', sort: 2, status: 0 },
    { label: '测试任务', value: 'testing', color: '#FF7D00', sort: 3, status: 0 },
    { label: '采购任务', value: 'procurement', color: '#0FC6C2', sort: 4, status: 0 },
    { label: '试制任务', value: 'prototyping', color: '#00B42A', sort: 5, status: 0 },
    { label: '文档任务', value: 'documentation', color: '#86909C', sort: 6, status: 0 },
    { label: '审批任务', value: 'approval', color: '#F53F3F', sort: 7, status: 0 },
    { label: '供应商协同', value: 'supplier_synergy', color: '#D25F00', sort: 8, status: 0 },
    { label: '其他', value: 'other', color: '#86909C', sort: 9, status: 0 }
  ],
  pms_project_type: [
    { label: '新研发项目', value: 'new_dev', color: '#2468F2', sort: 1, status: 0 },
    { label: '改型项目', value: 'modification', color: '#722ED1', sort: 2, status: 0 },
    { label: '降本项目', value: 'cost_down', color: '#FF7D00', sort: 3, status: 0 },
    { label: '平台项目', value: 'platform', color: '#0FC6C2', sort: 4, status: 0 }
  ],
  pms_quality_category: [
    { label: '设计问题', value: 'design', color: '#2468F2', sort: 1, status: 0 },
    { label: '工艺问题', value: 'process', color: '#722ED1', sort: 2, status: 0 },
    { label: '物料问题', value: 'material', color: '#FF7D00', sort: 3, status: 0 },
    { label: '测试问题', value: 'testing', color: '#0FC6C2', sort: 4, status: 0 },
    { label: '其他', value: 'other', color: '#86909C', sort: 5, status: 0 }
  ],
  pms_change_type: [
    { label: '工期变更', value: 'schedule', color: '#2468F2', sort: 1, status: 0 },
    { label: '内容变更', value: 'content', color: '#722ED1', sort: 2, status: 0 },
    { label: '物料变更', value: 'material', color: '#FF7D00', sort: 3, status: 0 },
    { label: '人员变更', value: 'personnel', color: '#0FC6C2', sort: 4, status: 0 },
    { label: '成本变更', value: 'cost', color: '#00B42A', sort: 5, status: 0 }
  ],
  pms_document_category: [
    { label: '技术文档', value: 'tech_doc', color: '#2468F2', sort: 1, status: 0 },
    { label: '管理文档', value: 'mgmt_doc', color: '#722ED1', sort: 2, status: 0 },
    { label: '项目文档', value: 'project_doc', color: '#FF7D00', sort: 3, status: 0 },
    { label: '输出物', value: 'deliverable', color: '#0FC6C2', sort: 4, status: 0 },
    { label: '图纸', value: 'drawing', color: '#00B42A', sort: 5, status: 0 },
    { label: '报告', value: 'report', color: '#86909C', sort: 6, status: 0 },
    { label: '标准文件', value: 'standard', color: '#F53F3F', sort: 7, status: 0 }
  ],
  pms_approval_type: [
    { label: '立项审批', value: 'project_init', color: '#00B42A', sort: 1, status: 0 },
    { label: '开模审批', value: 'mold_open', color: '#2468F2', sort: 2, status: 0 },
    { label: '设计评审', value: 'design_review', color: '#722ED1', sort: 3, status: 0 },
    { label: '试产转量产', value: 'trial_to_mass', color: '#FF7D00', sort: 4, status: 0 },
    { label: '变更审批', value: 'change', color: '#F53F3F', sort: 5, status: 0 },
    { label: '质量审批', value: 'quality', color: '#0FC6C2', sort: 6, status: 0 },
    { label: '延期审批', value: 'delay', color: '#D25F00', sort: 7, status: 0 }
  ],
  pms_member_role: [
    { label: '项目经理', value: 'pm', color: '#F53F3F', sort: 1, status: 0 },
    { label: '技术负责人', value: 'tech_lead', color: '#FF7D00', sort: 2, status: 0 },
    { label: '硬件工程师', value: 'hw_engineer', color: '#2468F2', sort: 3, status: 0 },
    { label: '软件工程师', value: 'sw_engineer', color: '#2468F2', sort: 4, status: 0 },
    { label: '测试工程师', value: 'qa_engineer', color: '#00B42A', sort: 5, status: 0 },
    { label: '结构工程师', value: 'mech_engineer', color: '#86909C', sort: 6, status: 0 },
    { label: '采购', value: 'procurement', color: '#0FC6C2', sort: 7, status: 0 }
  ],
  pms_material_type: [
    { label: '长周期物料', value: 'long_lead', color: '#F53F3F', sort: 1, status: 0 },
    { label: '标准件', value: 'standard', color: '#2468F2', sort: 2, status: 0 },
    { label: '定制件', value: 'custom', color: '#722ED1', sort: 3, status: 0 }
  ],
  pms_template_type: [
    { label: '标准研发模板', value: 'standard', color: '#2468F2', sort: 1, status: 0 },
    { label: '快速开发模板', value: 'fast_dev', color: '#00B42A', sort: 2, status: 0 },
    { label: '硬件研发模板', value: 'hardware', color: '#FF7D00', sort: 3, status: 0 },
    { label: '软件研发模板', value: 'software', color: '#722ED1', sort: 4, status: 0 },
    { label: '定制模板', value: 'custom', color: '#86909C', sort: 5, status: 0 }
  ],
  pms_priority: [
    { label: '紧急', value: 'urgent', color: '#F53F3F', sort: 1, status: 0 },
    { label: '高', value: 'high', color: '#FF7D00', sort: 2, status: 0 },
    { label: '普通', value: 'normal', color: '#4E5969', sort: 3, status: 0 },
    { label: '低', value: 'low', color: '#86909C', sort: 4, status: 0 }
  ]
})

function getGroupItems(key: string) {
  return dictData.value[key] || []
}

function handleAdd(key: string) {
  editing.value = null
  currentGroupKey.value = key
  currentGroupLabel.value = dictGroups.find(g => g.key === key)?.label || ''
  form.label = ''
  form.value = ''
  form.color = ''
  form.sort = (getGroupItems(key).length + 1) * 10
  form.statusBool = true
  showForm.value = true
}

function handleEdit(key: string, row: any) {
  editing.value = row
  currentGroupKey.value = key
  currentGroupLabel.value = dictGroups.find(g => g.key === key)?.label || ''
  form.label = row.label
  form.value = row.value
  form.color = row.color || ''
  form.sort = row.sort
  form.statusBool = row.status === 0
  showForm.value = true
}

function saveItem() {
  if (!form.label) { ElMessage.warning('请输入显示名称'); return }
  if (!form.value) { ElMessage.warning('请输入字典值'); return }

  const item = {
    label: form.label,
    value: form.value,
    color: form.color,
    sort: form.sort,
    status: form.statusBool ? 0 : 1
  }

  const items = dictData.value[currentGroupKey.value]
  if (editing.value) {
    const idx = items.findIndex(i => i === editing.value)
    if (idx > -1) items.splice(idx, 1, item)
    ElMessage.success('已更新')
  } else {
    items.push(item)
    ElMessage.success('已添加')
  }

  showForm.value = false
  editing.value = null
}

function handleDelete(key: string, row: any) {
  ElMessageBox.confirm(`确认删除选项「${row.label}」？`, '提示', { type: 'warning' }).then(() => {
    const items = dictData.value[key]
    const idx = items.findIndex(i => i === row)
    if (idx > -1) items.splice(idx, 1)
    ElMessage.success('已删除')
  }).catch(() => {})
}

let saveTimer: any = null
onMounted(() => {
  // 初始化时从 localStorage 加载（模拟数据库持久化）
  const saved = localStorage.getItem('pms_dict_data')
  if (saved) {
    try {
      const parsed = JSON.parse(saved)
      // 合并默认值，保留用户修改
      for (const key of Object.keys(dictData.value)) {
        if (parsed[key]) dictData.value[key] = parsed[key]
      }
    } catch { /* ignore */ }
  }
  // 监听变化自动保存
  saveTimer = setInterval(() => {
    localStorage.setItem('pms_dict_data', JSON.stringify(dictData.value))
  }, 5000)
})

onUnmounted(() => {
  if (saveTimer) { clearInterval(saveTimer); saveTimer = null }
  // 离开时保存一次
  localStorage.setItem('pms_dict_data', JSON.stringify(dictData.value))
})
</script>

<style scoped>
.pms-dict { }
.dict-toolbar {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;
}
.toolbar-title { font-size: 14px; font-weight: 600; color: #1D2129; }
.color-dot { display: inline-block; width: 16px; height: 16px; border-radius: 4px; border: 1px solid #E5E6EB; }
</style>
