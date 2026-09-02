<template>
  <div class="pms-template">
    <!-- 搜索栏 -->
    <ContentWrap>
      <div class="flex items-center justify-between">
        <el-form class="-mb-15px" :inline="true" label-width="80px">
          <el-form-item label="模板名称">
            <el-input v-model="queryName" placeholder="搜索模板名称" clearable @keyup.enter="getList" class="!w-240px" />
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="queryType" placeholder="全部" clearable class="!w-160px" @change="getList">
              <el-option label="标准板" value="standard_template" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button @click="getList"><Icon icon="ep:search" />搜索</el-button>
            <el-button type="primary" plain @click="openCreateDialog" v-hasPermi="['pms:template:query']">
              <Icon icon="ep:plus" />新增模板
            </el-button>
          </el-form-item>
        </el-form>
        <el-radio-group v-model="viewMode" class="ml-12px">
          <el-radio-button value="table"><Icon icon="ep:list" /></el-radio-button>
          <el-radio-button value="card"><Icon icon="ep:grid" /></el-radio-button>
        </el-radio-group>
      </div>
    </ContentWrap>

    <!-- 表格视图 -->
    <ContentWrap v-if="viewMode === 'table'">
      <el-table v-loading="loading" :data="filteredList" :stripe="true" style="width: 100%">
        <el-table-column label="模板名称" prop="projectName" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="template-name-link" @click="openPreview(row)">{{ row.projectName }}</span>
            <el-tag type="success" size="small" class="ml-4px">标准</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" prop="projectType" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="getTypeTag(row.projectType)">{{ getTypeLabel(row.projectType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="部门" width="120">
          <template #default="{ row }">
            {{ getDeptName(row.deptId) }}
          </template>
        </el-table-column>
        <el-table-column label="使用项目" width="90" align="center">
          <template #default="{ row }">
            <el-badge :value="getUsageCount(row.projectId)" :max="999" type="primary" />
          </template>
        </el-table-column>
        <el-table-column label="说明" prop="description" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'archived' ? 'info' : 'success'">{{ row.status === 'archived' ? '归档' : '启用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="170" :formatter="dateFormatter" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openPreview(row)">
              <Icon icon="ep:view" />预览
            </el-button>
            <el-button link type="primary" @click="openEdit(row)" v-hasPermi="['pms:template:query']">
              <Icon icon="ep:edit" />编辑
            </el-button>
            <el-button link type="primary" @click="handleCopy(row)" v-hasPermi="['pms:template:query']">
              <Icon icon="ep:copy-document" />复制
            </el-button>
            <el-button link type="danger" @click="handleDeleteTemplate(row)">
              <Icon icon="ep:delete" />删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </ContentWrap>

    <!-- 卡片视图 -->
    <ContentWrap v-else>
      <el-row :gutter="16" v-loading="loading">
        <el-col :span="8" v-for="tpl in filteredList" :key="String(tpl.projectId)" class="mb-16px">
          <el-card shadow="hover" class="template-card" :class="{ disabled: tpl.status === 'archived' }">
            <div class="card-header">
              <div class="card-title">
                <el-tag :type="getTypeTag(tpl.projectType)" size="small">{{ getTypeLabel(tpl.projectType) }}</el-tag>
                <span class="tpl-name" @click="openPreview(tpl)">{{ tpl.projectName }}</span>
              </div>
              <el-tag type="success" size="small">标准板</el-tag>
            </div>
            <div class="card-body">
              <div class="card-info">
                <div class="info-row">
                  <span class="info-label">使用项目</span>
                  <span class="info-value">{{ getUsageCount(tpl.projectId) }} 个</span>
                </div>
                <div class="info-row">
                  <span class="info-label">说明</span>
                  <span class="info-value text-ellipsis">{{ tpl.description || '-' }}</span>
                </div>
              </div>
            </div>
            <div class="card-footer">
              <el-tag size="small" :type="tpl.status === 'archived' ? 'info' : 'success'">{{ tpl.status === 'archived' ? '归档' : '启用' }}</el-tag>
              <div class="card-actions">
                <el-button link type="primary" @click="openPreview(tpl)"><Icon icon="ep:view" />预览</el-button>
                <el-button link type="primary" @click="openEdit(tpl)" v-hasPermi="['pms:template:query']"><Icon icon="ep:edit" />编辑</el-button>
                <el-button link type="primary" @click="handleCopy(tpl)" v-hasPermi="['pms:template:query']">
                  <Icon icon="ep:copy-document" />复制
                </el-button>
                <el-button link type="danger" @click="handleDeleteTemplate(tpl)">
                  <Icon icon="ep:delete" />删除
                </el-button>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-if="!loading && filteredList.length === 0" description="暂无模板" />
    </ContentWrap>

    <!-- 新增模板弹窗 -->
    <Dialog v-model="createVisible" title="新增模板" width="560px" :close-on-click-modal="false">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-form-item label="模板名称" prop="projectName">
          <el-input v-model="createForm.projectName" placeholder="请输入模板名称" />
        </el-form-item>
        <el-form-item label="模板类型" prop="projectType">
          <el-select v-model="createForm.projectType" placeholder="请选择" class="w-full">
            <el-option label="标准板" value="standard_template" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="createForm.status" placeholder="请选择" class="w-full">
            <el-option label="启用" value="active" />
            <el-option label="归档" value="archived" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属部门" prop="deptId">
          <el-select v-model="createForm.deptId" placeholder="请选择部门" class="w-full" filterable>
            <el-option v-for="d in deptList" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="createForm.description" type="textarea" :rows="3" placeholder="模板说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSaving" @click="submitCreate">创建</el-button>
      </template>
    </Dialog>

    <!-- 预览弹窗 -->
    <Dialog v-model="previewVisible" title="模板预览" width="900px">
      <div v-loading="previewLoading">
        <el-descriptions :column="2" border class="mb-16px">
          <el-descriptions-item label="模板名称">{{ previewData?.projectName }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ getTypeLabel(previewData?.projectType || '') }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ previewData?.status === 'archived' ? '归档' : '启用' }}</el-descriptions-item>
          <el-descriptions-item label="使用项目">{{ getUsageCount(previewData?.projectId) }} 个</el-descriptions-item>
          <el-descriptions-item label="说明" :span="2">{{ previewData?.description || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="preview-stats">
          <div class="stat-item">
            <div class="stat-num">{{ stageList.length }}</div>
            <div class="stat-label">阶段数</div>
          </div>
          <div class="stat-item">
            <div class="stat-num">{{ taskList.length }}</div>
            <div class="stat-label">任务数</div>
          </div>
          <div class="stat-item">
            <div class="stat-num">{{ taskList.filter(t => t.isMilestone).length }}</div>
            <div class="stat-label">里程碑</div>
          </div>
          <div class="stat-item">
            <div class="stat-num">{{ taskList.filter(t => t.isCriticalPath).length }}</div>
            <div class="stat-label">关键路径</div>
          </div>
        </div>

        <el-table
          :data="stageTreeData"
          row-key="taskId"
          :tree-props="{ children: 'children' }"
          border
          default-expand-all
          style="width: 100%"
          class="preview-table"
        >
          <el-table-column label="任务名称" prop="taskName" min-width="250" />
          <el-table-column label="任务类型" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.isStage" type="warning" size="small">阶段</el-tag>
              <el-tag v-else-if="row.isMilestone" type="danger" size="small">里程碑</el-tag>
              <el-tag v-else size="small" effect="plain">{{ getTypeLabel(row.taskType || '') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="计划工期" width="80" align="center">
            <template #default="{ row }">
              <span v-if="row.cycle">{{ row.cycle }}天</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="关键路径" width="80" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.isCriticalPath" type="danger" size="small">是</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="输出要求" prop="outputRequirement" min-width="200" show-overflow-tooltip />
        </el-table>
      </div>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
        <el-button type="primary" @click="handleCopy(previewData)" v-hasPermi="['pms:template:query']">
          <Icon icon="ep:copy-document" />复制此模板
        </el-button>
      </template>
    </Dialog>

    <!-- 编辑模板弹窗 -->
    <Dialog v-model="editVisible" title="编辑模板" width="1000px" :close-on-click-modal="false">
      <div v-loading="editLoading">
        <el-tabs v-model="editTab" type="border-card">
          <el-tab-pane label="基本信息" name="basic">
            <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="100px">
              <el-row :gutter="20">
                <el-col :span="12">
                  <el-form-item label="模板名称" prop="projectName">
                    <el-input v-model="editForm.projectName" placeholder="请输入模板名称" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="状态" prop="status">
                    <el-select v-model="editForm.status" placeholder="请选择" class="w-full">
                      <el-option label="启用" value="active" />
                      <el-option label="归档" value="archived" />
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="20">
                <el-col :span="12">
                  <el-form-item label="所属部门" prop="deptId">
                    <el-select v-model="editForm.deptId" placeholder="请选择部门" class="w-full" filterable>
                      <el-option v-for="d in deptList" :key="d.id" :label="d.name" :value="d.id" />
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-form-item label="说明">
                <el-input v-model="editForm.description" type="textarea" :rows="3" placeholder="模板说明" />
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="阶段任务" name="tasks">
            <div class="flex justify-between items-center mb-12px">
              <span class="text-14px font-600">阶段与任务</span>
              <div class="flex gap-8px">
                <el-button type="success" size="small" @click="openAddStage" v-hasPermi="['pms:template:query']">
                  <Icon icon="ep:plus" /> 添加阶段
                </el-button>
                <el-tooltip :content="editStageList.length === 0 ? '请先添加阶段' : ''" :disabled="editStageList.length > 0">
                  <el-button type="primary" size="small" :disabled="editStageList.length === 0" @click="openAddTask" v-hasPermi="['pms:template:query']">
                    <Icon icon="ep:plus" /> 添加任务
                  </el-button>
                </el-tooltip>
              </div>
            </div>
            <el-table
              :data="editStageTreeData"
              row-key="taskId"
              :tree-props="{ children: 'children' }"
              border
              default-expand-all
              style="width: 100%"
            >
              <el-table-column label="序号" width="60" align="center">
                <template #default="{ row }">
                  <span v-if="row.isStage">{{ row.sortOrder ?? '' }}</span>
                  <span v-else class="text-12px text-gray-400">{{ row.sortOrder ?? '' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="名称" prop="taskName" min-width="200" />
              <el-table-column label="类型" width="100">
                <template #default="{ row }">
                  <el-tag v-if="row.isStage" type="warning" size="small">阶段</el-tag>
                  <el-tag v-else-if="row.isMilestone" type="danger" size="small">里程碑</el-tag>
                  <el-tag v-else size="small" effect="plain">{{ getTypeLabel(row.taskType || '') }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="工期" width="80" align="center">
                <template #default="{ row }">
                  <span v-if="row.cycle">{{ row.cycle }}天</span>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="关键路径" width="80" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.isCriticalPath" type="danger" size="small">是</el-tag>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="230" align="center">
                <template #default="{ row }">
                  <template v-if="row.isStage">
                    <el-button v-hasPermi="['pms:template:query']" link type="default" size="small" :disabled="isFirstStage(row)" @click="moveStage(row, -1)">
                      <Icon icon="ep:top" />上移
                    </el-button>
                    <el-button v-hasPermi="['pms:template:query']" link type="default" size="small" :disabled="isLastStage(row)" @click="moveStage(row, 1)">
                      <Icon icon="ep:bottom" />下移
                    </el-button>
                    <el-button v-hasPermi="['pms:template:query']" link type="primary" size="small" @click="openEditStage(row)">
                      <Icon icon="ep:edit" />编辑
                    </el-button>
                    <el-button v-hasPermi="['pms:template:query']" link type="danger" size="small" @click="removeEditStage(row)">
                      <Icon icon="ep:delete" />删除
                    </el-button>
                  </template>
                  <template v-else>
                    <el-button v-hasPermi="['pms:template:query']" link type="default" size="small" :disabled="isFirstTask(row)" @click="moveTask(row, -1)">
                      <Icon icon="ep:top" />上移
                    </el-button>
                    <el-button v-hasPermi="['pms:template:query']" link type="default" size="small" :disabled="isLastTask(row)" @click="moveTask(row, 1)">
                      <Icon icon="ep:bottom" />下移
                    </el-button>
                    <el-button link type="primary" size="small" @click="openEditTask(row)">
                      <Icon icon="ep:edit" />编辑
                    </el-button>
                    <el-button link type="danger" size="small" @click="removeEditTask(row)">
                      <Icon icon="ep:delete" />删除
                    </el-button>
                  </template>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="saveEdit">保存</el-button>
      </template>
    </Dialog>

    <!-- 编辑任务/阶段弹窗 -->
    <Dialog v-model="taskEditVisible" :title="taskEditForm.isStage ? '编辑阶段' : '编辑任务'" width="560px">
      <el-form :model="taskEditForm" label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="taskEditForm.taskName" placeholder="请输入" />
        </el-form-item>
        <el-form-item v-if="!taskEditForm.isStage" label="所属阶段" required>
          <el-select v-model="taskEditForm.stageId" placeholder="请选择" class="w-full">
            <el-option v-for="s in editStageList" :key="s.stageId" :label="s.stageName" :value="s.stageId" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!taskEditForm.isStage" label="输出要求">
          <el-input v-model="taskEditForm.outputRequirement" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item v-if="!taskEditForm.isStage" label="里程碑">
          <el-switch v-model="taskEditForm.isMilestone" />
        </el-form-item>
        <el-form-item v-if="!taskEditForm.isStage" label="关键路径">
          <el-switch v-model="taskEditForm.isCriticalPath" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskEditVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTaskEdit">确定</el-button>
      </template>
    </Dialog>

    <!-- 复制模板弹窗 -->
    <Dialog v-model="copyVisible" title="复制模板" width="480px">
      <el-form label-width="100px">
        <el-form-item label="源模板">
          <span>{{ copySourceName }}</span>
        </el-form-item>
        <el-form-item label="新模板名称" required>
          <el-input v-model="copyForm.projectName" placeholder="请输入新模板名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="copyVisible = false">取消</el-button>
        <el-button type="primary" :loading="copySaving" @click="confirmCopy">确认复制</el-button>
      </template>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { getProjectList, updateProject, createProject, deleteProject, getTemplateUsageCount, ProjectVO } from '@/api/pms/project'
import { getTaskList, updateTask, createTask, deleteTask, TaskVO } from '@/api/pms/task'
import { getStageList, createStage, updateStage, deleteStage, StageVO } from '@/api/pms/stage'
import { dateFormatter } from '@/utils/formatTime'
import { getSimpleDeptList } from '@/api/system/dept'
import { useUserStore } from '@/store/modules/user'

defineOptions({ name: 'PmsTemplate' })

const message = useMessage()
const { t } = useI18n()
const userStore = useUserStore()

const loading = ref(true)
const list = ref<ProjectVO[]>([])
const queryName = ref('')
const queryType = ref('')
const viewMode = ref('table')

const previewVisible = ref(false)
const previewLoading = ref(false)
const previewData = ref<ProjectVO | null>(null)
const taskList = ref<TaskVO[]>([])
const stageList = ref<StageVO[]>([])
const projectList = ref<any[]>([])
const usageCountMap = ref<Record<string, number>>({})
const deptList = ref<any[]>([])

// 编辑模板相关
const editVisible = ref(false)
const editLoading = ref(false)
const editSaving = ref(false)
const editTab = ref('basic')
const editFormRef = ref<any>(null)
const editForm = reactive<ProjectVO>({
  projectId: '',
  projectCode: '',
  projectName: '',
  projectType: 'standard_template',
  status: 'active',
  deptId: undefined,
  description: ''
})
const editRules = {
  projectName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
  deptId: [{ required: true, message: '请选择所属部门', trigger: 'change' }]
}
const editTaskList = ref<TaskVO[]>([])
const editStageList = ref<StageVO[]>([])
const editDeletedTaskIds = ref<(string | number)[]>([])
const editDeletedStageIds = ref<(string | number)[]>([])

// 新增模板相关
const createVisible = ref(false)
const createSaving = ref(false)
const createFormRef = ref<any>(null)
const createForm = reactive({
  projectName: '',
  projectType: 'standard_template',
  status: 'active',
  deptId: undefined,
  description: ''
})
const createRules = {
  projectName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  projectType: [{ required: true, message: '请选择模板类型', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
  deptId: [{ required: true, message: '请选择所属部门', trigger: 'change' }]
}

const openCreateDialog = () => {
  Object.assign(createForm, {
    projectName: '',
    projectType: 'standard_template',
    status: 'active',
    deptId: (userStore.getUser?.deptId) as number | undefined,
    description: ''
  })
  createVisible.value = true
}

const submitCreate = async () => {
  if (!createFormRef.value) return
  try {
    await createFormRef.value.validate()
  } catch {
    return
  }
  createSaving.value = true
  try {
    await createProject({
      projectName: createForm.projectName,
      projectCode: `TPL-${new Date().getFullYear()}-${String(Date.now()).slice(-4)}`,
      projectType: createForm.projectType,
      status: createForm.status,
      deptId: createForm.deptId,
      description: createForm.description,
      createMethod: 'manual',
      planStartDate: new Date().toISOString().split('T')[0],
      planEndDate: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]
    } as ProjectVO)
    message.success('模板创建成功')
    createVisible.value = false
    getList()
  } catch (e) {
    console.error('创建模板失败', e)
    message.error('创建模板失败，请重试')
  } finally {
    createSaving.value = false
  }
}

const taskEditVisible = ref(false)
const taskEditForm = reactive<any>({
  taskId: undefined,
  stageId: undefined,
  taskName: '',
  taskType: 'design',
  cycle: 5,
  outputRequirement: '',
  isMilestone: false,
  isCriticalPath: false,
  isStage: false,
  isNew: false
})

// editStageList 加载时已按 sortOrder 排序(顺序=显示顺序),computed 不再二次排序,
// 直接透传 map 结果——moveStage splice 修改原数组顺序后,computed 重算即反映新顺序
const editStageTreeData = computed(() => {
  return editStageList.value.map(stage => {
    const children = editTaskList.value
      .filter(t => String(t.stageId) === String(stage.stageId))
      .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
    return {
      ...stage,
      isStage: true,
      taskName: stage.stageName,
      children
    }
  })
})

const getTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    standard_template: '标准板',
    standard_board: '标准板',
    stage_template: '阶段模板',
    task_template: '任务模板',
    design: '设计',
    review: '评审',
    testing: '测试',
    procurement: '采购',
    prototyping: '打样',
    documentation: '文档',
    approval: '审批',
    supplier_synergy: '供方协同',
    other: '其他'
  }
  return map[type] || type || '-'
}

const getTypeTag = (type: string) => {
  const map: Record<string, string> = {
    standard_template: 'success',
    standard_board: 'success',
    stage_template: 'warning',
    task_template: 'info'
  }
  return map[type] || ''
}

const getDeptName = (deptId?: number) => {
  if (!deptId) return '-'
  const dept = deptList.value.find(d => d.id === deptId)
  return dept?.name || '-'
}

const filteredList = computed(() => {
  let result = list.value
  if (queryName.value) {
    result = result.filter(t => t.projectName?.includes(queryName.value))
  }
  if (queryType.value) {
    result = result.filter(t => t.projectType === queryType.value)
  }
  return result
})

const getUsageCount = (templateId?: string | number) => {
  if (!templateId) return 0
  return usageCountMap.value[String(templateId)] || 0
}

// 构建预览树数据：阶段 -> 子任务
const stageTreeData = computed(() => {
  const stages = stageList.value.map(stage => {
    const children = taskList.value
      .filter(t => String(t.stageId) === String(stage.stageId))
      .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
    return {
      ...stage,
      isStage: true,
      taskName: stage.stageName,
      children
    }
  }).sort((a, b) => (Number(a.sortOrder) || 0) - (Number(b.sortOrder) || 0))
  return stages
})

const getList = async () => {
  loading.value = true
  try {
    const [projList, depts, usageCounts] = await Promise.all([
      getProjectList({ projectType: 'standard_template' }),
      getSimpleDeptList().catch(() => []),
      getTemplateUsageCount().catch(() => ({}))
    ])
    list.value = (projList || []).filter((p: ProjectVO) => p.projectType === 'standard_template')
    deptList.value = depts || []
    // 使用后端聚合接口获取模板使用统计（绕过权限过滤，数据更准确）
    usageCountMap.value = (usageCounts as Record<string, number>) || {}
  } catch (e) {
    console.error('加载模板列表失败', e)
  } finally {
    loading.value = false
  }
}

const openPreview = async (row: ProjectVO) => {
  previewVisible.value = true
  previewLoading.value = true
  previewData.value = row
  try {
    const [allTasks, allStages] = await Promise.all([getTaskList({ projectType: 'standard_template' }), getStageList()])
    const tplId = String(row.projectId)
    taskList.value = (allTasks as TaskVO[]).filter(t => String(t.projectId) === tplId)
    stageList.value = (allStages as StageVO[]).filter(s => String(s.projectId) === tplId)
  } catch (e) {
    console.error('加载模板任务失败', e)
  } finally {
    previewLoading.value = false
  }
}

const openEdit = async (row: ProjectVO) => {
  editVisible.value = true
  editLoading.value = true
  editTab.value = 'basic'
  editDeletedTaskIds.value = []
  editDeletedStageIds.value = []
  Object.assign(editForm, {
    projectId: row.projectId,
    projectCode: row.projectCode,
    projectName: row.projectName,
    projectType: row.projectType || 'standard_template',
    status: row.status || 'active',
    deptId: row.deptId,
    description: row.description || ''
  })
  try {
    const [allTasks, allStages] = await Promise.all([getTaskList({ projectType: 'standard_template' }), getStageList()])
    const tplId = String(row.projectId)
    editTaskList.value = (allTasks as TaskVO[]).filter(t => String(t.projectId) === tplId)
    // 防御：脏数据可能存在 stageId 为 null,补临时 ID 避免后续排序/匹配错位
    const stages = (allStages as StageVO[]).filter(s => String(s.projectId) === tplId)
    // 加载时即按 sortOrder 排序: 让 editStageList 的顺序 = 显示顺序,
    // 后续 moveStage 直接 splice 修改原数组顺序即可(Vue 3 原生响应式)
    editStageList.value = stages
      .map((s, i) => ({
        ...s,
        stageId: s.stageId != null ? s.stageId : `legacy_${s.stageName || 'stage'}_${i}`
      }))
      .sort((a, b) => (Number(a.sortOrder) || 0) - (Number(b.sortOrder) || 0))
  } catch (e) {
    console.error('加载模板任务失败', e)
  } finally {
    editLoading.value = false
  }
}

const saveEdit = async () => {
  if (!editFormRef.value) return
  try {
    await editFormRef.value.validate()
  } catch (e) {
    // 表单校验失败时给出明确提示，避免用户点保存无反应(Network 无请求)
    message.warning('请完善必填信息（模板名称、所属部门等）后再保存')
    return
  }
  editSaving.value = true
  const errors: string[] = []
  try {
    // 1. 保存模板基本信息
    await updateProject({
      projectId: editForm.projectId,
      projectName: editForm.projectName,
      projectCode: editForm.projectCode,
      projectType: editForm.projectType,
      status: editForm.status,
      deptId: editForm.deptId,
      description: editForm.description
    } as ProjectVO)

    // 2. 保存修改的阶段（新增/更新，并行，收集错误）
    // 关键: 记录临时 stageId -> 真实 stageId 的映射，供后续任务使用
    const stageIdMap: Record<string, string> = {}
    const stagesToSave = editStageList.value
      .filter(stage => stage.stageId && !editDeletedStageIds.value.includes(stage.stageId))
    const stageResults = await Promise.allSettled(
      stagesToSave.map(stage => {
        const oldStageId = String(stage.stageId)
        const stageData = {
          ...stage,
          projectId: editForm.projectId
        }
        if (oldStageId.startsWith('new_')) {
          return createStage({
            ...stageData,
            stageId: undefined
          }).then(res => {
            const createdStageId = (res as any)?.data ?? res
            if (!createdStageId) throw new Error('创建阶段后未返回阶段ID')
            stageIdMap[oldStageId] = String(createdStageId)
            // 同步更新前端 editStageList 中的 stageId 为真实ID
            const idx = editStageList.value.findIndex(s => String(s.stageId) === oldStageId)
            if (idx !== -1) editStageList.value[idx].stageId = String(createdStageId)
            return res
          })
        } else {
          return updateStage(stageData)
        }
      })
    )
    const filteredStages = editStageList.value.filter(stage => stage.stageId && !editDeletedStageIds.value.includes(stage.stageId))
    stageResults.forEach((r, i) => {
      if (r.status === 'rejected') errors.push(`阶段「${filteredStages[i]?.stageName || '未知'}」保存失败: ${r.reason}`)
    })

    // 3. 保存修改的任务（新增/更新，并行，收集错误）
    // 关键: 任务的 stageId 可能引用临时阶段ID, 需要替换为真实ID
    const taskResults = await Promise.allSettled(
      editTaskList.value
        .filter(task => !editDeletedTaskIds.value.includes(task.taskId))
        .map(task => {
          // 替换 stageId: 如果引用的是临时ID, 用映射中的真实ID
          let realStageId = task.stageId
          if (task.stageId && stageIdMap[String(task.stageId)]) {
            realStageId = stageIdMap[String(task.stageId)]
          }
          const taskData = {
            ...task,
            projectId: editForm.projectId,
            stageId: realStageId
          }
          if (task.taskId && !String(task.taskId).startsWith('new_')) {
            return updateTask(taskData)
          } else {
            return createTask({
              ...taskData,
              taskId: undefined
            })
          }
        })
    )
    const filteredTasks = editTaskList.value.filter(task => !editDeletedTaskIds.value.includes(task.taskId))
    taskResults.forEach((r, i) => {
      if (r.status === 'rejected') {
        errors.push(`任务「${filteredTasks[i]?.taskName || '未知'}」保存失败: ${r.reason}`)
      }
    })

    // 4. 删除标记的阶段
    const deleteStageResults = await Promise.allSettled(
      editDeletedStageIds.value
        .filter(id => !String(id).startsWith('new_'))
        .map(id => deleteStage(String(id)))
    )
    deleteStageResults.forEach((r) => {
      if (r.status === 'rejected') errors.push(`删除阶段失败: ${r.reason}`)
    })

    // 5. 删除标记的任务
    const deleteResults = await Promise.allSettled(
      editDeletedTaskIds.value
        .filter(id => !String(id).startsWith('new_'))
        .map(id => deleteTask(String(id)))
    )
    deleteResults.forEach((r) => {
      if (r.status === 'rejected') errors.push(`删除任务失败: ${r.reason}`)
    })

    if (errors.length > 0) {
      message.warning(`部分操作失败 (${errors.length} 项):\n${errors.slice(0, 5).join('\n')}${errors.length > 5 ? '\n...' : ''}`)
    } else {
      message.success('模板保存成功')
    }
    editVisible.value = false
    getList()
  } catch (e) {
    console.error('保存模板失败', e)
    message.error('保存模板失败，请重试')
  } finally {
    editSaving.value = false
  }
}

// 阶段排序：上移/下移（交换相邻阶段的 sortOrder 并归一化为 1..n，保存时随 updateStage 一并提交）
// 匹配用 taskName 而非 stageId：实际数据中存在 stageId 缺失(null)的脏数据,
// 用 stageId 匹配会错位；taskName 必非空更稳。
const findStageIdx = (sorted: any[], row: any) => {
  // 优先按 taskName(stage.stageName 透传)匹配;其次 stageId
  const byName = sorted.findIndex(s => s.stageName && row.taskName && s.stageName === row.taskName)
  if (byName !== -1) return byName
  if (row.stageId != null) {
    return sorted.findIndex(s => s.stageId != null && String(s.stageId) === String(row.stageId))
  }
  return -1
}

const sortedEditStages = () =>
  [...editStageList.value].sort((a, b) => (Number(a.sortOrder) || 0) - (Number(b.sortOrder) || 0))

const isFirstStage = (row: any) => {
  const sorted = sortedEditStages()
  if (sorted.length === 0) return true
  return findStageIdx(sorted, row) === 0
}

const isLastStage = (row: any) => {
  const sorted = sortedEditStages()
  if (sorted.length === 0) return true
  return findStageIdx(sorted, row) === sorted.length - 1
}

const moveStage = (row: any, dir: number) => {
  // 直接操作 editStageList.value 原数组: Vue 3 对 splice 有原生响应式拦截,
  // 会触发 computed editStageTreeData 重算;不需要替换整个数组(浅拷贝元素
  // 引用相同,Vue 3 可能不触发 setter)
  const list = editStageList.value
  const idx = findStageIdx(list, row)
  if (idx === -1) return
  const target = idx + dir
  if (target < 0 || target >= list.length) return
  // splice 交换位置(Vue 3 原生响应式追踪)
  const [moved] = list.splice(idx, 1)
  list.splice(target, 0, moved)
  // 按新顺序归一化 sortOrder
  list.forEach((s, i) => { s.sortOrder = i + 1 })
}

// 任务排序：上移/下移（同阶段内交换，归一化 sortOrder=1..n，保存时随 updateTask 一并提交）
// children 按 sortOrder 值排序展示(editStageTreeData)，改 sortOrder 值即触发重排
const sortedStageTasks = (stageId: any) =>
  editTaskList.value
    .filter(t => String(t.stageId) === String(stageId))
    .sort((a, b) => (Number(a.sortOrder) || 0) - (Number(b.sortOrder) || 0))

const findTaskIdx = (sorted: any[], row: any) => {
  // children 直接透传任务对象引用，优先引用匹配
  const byRef = sorted.findIndex(t => t === row)
  if (byRef !== -1) return byRef
  if (row.taskId != null) {
    return sorted.findIndex(t => t.taskId != null && String(t.taskId) === String(row.taskId))
  }
  return sorted.findIndex(t => t.taskName === row.taskName)
}

const isFirstTask = (row: any) => {
  const sorted = sortedStageTasks(row.stageId)
  if (sorted.length === 0) return true
  return findTaskIdx(sorted, row) === 0
}

const isLastTask = (row: any) => {
  const sorted = sortedStageTasks(row.stageId)
  if (sorted.length === 0) return true
  return findTaskIdx(sorted, row) === sorted.length - 1
}

const moveTask = (row: any, dir: number) => {
  // 同阶段任务按当前 sortOrder 排序后与相邻任务交换位置, 再归一化写回 sortOrder=1..n;
  // 修改的是 ref 数组内 reactive 对象的属性, computed editStageTreeData 会重算,
  // children 按 sortOrder 重排后界面立即反映新顺序
  const sorted = sortedStageTasks(row.stageId)
  const idx = findTaskIdx(sorted, row)
  if (idx === -1) return
  const target = idx + dir
  if (target < 0 || target >= sorted.length) return
  const [moved] = sorted.splice(idx, 1)
  sorted.splice(target, 0, moved)
  sorted.forEach((t, i) => { t.sortOrder = i + 1 })
}

const openEditStage = (row: any) => {
  Object.assign(taskEditForm, {
    taskId: row.stageId,
    taskName: row.stageName,
    isStage: true,
    isNew: false
  })
  taskEditVisible.value = true
}

const openEditTask = (row: any) => {
  Object.assign(taskEditForm, {
    taskId: row.taskId,
    stageId: row.stageId,
    taskName: row.taskName,
    taskType: row.taskType || 'design',
    cycle: row.cycle || 5,
    outputRequirement: row.outputRequirement || '',
    isMilestone: row.isMilestone || false,
    isCriticalPath: row.isCriticalPath || false,
    isStage: false,
    isNew: false
  })
  taskEditVisible.value = true
}

const openAddStage = () => {
  Object.assign(taskEditForm, {
    taskId: 'new_stage_' + Date.now(),
    taskName: '',
    isStage: true,
    isNew: true
  })
  taskEditVisible.value = true
}

const openAddTask = () => {
  if (editStageList.value.length === 0) {
    message.warning('没有可用阶段，无法添加任务')
    return
  }
  Object.assign(taskEditForm, {
    taskId: 'new_' + Date.now(),
    stageId: editStageList.value[0].stageId,
    taskName: '',
    taskType: 'design',
    cycle: 5,
    outputRequirement: '',
    isMilestone: false,
    isCriticalPath: false,
    isStage: false,
    isNew: true
  })
  taskEditVisible.value = true
}

const saveTaskEdit = () => {
  if (!taskEditForm.taskName) {
    message.warning('请输入名称')
    return
  }
  if (taskEditForm.isStage) {
    if (taskEditForm.isNew) {
      // 新增阶段
      editStageList.value.push({
        stageId: taskEditForm.taskId,
        projectId: editForm.projectId,
        stageName: taskEditForm.taskName,
        sortOrder: editStageList.value.length + 1
      } as StageVO)
    } else {
      const stage = editStageList.value.find(s => s.stageId === taskEditForm.taskId)
      if (stage) {
        stage.stageName = taskEditForm.taskName
      }
    }
  } else if (taskEditForm.isNew) {
    editTaskList.value.push({
      taskId: taskEditForm.taskId,
      projectId: editForm.projectId,
      stageId: taskEditForm.stageId,
      taskName: taskEditForm.taskName,
      taskType: taskEditForm.taskType,
      cycle: taskEditForm.cycle,
      outputRequirement: taskEditForm.outputRequirement,
      isMilestone: taskEditForm.isMilestone,
      isCriticalPath: taskEditForm.isCriticalPath,
      sortOrder: editTaskList.value.length + 1
    } as TaskVO)
  } else {
    const task = editTaskList.value.find(t => t.taskId === taskEditForm.taskId)
    if (task) {
      Object.assign(task, {
        taskName: taskEditForm.taskName,
        stageId: taskEditForm.stageId,
        taskType: taskEditForm.taskType,
        cycle: taskEditForm.cycle,
        outputRequirement: taskEditForm.outputRequirement,
        isMilestone: taskEditForm.isMilestone,
        isCriticalPath: taskEditForm.isCriticalPath
      })
    }
  }
  taskEditVisible.value = false
}

const removeEditStage = async (row: any) => {
  try {
    await message.confirm(`确认删除阶段「${row.stageName}」？其下任务也将被删除。`)
    // 删除该阶段下所有任务
    const taskIdsToDelete = editTaskList.value
      .filter(t => String(t.stageId) === String(row.stageId))
      .map(t => t.taskId)
    taskIdsToDelete.forEach(id => {
      if (!editDeletedTaskIds.value.includes(id)) {
        editDeletedTaskIds.value.push(id)
      }
    })
    editTaskList.value = editTaskList.value.filter(t => String(t.stageId) !== String(row.stageId))
    // 删除阶段
    editStageList.value = editStageList.value.filter(s => s.stageId !== row.stageId)
    editDeletedStageIds.value.push(row.stageId)
    message.success('已删除')
  } catch {}
}

const removeEditTask = async (row: any) => {
  try {
    await message.confirm(`确认删除任务「${row.taskName}」？`)
    editTaskList.value = editTaskList.value.filter(t => t.taskId !== row.taskId)
    editDeletedTaskIds.value.push(row.taskId)
    message.success('已删除')
  } catch {}
}

// 复制模板相关
const copyVisible = ref(false)
const copySaving = ref(false)
const copySourceName = ref('')
const copyForm = reactive({
  projectId: '' as string | number,
  projectName: '',
  deptId: undefined as number | undefined,
  description: ''
})

const handleCopy = (row: ProjectVO) => {
  copySourceName.value = row.projectName
  copyForm.projectId = row.projectId || ''
  copyForm.projectName = row.projectName + ' (副本)'
  copyForm.deptId = row.deptId
  copyForm.description = row.description || ''
  copyVisible.value = true
}

const confirmCopy = async () => {
  if (!copyForm.projectName?.trim()) {
    message.warning('请输入新模板名称')
    return
  }
  copySaving.value = true
  try {
    // 1. 创建新模板（projectType=standard_template, templateId=源模板ID 触发后端复制任务）
    const res = await createProject({
      projectName: copyForm.projectName.trim(),
      projectCode: `TPL-${new Date().getFullYear()}-${String(Date.now()).slice(-4)}`,
      projectType: 'standard_template',
      templateId: copyForm.projectId,  // 保持字符串，避免 JS Number 精度丢失
      status: 'active',
      deptId: copyForm.deptId,
      description: copyForm.description,
      createMethod: 'manual',
      planStartDate: new Date().toISOString().split('T')[0],
      planEndDate: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]
    } as any)
    // 2. 将新模板的 templateId 置为 null，使其成为独立模板
    const newProjectId = (res as any)?.data ?? res
    if (newProjectId) {
      try {
        await updateProject({
          projectId: newProjectId,
          templateId: null as any
        } as any)
      } catch {
        // 置空 templateId 失败不影响主流程，新模板仍可用
      }
    }
    message.success('模板复制成功')
    copyVisible.value = false
    getList()
  } catch (e) {
    console.error('复制模板失败', e)
    message.error('复制模板失败，请重试')
  } finally {
    copySaving.value = false
  }
}

// 删除模板（超管可用）
const handleDeleteTemplate = async (row: ProjectVO) => {
  try {
    await message.confirm(`确认删除模板「${row.projectName}」？删除后不可恢复，关联的阶段和任务也将被清除。`, '删除确认', {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteProject(row.projectId as any)
    message.success('模板已删除')
    getList()
  } catch {}
}

onMounted(() => {
  getList()
})
</script>

<style scoped lang="scss">
.pms-template {
  .template-name-link {
    color: var(--el-color-primary);
    cursor: pointer;
    font-weight: 500;
    &:hover { text-decoration: underline; }
  }

  // 卡片视图样式
  .template-card {
    transition: all 0.2s;
    &:hover { box-shadow: 0 4px 16px rgba(0,0,0,0.08); }
    &.disabled { opacity: 0.6; }

    .card-header {
      display: flex; align-items: center; justify-content: space-between;
      margin-bottom: 12px;
      .card-title {
        display: flex; align-items: center; gap: 8px;
        .tpl-name {
          font-size: 15px; font-weight: 600; cursor: pointer;
          color: var(--el-text-color-primary);
          &:hover { color: var(--el-color-primary); }
        }
      }
    }
    .card-body {
      .card-info {
        .info-row {
          display: flex; align-items: center; margin-bottom: 6px;
          .info-label { width: 70px; font-size: 12px; color: var(--el-text-color-secondary); flex-shrink: 0; }
          .info-value { font-size: 13px; color: var(--el-text-color-primary); }
        }
      }
      .card-desc {
        font-size: 12px; color: var(--el-text-color-secondary);
        margin-top: 8px; padding: 8px; background: var(--el-fill-color-light);
        border-radius: 4px; line-height: 1.5;
      }
    }
    .card-footer {
      display: flex; align-items: center; justify-content: space-between;
      margin-top: 12px; padding-top: 12px;
      border-top: 1px solid var(--el-border-color-lighter);
      .card-actions { display: flex; gap: 4px; }
    }
  }

  // 预览弹窗样式
  .preview-stats {
    display: flex; gap: 16px; margin-bottom: 16px;
    .stat-item {
      flex: 1; text-align: center; padding: 12px;
      background: var(--el-fill-color-light); border-radius: 8px;
      .stat-num { font-size: 24px; font-weight: 700; color: var(--el-color-primary); }
      .stat-label { font-size: 12px; color: var(--el-text-color-secondary); margin-top: 4px; }
    }
  }

  .preview-table {
    :deep(.el-table__row) {
      &[class*="level-1"] {
        background: var(--el-fill-color-lighter);
      }
    }
  }
}
</style>
