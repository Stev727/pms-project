<template>
  <div class="p-20px">
    <!-- 搜索栏 -->
    <ContentWrap>
      <el-form :model="queryParams" :inline="true" class="mb-0">
        <el-form-item label="项目名称">
          <el-input v-model="queryParams.projectName" placeholder="请输入项目名称" clearable style="width: 200px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="(v, k) in projectStatusMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="阶段">
          <el-select v-model="queryParams.currentStage" placeholder="全部" clearable style="width: 120px">
            <template v-for="(v, k) in phaseColorMap" :key="k">
              <el-option v-if="!['立项','设计','开发','测试','验收','结项'].includes(k as string)" :label="v.label" :value="k" />
            </template>
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryParams.projectType" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="opt in projectTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><Icon icon="ep:search" class="mr-5px" />查询</el-button>
          <el-button @click="handleReset"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        </el-form-item>
      </el-form>
    </ContentWrap>

    <!-- 工具栏 -->
    <ContentWrap>
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px">
        <div style="display: flex; gap: 8px; align-items: center">
          <el-button type="primary" @click="handleCreate" v-hasPermi="['pms:project:create']">
            <Icon icon="ep:plus" class="mr-5px" />新建项目
          </el-button>
          <!-- 快捷筛选 (MINOR-3 修复) -->
          <el-radio-group v-model="quickFilter" size="small" @change="handleQuickFilter">
            <el-radio-button label="all">全部</el-radio-button>
            <el-radio-button label="mine">我负责的</el-radio-button>
            <el-radio-button label="involved">我参与的</el-radio-button>
            <el-radio-button label="archived">已归档</el-radio-button>
          </el-radio-group>
        </div>
        <div style="display: flex; align-items: center; gap: 12px">
          <el-button size="small" @click="handleBatchArchive" :disabled="selectedProjects.length === 0" v-if="checkPermi(['pms:project:update'])">
            <Icon icon="ep:box" class="mr-4px" />批量归档
          </el-button>
          <el-button size="small" @click="handleExport" v-if="false">
            <Icon icon="ep:download" class="mr-4px" />导出Excel
          </el-button>
          <span style="color: #86909C; font-size: 14px">共 {{ filteredList.length }} 个项目</span>
          <el-radio-group v-model="viewMode" size="small">
            <el-radio-button label="card"><Icon icon="ep:grid" /></el-radio-button>
            <el-radio-button label="list"><Icon icon="ep:list" /></el-radio-button>
          </el-radio-group>
        </div>
      </div>

      <!-- 卡片视图 -->
      <div v-if="viewMode === 'card'" v-loading="loading">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="project in pagedCardList" :key="project.projectId" style="margin-bottom: 16px">
            <el-card class="project-card" shadow="hover" @click="goDetail(project)">
              <div class="card-header">
                <span class="card-title" :title="project.projectName">{{ project.projectName }}</span>
                <el-dropdown trigger="click" @click.stop>
                  <el-icon class="card-more"><Icon icon="ep:more-filled" /></el-icon>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item @click="goDetail(project)">查看详情</el-dropdown-item>
                      <el-dropdown-item @click="handleEdit(project)" v-if="checkPermi(['pms:project:update'])">编辑</el-dropdown-item>
                      <el-dropdown-item @click="handleArchive(project)" v-if="checkPermi(['pms:project:update'])">归档</el-dropdown-item>
                      <el-dropdown-item divided @click="handleDelete(project)" v-if="checkPermi(['pms:project:delete'])">
                        <span style="color: #F53F3F">删除</span>
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>

              <div class="card-phase">
                <el-tag :style="getPhaseTagStyle(project.currentStage)" size="small" effect="light">
                  {{ getPhaseLabel(project.currentStage) }}
                </el-tag>
                <el-tag v-if="project.isKeyProject" type="danger" size="small" effect="dark" style="margin-left: 4px">重点</el-tag>
                <el-tag :type="getStatusTag(project.status).type" size="small" effect="plain" style="margin-left: 4px">
                  {{ getStatusTag(project.status).label }}
                </el-tag>
              </div>

              <div class="card-progress">
                <div class="progress-info">
                  <span style="font-size: 13px; color: #4E5969">进度</span>
                  <span style="font-size: 16px; font-weight: 600; color: #1D2129">{{ getCompletionRate(project) }}%</span>
                </div>
                <el-progress :percentage="getCompletionRate(project)" :stroke-width="6" :show-text="false" :color="getProgressColor(project)" />
              </div>

              <div class="card-meta">
                <div class="meta-row">
                  <Icon icon="ep:user" class="meta-icon" />
                  <span>{{ getManagerName(project) }}</span>
                </div>
                <div class="meta-row">
                  <Icon icon="ep:calendar" class="meta-icon" />
                  <span>{{ formatDate(project.planStartDate) }} ~ {{ formatDate(project.planEndDate) }}</span>
                </div>
                <div class="meta-row">
                  <Icon icon="ep:document" class="meta-icon" />
                  <span>任务: {{ getProjectTaskStats(project.projectId).completed }}/{{ getProjectTaskStats(project.projectId).total }}</span>
                  <el-tag v-if="getProjectTaskStats(project.projectId).delayed > 0" type="danger" size="small" effect="plain" style="margin-left: auto">
                    延期 {{ getProjectTaskStats(project.projectId).delayed }}
                  </el-tag>
                </div>
                <div class="meta-row">
                  <Icon icon="ep:timer" class="meta-icon" />
                  <span>{{ getRemainingDays(project.planEndDate) }}</span>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
        <div v-if="pagedCardList.length === 0 && !loading" class="empty-state">
          <el-empty description="暂无项目">
            <template #default>
              <el-button type="primary" @click="handleCreate">新建第一个项目</el-button>
            </template>
          </el-empty>
        </div>
        <div style="display: flex; justify-content: flex-end; margin-top: 16px" v-if="filteredList.length > cardPageSize">
          <el-pagination
            v-model:current-page="cardCurrentPage"
            :page-size="cardPageSize"
            :total="filteredList.length"
            layout="prev, pager, next"
            background
          />
        </div>
      </div>

      <!-- 列表视图 -->
      <el-table v-else :data="filteredList" v-loading="loading" stripe style="width: 100%" @row-click="goDetail"
        @selection-change="handleTableSelection">
        <el-table-column type="selection" width="40" />
        <el-table-column label="项目名称" prop="projectName" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" @click.stop="goDetail(row)">{{ row.projectName }}</el-link>
            <el-tag v-if="row.isKeyProject" type="danger" size="small" effect="dark" style="margin-left: 6px">重点</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="项目编号" prop="projectCode" width="140" />
        <el-table-column label="阶段" width="100">
          <template #default="{ row }">
            <el-tag :style="getPhaseTagStyle(row.currentStage)" size="small" effect="light">
              {{ getPhaseLabel(row.currentStage) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="160">
          <template #default="{ row }">
            <el-progress :percentage="getCompletionRate(row)" :stroke-width="8" :color="getProgressColor(row)" />
          </template>
        </el-table-column>
        <el-table-column label="完成率" width="100" align="center">
          <template #default="{ row }">
            <span :style="{ color: getCompletionColor(row) }">{{ getCompletionRate(row) }}%</span>
          </template>
        </el-table-column>
        <el-table-column label="延期任务" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="getDelayCount(row) > 0" type="danger" size="small" effect="plain">
              {{ getDelayCount(row) }}
            </el-tag>
            <span v-else style="color: #86909C">0</span>
          </template>
        </el-table-column>
        <el-table-column label="负责人" width="100">
          <template #default="{ row }">{{ getManagerName(row) }}</template>
        </el-table-column>
        <el-table-column label="计划周期" width="200">
          <template #default="{ row }">
            <span style="font-size: 13px">{{ formatDate(row.planStartDate) }} ~ {{ formatDate(row.planEndDate) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="goDetail(row)">详情</el-button>
            <el-button link type="primary" @click.stop="handleEdit(row)" v-if="checkPermi(['pms:project:update'])">编辑</el-button>
            <el-button link type="danger" @click.stop="handleDelete(row)" v-if="checkPermi(['pms:project:delete'])">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </ContentWrap>

    <!-- 项目表单弹窗 -->
    <ProjectForm ref="projectFormRef" @success="loadList" />
  </div>
</template>

<script setup lang="ts">
import { getProjectList, ProjectVO } from '@/api/pms/project'
import { getTaskList } from '@/api/pms/task'
import ProjectForm from './ProjectForm.vue'
import {
  projectStatusMap, phaseColorMap, priorityMap, projectTypeOptions,
  formatDate, calcDelayDays
} from '../pms-utils'
import { checkPermi } from '@/utils/permission'
import { useUserNames } from '@/hooks/pms/useUserNames'
import { useAppStore } from '@/store/modules/app'

defineOptions({ name: 'PmsProject' })

const { push } = useRouter()
const route = useRoute()
const message = useMessage()
const { getUserName, ensureLoaded: ensureUsersLoaded } = useUserNames()
const appStore = useAppStore()
const loading = ref(false)
const viewMode = ref<'card' | 'list'>('card')
const quickFilter = ref('all')
const projectList = ref<ProjectVO[]>([])
const taskList = ref<any[]>([])
const selectedProjects = ref<ProjectVO[]>([])
const projectFormRef = ref()

const queryParams = reactive({
  projectName: '',
  status: '',
  currentStage: '',
  projectType: ''
})

// 卡片分页
const cardCurrentPage = ref(1)
const cardPageSize = 12

// 获取当前用户ID
const currentUserId = computed(() => {
  try {
    const userInfo = appStore.getUserInfo
    return userInfo?.id
  } catch { return undefined }
})

const filteredList = computed(() => {
  let list = projectList.value.filter(p => p.projectType !== 'standard_template')
  if (queryParams.projectName) {
    list = list.filter(p => p.projectName?.includes(queryParams.projectName))
  }
  if (queryParams.status) {
    list = list.filter(p => p.status === queryParams.status)
  }
  if (queryParams.currentStage) {
    list = list.filter(p => p.currentStage === queryParams.currentStage)
  }
  if (queryParams.projectType) {
    list = list.filter(p => p.projectType === queryParams.projectType)
  }
  // 快捷筛选
  if (quickFilter.value === 'mine') {
    if (currentUserId.value) {
      list = list.filter(p => String(p.projectManagerId) === String(currentUserId.value))
    }
  } else if (quickFilter.value === 'involved') {
    if (currentUserId.value) {
      // 筛选当前用户参与的项目（项目经理或有任务分配给该用户）
      const involvedProjectIds = new Set<string>()
      taskList.value.forEach(t => {
        if (String(t.mainOwnerId) === String(currentUserId.value) && t.projectId) {
          involvedProjectIds.add(String(t.projectId))
        }
      })
      list = list.filter(p => {
        if (String(p.projectManagerId) === String(currentUserId.value)) return true
        return involvedProjectIds.has(String(p.projectId))
      })
    }
  } else if (quickFilter.value === 'archived') {
    list = list.filter(p => p.archived || p.status === 'archived')
  } else {
    list = list.filter(p => !p.archived && p.status !== 'archived')
  }
  // 补充延期任务数
  list = list.map(p => ({
    ...p,
    delayCount: getDelayCountForProject(p)
  }))
  return list
})

const pagedCardList = computed(() => {
  const start = (cardCurrentPage.value - 1) * cardPageSize
  return filteredList.value.slice(start, start + cardPageSize)
})

const loadList = async () => {
  loading.value = true
  try {
    const [projects, tasks] = await Promise.all([getProjectList(), getTaskList()])
    projectList.value = projects || []
    taskList.value = tasks || []
  } catch (e) {
    console.error('加载项目列表失败', e)
  } finally {
    loading.value = false
  }
}

// ==================== 辅助函数 ====================
const getPhaseLabel = (stage?: string) => {
  if (!stage) return '未开始'
  return phaseColorMap[stage]?.label || stage
}

const getPhaseTagStyle = (stage?: string) => {
  const p = phaseColorMap[stage || '']
  if (!p) return ''
  return `color: ${p.color}; background: ${p.bg}; border-color: ${p.border};`
}

const getStatusType = (status: string) => projectStatusMap[status]?.type || 'info'
const getStatusLabel = (status: string) => projectStatusMap[status]?.label || status

const getProgressColor = (project: ProjectVO) => {
  if (project.status === 'completed') return '#00B42A'
  if (project.status === 'delayed') return '#F53F3F'
  return '#2468F2'
}

const getCompletionRate = (project: ProjectVO) => {
  const tasks = taskList.value.filter(t => String(t.projectId) === String(project.projectId))
  if (tasks.length === 0) return 0
  const completed = tasks.filter(t => t.completeStatus === 'completed').length
  return Math.round((completed / tasks.length) * 100)
}

const getCompletionColor = (project: ProjectVO) => {
  const rate = getCompletionRate(project)
  if (rate === 100) return '#00B42A'
  if (rate >= 60) return '#2468F2'
  return '#FF7D00'
}

const getDelayCount = (project: ProjectVO) => {
  return getDelayCountForProject(project)
}

const getDelayCountForProject = (project: ProjectVO) => {
  const tasks = taskList.value.filter(t => String(t.projectId) === String(project.projectId))
  return tasks.filter(t => {
    if (t.completeStatus === 'completed' || t.completeStatus === 'cancelled') return false
    return calcDelayDays(t.planEndDate, t.completeStatus) > 0
  }).length
}

// P2-03 修复: 统计项目任务数
const getTaskCount = (project: ProjectVO) => {
  return taskList.value.filter(t => String(t.projectId) === String(project.projectId)).length
}

// 任务统计：总数 / 已完成 / 延期
const getProjectTaskStats = (projectId: any) => {
  const tasks = taskList.value.filter(t => String(t.projectId) === String(projectId))
  const total = tasks.length
  const completed = tasks.filter(t => t.completeStatus === 'completed').length
  const delayed = tasks.filter(t => {
    if (t.completeStatus === 'completed') return false
    if (!t.planEndDate) return false
    const endDate = Array.isArray(t.planEndDate)
      ? new Date(t.planEndDate[0], t.planEndDate[1] - 1, t.planEndDate[2])
      : new Date(t.planEndDate)
    return endDate < new Date()
  }).length
  return { total, completed, delayed }
}

// 剩余天数
const getRemainingDays = (planEndDate: any) => {
  if (!planEndDate) return '-'
  const end = Array.isArray(planEndDate)
    ? new Date(planEndDate[0], planEndDate[1] - 1, planEndDate[2])
    : new Date(planEndDate)
  const diff = Math.ceil((end.getTime() - Date.now()) / (1000 * 60 * 60 * 24))
  if (diff < 0) return `延期${Math.abs(diff)}天`
  if (diff === 0) return '今天到期'
  return `剩${diff}天`
}

// 状态标签
const getStatusTag = (status: string) => {
  const map: Record<string, { label: string; type: any }> = {
    'initiating': { label: '立项中', type: 'info' },
    'planning': { label: '规划中', type: 'info' },
    'in_progress': { label: '进行中', type: 'primary' },
    'completed': { label: '已完成', type: 'success' },
    'delayed': { label: '已延期', type: 'warning' },
    'cancelled': { label: '已取消', type: 'danger' },
    'archived': { label: '已归档', type: 'info' },
    'active': { label: '活跃', type: 'primary' }
  }
  return map[status || ''] || { label: status || '-', type: 'info' }
}

const handleTableSelection = (rows: ProjectVO[]) => {
  selectedProjects.value = rows
}

const handleQuickFilter = () => { cardCurrentPage.value = 1 }

const handleBatchArchive = () => {
  if (selectedProjects.value.length === 0) return
  message.confirm(`确认归档 ${selectedProjects.value.length} 个项目？`).then(async () => {
    const updateModule = await import('@/api/pms/project')
    for (const p of selectedProjects.value) {
      await updateModule.updateProject({ ...p, archived: true, status: 'archived' } as any)
    }
    message.success('批量归档成功')
    selectedProjects.value = []
    loadList()
  }).catch(() => {})
}

const handleExport = () => {
  message.info('导出Excel功能开发中')
  // TODO: 调用导出API
}

const getManagerName = (project: ProjectVO) => {
  return getUserName(project.projectManagerId)
}

// ==================== 操作 ====================
const handleSearch = () => { cardCurrentPage.value = 1 }
const handleReset = () => {
  queryParams.projectName = ''
  queryParams.status = ''
  queryParams.currentStage = ''
  queryParams.projectType = ''
  cardCurrentPage.value = 1
}

const handleCreate = () => {
  push('/pms/project-create')
}

const handleEdit = (project: ProjectVO) => {
  projectFormRef.value?.open('update', project)
}

const handleDelete = (project: ProjectVO) => {
  message.delConfirm(`确认删除项目「${project.projectName}」吗？`).then(async () => {
    await import('@/api/pms/project').then(m => m.deleteProject(project.projectId))
    message.success('删除成功')
    loadList()
  }).catch(() => {})
}

const handleArchive = (project: ProjectVO) => {
  message.confirm(`确认归档项目「${project.projectName}」吗？`).then(async () => {
    await import('@/api/pms/project').then(m => m.updateProject({ ...project, archived: true, status: 'archived' }))
    message.success('归档成功')
    loadList()
  }).catch(() => {})
}

const goDetail = (project: ProjectVO) => {
  push({ name: 'PmsProjectDetail', params: { id: project.projectId } })
}

onMounted(async () => {
  ensureUsersLoaded()
  await loadList()
  // 检查是否从详情页跳转过来编辑
  const editId = route.query.edit
  if (editId) {
    const target = projectList.value.find(p => String(p.projectId) === String(editId))
    if (target) {
      handleEdit(target)
    }
  }
})
</script>

<style scoped>
.project-card {
  cursor: pointer;
  transition: all 0.3s ease;
  border-radius: 6px;
}
.project-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0,0,0,0.12);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}
.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1D2129;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 220px;
}
.card-more {
  cursor: pointer;
  color: #86909C;
  font-size: 18px;
}
.card-phase {
  margin-bottom: 12px;
}
.card-progress {
  margin-bottom: 16px;
}
.progress-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.card-meta {
  border-top: 1px solid #F2F3F5;
  padding-top: 12px;
}
.meta-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #4E5969;
  margin-bottom: 6px;
}
.meta-icon {
  font-size: 14px;
  color: #86909C;
}
.empty-state {
  padding: 60px 0;
  text-align: center;
}
</style>
