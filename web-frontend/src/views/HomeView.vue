<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { apiGet, getApiErrorMessage } from '@/api/http'
import { resolveMediaUrl } from '@/utils/mediaUrl'
import { dishRecommendationLevel, stars } from '@/utils/recommendationDisplay'
import { getPublicBrandSubtitle, getPublicBrandTitle } from '@/utils/publicBranding'
import HomeOrderFab from '@/components/HomeOrderFab.vue'
import headerLogoUrl from '@/assets/lemon.svg'

const router = useRouter()

/** 今日点餐 */
interface MealPeriodInfo {
  id: number
  code: string
  name: string
  sort_order: number
}

interface OrderItemRow {
  line_id: number
  dish_id: number
  quantity: number
  note: string | null
  dish: {
    name: string
    image_url: string | null
    category_id: number
    category_name: string
  }
}

interface OrderBlock {
  id: number
  remark: string | null
  items: OrderItemRow[]
  item_count: number
  created_at: string
  updated_at: string
}

interface TodayPeriodRow {
  meal_period: MealPeriodInfo
  order: OrderBlock | null
}

interface TodayData {
  date: string
  periods: TodayPeriodRow[]
}

/** GET /api/meal-orders/history 单条（含餐段，首页会按日合并菜品） */
interface HistoryOrderRow {
  order_date: string
  meal_period: { id: number; code: string; name: string }
  order: OrderBlock | null
}

interface HistoryListData {
  range: { from: string; to: string }
  items: HistoryOrderRow[]
  meta: { total: number; limit: number; offset: number }
}

interface ReservationFlatLine {
  dish_id: number
  name: string
  image_url: string | null
  qty: number
  note: string | null
}

interface ReservationDateGroup {
  date: string
  label: string
  relativeLabel: string
  lines: ReservationFlatLine[]
}

/** 推荐组合 */
interface RecDish {
  dish_id: number
  name: string
  category_name?: string
  image_url?: string | null
  /** 1–5，主厨推荐等级（后端 dishes.recommendation_level） */
  recommendation_level?: number
}

type PeriodCode = 'breakfast' | 'lunch' | 'dinner'

function resolveHeaderAvatarUrlRaw() {
  const fallback = headerLogoUrl
  try {
    const customLogo = (window as unknown as { __DISHES_PUBLIC_LOGO__?: string }).__DISHES_PUBLIC_LOGO__
    const v = (customLogo ?? '').trim()
    return v || String(fallback)
  } catch {
    return String(fallback)
  }
}

const headerAvatarUrl = computed(() => resolveMediaUrl(resolveHeaderAvatarUrlRaw()))

const publicBrandTitle = computed(() => getPublicBrandTitle())
const publicBrandSubtitle = computed(() => getPublicBrandSubtitle())

const loading = ref(true)
const error = ref<string | null>(null)
const today = ref<TodayData | null>(null)
const recList = ref<RecDish[]>([])
const recError = ref<string | null>(null)
const recTip = ref<string | null>(null)
const recStarted = ref(false)
const recReplacing = ref<Record<number, boolean>>({})
const recExhausted = ref(false)

const selectedPeriod = ref<PeriodCode>('lunch')
const boardKey = ref(0)
const shuffling = ref(false)

/** 当前推荐使用的餐段 code（与 selectedPeriod 保持一致，用于请求后端随机推荐） */
const recPeriod = ref<PeriodCode>('lunch')

type RecCacheState = {
  started: boolean
  exhausted: boolean
  list: RecDish[]
  tip: string | null
  error: string | null
}

const recCache = ref<Partial<Record<PeriodCode, RecCacheState>>>({})

const REC_RECENT_TTL_MS = 30 * 60 * 1000
const REC_IGNORE_TTL_MS = 7 * 24 * 60 * 60 * 1000

type RecTsId = { id: number; t: number }

function lsKeyRecent(period: string) {
  return `home_rec_recent_${period}`
}
function lsKeyIgnored() {
  return `home_rec_ignored`
}

function nowMs() {
  return Date.now()
}

function readRecent(period: string): RecTsId[] {
  try {
    const raw = localStorage.getItem(lsKeyRecent(period))
    if (!raw) return []
    const v = JSON.parse(raw) as unknown
    if (!Array.isArray(v)) return []
    const cutoff = nowMs() - REC_RECENT_TTL_MS
    return v
      .filter((x) => x && typeof x === 'object')
      .map((x) => x as { id?: unknown; t?: unknown })
      .map((x) => ({ id: Number(x.id), t: Number(x.t) }))
      .filter((x) => Number.isFinite(x.id) && x.id > 0 && Number.isFinite(x.t) && x.t >= cutoff)
  } catch {
    return []
  }
}

function writeRecent(period: string, items: RecTsId[]) {
  try {
    localStorage.setItem(lsKeyRecent(period), JSON.stringify(items.slice(-200)))
  } catch {
    // ignore
  }
}

function pushRecent(period: string, dishIds: number[]) {
  const t = nowMs()
  const cur = readRecent(period)
  const seen = new Set(cur.map((x) => x.id))
  for (const id of dishIds) {
    if (id > 0 && !seen.has(id)) cur.push({ id, t })
  }
  writeRecent(period, cur)
}

function readIgnored(): RecTsId[] {
  try {
    const raw = localStorage.getItem(lsKeyIgnored())
    if (!raw) return []
    const v = JSON.parse(raw) as unknown
    if (!Array.isArray(v)) return []
    const cutoff = nowMs() - REC_IGNORE_TTL_MS
    return v
      .filter((x) => x && typeof x === 'object')
      .map((x) => x as { id?: unknown; t?: unknown })
      .map((x) => ({ id: Number(x.id), t: Number(x.t) }))
      .filter((x) => Number.isFinite(x.id) && x.id > 0 && Number.isFinite(x.t) && x.t >= cutoff)
  } catch {
    return []
  }
}

function writeIgnored(items: RecTsId[]) {
  try {
    localStorage.setItem(lsKeyIgnored(), JSON.stringify(items.slice(-500)))
  } catch {
    // ignore
  }
}

