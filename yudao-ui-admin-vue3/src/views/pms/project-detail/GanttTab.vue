<template>
  <div class="gantt-container">
    <!-- 工具栏 -->
    <div class="gantt-toolbar">
      <div class="toolbar-left">
        <el-button-group size="small">
          <el-button @click="expandAll">展开全部</el-button>
          <el-button @click="collapseAll">折叠全部</el-button>
        </el-button-group>
        <el-divider direction="vertical" />
        <span class="toolbar-label">视图：</span>
        <el-radio-group v-model="scale" size="small" @change="changeScale">
          <el-radio-button label="day">日</el-radio-button>
          <el-radio-button label="week">周</el-radio-button>
          <el-radio-button label="month">月</el-radio-button>
          <el-radio-button label="quarter">季</el-radio-button>
        </el-radio-group>
      </div>
      <div class="toolbar-right">
        <el-checkbox v-model="showCritical" @change="renderGantt">显示关键路径</el-checkbox>
        <el-checkbox v-model="showToday" @change="renderGantt">显示今日线</el-checkbox>
        <el-checkbox v-model="showLinks" @change="renderGantt">显示依赖线</el-checkbox>
        <el-divider direction="vertical" />
        <el-button size="small" @click="zoomIn"><Icon icon="ep:zoom-in" /></el-button>
        <el-button size="small" @click="zoomOut"><Icon icon="ep:zoom-out" /></el-button>
      </div>
    </div>

    <!-- 甘特图区域 -->
    <div ref="ganttRef" class="gantt-chart"></div>

    <!-- 图例 -->
    <div class="gantt-legend">
      <span class="legend-item"><span class="legend-color" style="background: #2468F2"></span>正常任务</span>
      <span class="legend-item"><span class="legend-color" style="background: #00B42A"></span>已完成</span>
      <span class="legend-item"><span class="legend-color" style="background: #F53F3F; border: 2px solid #F53F3F"></span>延期</span>
      <span class="legend-item"><span class="legend-color" style="background: #86909C; transform: rotate(45deg)"></span>里程碑</span>
      <span class="legend-item"><span class="legend-color" style="background: #F53F3F; border: 2px solid #F53F3F; opacity: 0.3"></span>关键路径</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { TaskVO } from '@/api/pms/task'
import { StageVO } from '@/api/pms/stage'
import { taskStatusMap, formatDate, calcDelayDays } from '../pms-utils'
import { gantt } from 'dhtmlx-gantt'
import { onMounted, onUnmounted, ref, watch, nextTick } from 'vue'

defineOptions({ name: 'GanttTab' })

const props = defineProps<{
  projectId: string
  tasks: TaskVO[]
  stages: StageVO[]
  dependencies?: any[]
}>()

const ganttRef = ref<HTMLElement>()
const scale = ref('week')
const showCritical = ref(true)
const showToday = ref(true)
const showLinks = ref(true)

let ganttInitialized = false

// ==================== 甘特图配置 ====================
const configGantt = () => {
  gantt.config.row_height = 44
  gantt.config.bar_height = 22
  gantt.config.grid_width = 380
  gantt.config.scale_height = 50
  gantt.config.date_format = '%Y-%m-%d'
  gantt.config.drag_move = true
  gantt.config.drag_resize = true
  gantt.config.drag_progress = false
  gantt.config.drag_links = true
  gantt.config.work_time = false
  gantt.config.show_errors = false
  gantt.config.auto_scheduling = false

  // 列定义
  gantt.config.columns = [
    {
      name: 'text', label: '任务名称', tree: true, width: 200, resize: true
    },
    {
      name: 'owner', label: '负责人', align: 'center', width: 80,
      template: (obj: any) => obj.owner || '-'
    },
    {
      name: 'duration', label: '工期', align: 'center', width: 60,
      template: (obj: any) => obj.duration ? obj.duration + '天' : '-'
    }
  ]

  // 任务条样式
  gantt.templates.task_class = (start: any, end: any, task: any) => {
    if (task.type === 'project') return 'gantt-project'
    if (task.milestone) return 'gantt-milestone'
    if (showCritical.value && task.critical) return 'gantt-critical'
    if (task.delayed) return 'gantt-delayed'
    if (task.progress >= 1 || task.status === 'completed') return 'gantt-completed'
    return 'gantt-normal'
  }

  // 任务条文本
  gantt.templates.task_text = (start: any, end: any, task: any) => {
    if (task.milestone) return ''
    return `<span style="font-size:11px;color:#fff;">${task.text} ${task.progress ? Math.round(task.progress * 100) + '%' : ''}</span>`
  }

  // 日期格式化
  gantt.templates.date_grid = (date: any) => {
    return gantt.date.date_to_str('%Y-%m-%d')(date)
  }

  // 标尺配置
  setScaleConfig(scale.value)

  // 今日标线
  const markers = []
  if (showToday.value) {
    markers.push({
      start_date: new Date(),
      css: 'today-marker',
      text: '今日',
      title: '今日: ' + formatDate(new Date())
    })
  }
  gantt.config.markers = markers

  // 右键菜单
  gantt.config.context_menu = true
}

