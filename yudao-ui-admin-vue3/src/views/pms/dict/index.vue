<template>
  <div class="pms-dict">
    <el-alert
      title="字典由系统统一维护（系统管理 → 字典管理 亦可编辑）。本页修改即时全站生效，无需逐设备导出。"
      type="success"
      :closable="false"
      show-icon
      class="mb-12px"
    />
    <ContentWrap>
      <div class="dict-global-toolbar">
        <el-button size="small" :loading="loadingGroup" @click="loadAll"><Icon icon="ep:refresh" class="mr-4px" />刷新</el-button>
        <el-button size="small" @click="exportDict"><Icon icon="ep:download" class="mr-4px" />导出备份</el-button>
        <el-upload
          :show-file-list="false"
          :before-upload="importDict"
          accept=".json"
        >
          <el-button size="small"><Icon icon="ep:upload" class="mr-4px" />导入数据</el-button>
        </el-upload>
      </div>
      <el-tabs v-model="activeType" type="card">
        <el-tab-pane v-for="group in dictGroups" :key="group.key" :label="group.label" :name="group.key">
          <div class="dict-toolbar">
            <span class="toolbar-title">{{ group.label }} ({{ getGroupItems(group.key).length }})</span>
            <el-button type="primary" size="small" @click="handleAdd(group.key)">
              <Icon icon="ep:plus" class="mr-4px" />添加选项
            </el-button>
          </div>

          <el-table :data="getGroupItems(group.key)" border stripe size="small" v-loading="loadingGroup">
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
import {
  getDictDataByType,
  createDictData,
  updateDictData,
  deleteDictData
} from '@/api/system/dict/dict.data'

defineOptions({ name: 'PmsDict' })

// 字典分组定义（与系统字典 dict_type 一一对应）
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
const loadingGroup = ref(false)

const form = reactive({
  label: '',
  value: '',
  color: '',
  sort: 0,
  statusBool: true
})

const currentGroupLabel = ref('')

// 字典数据（真实来源：yudao 系统字典 system_dict_data，经后端 API 读写）
const dictData = ref<Record<string, any[]>>({})
dictGroups.forEach((g) => { dictData.value[g.key] = [] })

async function loadGroup(key: string) {
  try {
    const list: any[] = await getDictDataByType(key)
    dictData.value[key] = (list || [])
      .map((d: any) => ({
        id: d.id,
        label: d.label,
        value: d.value,
        color: d.colorType || '',
        sort: d.sort ?? 0,
        status: d.status ?? 0
      }))
      .sort((a: any, b: any) => (a.sort || 0) - (b.sort || 0))
  } catch {
    dictData.value[key] = []
  }
}

async function loadAll() {
  loadingGroup.value = true
  try {
    await Promise.all(dictGroups.map((g) => loadGroup(g.key)))
  } finally {
    loadingGroup.value = false
  }
}

function getGroupItems(key: string) {
  return dictData.value[key] || []
}

function handleAdd(key: string) {
  editing.value = null
  currentGroupKey.value = key
  currentGroupLabel.value = dictGroups.find((g) => g.key === key)?.label || ''
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
  currentGroupLabel.value = dictGroups.find((g) => g.key === key)?.label || ''
  form.label = row.label
  form.value = row.value
  form.color = row.color || ''
  form.sort = row.sort
  form.statusBool = row.status === 0
  showForm.value = true
}

async function saveItem() {
  if (!form.label) { ElMessage.warning('请输入显示名称'); return }
  if (!form.value) { ElMessage.warning('请输入字典值'); return }

  const payload: any = {
    dictType: currentGroupKey.value,
    label: form.label,
    value: form.value,
    colorType: form.color || '',
    cssClass: '',
    remark: '',
    sort: form.sort,
    status: form.statusBool ? 0 : 1
  }
  try {
    if (editing.value && editing.value.id) {
      await updateDictData({ ...payload, id: editing.value.id })
      ElMessage.success('已更新')
    } else {
      await createDictData(payload)
      ElMessage.success('已添加')
    }
    showForm.value = false
    editing.value = null
    await loadGroup(currentGroupKey.value)
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

async function handleDelete(key: string, row: any) {
  if (!row.id) {
    // 尚未持久化的本地项直接移除
    const items = dictData.value[key]
    const idx = items.findIndex((i) => i === row)
    if (idx > -1) items.splice(idx, 1)
    return
  }
  ElMessageBox.confirm(`确认删除选项「${row.label}」？`, '提示', { type: 'warning' }).then(async () => {
    try {
      await deleteDictData(row.id)
      ElMessage.success('已删除')
      await loadGroup(key)
    } catch (e: any) {
      ElMessage.error(e?.message || '删除失败')
    }
  }).catch(() => {})
}

function exportDict() {
  const json = JSON.stringify(dictData.value, null, 2)
  const blob = new Blob([json], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `pms_dict_backup_${new Date().toISOString().slice(0, 10)}.json`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('字典数据已导出（仅备份，编辑请使用本页或系统字典管理）')
}

async function importDict(file: File) {
  const reader = new FileReader()
  reader.onload = async (e) => {
    try {
      const data = JSON.parse((e.target?.result as string) || '{}')
      if (typeof data !== 'object') throw new Error('格式错误')
      let created = 0
      for (const group of dictGroups) {
        const items = data[group.key]
        if (Array.isArray(items)) {
          for (const it of items) {
            try {
              await createDictData({
                dictType: group.key,
                label: it.label,
                value: it.value,
                colorType: it.color || '',
                cssClass: '',
                remark: '',
                sort: it.sort || 0,
                status: it.status ?? 0
              })
              created++
            } catch { /* 跳过重复/异常项 */ }
          }
        }
      }
      ElMessage.success(`已导入 ${created} 项，正在刷新...`)
      await loadAll()
    } catch {
      ElMessage.error('导入失败：JSON 格式错误')
    }
  }
  reader.readAsText(file)
  return false // 阻止 el-upload 自动上传
}

onMounted(() => {
  loadAll()
})
onUnmounted(() => {
  // 已无 localStorage 持久化逻辑
})
</script>

<style scoped>
.pms-dict { }
.dict-global-toolbar {
  display: flex; gap: 8px; margin-bottom: 12px;
}
.dict-toolbar {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;
}
.toolbar-title { font-size: 14px; font-weight: 600; color: #1D2129; }
.color-dot { display: inline-block; width: 16px; height: 16px; border-radius: 4px; border: 1px solid #E5E6EB; }
</style>