function addIgnored(dishId: number) {
  const t = nowMs()
  const cur = readIgnored().filter((x) => x.id !== dishId)
  cur.push({ id: dishId, t })
  writeIgnored(cur)
}

function excludeIdsForRequest(period: string): number[] {
  const recent = readRecent(period).map((x) => x.id)
  const ignored = readIgnored().map((x) => x.id)
  return Array.from(new Set([...recent, ...ignored]))
}

function exhaustedKey(ymd: string, period: string) {
  return `home_rec_exhausted_${ymd}_${period}`
}

function readExhausted(ymd: string, period: string): boolean {
  try {
    return localStorage.getItem(exhaustedKey(ymd, period)) === '1'
  } catch {
    return false
  }
}

function exhaustedMessage(period: PeriodCode): string {
  if (period === 'breakfast') return '早餐推荐已经看完啦，去菜谱里再挑挑吧。'
  if (period === 'lunch') return '午餐推荐已经看完啦，去菜谱里再挑挑吧。'
  return '晚餐推荐已经看完啦，去菜谱里再挑挑吧。'
}

function saveRecState(code: PeriodCode) {
  recCache.value = {
    ...recCache.value,
    [code]: {
      started: recStarted.value,
      exhausted: recExhausted.value,
      list: recList.value,
      tip: recTip.value,
      error: recError.value,
    },
  }
}

function restoreRecState(code: PeriodCode) {
  const st = recCache.value[code]
  if (st) {
    recStarted.value = st.started
    recExhausted.value = st.exhausted
    recList.value = st.list
    recTip.value = st.tip
    recError.value = st.error
    return
  }

  // 没缓存时：按持久化 exhausted 初始化，否则回到“开始推荐”
  recStarted.value = false
  recList.value = []
  recError.value = null
  recTip.value = null
  recExhausted.value = readExhausted(localTodayYmd(), code)
  if (recExhausted.value) recTip.value = exhaustedMessage(code)
}

function onRecPeriodSelect(code: PeriodCode) {
  if (recPeriod.value === code) return
  saveRecState(recPeriod.value)
  recPeriod.value = code
  recReplacing.value = {}
  restoreRecState(code)
}

function inferPeriodFromClock(): PeriodCode {
  const h = new Date().getHours()
  if (h >= 5 && h < 10) return 'breakfast'
  if (h >= 11 && h < 14) return 'lunch'
  if (h >= 17 && h < 21) return 'dinner'
  if (h < 5) return 'dinner'
  if (h < 11) return 'breakfast'
  if (h < 17) return 'lunch'
  return 'dinner'
}

/**
 * 当前选中餐段是否已结束（本地时间，与 inferPeriodFromClock 窗口一致）。
 * 早餐 10:00 后、午餐 14:00 后、晚餐 21:00 后结束；凌晨 0–4 点视为晚餐已结束。
 */
function isMealPeriodEnded(code: PeriodCode, at = new Date()): boolean {
  const h = at.getHours()
  const mins = h * 60 + at.getMinutes()
  if (code === 'breakfast') return mins >= 10 * 60
  if (code === 'lunch') return mins >= 14 * 60
  if (code === 'dinner') return mins >= 21 * 60 || h < 5
  return false
}

/** 每分钟刷新，便于到点后「已完成 / 准备中」自动切换 */
const mealClockTick = ref(0)
let mealClockTimer: ReturnType<typeof setInterval> | undefined

async function fetchRandomRecommendationsForCurrentPeriod() {
  if (!recPeriod.value) return
  shuffling.value = true
  boardKey.value += 1
  recError.value = null
  recTip.value = null
  recExhausted.value = false
  try {
    const code = recPeriod.value
    const excludeIds = excludeIdsForRequest(code)
    const excludeQs = excludeIds.length ? `&exclude=${encodeURIComponent(excludeIds.join(','))}` : ''
    const res = await apiGet<{ meal_period_code: string; items: RecDish[] }>(
      `/recommendations/random-by-period?code=${encodeURIComponent(code)}&count=5${excludeQs}`,
    )
    recList.value = res.items ?? []
    pushRecent(code, recList.value.map((d) => d.dish_id))
    if (!recList.value.length && excludeIds.length) {
      recTip.value = '已为你避开近期推荐/忽略的菜，当前可选较少'
    }
    if (recList.value.length) recStarted.value = true
    saveRecState(code)
  } catch (e) {
    recList.value = []
    recError.value = getApiErrorMessage(e, '推荐加载失败', {
      DISHES_ACCESS_DENIED: '访问授权已失效，请重新验证',
    })
    saveRecState(recPeriod.value)
  } finally {
    window.setTimeout(() => {
      shuffling.value = false
    }, 320)
  }
}

function onStartRecommend() {
  void fetchRandomRecommendationsForCurrentPeriod()
}

async function replaceDish(dishId: number) {
  const code = recPeriod.value
  if (!code) return
  recReplacing.value = { ...recReplacing.value, [dishId]: true }
  try {
    addIgnored(dishId)
    pushRecent(code, [dishId])

    const exclude = new Set<number>([
      ...excludeIdsForRequest(code),
      ...recList.value.map((d) => d.dish_id),
      dishId,
    ])
    const excludeQs = exclude.size ? `&exclude=${encodeURIComponent([...exclude].join(','))}` : ''
    const res = await apiGet<{ meal_period_code: string; items: RecDish[] }>(
      `/recommendations/random-by-period?code=${encodeURIComponent(code)}&count=1${excludeQs}`,
    )
    const next = res.items?.[0]
    if (!next) {
      recList.value = recList.value.filter((d) => d.dish_id !== dishId)
      saveRecState(code)
      return
    }
    const idx = recList.value.findIndex((d) => d.dish_id === dishId)
    if (idx >= 0) {
      const list = [...recList.value]
      list[idx] = next
      recList.value = list
      pushRecent(code, [next.dish_id])
    }
    saveRecState(code)
  } catch (e) {
    recError.value = getApiErrorMessage(e, '替换失败', {
      DISHES_ACCESS_DENIED: '访问授权已失效，请重新验证',
    })
    saveRecState(code)
  } finally {
    const { [dishId]: _, ...rest } = recReplacing.value
    recReplacing.value = rest
  }
}

