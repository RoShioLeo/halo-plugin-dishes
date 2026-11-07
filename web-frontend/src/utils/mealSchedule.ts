export type PeriodCode = 'breakfast' | 'lunch' | 'dinner'

export interface MealSchedule {
  lunchStart: string
  dinnerStart: string
}

export const DEFAULT_MEAL_SCHEDULE: Readonly<MealSchedule> = { lunchStart: '10:00', dinnerStart: '15:00' }

function minutes(value: unknown): number | null {
  if (typeof value !== 'string' || !/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(value)) return null
  const [hour, minute] = value.split(':').map(Number)
  return hour! * 60 + minute!
}

export function mealPeriodAt(date: Date, schedule?: MealSchedule | null): PeriodCode {
  let lunch = minutes(schedule?.lunchStart)
  let dinner = minutes(schedule?.dinnerStart)
  if (lunch === null || dinner === null || lunch <= 0 || lunch >= dinner) {
    lunch = minutes(DEFAULT_MEAL_SCHEDULE.lunchStart)!
    dinner = minutes(DEFAULT_MEAL_SCHEDULE.dinnerStart)!
  }
  const parts = new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Shanghai', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).formatToParts(date)
  const hour = Number(parts.find((part) => part.type === 'hour')?.value)
  const minute = Number(parts.find((part) => part.type === 'minute')?.value)
  const current = hour * 60 + minute
  return current < lunch ? 'breakfast' : current < dinner ? 'lunch' : 'dinner'
}

export function isPeriod(value: unknown): value is PeriodCode {
  return value === 'breakfast' || value === 'lunch' || value === 'dinner'
}

/** Keep an explicit link or an already chosen context ahead of a refreshed default. */
export function preferredMealPeriod(explicit: unknown, selected: unknown): PeriodCode | undefined {
  return isPeriod(explicit) ? explicit : isPeriod(selected) ? selected : undefined
}
