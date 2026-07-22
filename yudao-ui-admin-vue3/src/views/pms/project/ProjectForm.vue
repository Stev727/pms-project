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
            <el-select v-model="formData.projectType" placeholder="请选择" style="width: 100%">
              <el-option v-for="opt in projectTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="优先级" prop="priority">
            <el-select v-model="formData.priority" placeholder="请选择" style="width: 100%">
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
            <el-select v-model="formData.projectManagerId" placeholder="请选择" filterable clearable style="width: 100%">
              <el-option v-for="u in userList" :key="u.id" :label="u.nickname" :value="u.id" />
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
import { projectTypeOptions, priorityOptions } from '../pms-utils'
import * as DeptApi from '@/api/system/dept'
import * as UserApi from '@/api/system/user'

defineOptions({ name: 'ProjectForm' })

const emit = defineEmits<{ success: [] }>()
const message = useMessage()
const formRef = ref()
const submitting = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const mode = ref<'create' | 'update'>('create')
const deptTree = ref<any[]>([])
const userList = ref<any[]>([])

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
  planStartDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  planEndDate: [
    { required: true, message: '请选择结束日期', trigger: 'change' },
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
  deptId: [{ required: true, message: '请选择所属部门', trigger: 'change' }]
})

const open = async (type: 'create' | 'update', data?: ProjectVO) => {
  mode.value = type
  dialogTitle.value = type === 'create' ? '新建项目' : '编辑项目'
  dialogVisible.value = true

  // 加载部门和用户数据
  try {
    const [depts, users] = await Promise.all([
      DeptApi.getSimpleDeptList(),
      UserApi.getSimpleUserList()
    ])
    deptTree.value = depts || []
    userList.value = users || []
  } catch (e) {
    console.error('加载部门/用户数据失败', e)
  }

  if (data) {
    Object.assign(formData, data)
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

const handleSubmit = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    if (mode.value === 'create') {
      await createProject(formData)
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