function onPeriodSelect(code: PeriodCode) {
  if (selectedPeriod.value === code) return
  selectedPeriod.value = code
  recReplacing.value = {}
}

const clockPeriod = computed((): PeriodCode => inferPeriodFromClock())

/** 当前选中的餐段是否已过结束时间（用于列表角标） */
const mealPeriodEndedForSelected = computed(() => {
  void mealClockTick.value
  return isMealPeriodEnded(selectedPeriod.value)
})

/** 首页「今日」餐段滑块：与点菜页 period-seg 同源思路，支持 API 返回的餐段数量 */
const todayPeriodActiveIndex = computed(() => {
  if (!today.value?.periods.length) return 0
  const i = today.value.periods.findIndex((p) => p.meal_period.code === selectedPeriod.value)
  return i >= 0 ? i : 0
})

const todayPeriodSegmentCount = computed(() =>
  Math.max(1, today.value?.periods.length ?? 1),
)

function orderForPeriod(code: string): TodayPeriodRow | undefined {
  return today.value?.periods.find((p) => p.meal_period.code === code)
}

const selectedPeriodRow = computed(() => orderForPeriod(selectedPeriod.value))

const selectedOrderItems = computed(() => selectedPeriodRow.value?.order?.items ?? [])

const todayDateLabel = computed(() => {
  const d = today.value?.date
  if (!d) return ''
  const parts = d.split('-')
  const m = parts[1]
  const day = parts[2]
  if (!m || !day) return d
  return `${parseInt(m, 10)}月${parseInt(day, 10)}日`
})

function localTodayYmd(): string {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function addCalendarDaysYmd(ymd: string, deltaDays: number): string {
  const [y, mo, d] = ymd.split('-').map(Number)
  const dt = new Date(y, mo - 1, d)
  dt.setDate(dt.getDate() + deltaDays)
  const yy = dt.getFullYear()
  const mm = String(dt.getMonth() + 1).padStart(2, '0')
  const dd = String(dt.getDate()).padStart(2, '0')
  return `${yy}-${mm}-${dd}`
}

function formatReserveDayLabel(ymd: string): string {
  const parts = ymd.split('-')
  const mo = parts[1]
  const day = parts[2]
  if (!mo || !day) return ymd
  return `${parseInt(mo, 10)}月${parseInt(day, 10)}日`
}

function reserveRelativeLabel(ymd: string): string {
  const [y, m, d] = ymd.split('-').map(Number)
  const [ty, tm, td] = localTodayYmd().split('-').map(Number)
  const t0 = Date.UTC(ty, tm - 1, td)
  const t1 = Date.UTC(y, m - 1, d)
  const n = Math.round((t1 - t0) / 86400000)
  if (n === 1) return '明天'
  if (n === 2) return '后天'
  return `还有 ${n} 天`
}

function mergeReservationLinesForDate(rows: HistoryOrderRow[]): ReservationFlatLine[] {
  const map = new Map<number, ReservationFlatLine>()
  for (const row of rows) {
    const o = row.order
    if (!o?.items?.length) continue
    for (const it of o.items) {
      const id = it.dish_id
      const qty = Number(it.quantity)
      const note = it.note != null && String(it.note).trim() !== '' ? String(it.note).trim() : null
      const cur = map.get(id)
      if (!cur) {
        map.set(id, {
          dish_id: id,
          name: it.dish.name,
          image_url: it.dish.image_url,
          qty,
          note,
        })
      } else {
        cur.qty += qty
        if (note) {
          cur.note = cur.note ? `${cur.note}；${note}` : note
        }
      }
    }
  }
  return [...map.values()].sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))
}

const reservationGroups = ref<ReservationDateGroup[]>([])
const reservationsLoading = ref(false)
const reservationsFetchFailed = ref(false)
/** 已展开的预约日期（默认全部折叠） */
const expandedReservationDates = ref<string[]>([])

function isReserveDateExpanded(date: string) {
  return expandedReservationDates.value.includes(date)
}

function toggleReserveDate(date: string) {
  const cur = expandedReservationDates.value
  if (cur.includes(date)) {
    expandedReservationDates.value = cur.filter((d) => d !== date)
  } else {
    expandedReservationDates.value = [...cur, date]
  }
}

async function fetchReservations() {
  reservationsLoading.value = true
  reservationsFetchFailed.value = false
  try {
    const todayStr = localTodayYmd()
    const fromStr = addCalendarDaysYmd(todayStr, 1)
    const toStr = addCalendarDaysYmd(todayStr, 120)
    const q = `/meal-orders/history?from=${encodeURIComponent(fromStr)}&to=${encodeURIComponent(toStr)}&limit=100&offset=0`
    const data = await apiGet<HistoryListData>(q)
    const byDate = new Map<string, HistoryOrderRow[]>()
    for (const it of data.items) {
      if (it.order_date <= todayStr) continue
      if (!it.order?.items?.length) continue
      const list = byDate.get(it.order_date) ?? []
      list.push(it)
      byDate.set(it.order_date, list)
    }
    const dates = [...byDate.keys()].sort()
    reservationGroups.value = dates.map((date) => ({
      date,
      label: formatReserveDayLabel(date),
      relativeLabel: reserveRelativeLabel(date),
      lines: mergeReservationLinesForDate(byDate.get(date)!),
    }))
  } catch {
    reservationGroups.value = []
    reservationsFetchFailed.value = true
  } finally {
    reservationsLoading.value = false
  }
}

const hasReservationContent = computed(() => reservationGroups.value.length > 0)

async function load() {
  loading.value = true
  error.value = null
  void fetchReservations()
  try {
    const [t] = await Promise.all([apiGet<TodayData>('/meal-orders/today')])
    today.value = t
    const initial = inferPeriodFromClock()
    selectedPeriod.value = initial
    recPeriod.value = initial
    restoreRecState(initial)
  } catch (e) {
    error.value = getApiErrorMessage(e, '加载失败', {
      DISHES_ACCESS_DENIED: '需要先完成访问验证',
    })
    today.value = null
    recList.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  mealClockTimer = window.setInterval(() => {
    mealClockTick.value++
  }, 60_000)
  void load()
})

