<template>
  <div class="changes-tab">
    <div class="tab-toolbar">
      <span class="toolbar-title">变更记录 ({{ filteredList.length }})</span>
      <div style="display: flex; gap: 8px">
        <el-select v-model="filterType" placeholder="变更类型" clearable size="small" style="width: 110px">
          <el-option v-for="(v, k) in changeTypes" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-select v-model="filterStatus" placeholder="状态" clearable size="small" style="width: 100px">
          <el-option v-for="(v, k) in changeStatusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-button type="primary" size="small" @click="showForm = true" v-if="checkPermi(['pms:change:create'])">
          <Icon icon="ep:plus" class="mr-4px" />发起变更
        </el-button>
      </div>
    </div>

    <div v-loading="loading" class="change-timeline">
      <el-timeline v-if="filteredList.length > 0">
        <el-timeline-item
          v-for="item in filteredList"
          :key="item.id"
          :timestamp="formatDate(item.applyTime, 'YYYY-MM-DD HH:mm')"
          :type="getTimelineType(item.status)"
          placement="top"
          @click="openDetail(item)"
        >
          <el-card shadow="hover" class="change-card" @click="openDetail(item)">
            <div class="change-card-header">
              <span class="change-card-number">{{ item.changeNumber }}</span>
              <el-tag :color="changeTypes[item.type]?.color" effect="dark" size="small">{{ changeTypes[item.type]?.label }}</el-tag>
              <el-tag :color="changeStatusMap[item.status]?.color" effect="dark" size="small">{{ changeStatusMap[item.status]?.label }}</el-tag>
              <el-tag v-if="item.urgent" type="danger" effect="dark" size="small">紧急</el-tag>
            </div>
            <div class="change-card-title">{{ item.title }}</div>

            <!-- P1: 变更前/变更后直接展示，无需点入详情 -->
            <div class="change-card-compare" v-if="item.beforeContent || item.afterContent">
              <div class="compare-cell before">
                <div class="compare-label">变更前</div>
                <div class="compare-text">{{ item.beforeContent || '--' }}</div>
              </div>
              <div class="compare-arrow">
                <Icon icon="ep:right" />
              </div>
              <div class="compare-cell after">
                <div class="compare-label">变更后</div>
                <div class="compare-text">{{ item.afterContent || '--' }}</div>
              </div>
            </div>

            <div class="change-card-meta">
              <span v-if="item.applicant"><Icon icon="ep:user" class="mr-2px" />{{ item.applicant }}</span>
              <span v-if="item.taskName" class="meta-task"><Icon icon="ep:list" class="mr-2px" />{{ item.taskName }}</span>
              <span v-if="item.reason" class="meta-reason" :title="item.reason">
                <Icon icon="ep:chat-line-square" class="mr-2px" />{{ item.reason.length > 30 ? item.reason.slice(0, 30) + '...' : item.reason }}
              </span>
              <template v-if="item.impacts && item.impacts.length > 0">
                <el-tag v-for="(impact, i) in item.impacts" :key="i" size="small" type="warning" effect="plain" style="margin-right: 4px">
                  {{ impact }}
                </el-tag>
              </template>
            </div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
    </div>

    <el-empty v-if="!loading && filteredList.length === 0" description="暂无变更记录" />

    <!-- 变更详情抽屉 -->
    <el-drawer v-model="drawerVisible" size="720px" :show-close="false">
      <template #header>
        <div class="drawer-header">
          <el-tag :color="changeTypes[selected?.type]?.color" effect="dark">{{ changeTypes[selected?.type]?.label }}</el-tag>
          <el-tag :color="changeStatusMap[selected?.status]?.color" effect="dark">{{ changeStatusMap[selected?.status]?.label }}</el-tag>
          <span class="title">{{ selected?.title }}</span>
          <el-button link @click="drawerVisible = false"><Icon icon="ep:close" /></el-button>
        </div>
      </template>
      <template v-if="selected">
        <el-descriptions title="变更信息" :column="2" border size="small">
          <el-descriptions-item label="变更编号">{{ selected.changeNumber }}</el-descriptions-item>
          <el-descriptions-item label="变更类型">{{ changeTypes[selected.type]?.label }}</el-descriptions-item>
          <el-descriptions-item label="关联任务">{{ selected.taskName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ selected.applicant }}</el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ formatDate(selected.applyTime) }}</el-descriptions-item>
          <el-descriptions-item label="审批人">{{ selected.approver || '待指派' }}</el-descriptions-item>
          <el-descriptions-item label="紧急程度">
            <el-tag :color="selected.urgent ? '#FF7D00' : '#86909C'" effect="dark" size="small">
              {{ selected.urgent ? '紧急' : '普通' }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <div class="section-title">变更内容</div>
        <el-row :gutter="16">
          <el-col :span="12">
            <div class="compare-block before">
              <div class="compare-label">变更前</div>
              <pre>{{ selected.beforeContent || '--' }}</pre>
            </div>
          </el-col>
          <el-col :span="12">
            <div class="compare-block after">
              <div class="compare-label">变更后</div>
              <pre>{{ selected.afterContent || '--' }}</pre>
            </div>
          </el-col>
        </el-row>

        <div class="section-title">变更原因</div>
        <p class="reason-text">{{ selected.reason || '-' }}</p>

        <div class="section-title">影响分析</div>
        <ul class="impact-list">
          <li v-for="(impact, i) in (selected.impacts || [])" :key="i">{{ impact }}</li>
          <li v-if="!selected.impacts || selected.impacts.length === 0">无</li>
        </ul>

        <div class="section-title">审批流程</div>
        <el-timeline>
          <el-timeline-item timestamp="申请" type="success">{{ selected.applicant }} 提交变更申请</el-timeline-item>
          <el-timeline-item v-if="selected.status !== 'draft'" timestamp="审批"
            :type="selected.status === 'approved' ? 'success' : selected.status === 'rejected' ? 'danger' : 'primary'">
            {{ selected.approver || '审批人' }} {{ changeStatusMap[selected.status]?.label }}
          </el-timeline-item>
          <el-timeline-item v-if="selected.status === 'approved'" timestamp="执行" type="info">系统自动执行变更</el-timeline-item>
        </el-timeline>

        <template v-if="selected.status === 'pending' || selected.status === 'approving'">
          <div class="section-title">审批操作</div>
          <el-form label-width="80px">
            <el-form-item label="审批结果">
              <el-radio-group v-model="approveResult">
                <el-radio value="approve">通过</el-radio>
                <el-radio value="reject">驳回</el-radio>
                <el-radio value="modify">需修改</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="审批意见">
              <el-input v-model="approveOpinion" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item>
              <el-checkbox v-model="autoAdjust">通过后自动调整项目计划</el-checkbox>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="submitApproval" :loading="saving">提交审批</el-button>
            </el-form-item>
          </el-form>
        </template>
        <div v-if="selected.status === 'approved'" class="section-title">执行操作</div>
      </template>
    </el-drawer>

    <!-- 发起变更弹窗 -->
    <el-dialog v-model="showForm" title="发起变更" width="600px">
      <el-form label-width="90px">
        <el-form-item label="变更标题" required><el-input v-model="newChange.title" placeholder="请输入变更标题" /></el-form-item>
        <el-form-item label="变更类型" required>
          <el-select v-model="newChange.type" class="w-full">
            <el-option v-for="(v, k) in changeTypes" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联任务">
          <el-select v-model="newChange.affectedTasks" filterable placeholder="选择关联任务（可选）" class="w-full">
            <el-option v-for="t in tasks" :key="t.taskId" :label="t.taskName" :value="String(t.taskId)" />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="紧急程度"><el-switch v-model="newChange.urgent" active-text="紧急" inactive-text="普通" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="成本影响(元)"><el-input-number v-model="newChange.costImpact" :min="0" class="w-full" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="变更前内容"><el-input v-model="newChange.beforeContent" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="变更后内容"><el-input v-model="newChange.afterContent" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="变更原因" required><el-input v-model="newChange.reason" type="textarea" :rows="2" placeholder="请详细说明变更原因" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showForm = false">取消</el-button><el-button type="primary" @click="submitChange" :loading="saving">提交</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getChangeRecordList, createChangeRecord, reviewChangeRecord, ChangeRecordVO } from '@/api/pms/change'
import { getTaskList, TaskVO } from '@/api/pms/task'
import { formatDate } from '../pms-utils'
import { checkPermi } from '@/utils/permission'
import { useUserNames } from '@/hooks/pms/useUserNames'
import { useCache } from '@/hooks/web/useCache'

defineOptions({ name: 'ChangesTab' })

const { getUserName } = useUserNames()

const props = defineProps<{
  projectId: string
}>()

const loading = ref(false)
const saving = ref(false)
const filterType = ref('')
const filterStatus = ref('')
const drawerVisible = ref(false)
const selected = ref<any>(null)
const showForm = ref(false)
const approveResult = ref('approve')
const approveOpinion = ref('')
const autoAdjust = ref(true)
const tasks = ref<TaskVO[]>([])

const changeTypes: Record<string, { label: string; color: string }> = {
  requirement: { label: '需求变更', color: '#2468F2' },
  technical: { label: '技术变更', color: '#722ED1' },
  schedule: { label: '计划变更', color: '#FF7D00' },
  personnel: { label: '人员变更', color: '#0FC6C2' }
}

const changeStatusMap: Record<string, { label: string; color: string }> = {
  draft: { label: '草稿', color: '#86909C' },
  pending: { label: '待审批', color: '#FF7D00' },
  approving: { label: '审批中', color: '#2468F2' },
  approved: { label: '已通过', color: '#00B42A' },
  rejected: { label: '已驳回', color: '#F53F3F' },
  revoked: { label: '已撤销', color: '#86909C' }
}

const changeList = ref<any[]>([])

const filteredList = computed(() => {
  let r = changeList.value
  if (filterType.value) r = r.filter(c => c.type === filterType.value)
  if (filterStatus.value) r = r.filter(c => c.status === filterStatus.value)
  return r
})

const newChange = reactive({
  title: '', type: 'requirement', urgent: false, beforeContent: '', afterContent: '',
  reason: '', affectedTasks: '', costImpact: 0, scheduleImpact: 0
})

const getTaskCode = (taskId: any) => {
  if (!taskId) return ''
  const task = tasks.value.find(t => String(t.taskId) === String(taskId))
  return task ? (task.taskCode || task.taskName || '') : String(taskId)
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getChangeRecordList()
    changeList.value = ((res as any[]) || [])
      .filter(item => String(item.projectId) === String(props.projectId))
      .map(item => ({
        ...item,
        changeNumber: item.changeCode || '',
        title: item.changeDescription || '',
        type: item.changeType || '',
        status: item.approvalStatus || '',
        applicant: item.initiatorId ? getUserName(item.initiatorId) : '',
        approver: item.approverId ? getUserName(item.approverId) : '',
        applyTime: item.createTime || '',
        urgent: item.scheduleImpact ? Number(item.scheduleImpact) > 0 : false,
        beforeContent: item.beforeContent || '',
        afterContent: item.afterContent || '',
        reason: item.changeReason || '',
        impacts: [
          ...(item.costImpact ? [`成本: ${item.costImpact}元`] : []),
          ...(item.scheduleImpact ? [`工期: ${item.scheduleImpact}天`] : [])
        ],
        taskName: getTaskCode(item.taskId || item.affectedTasks)
      }))
  } catch (e) { console.error(e); changeList.value = [] }
  finally { loading.value = false }
}

