<template>
  <div class="permission-tab">
    <!-- 工具栏 -->
    <div class="tab-toolbar">
      <span class="toolbar-title">
        权限配置
        <el-tooltip
          content="行=权限点，列=项目角色。勾选表示该角色在本项目内拥有该操作权限。项目经理默认拥有全部权限，不受此矩阵限制。"
          placement="top"
        >
          <Icon icon="ep:question-filled" class="ml-4px" style="color: #909399; cursor: help" />
        </el-tooltip>
      </span>
      <div>
        <el-button size="small" @click="loadMatrix" :loading="loading">
          <Icon icon="ep:refresh" class="mr-4px" />刷新
        </el-button>
        <el-button size="small" type="primary" plain @click="openRoleDialog()" v-if="editable">
          <Icon icon="ep:plus" class="mr-4px" />新建角色
        </el-button>
        <el-button size="small" type="primary" @click="handleSave" :loading="saving" v-if="editable">
          <Icon icon="ep:check" class="mr-4px" />保存矩阵
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="!editable"
      title="您没有权限配置的修改权限，当前为只读视图"
      type="info"
      :closable="false"
      show-icon
      class="mb-12px"
    />

    <el-alert
      v-if="roles.length === 0 && !loading"
      title="该项目尚未初始化项目角色"
      type="warning"
      :closable="false"
      show-icon
      class="mb-12px"
    >
      <template #default>
        <span>该项目尚未初始化项目角色，成员的项目级权限将全部为空。</span>
        <el-button link type="primary" size="small" @click="handleInit" v-if="editable">
          按默认模板初始化
        </el-button>
      </template>
    </el-alert>

    <!-- 权限矩阵 -->
    <div v-loading="loading" class="matrix-wrapper" v-if="roles.length > 0">
      <table class="perm-matrix">
        <thead>
          <tr>
            <th class="col-perm">权限点</th>
            <th v-for="role in roles" :key="role.roleId" class="col-role">
              <div class="role-head">
                <span class="role-name" :title="role.roleCode">{{ role.roleName }}</span>
                <el-tag v-if="role.isSystem" size="small" type="info" effect="plain">内置</el-tag>
                <span class="role-meta">{{ role.memberCount || 0 }} 人</span>
                <div class="role-ops" v-if="editable">
                  <el-button link type="primary" size="small" @click="openRoleDialog(role)">改名</el-button>
                  <el-button
                    link
                    type="danger"
                    size="small"
                    v-if="!role.isSystem"
                    @click="handleDeleteRole(role)"
                  >
                    删除
                  </el-button>
                </div>
                <div class="role-ops" v-if="editable">
                  <el-button link size="small" @click="toggleColumn(role.roleId, true)">全选</el-button>
                  <el-button link size="small" @click="toggleColumn(role.roleId, false)">清空</el-button>
                </div>
              </div>
            </th>
          </tr>
        </thead>
        <tbody v-for="group in permGroups" :key="group.group">
          <tr class="group-row">
            <td :colspan="roles.length + 1">{{ group.group }}</td>
          </tr>
          <tr v-for="item in group.items" :key="item.permKey">
            <td class="col-perm">
              <span>{{ item.label }}</span>
              <span class="perm-key">{{ item.permKey }}</span>
            </td>
            <td v-for="role in roles" :key="role.roleId" class="cell-check">
              <el-checkbox
                :model-value="isGranted(role.roleId, item.permKey)"
                :disabled="!editable"
                @change="(v) => toggleCell(role.roleId, item.permKey, v as boolean)"
              />
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 新建/改名角色弹窗 -->
    <el-dialog v-model="roleDialogVisible" :title="roleForm.roleId ? '编辑角色' : '新建项目角色'" width="440px">
      <el-form label-width="90px">
        <el-form-item label="角色名称" required>
          <el-input v-model="roleForm.roleName" placeholder="如 质量负责人" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="角色编码" required>
          <el-input
            v-model="roleForm.roleCode"
            placeholder="如 qa_lead，小写字母开头"
            :disabled="!!roleForm.roleId"
            maxlength="64"
          />
          <div class="form-tip">编码创建后不可修改，需与成员管理里选择的角色一致</div>
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="roleForm.sortOrder" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="roleForm.remark" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRole" :loading="roleSaving">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as PermApi from '@/api/pms/permission'
import type { ProjectRoleVO, PermGroupVO } from '@/api/pms/permission'
import { useProjectPerm } from '@/hooks/pms/useProjectPerm'

defineOptions({ name: 'PermissionTab' })

const props = defineProps<{ projectId: string }>()

const loading = ref(false)
const saving = ref(false)
const editable = ref(false)
const roles = ref<ProjectRoleVO[]>([])
const permGroups = ref<PermGroupVO[]>([])
/** 已勾选集合，元素格式 `${roleId}:${permKey}`，roleId 一律 string */
const granted = ref<Set<string>>(new Set())

const { clearPermCache } = useProjectPerm()

// ==================== 矩阵 ====================