onUnmounted(() => {
  if (mealClockTimer !== undefined) window.clearInterval(mealClockTimer)
})
</script>

<template>
  <div
    class="home-page -mx-4 flex min-h-0 flex-1 flex-col bg-slate-50 pb-1 pt-0 text-[15px] leading-normal text-slate-900 antialiased"
  >
    <header
      class="home-header sticky top-0 z-10 shrink-0 bg-slate-50 supports-[backdrop-filter]:backdrop-blur-sm"
    >
      <div
        class="ls-brand"
      >
        <img
          :src="headerAvatarUrl"
          alt=""
          width="40"
          height="40"
          decoding="async"
          class="ls-brand__logo"
        />

        <div class="min-w-0 flex-1">
          <p class="ls-brand__kicker">LEMONSLICE CLOUD</p>
          <h1>
            {{ publicBrandTitle }}
          </h1>
          <p class="ls-brand__subtitle">
            {{ publicBrandSubtitle }}
          </p>
        </div>
      </div>
    </header>

    <div
      class="home-body flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto px-4 pt-4 [-webkit-overflow-scrolling:touch]"
    >
      <!-- 错误 -->
      <div
        v-if="error"
        class="home-card home-card--bare border-red-100 bg-white p-4"
      >
        <p class="text-sm text-red-600">
          {{ error }}
        </p>
        <button
          type="button"
          class="home-btn home-btn--primary mt-4"
          @click="load"
        >
          重试
        </button>
      </div>

      <!-- 骨架 -->
      <div
        v-if="loading"
        class="home-skeleton flex flex-col gap-4"
        aria-busy="true"
      >
        <div class="home-card overflow-hidden p-4">
          <div class="mb-4 flex items-end justify-between gap-4">
            <div class="h-6 w-24 animate-pulse rounded-md bg-slate-100" />
            <div class="h-5 w-16 animate-pulse rounded bg-slate-100" />
          </div>
          <div class="h-12 animate-pulse rounded-xl bg-slate-100" />
          <div class="mt-4 space-y-2">
            <div
              v-for="n in 3"
              :key="n"
              class="flex gap-4 rounded-lg border border-slate-100 p-2"
            >
              <div class="h-12 w-12 shrink-0 animate-pulse rounded-md bg-slate-100" />
              <div class="min-w-0 flex-1 space-y-2 py-1">
                <div class="h-3.5 w-3/4 animate-pulse rounded bg-slate-100" />
                <div class="h-3 w-1/3 animate-pulse rounded bg-slate-100" />
              </div>
            </div>
          </div>
        </div>
        <div class="home-card p-4">
          <div class="mb-4 flex justify-between gap-4">
            <div class="h-5 w-28 animate-pulse rounded bg-slate-100" />
            <div class="h-8 w-16 animate-pulse rounded-lg bg-slate-100" />
          </div>
          <div class="flex min-h-[12rem] gap-0 overflow-hidden rounded-xl border border-slate-100">
            <div class="w-14 shrink-0 animate-pulse bg-slate-50" />
            <div class="min-w-0 flex-1 animate-pulse bg-white" />
          </div>
        </div>
        <div class="home-card p-4">
          <div class="mb-4 h-4 w-20 animate-pulse rounded bg-slate-100" />
          <div class="space-y-2">
            <div class="h-12 animate-pulse rounded-xl bg-slate-100" />
            <div class="h-12 animate-pulse rounded-xl bg-slate-100" />
          </div>
        </div>
      </div>

      <template v-else-if="today">
        <!-- 今日点餐 -->
        <section class="home-card home-card--today overflow-hidden">
          <div class="home-card__head px-4 pb-1 pt-4">
            <div class="flex items-end justify-between gap-4">
              <div>
                <p class="home-eyebrow">
                  今日餐品
                </p>
                <h2 class="home-card__title">
                  用餐进度
                </h2>
              </div>
              <time
                v-if="todayDateLabel"
                class="home-date-pill tabular-nums"
                :datetime="today.date"
              >
                {{ todayDateLabel }}
              </time>
            </div>
          </div>

          <div class="px-4 pb-4 pt-1">
            <div
              class="home-period-seg relative rounded-xl bg-slate-100/95 p-1"
              role="tablist"
              aria-label="餐段"
              :style="{
                '--period-i': todayPeriodActiveIndex,
                '--seg-n': todayPeriodSegmentCount,
              }"
            >
              <div
                class="home-period-seg__glider pointer-events-none absolute z-0 rounded-lg bg-white shadow-[0_1px_2px_rgba(15,23,42,0.05)] ring-1 ring-slate-200/70"
                aria-hidden="true"
              />
              <div class="relative z-[1] flex gap-2">
                <button
                  v-for="row in today.periods"
                  :key="row.meal_period.code"
                  type="button"
                  role="tab"
                  :aria-selected="selectedPeriod === row.meal_period.code"
                  class="home-period-seg__btn relative min-h-[2.5rem] min-w-0 flex-1 rounded-lg py-2 text-center transition-[color,transform] duration-300 ease-[cubic-bezier(0.25,0.46,0.45,0.94)] motion-safe:active:scale-[0.98]"
                  :class="
                    selectedPeriod === row.meal_period.code
                      ? 'text-blue-700'
                      : 'text-slate-500 motion-safe:hover:text-slate-700'
                  "
                  @click="onPeriodSelect(row.meal_period.code as PeriodCode)"
                >
                  <span class="relative z-[1] block text-sm font-medium leading-tight">
                    {{ row.meal_period.name }}
                  </span>
                  <span
                    class="relative z-[1] mt-1 block text-[10px] leading-tight text-slate-400"
                  >
                    <template v-if="clockPeriod === row.meal_period.code">此刻 · </template>
                    {{ row.order ? `已点 ${row.order.item_count} 样` : '未点' }}
                  </span>
                </button>
              </div>
            </div>
          </div>

          <div
            class="mx-4 border-t border-slate-100"
            aria-hidden="true"
          />

          <div class="px-4 pb-4 pt-4 sm:px-4">
            <Transition name="home-period-swap" mode="out-in">
              <div :key="selectedPeriod" class="min-h-[3rem]">
                <ul
                  v-if="selectedOrderItems.length"
                  class="flex flex-col gap-2"
                  role="list"
                >
                  <li
                    v-for="it in selectedOrderItems"
                    :key="it.line_id"
                  >
                    <div
                      class="home-order-row relative min-h-[3.75rem] rounded-lg border border-slate-100 bg-white py-2 pl-2 pr-[6rem] shadow-[0_1px_2px_rgba(15,23,42,0.04)] transition-[border-color,box-shadow] duration-200 ease-out hover:border-slate-200 hover:shadow-[0_2px_8px_-2px_rgba(15,23,42,0.06)]"
                    >
                      <span
                        class="home-meal-pill"
                        :class="
                          mealPeriodEndedForSelected
                            ? 'home-meal-pill--done'
                            : 'home-meal-pill--prep'
                        "
                      >
                        {{ mealPeriodEndedForSelected ? '已流转至胃部' : '食材准备中…' }}
                      </span>
                      <div class="flex min-w-0 items-center gap-4">
                        <div
                          class="home-thumb h-12 w-12 shrink-0 overflow-hidden rounded-md bg-slate-100 ring-1 ring-slate-200/60"
                        >
                          <img
                            v-if="it.dish.image_url"
                            :src="resolveMediaUrl(it.dish.image_url)"
                            :alt="it.dish.name"
                            class="h-full w-full object-cover"
                            loading="lazy"
                            decoding="async"
                          />
                        </div>
                        <p class="min-w-0 flex-1 truncate pb-1 pt-1 text-[15px] font-medium leading-snug text-slate-900">
                          {{ it.dish.name }}
                        </p>
                      </div>
                      <span class="home-order-qty">
                        ×{{ it.quantity }}
                      </span>
                    </div>
                  </li>
                </ul>
                <div
                  v-else
                  class="flex flex-col items-center justify-center rounded-lg border border-dashed border-slate-200/90 bg-slate-50/50 py-8 text-center"
                >
                  <p class="text-sm text-slate-500">
                    本餐还没有选菜
                  </p>
                  <p class="mt-1 max-w-[14rem] text-xs leading-relaxed text-slate-400">
                    去点单页挑几样，会显示在这里
                  </p>
                </div>
              </div>
            </Transition>
          </div>
        </section>

        <!-- 今日推荐 -->
        <section class="home-card home-card--rec overflow-hidden">
          <div class="flex items-start justify-between gap-4 px-4 pb-2 pt-4">
            <div class="min-w-0">
              <p class="home-eyebrow">
                厨师长精选
              </p>
              <div class="flex items-center gap-2">
                <h2 class="home-card__title">
                  今日推荐
                </h2>
              </div>
              <p class="mt-1 text-xs leading-relaxed text-slate-500">
                不知道吃什么？试试这个！
              </p>
            </div>
            <div
              class="mt-1 inline-flex shrink-0 items-center rounded-full bg-slate-100 p-1 text-[11px] text-slate-600 ring-1 ring-slate-200/70"
              role="tablist"
              aria-label="推荐餐段"
            >
              <button
                type="button"
                class="rounded-full px-2 py-1 font-medium transition-colors"
                :class="recPeriod === 'breakfast' ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-600 hover:text-slate-900'"
                @click="onRecPeriodSelect('breakfast')"
              >
                早餐
              </button>
              <button
                type="button"
                class="rounded-full px-2 py-1 font-medium transition-colors"
                :class="recPeriod === 'lunch' ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-600 hover:text-slate-900'"
                @click="onRecPeriodSelect('lunch')"
              >
                午餐
              </button>
              <button
                type="button"
                class="rounded-full px-2 py-1 font-medium transition-colors"
                :class="recPeriod === 'dinner' ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-600 hover:text-slate-900'"
                @click="onRecPeriodSelect('dinner')"
              >
                晚餐
              </button>
            </div>
          </div>

          <div class="px-4 pb-4 pt-0 sm:px-4">
            <Transition name="home-board-swap" mode="out-in">
              <div :key="boardKey" class="relative">
                <template v-if="!recStarted && !loading && !recExhausted">
                  <div
                    class="min-h-[2rem] rounded-xl  bg-white p-4"
                    :aria-busy="shuffling"
                  >
                    <template v-if="shuffling">
                      <div class="mb-4 flex items-center justify-between gap-4">
                        <div class="h-4 w-28 animate-pulse rounded bg-slate-100" />
                        <div class="h-4 w-16 animate-pulse rounded bg-slate-100" />
                      </div>
                      <ul class="grid grid-cols-1 gap-2 sm:grid-cols-2" role="list" aria-label="推荐加载中">
                        <li
                          v-for="n in 5"
                          :key="n"
                          class="flex items-center gap-4 rounded-xl border border-slate-100 bg-slate-50/40 p-2"
                        >
                          <div class="h-12 w-11 shrink-0 animate-pulse rounded-md bg-slate-100 ring-1 ring-slate-200/60" />
                          <div class="min-w-0 flex-1 space-y-2 py-1">
                            <div class="h-3.5 w-3/4 animate-pulse rounded bg-slate-100" />
                            <div class="h-3 w-1/3 animate-pulse rounded bg-slate-100" />
                          </div>
                        </li>
                      </ul>
                    </template>
                    <template v-else>
                      <div class="flex h-full flex-col items-center justify-center gap-2">
                        <p class="text-center text-sm text-slate-500">
                          为你推荐 5 道适合当前餐段的菜
                        </p>
                        <button
                          type="button"
                          class="home-btn home-btn--primary"
                          @click="onStartRecommend"
                        >
                          开始推荐
                        </button>
                      </div>
                    </template>
                  </div>
                </template>

                <template v-else-if="!recList.length && !loading">
                  <p v-if="recError" class="py-8 text-center text-sm text-red-600">
                    {{ recError }}
                  </p>
                  <p v-else-if="recTip" class="py-8 text-center text-sm text-slate-500">
                    {{ recTip }}
                  </p>
                  <p v-else class="home-muted py-8 text-center text-sm">
                    暂无推荐数据
                  </p>
                  
                </template>
                <template v-else>
                  <div
                    class="home-rec-list min-h-[12rem] w-full items-stretch overflow-hidden rounded-xl border border-slate-200/80 bg-white p-2"
                  >
                    <div class="rounded-lg p-1">
                      <TransitionGroup
                        v-if="recList.length"
                        name="home-rec-cards"
                        tag="ul"
                        class="grid grid-cols-1 gap-2 sm:grid-cols-2"
                        role="list"
                      >
                        <li
                          v-for="d in recList"
                          :key="d.dish_id"
                          class="group rounded-xl border border-slate-100 bg-slate-50/40 p-2 transition-[border-color,background-color,transform] duration-200 ease-out hover:border-slate-200 hover:bg-white motion-safe:hover:-translate-y-[1px]"
                        >
                          <div class="flex items-center gap-4">
                            <div
                              class="home-thumb h-12 w-11 shrink-0 overflow-hidden rounded-md bg-slate-100 ring-1 ring-slate-200/60"
                            >
                              <img
                                v-if="d.image_url"
                                :src="resolveMediaUrl(d.image_url)"
                                :alt="d.name"
                                class="h-full w-full object-cover"
                                loading="lazy"
                                decoding="async"
                              />
                            </div>
                            <div class="min-w-0 flex-1 py-1">
                              <div class="flex items-start justify-between gap-2">
                                <p class="min-w-0 truncate text-[15px] font-medium leading-tight text-slate-900">
                                  {{ d.name }}
                                </p>
                                <p
                                  class="shrink-0 text-[10px] leading-none tracking-wide text-amber-500"
                                  :aria-label="`推荐 ${dishRecommendationLevel(d)} 级`"
                                >
                                  {{ stars(dishRecommendationLevel(d)) }}
                                </p>
                              </div>
                              <div class="mt-1 sm:mt-0">
                                <div class="relative">
                                  <p
                                    v-if="d.category_name"
                                    class="min-w-0 truncate pr-10 text-xs leading-tight text-slate-500"
                                  >
                                    {{ d.category_name }}
                                  </p>
                                  <button
                                    type="button"
                                    class="absolute right-0 top-1/2 inline-flex h-10 w-10 -translate-y-1/2 items-center justify-center rounded-full bg-transparent p-0 text-slate-500 transition-opacity hover:opacity-80 disabled:cursor-not-allowed disabled:opacity-40"
                                    :disabled="shuffling || recReplacing[d.dish_id]"
                                    :aria-label="recReplacing[d.dish_id] ? '替换中' : '不喜欢，换一道'"
                                    @click="replaceDish(d.dish_id)"
                                  >
                                    <svg
                                      v-if="!recReplacing[d.dish_id]"
                                      class="block h-4 w-4"
                                      viewBox="0 0 1024 1024"
                                      version="1.1"
                                      xmlns="http://www.w3.org/2000/svg"
                                      aria-hidden="true"
                                      focusable="false"
                                    >
                                      <path
                                        d="M119.808 59.434667l784.384 784.426666-60.330667 60.330667-159.829333-159.872L512 916.693333l-361.728-362.325333a256 256 0 0 1 1.408-342.314667L59.477333 119.808l60.330667-60.373333z m92.672 436.48L512 795.904l111.701333-111.914667-411.562666-411.52a170.666667 170.666667 0 0 0 0.341333 223.445334z m651.221333-292.949334a256 256 0 0 1 10.069334 351.402667l-69.76 69.802667-60.330667-60.330667 67.84-67.925333a170.666667 170.666667 0 0 0-242.474667-239.189334l-56.96 51.114667-57.002666-51.072a169.557333 169.557333 0 0 0-49.28-30.848l-96-96A255.573333 255.573333 0 0 1 512 193.28a255.914667 255.914667 0 0 1 351.658667 9.728z"
                                        fill="#dd6572"
                                      />
                                    </svg>
                                    <span
                                      v-else
                                      class="text-xs font-semibold text-slate-500"
                                      aria-hidden="true"
                                    >
                                      …
                                    </span>
                                  </button>
                                </div>
                              </div>
                            </div>
                          </div>

                        </li>
                      </TransitionGroup>
                      <p
                        v-else
                        class="home-muted py-10 text-center text-sm"
                      >
                        该餐段暂无推荐
                      </p>
                    </div>
                  </div>
                </template>
              </div>
            </Transition>
          </div>
        </section>

        <!-- 预约模块：按日期折叠，合并三餐菜品 -->
        <section class="home-card home-card--reserve overflow-hidden">
          <div class="home-card__head px-4 pb-2 pt-4">
            <p class="home-eyebrow">
              预约
            </p>
            <h2 class="home-card__title">
              未来用餐
            </h2>
            <p class="mt-1 text-xs leading-relaxed text-slate-500">
              已点菜、用餐日晚于今天的菜品
            </p>
          </div>

          <div class="px-4 pb-4 pt-0 sm:px-4">
            <div
              v-if="reservationsLoading"
              class="space-y-2"
              aria-busy="true"
            >
              <div
                v-for="n in 2"
                :key="n"
                class="h-12 animate-pulse rounded-xl bg-slate-100"
              />
            </div>

            <p
              v-else-if="reservationsFetchFailed"
              class="rounded-xl border border-dashed border-slate-200 bg-slate-50/60 py-6 text-center text-xs text-slate-500"
            >
              预约列表加载失败
              <button
                type="button"
                class="mt-2 block w-full text-sm font-medium text-blue-600"
                @click="fetchReservations"
              >
                点击重试
              </button>
            </p>

            <div
              v-else-if="!hasReservationContent"
              class="flex flex-col items-center justify-center rounded-xl border border-dashed border-slate-200/90 bg-slate-50/40 py-8 text-center"
            >
              <p class="text-sm text-slate-600">
                暂无预约
              </p>
             
              <button
                type="button"
                class="home-btn home-btn--ghost mt-2"
                @click="router.push('/order')"
              >
                去预约点菜
              </button>
            </div>

            <ul
              v-else
              class="flex flex-col gap-2"
              role="list"
            >
              <li
                v-for="g in reservationGroups"
                :key="g.date"
                class="reserve-acc shrink-0 overflow-hidden rounded-xl border border-slate-100 bg-slate-50/50 shadow-[0_1px_2px_rgba(15,23,42,0.03)]"
              >
                <button
                  type="button"
                  class="reserve-acc__trigger flex w-full items-center gap-4 px-4 py-4 text-left transition-colors hover:bg-white/80"
                  :aria-expanded="isReserveDateExpanded(g.date)"
                  @click="toggleReserveDate(g.date)"
                >
                  <div class="flex min-w-0 flex-1 flex-col gap-1">
                    <div class="flex flex-wrap items-baseline gap-x-2 gap-y-1">
                      <span class="text-[15px] font-semibold tracking-tight text-slate-900">
                        {{ g.label }}
                      </span>
                      <span class="text-[11px] font-medium text-blue-600/90">
                        {{ g.relativeLabel }}
                      </span>
                    </div>
                    <span class="text-[11px] text-slate-500">
                      {{ g.lines.length }} 道菜 · 点击展开
                    </span>
                  </div>
                  <span
                    class="reserve-acc__badge shrink-0 rounded-full bg-white px-2 py-1 text-[11px] font-semibold tabular-nums text-slate-600 ring-1 ring-slate-200/80"
                  >
                    {{ g.lines.length }}
                  </span>
                  <svg
                    class="reserve-acc__chev h-5 w-5 shrink-0 text-slate-400"
                    :class="{ 'reserve-acc__chev--open': isReserveDateExpanded(g.date) }"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    aria-hidden="true"
                  >
                    <path d="m6 9 6 6 6-6" stroke-linecap="round" stroke-linejoin="round" />
                  </svg>
                </button>
                <div
                  class="reserve-acc__panel"
                  :class="{ 'reserve-acc__panel--open': isReserveDateExpanded(g.date) }"
                >
                  <div class="reserve-acc__inner border-t border-slate-100/90 bg-white/90">
                    <ul class="flex flex-col gap-2 p-2" role="list">
                      <li
                        v-for="line in g.lines"
                        :key="`${g.date}-${line.dish_id}`"
                      >
                        <div
                          class="flex items-center gap-4 rounded-lg border border-slate-100/90 bg-slate-50/30 px-2 py-2"
                        >
                          <div
                            class="home-thumb h-10 w-10 shrink-0 overflow-hidden rounded-md bg-slate-100 ring-1 ring-slate-200/60"
                          >
                            <img
                              v-if="line.image_url"
                              :src="resolveMediaUrl(line.image_url)"
                              :alt="line.name"
                              class="h-full w-full object-cover"
                              loading="lazy"
                              decoding="async"
                            />
                          </div>
                          <div class="min-w-0 flex-1">
                            <p class="truncate text-sm font-medium text-slate-900">
                              {{ line.name }}
                            </p>
                            <p
                              v-if="line.note"
                              class="mt-1 line-clamp-2 text-[11px] leading-snug text-slate-500"
                            >
                              {{ line.note }}
                            </p>
                          </div>
                          <span
                            class="shrink-0 rounded-md bg-white px-2 py-1 text-[11px] font-bold tabular-nums text-slate-600 ring-1 ring-slate-200/70"
                          >
                            ×{{ line.qty }}
                          </span>
                        </div>
                      </li>
                    </ul>
                  </div>
                </div>
              </li>
            </ul>
          </div>
        </section>

        
      </template>
      <footer class="ls-source-credit">
        LemonSlice edition · 基于
        <a href="https://github.com/Avrinbai/halo-plugin-dishes" target="_blank" rel="noopener noreferrer">Dishes</a>
        · <a href="https://github.com/RoShioLeo/halo-plugin-dishes" target="_blank" rel="noopener noreferrer">源码 · GPL-3.0</a>
      </footer>
    </div>

    <HomeOrderFab />
  </div>
