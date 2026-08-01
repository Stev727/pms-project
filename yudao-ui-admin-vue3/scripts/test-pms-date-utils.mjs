import assert from 'node:assert/strict'
import {
  getWeekRange,
  inclusiveDuration,
  isDateInRange,
  summarizeStageDates
} from '../src/views/pms/pms-date-utils.ts'

const { start, end } = getWeekRange(new Date(2026, 6, 30, 12, 0, 0))
assert.equal(start, '2026-07-27')
assert.equal(end, '2026-08-02')
assert.equal(isDateInRange('2026-08-02', start, end), true)
assert.equal(isDateInRange('2026-08-03', start, end), false)
assert.equal(inclusiveDuration('2026-08-04', '2026-08-09'), 6)
assert.equal(inclusiveDuration('2026-08-09', '2026-08-04'), 0)
assert.deepEqual(
  summarizeStageDates([
    { planStartDate: '', planEndDate: '' },
    { planStartDate: '2026-08-04', planEndDate: '2026-08-09' },
    { planStartDate: '2026-08-01', planEndDate: '2026-08-06' }
  ]),
  { start: '2026-08-01', end: '2026-08-09', duration: 9 }
)
assert.deepEqual(summarizeStageDates([]), { start: '', end: '', duration: 0 })

console.log('PMS date utilities: 8 assertions passed')
