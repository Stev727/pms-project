<template>
  <div class="p-20px">
    <ContentWrap>
      <!-- 项目选择 + 工具栏 -->
      <div class="gantt-header">
        <div class="header-left">
          <el-select v-model="selectedProjectId" placeholder="请选择项目" clearable filterable @change="loadGanttData" style="width: 280px">
            <el-option v-for="p in projectList" :key="p.projectId" :label="p.projectName" :value="p.projectId" />
          </el-select>
        </div>
        <div class="header-right">
          <el-button-group size="small">
            <el-button @click="expandAll">展开全部</el-button>
            <el-button @click="collapseAll">折叠全部</el-button>
          </el-button-group>
          <el-divider direction="vertical" />
          <el-radio-group v-model="scale" size="small" @change="changeScale">
            <el-radio-button label="day">日</el-radio-button>
            <el-radio-button label="week">周</el-radio-button>
            <el-radio-button label="month">月</el-radio-button>
            <el-radio-button label="quarter">季</el-radio-button>
          </el-radio-group>
          <el-divider direction="vertical" />
          <el-checkbox v-model="showCritical" @change="renderGantt">关键路径</el-checkbox>
          <el-checkbox v-model="showToday" @change="renderGantt">今日线</el-checkbox>
          <el-checkbox v-model="showLinks" @change="renderGantt">依赖线</el-checkbox>
        </div>
      </div>

      <!-- 甘特图区域 -->
      <div ref="ganttRef" style="width: 100%; height: calc(100vh - 280px); min-height: 400px"></div>

      <!-- 图例 -->
      <div class="gantt-legend">
        <span class="legend-item"><span class="legend-color" style="background: #2468F2"></span>正常任务</span>
        <span class="legend-item"><span class="legend-color" style="background: #00B42A"></span>已完成</span>
        <span class="legend-item"><span class="legend-color" style="background: #F53F3F; border: 2px solid #F53F3F"></span>延期</span>
        <span class="legend-item"><span class="legend-color" style="background: #86909C; transform: rotate(45deg)"></span>里程碑</span>
        <span class="legend-item"><span class="legend-color" style="background: #F53F3F; opacity: 0.3; border: 2px solid #F53F3F"></span>关键路径</span>
        <span class="legend-item"><span class="legend-line"></span>依赖关系</span>
      </div>
    </ContentWrap>
  </div>
</template>

<script setup lang="ts">
import { getProjectList, ProjectVO } from '@/api/pms/project'
import { getTaskList, TaskVO } from '@/api/pms/task'
import { getStageList, StageVO } from '@/api/pms/stage'
import { calcDelayDays } from '../pms-utils'
import { gantt } from 'dhtmlx-gantt'

defineOptions({ name: 'PmsGantt' })

const message = useMessage()
const selectedProjectId = ref<string | undefined>()
const projectList = ref<ProjectVO[]>([])
const allTasks = ref<TaskVO[]>([])
const allStages = ref<StageVO[]>([])
const ganttRef = ref<HTMLElement>()
const scale = ref('week')
const showCritical = ref(true)
const showToday = ref(true)
const showLinks = ref(true)
let ganttReady = false

const loadProjects = async () => {
  projectList.value = (await getProjectList()).filter((p: ProjectVO) => p.projectType !== 'standard_template')
  // 预加载所有任务和阶段
  const [tasks, stages] = await Promise.all([getTaskList(), getStageList()])
  allTasks.value = tasks || []
  allStages.value = stages || []
}

const loadGanttData = () => {
  renderGantt()
}

const configGantt = () => {
  gantt.config.row_height = 44
  gantt.config.bar_height = 22
  gantt.config.grid_width = 420
  gantt.config.scale_height = 50
  gantt.config.date_format = '%Y-%m-%d'
  gantt.config.drag_move = true
  gantt.config.drag_resize = true
  gantt.config.drag_links = true
  gantt.config.show_errors = false

  gantt.config.columns = [
    { name: 'text', label: '任务名称', tree: true, width: 220, resize: true },
    { name: 'owner', label: '负责人', align: 'center', width: 80, template: (o: any) => o.owner || '-' },
    { name: 'duration', label: '工期', align: 'center', width: 60, template: (o: any) => o.duration ? o.duration + '天' : '-' }
  ]

  gantt.templates.task_class = (_s: any, _e: any, task: any) => {
    if (task.type === 'project') return 'gantt-project'
    if (task.milestone) return 'gantt-milestone'
    if (showCritical.value && task.critical) return 'gantt-critical'
    if (task.delayed) return 'gantt-delayed'
    if (task.progress >= 1 || task.status === 'completed') return 'gantt-completed'
    return 'gantt-normal'
  }

  gantt.templates.task_text = (_s: any, _e: any, task: any) => {
    if (task.milestone) return ''
    const pct = task.progress ? Math.round(task.progress * 100) + '%' : ''
    return `<span style="font-size:11px;color:#fff;">${task.text} ${pct}</span>`
  }

  gantt.templates.date_grid = (date: any) => gantt.date.date_to_str('%Y-%m-%d')(date)

  setScaleConfig(scale.value)

  // 今日标线
  gantt.config.markers = showToday.value ? [{
    start_date: new Date(),
    css: 'today-marker',
    text: '今日'
  }] : []

  // 拖拽后更新
  gantt.attachEvent('onAfterTaskDrag', (id: any, mode: any, _e: any) => {
    const task = gantt.getTask(id)
    if (task.type === 'project') return
    message.success(`任务「${task.text}」日期已更新`)
    // TODO: 调用 updateTask API 更新后端数据
  })

  // 双击编辑
  gantt.attachEvent('onTaskDblClick', (id: any, _e: any) => {
    const task = gantt.getTask(id)
    if (task.type === 'project') return
    // TODO: 打开编辑弹窗
  })
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
      gantt.config.subscales = [{ unit: 'day', step: 1, date: '%m-%d' }]
      break
    case 'month':
      gantt.config.scale_unit = 'month'
      gantt.config.step = 1
      gantt.config.date_scale = '%Y年%m月'
      gantt.config.subscales = [{ unit: 'day', step: 1, date: '%d' }]
      break
    case 'quarter':
      gantt.config.scale_unit = 'month'
      gantt.config.step = 3
      gantt.config.date_scale = (date: any) => {
        return date.getFullYear() + ' Q' + (Math.floor(date.getMonth() / 3) + 1)
      }
      gantt.config.subscales = [{ unit: 'month', step: 1, date: '%m月' }]
      break
  }
}