</template>

<style scoped>
/* —— 布局与卡片 —— */
/*
 * home-body 为纵向 flex 时，子项默认 flex-shrink:1；再叠加 overflow-hidden 的卡片，
 * 在部分引擎下最小高度可被压成 0，出现「展开被裁切、下方块不顶下去」的错觉。
 */
.home-body > * {
  flex-shrink: 0;
}

.home-skeleton > * {
  flex-shrink: 0;
}

.home-body {
  scrollbar-width: none;
  -ms-overflow-style: none;
  /* 悬浮「点菜」球由 HomeOrderFab 写入 --home-order-fab-h */
  padding-bottom: calc(var(--home-order-fab-h, 4.5rem) + 0.75rem);
}

.home-body::-webkit-scrollbar {
  display: none;
  width: 0;
  height: 0;
}

/* —— 推荐卡片动画 —— */
.home-rec-cards-enter-active,
.home-rec-cards-leave-active {
  transition:
    opacity 220ms ease,
    transform 240ms cubic-bezier(0.2, 0.9, 0.2, 1);
}

.home-rec-cards-enter-from,
.home-rec-cards-leave-to {
  opacity: 0;
  transform: translateY(6px);
}

.home-rec-cards-move {
  transition: transform 240ms cubic-bezier(0.2, 0.9, 0.2, 1);
}