const setScaleConfig = (s: string) => {
  switch (s) {
    case 'day':
      gantt.config.scale_unit = 'day'
      gantt.config.step = 1
      gantt.config.date_scale = '%Y-%m-%d'
      gantt.config.subscales = [{ unit: 'hour', step: 4, date: '%H' }]
      break
    case 'week':
      gantt.config.scale_unit = 'week'
      gantt.config.step = 1
      gantt.config.date_scale = '%Y年 %W周'
      gantt.config.subscales = [
        { unit: 'day', step: 1, date: '%m-%d' }
      ]
      break
    case 'month':
      gantt.config.scale_unit = 'month'
      gantt.config.step = 1
      gantt.config.date_scale = '%Y年%m月'
      gantt.config.subscales = [
        { unit: 'day', step: 1, date: '%d' }
      ]
      break
    case 'quarter':
      gantt.config.scale_unit = 'month'
      gantt.config.step = 3
      gantt.config.date_scale = (date: any) => {
        const month = date.getMonth()
        const q = Math.floor(month / 3) + 1
        return date.getFullYear() + ' Q' + q
      }
      gantt.config.subscales = [
        { unit: 'month', step: 1, date: '%m月' }
      ]
      break
  }
}

// ==================== 数据转换 ====================
const buildGanttData = () => {
  const tasks: any[] = []
  const links: any[] = []

  // 创建阶段作为父节点
  const stageMap = new Map<string, any>()
  for (const stage of props.stages) {
    const stageId = `stage_${stage.stageId}`
    const stageTask = {
      id: stageId,
      text: stage.stageName,
      type: 'project',
      open: true,
      render: 'split',
      start_date: stage.planStartDate || stage.createTime?.split(' ')[0] || new Date().toISOString().split('T')[0],
      duration: 1,
      progress: 0,
      parent: 0,
      owner: ''
    }
    stageMap.set(String(stage.stageId), stageTask)
    tasks.push(stageTask)
  }

  // 创建任务
  for (const task of props.tasks) {
    const parentStage = stageMap.get(String(task.stageId || ''))
    const isDelayed = calcDelayDays(task.planEndDate, task.completeStatus) > 0

    tasks.push({
      id: String(task.taskId),
      text: task.taskName,
      start_date: task.planStartDate || new Date().toISOString().split('T')[0],
      duration: task.cycle || 1,
      progress: (task.progress || 0) / 100,
      parent: parentStage?.id || 0,
      owner: task.mainOwnerId ? `用户${task.mainOwnerId}` : '-',
      status: task.completeStatus,
      critical: task.isCriticalPath,
      delayed: isDelayed,
      milestone: task.isMilestone,
      type: task.isMilestone ? 'milestone' : 'task'
    })
  }

  // 创建依赖关系
  if (showLinks.value && props.dependencies) {
    for (const dep of props.dependencies) {
      links.push({
        id: String(dep.id || links.length + 1),
        source: String(dep.predecessorTaskId),
        target: String(dep.successorTaskId),
        type: '0' // finish-to-start
      })
    }
  }

  // 如果没有外部依赖数据，从任务的 parentTaskId 构建
  if (showLinks.value && links.length === 0) {
    for (const task of props.tasks) {
      if (task.parentTaskId) {
        links.push({
          id: 'link_' + links.length,
          source: String(task.parentTaskId),
          target: String(task.taskId),
          type: '0'
        })
      }
    }
  }

  return { data: tasks, links }
}

// ==================== 渲染 ====================
const renderGantt = () => {
  if (!ganttRef.value) return

  if (!ganttInitialized) {
    configGantt()
    gantt.init(ganttRef.value)
    ganttInitialized = true
  } else {
    gantt.clearAll()
    configGantt()
    gantt.render()
  }

  const ganttData = buildGanttData()
  gantt.parse(ganttData)

  // 拖拽事件
  gantt.attachEvent('onAfterTaskDrag', (id: any, mode: any, e: any) => {
    const task = gantt.getTask(id)
    if (task.type === 'project') return // 阶段节点不可拖拽
    // 触发更新
    console.log('Task dragged:', id, task.start_date, task.duration)
    // TODO: 调用 API 更新任务日期
  })

  // 点击事件
  gantt.attachEvent('onTaskClick', (id: any, e: any) => {
    const task = gantt.getTask(id)
    if (task.type !== 'project') {
      // 触发任务详情
    }
    return true
  })
}

