<template>
  <Dialog :title="dialogTitle" v-model="dialogVisible" width="680px" @close="closeDialog">
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px" label-position="right">
      <el-divider content-position="left">基本信息</el-divider>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="项目名称" prop="projectName">
            <el-input v-model="formData.projectName" placeholder="请输入项目名称" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="项目编号" prop="projectCode">
            <el-input v-model="formData.projectCode" placeholder="自动生成或手动输入" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="项目类型" prop="projectType">
            <el-select filterable v-model="formData.projectType" placeholder="请选择" style="width: 100%">
              <el-option v-for="opt in projectTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="优先级" prop="priority">
            <el-select filterable v-model="formData.priority" placeholder="请选择" style="width: 100%">
              <el-option v-for="opt in priorityOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="计划开始" prop="planStartDate">
            <el-date-picker v-model="formData.planStartDate" type="date" placeholder="选择日期" format="YYYY-MM-DD" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="计划结束" prop="planEndDate">
            <el-date-picker v-model="formData.planEndDate" type="date" placeholder="选择日期" format="YYYY-MM-DD" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="所属部门" prop="deptId">
            <el-tree-select
              v-model="formData.deptId"
              :data="deptTree"
              :props="{ label: 'name', value: 'id', children: 'children' }"
              placeholder="请选择部门" check-strictly clearable filterable
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="项目经理" prop="projectManagerId">
            <el-select v-model="formData.projectManagerId" placeholder="请选择" filterable remote clearable :remote-method="searchUsers" :loading="remoteLoading" style="width: 100%" @change="onManagerChange">
              <el-option v-for="u in remoteUserList" :key="u.id" :label="`${u.nickname}`" :value="Number(u.id)" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">项目描述</el-divider>
      <el-form-item label="描述" prop="description">
        <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="项目描述、目标、范围等" />
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="formData.remark" type="textarea" :rows="2" placeholder="备注信息" />
      </el-form-item>

      <el-divider content-position="left">其他</el-divider>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="预算">
            <el-input-number v-model="formData.budget" :min="0" :precision="2" placeholder="预算金额" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="重点项目">
            <el-switch v-model="formData.isKeyProject" :active-value="true" :inactive-value="false" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <template #footer>
      <el-button @click="closeDialog">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { createProject, updateProject, ProjectVO } from '@/api/pms/project'
import { createProjectMember } from '@/api/pms/member'
import { getDynamicProjectTypeOptions, getDynamicPriorityOptions } from '../pms-utils'
import * as DeptApi from '@/api/system/dept'
import { useUserNames } from '@/hooks/pms/useUserNames'

defineOptions({ name: 'ProjectForm' })

// 【PMS】下拉统一走系统字典真源
const projectTypeOptions = getDynamicProjectTypeOptions()
const priorityOptions = getDynamicPriorityOptions()

const emit = defineEmits<{ success: [] }>()
const message = useMessage()
const formRef = ref()
const submitting = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const mode = ref<'create' | 'update'>('create')
const deptTree = ref<any[]>([])
// P1-02: 使用 useUserNames 的远程搜索功能替代全量加载
const { ensureLoaded: ensureUsers, getUserName, userList, searchUsers, remoteUserList, remoteLoading } = useUserNames()

const defaultForm: ProjectVO = {
  projectId: undefined,
  projectCode: '',
  projectName: '',
  projectType: 'hardware',
  status: 'initiating',
  priority: 'normal',
  progress: 0,
  planStartDate: '',
  planEndDate: '',
  projectManagerId: undefined,
  deptId: undefined,
  createMethod: 'manual',
  budget: 0,
  description: '',
  isKeyProject: false,
  archived: false,
  remark: ''
}

const formData = reactive<ProjectVO>({ ...defaultForm })

const formRules = reactive({
  projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  projectType: [{ required: true, message: '请选择项目类型', trigger: 'change' }],
  planStartDate: [{ required: false }],
  planEndDate: [
    { required: false },
    {
      validator: (_rule: any, value: string, callback: any) => {
        if (value && formData.planStartDate && new Date(value) <= new Date(formData.planStartDate)) {
          callback(new Error('结束日期必须大于开始日期'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  projectManagerId: [{ required: true, message: '请选择项目经理', trigger: 'change' }],
  deptId: [{ required: false }]
})

const open = async (type: 'create' | 'update', data?: ProjectVO) => {
  mode.value = type
  dialogTitle.value = type === 'create' ? '新建项目' : '编辑项目'
  dialogVisible.value = true

  // 加载部门数据（用户列表改用远程搜索，不再全量加载）
  try {
    const depts = await DeptApi.getSimpleDeptList()
    deptTree.value = depts || []
    await ensureUsers()  // 确保用户名称解析可用
  } catch (e) {
    console.error('加载部门数据失败', e)
  }

  if (data) {
    Object.assign(formData, data)
    // 修复: projectManagerId 用 Number 类型匹配 el-option Number(u.id)
    if (formData.projectManagerId !== undefined && formData.projectManagerId !== null) {
      formData.projectManagerId = Number(formData.projectManagerId) as any
      // 将当前项目经理加入 remoteUserList，使 el-select 能显示姓名
      const mgrIdNum = Number(formData.projectManagerId)
      if (!remoteUserList.value.find(u => Number(u.id) === mgrIdNum)) {
        const mgrUser = userList.value.find(u => Number(u.id) === mgrIdNum)
        if (mgrUser) {
          remoteUserList.value = [mgrUser]
        }
      }
    }
  } else {
    Object.assign(formData, defaultForm)
    // 自动生成项目编号
    formData.projectCode = `PRJ-${new Date().getFullYear()}-${String(Date.now()).slice(-4)}`
  }
}

const closeDialog = () => {
  dialogVisible.value = false
  Object.assign(formData, defaultForm)
  formRef.value?.resetFields()
}

// 项目经理变更时自动带出所属部门（用户仍可手动修改）
const onManagerChange = (val: number | string | undefined) => {
  if (!val) return
  const idStr = String(val)
  const pmUser = remoteUserList.value.find((u: any) => String(u.id) === idStr)
    || userList.value.find((u: any) => String(u.id) === idStr)
  if (pmUser?.deptId) {
    formData.deptId = Number(pmUser.deptId)
  }
}

const handleSubmit = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    if (mode.value === 'create') {
      const res = await createProject(formData)
      // P1-03: 自动将项目经理添加为项目成员
      if (formData.projectManagerId && (res as any)?.projectId) {
        createProjectMember({
          projectId: String((res as any).projectId),
          userId: String(formData.projectManagerId),
          roleCode: 'pm',
          status: 'active'
        }).catch(() => { /* 非阻塞 */ })
      }
      message.success('创建成功')
    } else {
      await updateProject(formData)
      message.success('更新成功')
    }
    emit('success')
    closeDialog()
  } catch (e) {
    console.error('提交失败', e)
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>