.home-card {
  border-radius: 1rem;
  border: 1px solid var(--ls-line);
  background: var(--ls-paper);
  box-shadow: 0 1px 2px rgb(15 23 42 / 0.04);
}

.home-card--bare {
  box-shadow: 0 1px 2px rgb(15 23 42 / 0.04);
}

.home-card__title {
  margin-top: 0.125rem;
  font-size: 1.125rem;
  font-weight: 600;
  letter-spacing: -0.02em;
  color: var(--ls-ink);
}

.home-eyebrow {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--ls-faint);
}

.home-date-pill {
  border-radius: 0.5rem;
  border: 1px solid var(--ls-line);
  background: var(--ls-soft);
  padding: 0.25rem 0.5rem;
  font-size: 12px;
  font-weight: 500;
  color: var(--ls-muted);
}

.home-muted {
  color: var(--ls-faint);
}

/* 今日已选：餐段状态胶囊（右上）+ 份数角标（右下） */
.home-order-row {
  isolation: isolate;
}

.home-meal-pill {
  position: absolute;
  top: 0.375rem;
  right: 0.375rem;
  z-index: 1;
  max-width: calc(100% - 5rem);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  border-radius: 9999px;
  padding: 0.125rem 0.4375rem;
  font-size: 10px;
  font-weight: 600;
  line-height: 1.35;
  letter-spacing: 0.02em;
}