const changeScale = () => {
  setScaleConfig(scale.value)
  gantt.render()
}

const expandAll = () => {
  gantt.eachTask((task: any) => {
    task.$open = true
  })
  gantt.render()
}

const collapseAll = () => {
  gantt.eachTask((task: any) => {
    task.$open = false
  })
  gantt.render()
}

const zoomIn = () => {
  gantt.ext.zoom.zoomIn()
}

const zoomOut = () => {
  gantt.ext.zoom.zoomOut()
}

// ==================== 生命周期 ====================
watch(() => props.tasks, () => {
  nextTick(() => renderGantt())
}, { deep: true })

onMounted(() => {
  nextTick(() => {
    renderGantt()
  })
})

onUnmounted(() => {
  gantt.clearAll()
  ganttInitialized = false
})
</script>

<style scoped>
.gantt-container {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.gantt-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #F7F8FA;
  border-radius: 4px;
  margin-bottom: 8px;
  flex-wrap: wrap;
  gap: 8px;
}
.toolbar-left, .toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.toolbar-label {
  font-size: 13px;
  color: #4E5969;
}
.gantt-chart {
  width: 100%;
  flex: 1;
  min-height: 400px;
  overflow: hidden;
}
.gantt-legend {
  display: flex;
  gap: 16px;
  padding: 8px 12px;
  background: #F7F8FA;
  border-radius: 4px;
  margin-top: 8px;
  flex-wrap: wrap;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #4E5969;
}
.legend-color {
  display: inline-block;
  width: 14px;
  height: 14px;
  border-radius: 2px;
}
</style>

<style>
/* dhtmlx-gantt 全局样式覆盖 */
@import 'dhtmlx-gantt/codebase/dhtmlxgantt.css';

/* 任务条样式 */
.gantt_task_line.gantt-project {
  background: transparent !important;
  border: none !important;
  box-shadow: none !important;
}
.gantt_task_line.gantt-project .gantt_task_progress {
  display: none;
}
.gantt_task_line.gantt-project .gantt_task_content {
  font-weight: 600;
  color: #1D2129;
}

.gantt_task_line.gantt-normal {
  background: #2468F2 !important;
  border: 1px solid #1A56DB !important;
  border-radius: 3px;
}
.gantt_task_line.gantt-normal .gantt_task_progress {
  background: #1A56DB !important;
}

.gantt_task_line.gantt-completed {
  background: #00B42A !important;
  border: 1px solid #009A29 !important;
  border-radius: 3px;
}
.gantt_task_line.gantt-completed .gantt_task_progress {
  background: #009A29 !important;
}

.gantt_task_line.gantt-delayed {
  background: #F53F3F !important;
  border: 2px solid #CB2634 !important;
  border-radius: 3px;
}
.gantt_task_line.gantt-delayed .gantt_task_progress {
  background: #CB2634 !important;
}

.gantt_task_line.gantt-critical {
  background: #2468F2 !important;
  border: 2px solid #F53F3F !important;
  border-radius: 3px;
  box-shadow: 0 0 4px rgba(245, 63, 63, 0.3);
}

.gantt_task_line.gantt-milestone .gantt_task_content {
  background: #86909C !important;
  transform: rotate(45deg);
  width: 12px !important;
  height: 12px !important;
  border-radius: 0;
  margin: 5px auto 0;
}

/* 今日标线 */
.today-marker {
  background-color: #F53F3F;
  width: 2px !important;
}
.today-marker::before {
  content: '今日';
  position: absolute;
  top: -20px;
  left: -12px;
  font-size: 11px;
  color: #F53F3F;
  font-weight: 600;
}

/* 网格线 */
.gantt_grid_scale,
.gantt_task_scale {
  background: #F2F3F5 !important;
  font-size: 12px;
}

/* 行交替色 */
.gantt_task_row:nth-child(even) {
  background: #FAFBFC;
}
.gantt_task_row.odd {
  background: #FFF;
}

/* 网格线 */
.gantt_grid_data .gantt_row,
.gantt_task .gantt_task_row {
  border-bottom: 1px solid #E5E6EB;
}
</style>