const buildGanttData = () => {
  const tasks: any[] = []
  const links: any[] = []

  let projectTasks = allTasks.value
  let projectStages = allStages.value

  if (selectedProjectId.value) {
    projectTasks = projectTasks.filter(t => String(t.projectId) === String(selectedProjectId.value))
    projectStages = projectStages.filter(s => String(s.projectId) === String(selectedProjectId.value))
  }

  // 阶段节点
  const stageMap = new Map<string, any>()
  for (const stage of projectStages) {
    const stageId = `stage_${stage.stageId}`
    const stageTask = {
      id: stageId,
      text: stage.stageName,
      type: 'project',
      open: true,
      render: 'split',
      start_date: stage.planStartDate || new Date().toISOString().split('T')[0],
      duration: 1,
      progress: 0,
      parent: 0,
      owner: ''
    }
    stageMap.set(String(stage.stageId), stageTask)
    tasks.push(stageTask)
  }

  // 任务
  for (const task of projectTasks) {
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

  // 依赖关系
  if (showLinks.value) {
    for (const task of projectTasks) {
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

const renderGantt = () => {
  if (!ganttRef.value) return
  if (!ganttReady) {
    configGantt()
    gantt.init(ganttRef.value)
    ganttReady = true
  } else {
    gantt.clearAll()
    setScaleConfig(scale.value)
    gantt.config.markers = showToday.value ? [{
      start_date: new Date(), css: 'today-marker', text: '今日'
    }] : []
    gantt.render()
  }
  gantt.parse(buildGanttData())
}

const changeScale = () => {
  setScaleConfig(scale.value)
  gantt.render()
}

const expandAll = () => {
  gantt.eachTask((t: any) => { t.$open = true })
  gantt.render()
}

const collapseAll = () => {
  gantt.eachTask((t: any) => { t.$open = false })
  gantt.render()
}

onMounted(async () => {
  await loadProjects()
  nextTick(() => renderGantt())
})

onUnmounted(() => {
  gantt.clearAll()
  ganttReady = false
})
</script>

<style scoped>
.gantt-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  flex-wrap: wrap;
  gap: 8px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
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
.legend-line {
  display: inline-block;
  width: 14px;
  height: 2px;
  background: #86909C;
}
</style>

<style>
@import 'dhtmlx-gantt/codebase/dhtmlxgantt.css';

.gantt_task_line.gantt-project { background: transparent !important; border: none !important; }
.gantt_task_line.gantt-project .gantt_task_progress { display: none; }
.gantt_task_line.gantt-normal { background: #2468F2 !important; border: 1px solid #1A56DB !important; border-radius: 3px; }
.gantt_task_line.gantt-normal .gantt_task_progress { background: #1A56DB !important; }
.gantt_task_line.gantt-completed { background: #00B42A !important; border: 1px solid #009A29 !important; border-radius: 3px; }
.gantt_task_line.gantt-delayed { background: #F53F3F !important; border: 2px solid #CB2634 !important; border-radius: 3px; }
.gantt_task_line.gantt-critical { background: #2468F2 !important; border: 2px solid #F53F3F !important; border-radius: 3px; box-shadow: 0 0 4px rgba(245,63,63,0.3); }
.gantt_task_line.gantt-milestone .gantt_task_content { background: #86909C !important; transform: rotate(45deg); width: 12px !important; height: 12px !important; margin: 5px auto 0; }
.today-marker { background-color: #F53F3F; width: 2px !important; }
.today-marker::before { content: '今日'; position: absolute; top: -20px; left: -12px; font-size: 11px; color: #F53F3F; font-weight: 600; }
.gantt_grid_scale, .gantt_task_scale { background: #F2F3F5 !important; font-size: 12px; }
.gantt_task_row:nth-child(even) { background: #FAFBFC; }
</style>
