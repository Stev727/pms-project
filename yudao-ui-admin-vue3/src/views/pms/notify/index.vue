<template>
  <div class="pms-notify">
    <ContentWrap>
      <div class="toolbar">
        <el-button type="primary" @click="openForm()" v-if="checkPermi(['pms:notify:create'])">
          <el-icon><Plus /></el-icon> 新建通知规则
        </el-button>
      </div>

      <el-table :data="ruleList" border size="small" v-loading="loading">
        <el-table-column prop="ruleName" label="规则名称" min-width="160" />
        <el-table-column prop="triggerEvent" label="触发事件" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="getEventTagType(row.triggerEvent)">{{ getEventLabel(row.triggerEvent) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="通知渠道" width="140">
          <template #default="{ row }">
            <el-tag v-for="ch in parseChannels(row.notifyChannel)" :key="ch" size="small" :type="ch === 'dingtalk' ? '' : ch === 'email' ? 'info' : 'warning'" class="mr-4px">{{ getChannelLabel(ch) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间规则" width="160">
          <template #default="{ row }">{{ row.timeRule || '-' }}</template>
        </el-table-column>
        <el-table-column label="升级规则" width="140">
          <template #default="{ row }">
            <span v-if="row.escalationFlag">{{ row.escalationCondition || '有升级' }} → {{ row.escalationTarget || '' }}</span>
            <span v-else style="color: var(--el-text-color-placeholder)">无</span>
          </template>
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
    </ContentWrap>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="formVisible" :title="editingRule ? '编辑通知规则' : '新建通知规则'" width="560px">
      <el-form ref="formRef" :model="form" label-width="100px">
        <el-form-item label="规则名称" required>
          <el-input v-model="form.ruleName" placeholder="如：任务即将到期提醒" />
        </el-form-item>
        <el-form-item label="触发事件" required>
          <el-select v-model="form.triggerEvent" placeholder="请选择" class="w-full">
            <el-option label="任务即将到期(T-3)" value="task_t_minus_3" />
            <el-option label="任务即将到期(T-1)" value="task_t_minus_1" />
            <el-option label="任务已延期(D+1)" value="task_d_plus_1" />
            <el-option label="任务延期超3天" value="task_overdue_3" />
            <el-option label="任务延期超7天" value="task_overdue_7" />
            <el-option label="任务派发" value="task_dispatched" />
            <el-option label="任务提交完成" value="task_submitted" />
            <el-option label="任务审核通过" value="task_approved" />
            <el-option label="任务审核驳回" value="task_rejected" />
            <el-option label="阶段即将开始" value="stage_starting" />
            <el-option label="项目里程碑达成" value="milestone_reached" />
            <el-option label="质量问题创建" value="quality_created" />
            <el-option label="变更待审批" value="change_pending" />
            <el-option label="月度绩效推送" value="monthly_performance" />
          </el-select>
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
            <el-option label="技术总监" value="director" />
            <el-option label="管理层+HR" value="management_hr" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间规则">
          <el-input v-model="form.timeRule" placeholder="如：计划完成日期前3个工作日" />
        </el-form-item>
        <el-divider content-position="left">免打扰设置</el-divider>
        <el-form-item label="免打扰">
          <el-switch v-model="form.doNotDisturb" />
        </el-form-item>
        <template v-if="form.doNotDisturb">
          <el-form-item label="免打扰时段">
            <el-time-picker v-model="form.dndStartTime" placeholder="开始时间" format="HH:mm" value-format="HH:mm" style="width: 120px" />
            <span style="margin: 0 8px">至</span>
            <el-time-picker v-model="form.dndEndTime" placeholder="结束时间" format="HH:mm" value-format="HH:mm" style="width: 120px" />
          </el-form-item>
        </template>
        <el-divider content-position="left">升级规则</el-divider>
        <el-form-item label="启用升级">
          <el-switch v-model="form.escalationFlag" />
        </el-form-item>
        <template v-if="form.escalationFlag">
          <el-form-item label="升级条件">
            <el-input v-model="form.escalationCondition" placeholder="如：延期3天" />
          </el-form-item>
          <el-form-item label="升级通知人">
            <el-select v-model="form.escalationTarget" placeholder="请选择" class="w-full">
              <el-option label="项目经理" value="project_manager" />
              <el-option label="部门负责人" value="dept_head" />
              <el-option label="总监" value="director" />
            </el-select>
          </el-form-item>
        </template>
        <el-form-item label="通知标题">
          <el-input v-model="form.templateTitle" placeholder="如：【任务提醒】{task_name} 即将到期" />
        </el-form-item>
        <el-form-item label="通知内容模板">
          <el-input v-model="form.templateContent" type="textarea" :rows="3"
            placeholder="支持变量：{task_name} {project_name} {plan_end_date} {delay_days}" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRule" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { checkPermi } from '@/utils/permission'
import { getNotifyRuleList, createNotifyRule, updateNotifyRule, deleteNotifyRule } from '@/api/pms/notify'

defineOptions({ name: 'PmsNotify' })

const loading = ref(false)
const saving = ref(false)
const formVisible = ref(false)
const editingRule = ref<any>(null)
const ruleList = ref<any[]>([])

const form = reactive({
  ruleName: '', triggerEvent: '', channels: ['dingtalk'],
  notifyTargets: ['main_owner', 'helper'], timeRule: '',
  doNotDisturb: false, dndStartTime: '', dndEndTime: '',
  escalationFlag: false, escalationCondition: '', escalationTarget: '',
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
  const map: Record<string, string> = {
    task_t_minus_3: 'T-3提醒', task_due_soon: '任务到期', task_overdue: '任务延期',
    stage_starting: '阶段开始', milestone_reached: '里程碑达成',
    quality_created: '质量问题', change_pending: '变更待审'
  }
  return map[e] || e
}

function getEventTagType(e: string): string {
  const map: Record<string, string> = {
    task_t_minus_3: 'warning', task_due_soon: 'warning', task_overdue: 'danger',
    stage_starting: 'primary', milestone_reached: 'success',
    quality_created: 'danger', change_pending: 'warning'
  }
  return map[e] || 'info'
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getNotifyRuleList()
    ruleList.value = res || []
  } catch (e) {
    ElMessage.error('加载通知规则失败')
  } finally {
    loading.value = false
  }
}

function openForm(row?: any) {
  editingRule.value = row
  if (row) {
    Object.assign(form, {
      ruleName: row.ruleName, triggerEvent: row.triggerEvent,
      channels: parseChannels(row.notifyChannel),
      notifyTargets: parseChannels(row.notifyTarget || 'main_owner,helper'),
      timeRule: row.timeRule || '',
      escalationFlag: row.escalationFlag || false,
      escalationCondition: row.escalationCondition || '',
      escalationTarget: row.escalationTarget || '',
      templateTitle: row.templateTitle || '',
      templateContent: row.templateContent || '',
      status: row.status || 'enabled'
    })
  } else {
    Object.assign(form, {
      ruleName: '', triggerEvent: '', channels: ['dingtalk'],
      notifyTargets: ['main_owner', 'helper'], timeRule: '',
      doNotDisturb: false, dndStartTime: '', dndEndTime: '',
      escalationFlag: false, escalationCondition: '', escalationTarget: '',
      templateTitle: '', templateContent: '', status: 'enabled'
    })
  }
  formVisible.value = true
}

async function saveRule() {
  if (!form.ruleName || !form.triggerEvent) {
    ElMessage.warning('请填写规则名称和触发事件')
    return
  }
  saving.value = true
  const data = {
    ruleName: form.ruleName,
    triggerEvent: form.triggerEvent,
    notifyChannel: form.channels.join(','),
    notifyTarget: form.notifyTargets ? form.notifyTargets.join(',') : '',
    timeRule: form.timeRule,
    escalationFlag: form.escalationFlag,
    escalationCondition: form.escalationFlag ? form.escalationCondition : null,
    escalationTarget: form.escalationFlag ? form.escalationTarget : null,
    templateTitle: form.templateTitle,
    templateContent: form.templateContent,
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
    fetchList()
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
    ElMessage.success(`规则「${row.ruleName}」已${newStatus === 'enabled' ? '启用' : '停用'}`)
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

function deleteRule(row: any) {
  ElMessageBox.confirm(`确认删除规则「${row.ruleName}」？`, '提示', { type: 'warning' }).then(async () => {
    try {
      await deleteNotifyRule(row.ruleId)
      ElMessage.success('规则已删除')
      fetchList()
    } catch (e) {
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.pms-notify {
  .toolbar { display: flex; justify-content: flex-end; margin-bottom: 12px; }
}
</style>
