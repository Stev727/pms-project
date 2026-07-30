import assert from 'node:assert/strict'

import { buildTasksFromTemplate, buildTaskCreatePayload } from '../src/views/pms/pms-template-utils.ts'

const templateTasks = [{
  taskId: '10',
  taskName: '结构设计',
  stageId: '20',
  taskType: 'design',
  cycle: 8,
  priority: 'high',
  isMilestone: true,
  planStartDate: '2026-08-01',
  planEndDate: '2026-08-08',
  mainOwnerId: 1353,
  helperIds: '1354,1355',
  progress: 70,
  completeStatus: 'in_progress',
  description: '模板说明',
  outputRequirement: '模板输出物'
}]
const stages = [{ stageId: '20', stageName: '设计阶段', projectId: '1' }]

const copied = buildTasksFromTemplate(templateTasks, stages, 1000)
assert.equal(copied.length, 1)
assert.equal(copied[0].taskName, '结构设计')
assert.equal(copied[0].stageName, '设计阶段')
assert.equal(copied[0].stageId, '20')
assert.equal(copied[0].planStartDate, '')
assert.equal(copied[0].planEndDate, '')
assert.equal(copied[0].mainOwnerId, undefined)
assert.deepEqual(copied[0].helperIds, [])
assert.equal(copied[0].progress, 0)
assert.equal(copied[0].completeStatus, 'not_started')
assert.equal(copied[0].description, '')
assert.equal(copied[0].outputRequirement, '')

const untouchedPayload = buildTaskCreatePayload(copied[0])
assert.equal(untouchedPayload.planStartDate, undefined)
assert.equal(untouchedPayload.planEndDate, undefined)
assert.equal(untouchedPayload.mainOwnerId, undefined)
assert.equal(untouchedPayload.helperIds, null)
assert.equal(untouchedPayload.progress, 0)
assert.equal(untouchedPayload.completeStatus, 'not_started')

const filledPayload = buildTaskCreatePayload({
  ...copied[0],
  planStartDate: '2026-09-01',
  planEndDate: '2026-09-05',
  mainOwnerId: 2001,
  helperIds: [2002, 2003]
})
assert.equal(filledPayload.planStartDate, '2026-09-01')
assert.equal(filledPayload.planEndDate, '2026-09-05')
assert.equal(filledPayload.mainOwnerId, 2001)
assert.equal(filledPayload.helperIds, '2002,2003')

console.log('PMS template utilities: 21 assertions passed')