function getTimelineType(status: string): string {
  const map: Record<string, string> = {
    draft: 'info',
    pending: 'warning',
    approving: 'primary',
    approved: 'success',
    rejected: 'danger',
    executed: 'success'
  }
  return map[status] || 'info'
}

function openDetail(row: any) { selected.value = row; drawerVisible.value = true }

async function submitChange() {
  if (!newChange.title) { ElMessage.warning('请填写变更标题'); return }
  if (!newChange.reason) { ElMessage.warning('请填写变更原因'); return }
  saving.value = true
  try {
    // P1-05: 获取当前用户 ID 作为申请人
    const userInfo = useCache().wsCache.get('userInfo')
    const currentUserId = userInfo?.id
    const submitData: any = {
      changeDescription: newChange.title,
      changeType: newChange.type,
      changeReason: newChange.reason,
      projectId: props.projectId,
      affectedTasks: newChange.affectedTasks || null,
      taskId: newChange.affectedTasks || null,
      beforeContent: newChange.beforeContent,
      afterContent: newChange.afterContent,
      urgent: newChange.urgent,
      costImpact: newChange.costImpact ?? 0,
      scheduleImpact: newChange.scheduleImpact ?? 0,
      changeCode: `CR-${Date.now().toString().slice(-6)}`,
      approvalStatus: 'pending'
    }
    // 仅在有 userInfo 时才传 initiatorId，避免传入字符串 "undefined"
    if (currentUserId) {
      submitData.initiatorId = String(currentUserId)
    }
    await createChangeRecord(submitData as unknown as ChangeRecordVO)
    ElMessage.success('变更已提交')
    showForm.value = false
    Object.assign(newChange, { title: '', type: 'requirement', urgent: false, beforeContent: '', afterContent: '', reason: '', affectedTasks: '', costImpact: 0, scheduleImpact: 0 })
    await fetchList()
  } catch (e) { console.error(e); ElMessage.error('提交失败') }
  finally { saving.value = false }
}

