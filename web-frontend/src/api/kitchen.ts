import { ref } from 'vue'
import { apiGet } from '@/api/http'

export type PeriodCode = 'breakfast' | 'lunch' | 'dinner'
export const mealPeriods: { code: PeriodCode; name: string }[] = [
  { code: 'breakfast', name: '早餐' },
  { code: 'lunch', name: '午餐' },
  { code: 'dinner', name: '晚餐' },
]
export interface Viewer {
  authenticated: boolean
  displayName: string
  canManage: boolean
  today: string
  maxDate: string
}
export interface Dish {
  id: number
  categoryId: number
  categoryName: string
  name: string
  imageUrl: string
  recommendationLevel: number
  description: string
  isAvailable: boolean
  soldOut: boolean
  tags: string[]
}
export interface Selection {
  dishId: number
  name: string
  imageUrl: string
  quantity: number
  note: string
  tags: string[]
}
export interface MenuItem {
  dishId: number | null
  customId?: string | null
  name: string
  quantity: number
  imageUrl: string
  note: string
}
export interface CustomMenuItem extends MenuItem {
  dishId: null
  customId: string
}
export interface Participant {
  id: string
  displayName: string
  avatarUrl?: string
  guest: boolean
  mine: boolean
  revision: number
  items: Selection[]
  customItems?: CustomMenuItem[]
  offMenu: string
  remark: string
  updatedAt: string
  lastEditedBy?: string
  lastEditedAt?: string
  batchId: string
  submitted: boolean
  submittedAt: string | null
}
export interface SubmittedBatch { id: string; submittedAt: string; submittedBy: string }
export interface SharedOrder {
  date: string
  period: PeriodCode
  periodName: string
  needsConfirmation: boolean
  activeBatchId: string
  draftContributionVersions: Record<string, number>
  submittedBatches: SubmittedBatch[]
  hasDraftChanges: boolean
  participants: Participant[]
  totals: { dishId: number; name: string; imageUrl: string; quantity: number; people: { id: string; displayName: string; quantity: number }[] }[]
  customTotals?: CustomMenuItem[]
  mine: { revision: number; items: Selection[]; offMenu: string; remark: string; nickname: string }
  menu: { status: 'draft' | 'confirmed'; revision: number; items: MenuItem[]; note: string; confirmedAt: string | null; confirmedBy: string | null }
}
export interface DailyMenu {
  date: string
  periods: { code: PeriodCode; name: string; status: 'pending' | 'confirmed'; needsConfirmation: boolean; items: MenuItem[]; note: string; confirmedAt: string | null }[]
}

export const kitchenViewer = ref<Viewer | null>(null)
let viewerLoading: Promise<Viewer> | null = null
export async function loadViewer(force = false): Promise<Viewer> {
  if (kitchenViewer.value && !force) return kitchenViewer.value
  if (!viewerLoading) {
    viewerLoading = apiGet<Viewer>('/viewer').then((data) => {
      kitchenViewer.value = data
      return data
    }).finally(() => { viewerLoading = null })
  }
  return viewerLoading
}

export function shanghaiToday(): string {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date())
  const part = (type: string) => parts.find((item) => item.type === type)?.value ?? ''
  return `${part('year')}-${part('month')}-${part('day')}`
}
export function defaultPeriod(): PeriodCode {
  const hour = Number(new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Shanghai', hour: '2-digit', hourCycle: 'h23' }).format(new Date()))
  return hour < 10 ? 'breakfast' : hour < 16 ? 'lunch' : 'dinner'
}
export function isPeriod(value: unknown): value is PeriodCode {
  return value === 'breakfast' || value === 'lunch' || value === 'dinner'
}
export function isDate(value: unknown): value is string {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const date = new Date(`${value}T00:00:00Z`)
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value
}
export const mealContext = ref({ date: shanghaiToday(), period: defaultPeriod() })
export function periodName(code: PeriodCode): string {
  return mealPeriods.find((item) => item.code === code)?.name ?? '午餐'
}
export function quantityLabel(value: number): string {
  return String(value)
}
export function normalizeCustomItems(raw: unknown): CustomMenuItem[] {
  if (!Array.isArray(raw)) return []
  return raw.flatMap((entry) => {
    if (!entry || typeof entry !== 'object') return []
    const item = entry as Record<string, unknown>
    if (item.dishId !== null || typeof item.customId !== 'string' || !item.customId.trim()
      || typeof item.name !== 'string' || !item.name.trim() || typeof item.quantity !== 'number'
      || !Number.isSafeInteger(item.quantity) || item.quantity <= 0 || item.quantity > 9999) return []
    return [{ dishId: null, customId: item.customId, name: item.name, quantity: item.quantity,
      imageUrl: typeof item.imageUrl === 'string' ? item.imageUrl : '', note: typeof item.note === 'string' ? item.note : '' }]
  })
}
export function withinPublicCartLimit(source: Pick<Participant, 'items'>): boolean {
  return source.items.every((item) => Number.isSafeInteger(item.quantity) && item.quantity > 0 && item.quantity <= 99)
}
export function countSharedDishes(order: Pick<SharedOrder, 'totals' | 'customTotals'> | null): number {
  const keys = new Set((order?.totals ?? []).map((item) => `dish:${item.dishId}`))
  for (const item of normalizeCustomItems(order?.customTotals)) keys.add(`custom:${item.customId}`)
  return keys.size
}
export function normalizeDish(raw: unknown): Dish {
  const d = raw && typeof raw === 'object' ? raw as Record<string, unknown> : {}
  return {
    id: Number(d.id), categoryId: Number(d.categoryId ?? 0),
    categoryName: String(d.categoryName ?? ''), name: String(d.name ?? ''),
    imageUrl: String(d.imageUrl ?? ''), description: String(d.description ?? ''),
    recommendationLevel: Number(d.recommendationLevel ?? 3),
    isAvailable: d.isAvailable === true,
    soldOut: d.soldOut === true,
    tags: Array.isArray(d.tags) ? d.tags.map(String).filter(Boolean) : [],
  }
}
