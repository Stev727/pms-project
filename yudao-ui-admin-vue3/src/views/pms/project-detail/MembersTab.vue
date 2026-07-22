<template>
  <div class="members-tab">
    <!-- 工具栏 -->
    <div class="tab-toolbar">
      <span class="toolbar-title">项目成员 ({{ memberList.length }})</span>
      <el-button type="primary" size="small" @click="handleAdd" v-if="checkPermi(['pms:member:create'])">
        <Icon icon="ep:plus" class="mr-4px" />添加成员
      </el-button>
    </div>

    <el-table :data="memberList" border stripe v-loading="loading">
      <el-table-column prop="userId" label="成员" min-width="120">
        <template #default="{ row }">
          <span>{{ getUserName(row.userId) }}</span>
          <el-tag v-if="row.isExternal" size="small" type="warning" style="margin-left: 6px">外部</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="roleCode" label="项目角色" width="140">
        <template #default="{ row }">
          <el-tag size="small" :type="getRoleTagType(row.roleCode)">{{ getRoleLabel(row.roleCode) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="加入时间" width="120">
        <template #default="{ row }">{{ formatDate(row.joinTime) }}</template>
      </el-table-column>
      <el-table-column label="退出时间" width="120">
        <template #default="{ row }">{{ formatDate(row.quitTime) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 'active' ? 'success' : 'info'">
            {{ row.status === 'active' ? '活跃' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="editMember(row)" v-if="checkPermi(['pms:member:update'])">编辑</el-button>
          <el-button link type="danger" size="small" @click="removeMember(row)" v-if="checkPermi(['pms:member:delete'])">移除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && memberList.length === 0" description="暂无项目成员，请添加" />

    <!-- 添加/编辑成员弹窗 -->
    <el-dialog v-model="showDialog" :title="editing ? '编辑成员' : '添加成员'" width="480px">
      <el-form label-width="80px">
        <el-form-item label="成员" required>
          <el-select v-model="form.userId" filterable placeholder="选择用户" class="w-full">
            <el-option v-for="u in userList" :key="u.id" :label="u.nickname" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目角色" required>
          <el-select v-model="form.roleCode" class="w-full">
            <el-option label="项目经理" value="pm" />
            <el-option label="技术负责人" value="tech_lead" />
            <el-option label="硬件工程师" value="hw_engineer" />
            <el-option label="软件工程师" value="sw_engineer" />
            <el-option label="测试工程师" value="qa_engineer" />
            <el-option label="结构工程师" value="mech_engineer" />
            <el-option label="采购" value="procurement" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否外部">
          <el-switch v-model="form.isExternal" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" class="w-full">
            <el-option label="活跃" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="saveMember">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getProjectMemberList, createProjectMember, updateProjectMember, deleteProjectMember } from '@/api/pms/member'
import { getSimpleUserList } from '@/api/system/user'
import { formatDate } from '../pms-utils'
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'MembersTab' })

const props = defineProps<{
  projectId: string
}>()

const loading = ref(false)
const memberList = ref<any[]>([])
const userList = ref<any[]>([])
const showDialog = ref(false)
const editing = ref<any>(null)

const form = reactive({
  memberId: undefined as any,
  userId: undefined as any,
  roleCode: 'hw_engineer',
  isExternal: false,
  status: 'active'
})

const roleLabelMap: Record<string, string> = {
  pm: '项目经理', tech_lead: '技术负责人', hw_engineer: '硬件工程师',
  sw_engineer: '软件工程师', qa_engineer: '测试工程师',
  mech_engineer: '结构工程师', procurement: '采购'
}

function getRoleLabel(code: string): string { return roleLabelMap[code] || code || '-' }
function getRoleTagType(role: string): string {
  const map: Record<string, string> = { pm: 'danger', tech_lead: 'warning', hw_engineer: 'primary', sw_engineer: 'primary', qa_engineer: 'success', mech_engineer: 'info', procurement: '' }
  return map[role] || 'info'
}
function getUserName(userId?: number): string {
  if (!userId) return '-'
  return userList.value.find(u => u.id === userId)?.nickname || `用户${userId}`
}

function handleAdd() {
  editing.value = null
  form.memberId = undefined
  form.userId = undefined
  form.roleCode = 'hw_engineer'
  form.isExternal = false
  form.status = 'active'
  showDialog.value = true
}

function editMember(row: any) {
  editing.value = row
  form.memberId = row.memberId
  form.userId = row.userId
  form.roleCode = row.roleCode
  form.isExternal = row.isExternal
  form.status = row.status
  showDialog.value = true
}

async function saveMember() {
  if (!form.userId) { ElMessage.warning('请选择成员'); return }
  if (!form.roleCode) { ElMessage.warning('请选择项目角色'); return }
  try {
    const data = {
      memberId: form.memberId,
      projectId: Number(props.projectId),
      userId: form.userId,
      roleCode: form.roleCode,
      isExternal: form.isExternal,
      status: form.status
    }
    if (editing.value) {
      await updateProjectMember(data)
      ElMessage.success('成员已更新')
    } else {
      await createProjectMember(data)
      ElMessage.success('成员已添加')
    }
    showDialog.value = false
    editing.value = null
    await loadMembers()
  } catch (e) { console.error(e); ElMessage.error('操作失败') }
}

function removeMember(row: any) {
  ElMessageBox.confirm('确认移除该成员？', '提示', { type: 'warning' }).then(async () => {
    try {
      await deleteProjectMember(row.memberId as number)
      ElMessage.success('成员已移除')
      await loadMembers()
    } catch (e) { console.error(e) }
  }).catch(() => {})
}

async function loadMembers() {
  loading.value = true
  try {
    const data = await getProjectMemberList()
    memberList.value = ((data as any[]) || []).filter(m => String(m.projectId) === String(props.projectId))
  } catch (e) { console.error(e); memberList.value = [] }
  finally { loading.value = false }
}

async function loadUsers() {
  try {
    const res = await getSimpleUserList()
    userList.value = (res as any[]) || []
  } catch (e) { console.error(e) }
}

onMounted(() => {
  loadUsers()
  loadMembers()
})

defineExpose({ refresh: loadMembers })
</script>

<style scoped>
.members-tab { }
.tab-toolbar {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;
}
.toolbar-title { font-size: 14px; font-weight: 600; color: #1D2129; }
</style>