.home-meal-pill--prep {
  border: 1px solid var(--ls-line);
  background: var(--ls-soft);
  color: var(--ls-muted);
}

.home-meal-pill--done {
  border: 1px solid var(--ls-success-line);
  background: var(--ls-success-soft);
  color: var(--ls-success);
}

.home-order-qty {
  position: absolute;
  right: 0.375rem;
  bottom: 0.375rem;
  z-index: 1;
  min-width: 1.125rem;
  padding: 0.125rem 0.3125rem;
  border-radius: 0.25rem;
  font-size: 10px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
  color: var(--ls-muted);
  background: var(--ls-hover);
}

/* —— 按钮 —— */
.home-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 0.5rem;
  font-size: 13px;
  font-weight: 500;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease,
    opacity 0.2s ease,
    transform 0.2s ease;
}

.home-btn:focus-visible {
  outline: 2px solid var(--ls-accent-line);
  outline-offset: 2px;
}

.home-btn--primary {
  min-height: 2.5rem;
  padding: 0 1rem;
  background: var(--ls-action);
  color: #fff;
}

.home-btn--primary:hover {
  background: var(--ls-action-hover);
}

.home-btn--primary:active {
  transform: scale(0.98);
}

.home-btn--ghost {
  min-height: 2rem;
  padding: 0.375rem 0.75rem;
  border: 1px solid var(--ls-line);
  background: var(--ls-paper);
  color: var(--ls-ink);
}

