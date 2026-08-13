<template>
  <div class="pms-notify-project">
    <ContentWrap>
      <div class="toolbar">
        <span class="tip">项目级通知规则：到达设定条件后自动发送钉钉 / 系统通知</span>
        <div class="spacer" />
        <el-button @click="manualCheck" :loading="checking" v-if="checkPermi(['pms:notify:update'])">
          <el-icon><Refresh /></el-icon> 立即检查
        </el-button>
        <el-button type="primary" @click="openForm()" v-if="checkPermi(['pms:notify:create'])">
          <el-icon><Plus /></el-icon> 新建通知规则
        </el-button>
      </div>

      <el-table :data="ruleList" border size="small" v-loading="loading">
        <el-table-column prop="ruleName" label="规则名称" min-width="170" />
        <el-table-column label="触发条件" width="140">
          <template #default="{ row }">
            <el-tag size="small" :type="getEventTagType(row.triggerEvent)">{{ getEventLabel(row.triggerEvent) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="通知渠道" width="160">
          <template #default="{ row }">
            <template v-if="row.notifyChannel">
              <el-tag v-for="ch in parseChannels(row.notifyChannel)" :key="ch" size="small" class="mr-4px"
                :type="ch === 'dingtalk' ? '' : ch === 'email' ? 'info' : 'warning'">{{ getChannelLabel(ch) }}</el-tag>
            </template>
            <span v-else style="color: var(--el-text-color-placeholder)">-</span>
          </template>
        </el-table-column>
        <el-table-column label="通知对象" min-width="170">
          <template #default="{ row }">{{ getTargetLabel(row.notifyTarget) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 'enabled'" @change="toggleRule(row)" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="deleteRule(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && ruleList.length === 0" description="暂无通知规则，点击右上角新建" />
    </ContentWrap>

    <!-- 编辑 / 新建弹窗 -->
    <el-dialog v-model="formVisible" :title="editingRule ? '编辑通知规则' : '新建通知规则'" width="580px">
      <el-form ref="formRef" :model="form" label-width="100px">
        <el-form-item label="规则名称" required>
          <el-input v-model="form.ruleName" placeholder="如：本项目任务延期超3天提醒" />
        </el-form-item>
        <el-form-item label="触发条件" required>
          <el-select v-model="form.triggerEvent" placeholder="请选择" class="w-full" @change="onEventChange">
            <el-option v-for="e in triggerEvents" :key="e.value" :label="e.label" :value="e.value" />
          </el-select>
          <div v-if="form.triggerEvent === '__custom__'" class="mt-8px">
            任务延期满
            <el-input-number v-model="form.customDays" :min="1" :max="90" controls-position="right" />
            天后提醒
          </div>
        </el-form-item>
        <el-form-item label="通知渠道">
          <el-checkbox-group v-model="form.channels">
            <el-checkbox value="dingtalk">钉钉</el-checkbox>
            <el-checkbox value="email">邮件</el-checkbox>
            <el-checkbox value="sms">短信</el-checkbox>
            <el-checkbox value="system_msg">系统消息</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="通知对象">
          <el-select v-model="form.notifyTargets" multiple placeholder="选择通知对象" class="w-full">
            <el-option label="主责任人" value="main_owner" />
            <el-option label="协助人" value="helper" />
            <el-option label="项目经理" value="pm" />
            <el-option label="部门负责人" value="dept_head" />
          </el-select>
        </el-form-item>
        <el-form-item label="通知标题">
          <el-input v-model="form.templateTitle" placeholder="如：【任务提醒】{task_name} 即将到期" />
        </el-form-item>
        <el-form-item label="通知内容模板">
          <el-input v-model="form.templateContent" type="textarea" :rows="3"
            placeholder="支持变量：{task_name} {project_name} {plan_end_date} {delay_days}" />
        </el-form-item>
        <el-alert type="info" :closable="false" class="mt-4px">
          <template #title>
            可用变量：{task_name} 任务名称、{project_name} 项目名称、{plan_end_date} 计划结束日期、{delay_days} 延期天数
          </template>
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRule" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, PropType } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { checkPermi } from '@/utils/permission'
import {
  getNotifyRuleList, createNotifyRule, updateNotifyRule, deleteNotifyRule, manualTriggerNotifyCheck
} from '@/api/pms/notify'

const props = defineProps({
  projectId: { type: [String, Number] as PropType<string | number>, required: true }
})

const loading = ref(false)
const saving = ref(false)
const checking = ref(false)
const formVisible = ref(false)
const editingRule = ref<any>(null)
const ruleList = ref<any[]>([])

const triggerEvents = [
  { label: '任务到期前3天提醒', value: 'task_t_minus_3' },
  { label: '任务到期前1天提醒', value: 'task_t_minus_1' },
  { label: '任务逾期后每日提醒', value: 'task_overdue' },
  { label: '任务延期满1天提醒', value: 'task_overdue_1' },
  { label: '任务延期满3天提醒', value: 'task_overdue_3' },
  { label: '任务延期满7天提醒', value: 'task_overdue_7' },
  { label: '自定义延期天数提醒', value: '__custom__' }
]

const form = reactive({
  ruleName: '', triggerEvent: 'task_t_minus_3', customDays: 3,
  channels: ['dingtalk'], notifyTargets: ['main_owner', 'helper'],
  templateTitle: '', templateContent: '', status: 'enabled'
})

function parseChannels(ch: string): string[] {
  return ch ? ch.split(',').filter(Boolean) : []
}

function getChannelLabel(ch: string): string {
  const map: Record<string, string> = { dingtalk: '钉钉', email: '邮件', sms: '短信', system_msg: '系统' }
  return map[ch] || ch
}

function getEventLabel(e: string): string {
  if (!e) return '-'
  if (e === 'task_t_minus_3') return '到期前3天'
  if (e === 'task_t_minus_1') return '到期前1天'
  if (e === 'task_overdue') return '逾期每日'
  if (e === 'task_overdue_1') return '延期满1天'
  if (e === 'task_overdue_3') return '延期满3天'
  if (e === 'task_overdue_7') return '延期满7天'
  if (e.startsWith('task_overdue_')) return `延期满${e.substring('task_overdue_'.length)}天`
  if (e.startsWith('task_t_minus_')) return `到期前${e.substring('task_t_minus_'.length)}天`
  return e
}

function getEventTagType(e: string): string {
  if (e && e.startsWith('task_t_minus_')) return 'warning'
  return 'danger'
}

function getTargetLabel(t: string): string {
  if (!t) return '-'
  const map: Record<string, string> = {
    main_owner: '主责任人', helper: '协助人', pm: '项目经理',
    dept_head: '部门负责人', director: '总监', management_hr: '管理层+HR'
  }
  return t.split(',').map(x => map[x.trim()] || x.trim()).join('、')
}

async function loadRules() {
  loading.value = true
  try {
    const res = await getNotifyRuleList()
    ruleList.value = (res || []).filter((r: any) =>
      r.scopeType === 'project' && String(r.projectId) === String(props.projectId))
  } catch (e) {
    ElMessage.error('加载通知规则失败')
  } finally {
    loading.value = false
  }
}

function onEventChange() {
  if (form.triggerEvent === '__custom__' && (!form.customDays || form.customDays < 1)) {
    form.customDays = 3
  }
}

function openForm(row?: any) {
  editingRule.value = row
  if (row) {
    Object.assign(form, {
      ruleName: row.ruleName, triggerEvent: row.triggerEvent, customDays: 3,
      channels: parseChannels(row.notifyChannel),
      notifyTargets: parseChannels(row.notifyTarget || 'main_owner,helper'),
      templateTitle: row.templateTitle || '', templateContent: row.templateContent || '',
      status: row.status || 'enabled'
    })
  } else {
    Object.assign(form, {
      ruleName: '', triggerEvent: 'task_t_minus_3', customDays: 3,
      channels: ['dingtalk'], notifyTargets: ['main_owner', 'helper'],
      templateTitle: '', templateContent: '', status: 'enabled'
    })
  }
  formVisible.value = true
}

async function saveRule() {
  if (!form.ruleName || !form.triggerEvent) {
    ElMessage.warning('请填写规则名称和触发条件')
    return
  }
  let triggerEvent = form.triggerEvent
  if (triggerEvent === '__custom__') {
    if (!form.customDays || form.customDays < 1) {
      ElMessage.warning('请填写正确的延期天数')
      return
    }
    triggerEvent = `task_overdue_${form.customDays}`
  }
  saving.value = true
  const data: any = {
    projectId: props.projectId, scopeType: 'project',
    ruleName: form.ruleName, triggerEvent,
    notifyChannel: form.channels.join(','),
    notifyTarget: form.notifyTargets.join(','),
    templateTitle: form.templateTitle, templateContent: form.templateContent,
    status: form.status
  }
  try {
    if (editingRule.value) {
      await updateNotifyRule({ ...data, ruleId: editingRule.value.ruleId })
      ElMessage.success('规则已更新')
    } else {
      await createNotifyRule(data)
      ElMessage.success('规则已创建')
    }
    formVisible.value = false
    loadRules()
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleRule(row: any) {
  const newStatus = row.status === 'enabled' ? 'disabled' : 'enabled'
  try {
    await updateNotifyRule({ ruleId: row.ruleId, ruleName: row.ruleName, status: newStatus })
    row.status = newStatus
    ElMessage.success(`规则已${newStatus === 'enabled' ? '启用' : '停用'}`)
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

function deleteRule(row: any) {
  ElMessageBox.confirm(`确认删除规则「${row.ruleName}」？`, '提示', { type: 'warning' }).then(async () => {
    try {
      await deleteNotifyRule(row.ruleId)
      ElMessage.success('规则已删除')
      loadRules()
    } catch (e) {
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}

async function manualCheck() {
  checking.value = true
  try {
    await manualTriggerNotifyCheck()
    ElMessage.success('已触发一次通知检查，详情见通知日志')
  } catch (e) {
    ElMessage.error('触发失败')
  } finally {
    checking.value = false
  }
}

onMounted(() => {
  loadRules()
})
</script>

<style scoped lang="scss">
.pms-notify-project {
  .toolbar {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    margin-bottom: 12px;

    .tip {
      margin-right: auto;
      font-size: 13px;
      color: var(--el-text-color-secondary);
    }
  }
}
</style>
