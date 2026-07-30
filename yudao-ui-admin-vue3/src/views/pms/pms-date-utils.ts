const DAY_MS = 24 * 60 * 60 * 1000

function parseCalendarDate(value: unknown): Date | null {
  if (!value) return null
  if (value instanceof Date) {
    return new Date(value.getFullYear(), value.getMonth(), value.getDate())
  }
  if (Array.isArray(value) && value.length >= 3) {
    return new Date(Number(value[0]), Number(value[1]) - 1, Number(value[2]))
  }
  const match = String(value).match(/^(\d{4})-(\d{2})-(\d{2})/)
  if (!match) return null
  const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
  return Number.isNaN(date.getTime()) ? null : date
}

function formatCalendarDate(date: Date): string {
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0')
  ].join('-')
}

export function getWeekRange(value: Date = new Date()): { start: string; end: string } {
  const date = new Date(value.getFullYear(), value.getMonth(), value.getDate())
  const mondayOffset = (date.getDay() + 6) % 7
  const start = new Date(date)
  start.setDate(date.getDate() - mondayOffset)
  const end = new Date(start)
  end.setDate(start.getDate() + 6)
  return { start: formatCalendarDate(start), end: formatCalendarDate(end) }
}

export function isDateInRange(value: unknown, start: unknown, end: unknown): boolean {
  const dateValue = parseCalendarDate(value)
  const startValue = parseCalendarDate(start)
  const endValue = parseCalendarDate(end)
  if (!dateValue || !startValue || !endValue) return false
  return dateValue >= startValue && dateValue <= endValue
}

export function inclusiveDuration(start: unknown, end: unknown): number {
  const startValue = parseCalendarDate(start)
  const endValue = parseCalendarDate(end)
  if (!startValue || !endValue || endValue < startValue) return 0
  return Math.round((endValue.getTime() - startValue.getTime()) / DAY_MS) + 1
}

export function summarizeStageDates(
  tasks: Array<{ planStartDate?: unknown; planEndDate?: unknown }>
): { start: string; end: string; duration: number } {
  const starts = tasks
    .map((task) => parseCalendarDate(task.planStartDate))
    .filter((date): date is Date => Boolean(date))
  const ends = tasks
    .map((task) => parseCalendarDate(task.planEndDate))
    .filter((date): date is Date => Boolean(date))
  if (starts.length === 0 || ends.length === 0) return { start: '', end: '', duration: 0 }
  const start = new Date(Math.min(...starts.map((date) => date.getTime())))
  const end = new Date(Math.max(...ends.map((date) => date.getTime())))
  const startText = formatCalendarDate(start)
  const endText = formatCalendarDate(end)
  return { start: startText, end: endText, duration: inclusiveDuration(startText, endText) }
}
