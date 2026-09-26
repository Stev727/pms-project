type TemplateTask = Record<string, any>
type TemplateStage = { stageId?: string | number; stageName?: string }

/**
 * 新项目继承模板的阶段归属、任务名称与输出物要求（outputRequirement / requireDeliverable）；
 * 执行数据（日期、负责人、进度等）必须由新项目重新填写。
 */
export function buildTasksFromTemplate(
  templateTasks: TemplateTask[],
  templateStages: TemplateStage[],
  taskIdBase = Date.now()
) {
  return templateTasks.map((task, index) => {
    const stage = templateStages.find(item => String(item.stageId) === String(task.stageId))
    return {
      taskId: taskIdBase + index + 1,
      taskName: task.taskName,
      stageName: stage?.stageName || '未分组',
      stageId: stage?.stageId ?? task.stageId,
      taskType: 'design',
      cycle: undefined,
      priority: 'normal',
      isMilestone: false,
      planStartDate: '',
      planEndDate: '',
      mainOwnerId: undefined,
      helperIds: [],
      progress: 0,
      completeStatus: 'not_started',
      description: '',
      outputRequirement: task.outputRequirement || '',
      requireDeliverable: !!task.requireDeliverable,
      roleName: ''
    }
  })
}

/** 创建接口不得把项目日期隐式写回尚未填写的模板任务。 */
export function buildTaskCreatePayload(task: TemplateTask) {
  return {
    taskName: task.taskName,
    stageId: task.stageId,
    taskType: task.taskType || 'design',
    cycle: task.cycle || undefined,
    priority: task.priority || 'normal',
    isMilestone: !!task.isMilestone,
    mainOwnerId: task.mainOwnerId,
    helperIds: Array.isArray(task.helperIds) && task.helperIds.length > 0
      ? task.helperIds.join(',')
      : null,
    description: task.description || '',
    outputRequirement: task.outputRequirement || '',
    requireDeliverable: !!task.requireDeliverable,
    planStartDate: task.planStartDate || undefined,
    planEndDate: task.planEndDate || undefined,
    progress: 0,
    completeStatus: 'not_started',
    roleName: task.roleName || ''
  }
}