async function submitApproval() {
  if (!selected.value) return
  try {
    await ElMessageBox.confirm('确认提交审批结果？', '提示', { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' })
  } catch { return }
  saving.value = true
  try {
    if (approveResult.value === 'modify') {
      ElMessage.warning('请选择通过或驳回')
      return
    }
    await reviewChangeRecord(selected.value.changeId, approveResult.value === 'approve')
    ElMessage.success('审批已提交')
    drawerVisible.value = false
    await fetchList()
  } catch (e) { console.error(e); ElMessage.error('审批失败') }
  finally { saving.value = false }
}

async function loadTasks() {
  try {
    const data = await getTaskList()
    tasks.value = ((data as TaskVO[]) || []).filter(t => String(t.projectId) === String(props.projectId))
  } catch (e) { console.error(e) }
}

onMounted(async () => {
  await loadTasks()
  await fetchList()
})

defineExpose({ refresh: fetchList })
</script>

<style scoped>
.changes-tab { }
.tab-toolbar {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; flex-wrap: wrap; gap: 8px;
}
.toolbar-title { font-size: 14px; font-weight: 600; color: #1D2129; }
.drawer-header { display: flex; align-items: center; gap: 8px; width: 100%; .title { font-size: 16px; font-weight: 600; flex: 1; } }
.section-title { font-size: 14px; font-weight: 600; margin: 20px 0 10px; padding-left: 8px; border-left: 3px solid var(--el-color-primary); }
.compare-block {
  border: 1px solid var(--el-border-color); border-radius: 4px; padding: 12px; min-height: 100px;
  &.before { background: #FFF7E8; .compare-label { color: #FF7D00; } }
  &.after { background: #E8FFEA; .compare-label { color: #00B42A; } }
  .compare-label { font-weight: 600; margin-bottom: 8px; }
  pre { white-space: pre-wrap; word-break: break-all; margin: 0; font-size: 13px; }
}
.reason-text { font-size: 14px; line-height: 1.6; margin: 0; }
.impact-list { padding-left: 20px; li { font-size: 14px; line-height: 1.8; } }
.change-timeline { padding: 8px 0; }
.change-card { cursor: pointer; transition: all 0.2s; }
.change-card:hover { box-shadow: 0 2px 12px rgba(0,0,0,0.12); }
.change-card-header { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; }
.change-card-number { font-size: 13px; color: #86909C; font-weight: 600; }
.change-card-title { font-size: 14px; font-weight: 500; color: #1D2129; margin-bottom: 6px; }
.change-card-meta { display: flex; align-items: center; gap: 12px; font-size: 12px; color: #86909C; flex-wrap: wrap; margin-top: 8px; }
.change-card-meta .meta-task { color: #2468F2; }
.change-card-meta .meta-reason { color: #FF7D00; }
/* 变更前/变更后对比 */
.change-card-compare {
  display: flex;
  align-items: stretch;
  gap: 6px;
  margin: 8px 0 4px;
  font-size: 13px;
}
.change-card-compare .compare-cell {
  flex: 1;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  padding: 6px 10px;
  min-height: 36px;
}
.change-card-compare .compare-cell.before { background: #FFF7E8; border-color: #FFD79C; }
.change-card-compare .compare-cell.after { background: #E8FFEA; border-color: #A8E5B5; }
.change-card-compare .compare-label {
  font-size: 11px;
  font-weight: 600;
  margin-bottom: 2px;
}
.change-card-compare .compare-cell.before .compare-label { color: #FF7D00; }
.change-card-compare .compare-cell.after .compare-label { color: #00B42A; }
.change-card-compare .compare-text {
  white-space: pre-wrap;
  word-break: break-all;
  color: #1D2129;
  line-height: 1.5;
}
.change-card-compare .compare-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #86909C;
  font-size: 16px;
}
</style>