.home-btn--ghost:hover:not(:disabled) {
  border-color: var(--ls-input);
  background: var(--ls-soft);
}

.home-btn--ghost:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.home-btn--busy {
  pointer-events: none;
  opacity: 0.55;
}

/* —— 今日 · 餐段分段（--period-i / --seg-n 由内联注入） —— */
.home-period-seg {
  --seg-pad: 0.25rem;
  --seg-gap: 0.5rem;
  --period-i: 0;
  --seg-n: 3;
}

.home-period-seg__glider {
  top: var(--seg-pad);
  bottom: var(--seg-pad);
  width: calc(
    (100% - 2 * var(--seg-pad) - (var(--seg-n) - 1) * var(--seg-gap)) / var(--seg-n)
  );
  left: calc(
    var(--seg-pad) +
      var(--period-i) *
        (
          (100% - 2 * var(--seg-pad) - (var(--seg-n) - 1) * var(--seg-gap)) / var(--seg-n) +
            var(--seg-gap)
        )
  );
  transition: left 0.32s cubic-bezier(0.25, 0.46, 0.45, 0.94);
}

.home-period-seg__btn:focus-visible {
  outline: 2px solid var(--ls-accent-line);
  outline-offset: 1px;
  border-radius: 0.5rem;
}

.home-rec-list {
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.home-rec-list::-webkit-scrollbar {
  display: none;
  width: 0;
  height: 0;
}

/* —— 过渡：轻、短 —— */
.home-period-swap-enter-active,
.home-period-swap-leave-active {
  transition:
    opacity 0.22s cubic-bezier(0.25, 0.46, 0.45, 0.94),
    transform 0.24s cubic-bezier(0.25, 0.46, 0.45, 0.94);
}

.home-period-swap-enter-from,
.home-period-swap-leave-to {
  opacity: 0;
  transform: translate3d(0, 0.35rem, 0);
}

.home-board-swap-enter-active,
.home-board-swap-leave-active {
  transition: opacity 0.2s ease;
}

.home-board-swap-enter-from,
.home-board-swap-leave-to {
  opacity: 0;
}

/* —— 预约：手风琴展开（grid 行高过渡） —— */
.reserve-acc__panel {
  display: grid;
  grid-template-rows: 0fr;
  transition: grid-template-rows 0.34s cubic-bezier(0.25, 0.46, 0.45, 0.94);
}

.reserve-acc__panel--open {
  grid-template-rows: 1fr;
}

.reserve-acc__inner {
  min-height: 0;
  overflow: hidden;
}

.reserve-acc__panel--open .reserve-acc__inner {
  /* 展开后允许子项自然增高，避免底部一行/图片被裁切 */
  overflow: visible;
}

.reserve-acc__chev {
  transition: transform 0.32s cubic-bezier(0.25, 0.46, 0.45, 0.94);
}

.reserve-acc__chev--open {
  transform: rotate(180deg);
}

@media (prefers-reduced-motion: reduce) {
  .home-period-swap-enter-active,
  .home-period-swap-leave-active,
  .home-board-swap-enter-active,
  .home-board-swap-leave-active,
  .home-rec-cards-enter-active,
  .home-rec-cards-leave-active {
    transition-duration: 0.01ms;
  }

  .home-period-swap-enter-from,
  .home-period-swap-leave-to,
  .home-rec-cards-enter-from,
  .home-rec-cards-leave-to {
    transform: none;
  }

  .home-rec-cards-move {
    transition-duration: 0.01ms;
  }

  .home-period-seg__glider {
    transition-duration: 0.01ms;
  }

  .reserve-acc__panel {
    transition-duration: 0.01ms;
  }

  .reserve-acc__chev {
    transition-duration: 0.01ms;
  }

  .reserve-acc__chev--open {
    transform: rotate(180deg);
  }
}
</style>
