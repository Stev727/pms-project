<template>
  <div>
    <!-- 工具栏 -->
    <div class="task-toolbar">
      <div style="display: flex; gap: 8px; align-items: center">
        <el-button type="primary" size="small" @click="$emit('create-task')" v-if="checkPermi(['pms:task:create'])">
          <Icon icon="ep:plus" class="mr-4px" />新建任务
        </el-button>
        <el-input v-model="searchKeyword" placeholder="搜索任务名称" clearable size="small" style="width: 200px">
          <template #prefix><Icon icon="ep:search" /></template>
        </el-input>
        <el-select v-model="filterStage" placeholder="全部阶段" clearable size="small" style="width: 120px">
          <el-option v-for="s in stages" :key="s.stageId" :label="s.stageName" :value="s.stageId" />
        </el-select>
        <el-select v-model="filterStatus" placeholder="全部状态" clearable size="small" style="width: 120px">
          <el-option v-for="(v, k) in taskStatusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </div>
      <div>
        <el-button size="small" @click="expandAll = !expandAll">
          <Icon :icon="expandAll ? 'ep:folder-opened' : 'ep:folder'" class="mr-4px" />
          {{ expandAll ? '折叠全部' : '展开全部' }}
        </el-button>
      </div>
    </div>

    <!-- 树形表格 -->
    <el-table
      :data="filteredTreeData"
      row-key="rowKey"
      :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
      :default-expand-all="expandAll"
      :expand-row-keys="expandedRowKeys"
      border
      stripe
      style="width: 100%"
      @row-click="handleRowClick"
    >
      <el-table-column label="任务名称" prop="taskName" min-width="250" show-overflow-tooltip>
        <template #default="{ row }">
          <div style="display: flex; align-items: center; gap: 6px">
            <el-icon v-if="row.isStageRow" style="color: #2468F2"><Icon icon="ep:folder" /></el-icon>
            <el-icon v-else-if="row.isMilestone" style="color: #FF7D00"><Icon icon="ep:star-filled" /></el-icon>
            <span :style="{ fontWeight: row.isStageRow ? '600' : 'normal', color: row.isStageRow ? '#1D2129' : '#4E5969' }">
              {{ row.taskName }}
            </span>
            <el-tag v-if="row.isCriticalPath && !row.isStageRow" type="danger" size="small" effect="plain">关键路径</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="负责人" width="100">
        <template #default="{ row }">
          <span v-if="row.isStageRow">-</span>
          <span v-else>{{ getOwnerName(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <template v-if="!row.isStageRow">
            <el-tag
              :style="getTaskStatusStyle(row.completeStatus)"
              size="small" effect="light"
            >{{ getTaskStatusLabel(row.completeStatus) }}</el-tag>
          </template>
        </template>
      </el-table-column>
      <el-table-column label="进度" width="120">
        <template #default="{ row }">
          <template v-if="!row.isStageRow">
            <el-progress
              :percentage="row.progress || 0"
              :stroke-width="8"
              :color="getProgressColor(row)"
            />
          </template>
        </template>
      </el-table-column>
      <el-table-column label="计划开始" width="110">
        <template #default="{ row }">
          <span v-if="!row.isStageRow" style="font-size: 13px">{{ formatDate(row.planStartDate, 'MM-DD') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="到期日" width="120">
        <template #default="{ row }">
          <template v-if="!row.isStageRow">
            <span :style="{ color: isDelayed(row) ? '#F53F3F' : '#4E5969' }">
              {{ formatDate(row.planEndDate, 'MM-DD') }}
            </span>
            <el-tag v-if="isDelayed(row)" type="danger" size="small" effect="plain" style="margin-left: 4px">
              延期{{ getDelayDays(row) }}天
            </el-tag>
          </template>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button v-if="!row.isStageRow" link type="primary" size="small" @click.stop="$emit('taskClick', row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { TaskVO } from '@/api/pms/task'
import { StageVO } from '@/api/pms/stage'
import { taskStatusMap, formatDate, calcDelayDays } from '../pms-utils'
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'TaskListTab' })

const props = defineProps<{
  projectId: string
  tasks: TaskVO[]
  stages: StageVO[]
}>()

defineEmits<{
  taskClick: [task: TaskVO]
  refresh: []
  'create-task': []
}>()

const searchKeyword = ref('')
const filterStage = ref<string | undefined>()
const filterStatus = ref('')
const expandAll = ref(true)
const expandedRowKeys = ref<string[]>([])

interface TreeRow extends TaskVO {
  rowKey: string
  isStageRow?: boolean
  children?: TreeRow[]
  hasChildren?: boolean
}

const filteredTreeData = computed<TreeRow[]>(() => {
  let tasks = props.tasks

  // 搜索过滤
  if (searchKeyword.value) {
    tasks = tasks.filter(t => t.taskName?.includes(searchKeyword.value))
  }
  if (filterStage.value) {
    tasks = tasks.filter(t => String(t.stageId) === String(filterStage.value))
  }
  if (filterStatus.value) {
    tasks = tasks.filter(t => t.completeStatus === filterStatus.value)
  }

  // 按阶段分组
  const tree: TreeRow[] = []
  const stageMap = new Map<string, TreeRow>()

  // 先创建阶段节点
  for (const stage of props.stages) {
    const stageRow: TreeRow = {
      ...stage,
      taskName: stage.stageName,
      rowKey: `stage_${stage.stageId}`,
      isStageRow: true,
      children: [],
      hasChildren: true
    }
    stageMap.set(String(stage.stageId), stageRow)
    tree.push(stageRow)
  }

  // 把任务放到对应阶段下
  for (const task of tasks) {
    const stageKey = String(task.stageId || '')
    const stageNode = stageMap.get(stageKey)
    const taskRow: TreeRow = {
      ...task,
      rowKey: `task_${task.taskId}`
    }
    if (stageNode) {
      stageNode.children!.push(taskRow)
    } else {
      // 没有阶段的任务直接放在根级
      tree.push(taskRow)
    }
  }

  // 移除空阶段
  return tree.filter(node => !node.isStageRow || (node.children && node.children.length > 0))
})

// ==================== 辅助函数 ====================
const getOwnerName = (task: TaskVO) => {
  return task.mainOwnerId ? `用户${task.mainOwnerId}` : '未分配'
}

const getTaskStatusStyle = (status?: string) => {
  const s = taskStatusMap[status || '']
  return s ? `color: ${s.textColor}; background: ${s.bgColor}; border-color: ${s.borderColor};` : ''
}

const getTaskStatusLabel = (status?: string) => taskStatusMap[status || '']?.label || status || '-'

const getProgressColor = (task: TaskVO) => {
  if (task.completeStatus === 'completed') return '#00B42A'
  if (isDelayed(task)) return '#F53F3F'
  return '#2468F2'
}

const isDelayed = (task: TaskVO) => calcDelayDays(task.planEndDate, task.completeStatus) > 0

const getDelayDays = (task: TaskVO) => calcDelayDays(task.planEndDate, task.completeStatus)

const handleRowClick = (row: TreeRow) => {
  if (!row.isStageRow) {
    // 触发任务详情
  }
}
</script>

<style scoped>
.task-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
</style>
