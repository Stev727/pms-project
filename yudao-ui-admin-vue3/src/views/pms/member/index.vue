<template>
  <div class="pms-member">
    <ContentWrap>
      <div class="toolbar">
        <el-select v-model="selectedProject" clearable placeholder="选择项目" @change="loadMembers" style="width: 200px">
          <el-option v-for="p in projects" :key="p.projectId" :label="p.projectName" :value="p.projectId" />
        </el-select>
        <el-button type="primary" size="small" @click="handleAdd" v-if="checkPermi(['pms:member:create'])"><el-icon><Plus /></el-icon> 添加成员</el-button>
      </div>

      <el-table :data="memberList" border size="small" v-loading="loading">
        <el-table-column prop="userId" label="用户ID" width="100" />
        <el-table-column prop="roleCode" label="项目角色" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="getRoleTag(row.roleCode)">{{ getRoleLabel(row.roleCode) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="是否外部" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.isExternal ? 'warning' : 'info'">{{ row.isExternal ? '外部' : '内部' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="加入时间" width="120">
          <template #default="{ row }">{{ formatDate(row.joinTime) }}</template>
        </el-table-column>
        <el-table-column label="退出时间" width="120">
          <template #default="{ row }">{{ formatDate(row.quitTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'active' ? 'success' : 'info'">{{ row.status === 'active' ? '活跃' : ' inactive' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="editMember(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="removeMember(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </ContentWrap>

    <el-dialog v-model="showAdd" :title="editing ? '编辑成员' : '添加成员'" width="480px">
      <el-form label-width="80px">
        <el-form-item label="成员"><el-select v-model="form.userId" filterable placeholder="选择用户" class="w-full"><el-option v-for="u in userList" :key="u.id" :label="u.nickname" :value="u.id" /></el-select></el-form-item>
        <el-form-item label="项目角色"><el-select v-model="form.roleCode" class="w-full"><el-option label="项目经理" value="pm" /><el-option label="技术负责人" value="tech_lead" /><el-option label="硬件工程师" value="hw_engineer" /><el-option label="软件工程师" value="sw_engineer" /><el-option label="测试工程师" value="qa_engineer" /><el-option label="结构工程师" value="mech_engineer" /><el-option label="采购" value="procurement" /></el-select></el-form-item>
        <el-form-item label="是否外部"><el-switch v-model="form.isExternal" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="form.status" class="w-full"><el-option label="活跃" value="active" /><el-option label="停用" value="inactive" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="showAdd = false">取消</el-button><el-button type="primary" @click="saveMember">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getProjectList, ProjectVO } from '@/api/pms/project'
import { getProjectMemberList, createProjectMember, updateProjectMember, deleteProjectMember } from '@/api/pms/member'
import { getUserList } from '@/api/system/user'
import { checkPermi } from '@/utils/permission'
import { formatDate } from '../pms-utils'

defineOptions({ name: 'PmsMember' })

const selectedProject = ref('')
const projects = ref<ProjectVO[]>([])
const showAdd = ref(false)
const editing = ref<any>(null)
const userList = ref<any[]>([])
const loading = ref(false)
const form = reactive({
  memberId: undefined as any,
  projectId: undefined as any,
  userId: undefined as any,
  roleCode: 'hw_engineer',
  isExternal: false,
  status: 'active'
})

const memberList = ref<any[]>([])

const roleLabelMap: Record<string, string> = {
  pm: '项目经理',
  tech_lead: '技术负责人',
  hw_engineer: '硬件工程师',
  sw_engineer: '软件工程师',
  qa_engineer: '测试工程师',
  mech_engineer: '结构工程师',
  procurement: '采购'
}

function getRoleLabel(code: string): string {
  return roleLabelMap[code] || code || '-'
}

function getRoleTag(role: string): string {
  const map: Record<string, string> = { pm: 'danger', tech_lead: 'warning', hw_engineer: 'primary', sw_engineer: 'primary', qa_engineer: 'success', mech_engineer: 'info', procurement: '' }
  return map[role] || 'info'
}

function handleAdd() {
  editing.value = null
  form.memberId = undefined
  form.userId = undefined
  form.roleCode = 'hw_engineer'
  form.isExternal = false
  form.status = 'active'
  form.projectId = selectedProject.value || undefined
  showAdd.value = true
}

function editMember(row: any) {
  editing.value = row
  form.memberId = row.memberId
  form.projectId = row.projectId
  form.userId = row.userId
  form.roleCode = row.roleCode
  form.isExternal = row.isExternal
  form.status = row.status
  showAdd.value = true
}

async function saveMember() {
  if (!form.userId) { ElMessage.warning('请选择成员'); return }
  if (!form.roleCode) { ElMessage.warning('请选择项目角色'); return }
  try {
    const data = {
      memberId: form.memberId,
      projectId: form.projectId || selectedProject.value,
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
    showAdd.value = false
    editing.value = null
    await loadMembers()
  } catch (e) {
    console.error(e)
  }
}

function removeMember(row: any) {
  ElMessageBox.confirm(`确认移除该成员？`, '提示', { type: 'warning' }).then(async () => {
    try {
      await deleteProjectMember(row.memberId)
      ElMessage.success('成员已移除')
      await loadMembers()
    } catch (e) {
      console.error(e)
    }
  }).catch(() => {})
}

async function loadMembers() {
  loading.value = true
  try {
    const data = await getProjectMemberList()
    let list = data as any[]
    if (selectedProject.value) {
      list = list.filter(m => String(m.projectId) === String(selectedProject.value))
    }
    memberList.value = list
  } catch (e) {
    console.error(e)
    memberList.value = []
  } finally {
    loading.value = false
  }
}

async function loadData() {
  try {
    const [projectRes, userRes] = await Promise.all([
      getProjectList(),
      getUserList({ pageNo: 1, pageSize: 200 })
    ])
    projects.value = projectRes as ProjectVO[]
    userList.value = (userRes as any)?.list || []
  } catch (e) {
    console.error(e)
  }
  await loadMembers()
}

onMounted(() => { loadData() })
</script>

<style scoped lang="scss">.pms-member { .toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; } }</style>