async function loadMatrix() {
  if (!props.projectId) return
  loading.value = true
  try {
    const res: any = await PermApi.getPermMatrix(props.projectId)
    roles.value = (res?.roles || []).map((r: any) => ({ ...r, roleId: String(r.roleId) }))
    permGroups.value = res?.permGroups || []
    granted.value = new Set<string>((res?.grantedPairs || []).map((p: string) => String(p)))
    editable.value = !!res?.editable
  } catch {
    ElMessage.error('加载权限矩阵失败')
  } finally {
    loading.value = false
  }
}

function cellKey(roleId: string | number, permKey: string) {
  return `${String(roleId)}:${permKey}`
}

function isGranted(roleId: string | number, permKey: string) {
  return granted.value.has(cellKey(roleId, permKey))
}

function toggleCell(roleId: string | number, permKey: string, checked: boolean) {
  const key = cellKey(roleId, permKey)
  const next = new Set(granted.value)
  if (checked) {
    next.add(key)
  } else {
    next.delete(key)
  }
  granted.value = next
}

/** 整列全选/清空 */
function toggleColumn(roleId: string | number, checked: boolean) {
  const next = new Set(granted.value)
  permGroups.value.forEach((g) => {
    g.items.forEach((item) => {
      const key = cellKey(roleId, item.permKey)
      if (checked) {
        next.add(key)
      } else {
        next.delete(key)
      }
    })
  })
  granted.value = next
}

async function handleSave() {
  saving.value = true
  try {
    await PermApi.savePermMatrix({
      projectId: String(props.projectId),
      grantedPairs: Array.from(granted.value)
    })
    ElMessage.success('权限矩阵已保存')
    // 权限变了，清掉本项目权限缓存，让其它 Tab 下次拉到最新值
    clearPermCache(props.projectId)
    await loadMatrix()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function handleInit() {
  try {
    await PermApi.initProjectPermission(props.projectId)
    ElMessage.success('已按默认模板初始化')
    clearPermCache(props.projectId)
    await loadMatrix()
  } catch {
    ElMessage.error('初始化失败')
  }
}

// ==================== 角色管理 ====================

const roleDialogVisible = ref(false)
const roleSaving = ref(false)
const roleForm = ref<ProjectRoleVO>({
  roleId: '',
  roleName: '',
  roleCode: '',
  sortOrder: 0,
  remark: ''
})

function openRoleDialog(role?: ProjectRoleVO) {
  if (role) {
    roleForm.value = { ...role }
  } else {
    roleForm.value = {
      roleId: '',
      projectId: String(props.projectId),
      roleName: '',
      roleCode: '',
      sortOrder: roles.value.length + 1,
      remark: ''
    }
  }
  roleDialogVisible.value = true
}

async function submitRole() {
  if (!roleForm.value.roleName?.trim()) {
    ElMessage.warning('请填写角色名称')
    return
  }
  if (!roleForm.value.roleId && !/^[a-z][a-z0-9_]{1,63}$/.test(roleForm.value.roleCode || '')) {
    ElMessage.warning('角色编码需为小写字母开头的字母数字下划线组合')
    return
  }
  roleSaving.value = true
  try {
    if (roleForm.value.roleId) {
      await PermApi.updateProjectRole(roleForm.value)
    } else {
      await PermApi.createProjectRole({ ...roleForm.value, projectId: String(props.projectId) })
    }
    ElMessage.success('保存成功')
    roleDialogVisible.value = false
    await loadMatrix()
  } catch {
    // 后端已返回具体错误提示（编码重复 / 格式非法等），此处不覆盖
  } finally {
    roleSaving.value = false
  }
}

async function handleDeleteRole(role: ProjectRoleVO) {
  if ((role.memberCount || 0) > 0) {
    ElMessage.warning(`该角色仍有 ${role.memberCount} 名成员使用，请先在「项目成员」调整`)
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除角色「${role.roleName}」？该角色的权限配置会一并清除。`, '提示', {
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await PermApi.deleteProjectRole(role.roleId)
    ElMessage.success('删除成功')
    clearPermCache(props.projectId)
    await loadMatrix()
  } catch {
    // 后端错误提示已展示
  }
}

onMounted(() => {
  loadMatrix()
})

defineExpose({ loadMatrix })
</script>

<style scoped>
.permission-tab {
  padding: 4px 0;
}

.tab-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.toolbar-title {
  font-size: 15px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
}

.matrix-wrapper {
  overflow-x: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.perm-matrix {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.perm-matrix th,
.perm-matrix td {
  border: 1px solid var(--el-border-color-lighter);
  padding: 8px 10px;
}

.perm-matrix thead th {
  background: var(--el-fill-color-light);
  position: sticky;
  top: 0;
  z-index: 2;
}

.col-perm {
  min-width: 200px;
  text-align: left;
  background: var(--el-bg-color);
  position: sticky;
  left: 0;
  z-index: 1;
}

.col-role {
  min-width: 130px;
  text-align: center;
}

.role-head {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.role-name {
  font-weight: 600;
}

.role-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.role-ops {
  display: flex;
  gap: 2px;
}

.perm-key {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.group-row td {
  background: var(--el-fill-color);
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.cell-check {
  text-align: center;
}

.form-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
}

.mb-12px {
  margin-bottom: 12px;
}

.ml-4px {
  margin-left: 4px;
}
</style>

