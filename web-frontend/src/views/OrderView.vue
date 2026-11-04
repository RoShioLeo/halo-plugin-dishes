<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { apiGet, apiPost, getApiErrorMessage } from '@/api/http'
import { resolveMediaUrl } from '@/utils/mediaUrl'
import { stars } from '@/utils/recommendationDisplay'
import { getPublicBrandSubtitle, getPublicBrandTitle } from '@/utils/publicBranding'
import OrderSchedulingPromptModal from '@/components/OrderSchedulingPromptModal.vue'
import OrderSubmitNotesModal from '@/components/OrderSubmitNotesModal.vue'
import headerLogoUrl from '@/assets/lemon.svg'

const PERIODS = [
  { code: 'breakfast' as const, label: '早餐' },
  { code: 'lunch' as const, label: '午餐' },
  { code: 'dinner' as const, label: '晚餐' },
]

type PeriodCode = (typeof PERIODS)[number]['code']

interface DishRow {
  id: number
  category_id: number
  category_name: string
  name: string
  image_url: string | null
  recommendation_level: number
  description: string | null
  is_available: number | boolean
  sort_order: number
  meal_period_ids: number[]
}

/** 后端 `DishesStore.Dish` record 经 Jackson 为 camelCase；此处统一为前台使用的 snake 字段 */
function normalizeDishRow(raw: unknown): DishRow {
  const o = raw as Record<string, unknown>
  const num = (v: unknown, fallback = 0) => {
    if (typeof v === 'number' && !Number.isNaN(v)) return v
    if (typeof v === 'string' && v.trim() !== '') {
      const n = Number(v)
      return Number.isNaN(n) ? fallback : n
    }
    return fallback
  }
  const idsRaw = o.meal_period_ids ?? o.mealPeriodIds
  const meal_period_ids = Array.isArray(idsRaw)
    ? idsRaw.map((x) => num(x, NaN)).filter((x) => !Number.isNaN(x))
    : []
  return {
    id: num(o.id),
    category_id: num(o.category_id ?? o.categoryId),
    category_name: String(o.category_name ?? o.categoryName ?? ''),
    name: String(o.name ?? ''),
    image_url: (o.image_url ?? o.imageUrl ?? null) as string | null,
    recommendation_level: num(o.recommendation_level ?? o.recommendationLevel, 3),
    description: (o.description ?? null) as string | null,
    is_available: (o.is_available ?? o.isAvailable ?? true) as number | boolean,
    sort_order: num(o.sort_order ?? o.sortOrder, 0),
    meal_period_ids,
  }
}

interface DishLite {
  id: number
  name: string
  image_url: string | null
  category_id: number
  category_name: string
}

interface TodayPeriodRow {
  meal_period: { id: number; code: string; name: string; sort_order: number }
  order: {
    id: number
    remark: string | null
    items: Array<{
      dish_id: number
      quantity: number
      note: string | null
      dish: {
        name: string
        image_url: string | null
        category_id: number
        category_name: string
      }
    }>
    item_count: number
    created_at: string
    updated_at: string
  } | null
}

interface TodayData {
  date: string
  periods: TodayPeriodRow[]
}

type SelMap = Record<number, { dish: DishLite; qty: number; note?: string | null }>

const route = useRoute()
const router = useRouter()

const menuScrollEl = ref<HTMLElement | null>(null)

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
const loadError = ref<string | null>(null)
const submitting = ref(false)
const submitTip = ref<string | null>(null)

const submitModalOpen = ref(false)
const submitModalOutcome = ref<'idle' | 'success' | 'error'>('idle')
const submitModalError = ref<string | null>(null)

/** 是否已完成「今日 / 预约」选择并允许加载点菜页 */
const schedulingReady = ref(false)
/** 用户是否从预约入口进入（用于顶栏提示；时间戳对齐另由 shouldAlignOrderTimestamps 决定） */
const isSchedulingMode = ref(false)
/** 当前订单归属日期 YYYY-MM-DD（今日点餐 = 当天；预约 = 所选日） */
const orderingDate = ref('')

function localTodayYmd(): string {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

const shouldAlignOrderTimestamps = computed(() => {
  if (!isSchedulingMode.value) return false
  const d = orderingDate.value
  if (!d) return false
  return d > localTodayYmd()
})

function formatOrderingDayLabel(ymd: string): string {
  const parts = ymd.split('-')
  const mo = parts[1]
  const day = parts[2]
  if (!mo || !day) return ymd
  return `${parseInt(mo, 10)}月${parseInt(day, 10)}日`
}

function onSchedulingResolved(payload: { isScheduled: boolean; orderDate: string }) {
  isSchedulingMode.value = payload.isScheduled
  orderingDate.value = payload.orderDate
  schedulingReady.value = true
  void bootstrap()
}

const dishes = ref<DishRow[]>([])
const periodIdByCode = ref<Record<string, number>>({})
const activeCode = ref<PeriodCode>('lunch')
const activeCategoryId = ref<number | null>(null)

const selections = reactive<Record<PeriodCode, SelMap>>({
  breakfast: {},
  lunch: {},
  dinner: {},
})

function toLite(d: DishRow): DishLite {
  return {
    id: d.id,
    name: d.name,
    image_url: d.image_url,
    category_id: d.category_id,
    category_name: d.category_name,
  }
}

function parsePeriodQuery(q: unknown): PeriodCode {
  if (q === 'breakfast' || q === 'lunch' || q === 'dinner') return q
  return 'lunch'
}

const currentPid = computed(() => periodIdByCode.value[activeCode.value] ?? null)

/** 餐段 Tab 滑动高亮用（0–2） */
const periodActiveIndex = computed(() => {
  const i = PERIODS.findIndex((p) => p.code === activeCode.value)
  return i >= 0 ? i : 0
})

/** 餐段角标用，避免模板里反复 Object.keys */
const periodSelectionCounts = computed(() => ({
  breakfast: Object.keys(selections.breakfast).length,
  lunch: Object.keys(selections.lunch).length,
  dinner: Object.keys(selections.dinner).length,
}))

const categories = computed(() => {
  const pid = currentPid.value
  if (!pid) return [] as { id: number; name: string }[]
  const map = new Map<number, string>()
  for (const d of dishes.value) {
    if (!d.meal_period_ids.includes(pid)) continue
    if (!isAvailable(d)) continue
    map.set(d.category_id, d.category_name)
  }
  return [...map.entries()]
    .map(([id, name]) => ({ id, name }))
    .sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))
})

const visibleDishes = computed(() => {
  const pid = currentPid.value
  if (!pid) return []
  return dishes.value.filter((d) => {
    if (!d.meal_period_ids.includes(pid)) return false
    if (!isAvailable(d)) return false
    if (activeCategoryId.value !== null && d.category_id !== activeCategoryId.value) return false
    return true
  })
})

/** 仅随「分类」变化，避免切换顶部餐段时整表 remount + out-in 造成整页闪动 */
const dishPanelCategoryKey = computed(
  () => String(activeCategoryId.value ?? 'all'),
)

const rightHeading = computed(() => {
  if (activeCategoryId.value === null) return '全部菜品'
  const c = categories.value.find((x) => x.id === activeCategoryId.value)
  return c?.name ?? '菜品'
})

const currentLines = computed(() => {
  const m = selections[activeCode.value]
  return Object.entries(m)
    .map(([id, v]) => ({ id: Number(id), ...v }))
    .sort((a, b) => a.dish.name.localeCompare(b.dish.name, 'zh-CN'))
})

const totalQty = computed(() =>
  currentLines.value.reduce((s, l) => s + l.qty, 0),
)

function isAvailable(d: DishRow) {
  const v = d.is_available
  return v === true || v === 1 || String(v) === '1'
}

function setActiveCode(code: PeriodCode) {
  activeCode.value = code
  activeCategoryId.value = null
  void router.replace({ query: { ...route.query, period: code } })
}

function toggleDish(d: DishRow) {
  const code = activeCode.value
  const cur = selections[code]
  if (cur[d.id]) {
    delete cur[d.id]
  } else {
    cur[d.id] = { dish: toLite(d), qty: 1 }
  }
}

function isSelected(id: number) {
  return Boolean(selections[activeCode.value][id])
}

function incLine(id: number) {
  const line = selections[activeCode.value][id]
  if (!line) return
  line.qty = Math.min(99, Math.round((line.qty + 1) * 10) / 10)
}

function decLine(id: number) {
  const cur = selections[activeCode.value]
  const line = cur[id]
  if (!line) return
  if (line.qty <= 1) {
    delete cur[id]
  } else {
    line.qty = Math.max(1, Math.round((line.qty - 1) * 10) / 10)
  }
}

async function bootstrap() {
  loading.value = true
  loadError.value = null
  submitTip.value = null
  try {
    const dateQ = encodeURIComponent(orderingDate.value || localTodayYmd())
    const [today, dishRes] = await Promise.all([
      apiGet<TodayData>(`/meal-orders/today?date=${dateQ}`),
      apiGet<{ items: unknown[] }>('/dishes'),
    ])
    dishes.value = (dishRes.items ?? []).map(normalizeDishRow)
    const map: Record<string, number> = {}
    for (const row of today.periods) {
      map[row.meal_period.code] = row.meal_period.id
    }
    periodIdByCode.value = map
    hydrateFromToday(today, dishes.value)
  } catch (e) {
    loadError.value = getApiErrorMessage(e, '加载失败', {
      DISHES_ACCESS_DENIED: '需要先完成访问验证',
    })
  } finally {
    loading.value = false
  }
}

function hydrateFromToday(today: TodayData, list: DishRow[]) {
  const byId = new Map(list.map((d) => [d.id, d]))
  const codes: PeriodCode[] = ['breakfast', 'lunch', 'dinner']
  for (const row of today.periods) {
    const code = row.meal_period.code as PeriodCode
    if (!codes.includes(code)) continue
    const next: SelMap = {}
    const o = row.order
    if (o?.items?.length) {
      for (const it of o.items) {
        const full = byId.get(it.dish_id)
        const dish: DishLite = full
          ? toLite(full)
          : {
              id: it.dish_id,
              name: it.dish.name,
              image_url: it.dish.image_url,
              category_id: it.dish.category_id,
              category_name: it.dish.category_name,
            }
        next[it.dish_id] = {
          dish,
          qty: Number(it.quantity),
          note: it.note != null && String(it.note).trim() !== '' ? String(it.note) : undefined,
        }
      }
    }
    selections[code] = next
  }
}

function trimNoteForApi(s: string | undefined): string | null {
  const t = (s ?? '').trim()
  if (t === '') return null
  return t.slice(0, 255)
}

const submitModalPeriodLabel = computed(
  () => PERIODS.find((p) => p.code === activeCode.value)?.label ?? '',
)

const submitModalInitialNotes = computed(() => {
  const m = selections[activeCode.value]
  const o: Record<number, string> = {}
  for (const l of currentLines.value) {
    const n = m[l.id]?.note
    o[l.id] = n != null && String(n).trim() !== '' ? String(n) : ''
  }
  return o
})

function openSubmitModal() {
  if (!currentLines.value.length) {
    submitTip.value = '请先选择至少一道菜'
    return
  }
  submitTip.value = null
  submitModalError.value = null
  submitModalOutcome.value = 'idle'
  submitModalOpen.value = true
}

async function onSubmitNotesConfirmed(notes: Record<number, string>) {
  submitting.value = true
  submitModalError.value = null
  submitTip.value = null
  try {
    const lines = currentLines.value
    await apiPost<unknown>('/meal-orders', {
      order_date: orderingDate.value || localTodayYmd(),
      meal_period_code: activeCode.value,
      remark: null,
      set_timestamps_to_order_date: shouldAlignOrderTimestamps.value,
      items: lines.map((l) => ({
        dish_id: l.dish.id,
        quantity: l.qty,
        note: trimNoteForApi(notes[l.id]),
      })),
    })
    for (const l of lines) {
      const row = selections[activeCode.value][l.id]
      if (row) {
        const n = trimNoteForApi(notes[l.id])
        row.note = n ?? undefined
      }
    }
    submitModalOutcome.value = 'success'
  } catch (e) {
    submitModalOutcome.value = 'error'
    submitModalError.value = getApiErrorMessage(e, '提交失败', {
      DISHES_ACCESS_DENIED: '访问授权已失效，请重新验证后提交',
      DISHES_INVALID_MEAL_PERIOD_CODE: '餐段无效，请切换餐段后重试',
      DISHES_BAD_REQUEST: '提交内容不合法，请检查后重试',
    })
  } finally {
    submitting.value = false
  }
}

function onSubmitModalAfterSuccess() {
  submitModalOpen.value = false
  submitModalOutcome.value = 'idle'
  void router.push('/')
}

onMounted(() => {
  activeCode.value = parsePeriodQuery(route.query.period)
})

watch(
  () => route.query.period,
  (p) => {
    activeCode.value = parsePeriodQuery(p)
    activeCategoryId.value = null
  },
)

watch(activeCode, () => {
  const el = menuScrollEl.value
  if (el) el.scrollTop = 0
})
</script>

<template>
  <!-- 底栏不可放在带 transform 的 Transition 内，否则 fixed 会相对动画层定位，结束瞬间会「跳」到视口底 -->
  <div class="order-page flex min-h-0 min-w-0 flex-1 flex-col">
    <OrderSchedulingPromptModal
      v-if="!schedulingReady"
      @resolved="onSchedulingResolved"
    />

    <template v-if="schedulingReady">
    <Transition name="order-mount" appear>
      <div
        class="order-shell -mx-4 flex min-h-0 min-w-0 flex-1 flex-col gap-0 overflow-hidden bg-slate-50"
      >
      <header
        class="sticky top-0 z-20 shrink-0 bg-slate-50 supports-[backdrop-filter]:backdrop-blur-sm"
      >
        <div class="ls-brand">
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

        <!-- 餐段：留白分段 + 滑动白底高亮 -->
        <div
          class="order-header border-b border-slate-200/90 bg-white px-4 pb-3.5 pt-4"
        >
          <p
            class="mb-2 text-xs font-medium tracking-wide"
            :class="
              isSchedulingMode && orderingDate
                ? 'text-blue-700'
                : 'text-slate-400'
            "
          >
            <template v-if="isSchedulingMode && orderingDate">
              预约点餐 · {{ formatOrderingDayLabel(orderingDate) }}
            </template>
            <template v-else>
              选择餐段
            </template>
          </p>
          <div
            class="period-seg relative rounded-xl bg-slate-100/95 p-1"
            role="tablist"
            aria-label="餐段"
            :style="{ '--period-i': periodActiveIndex }"
          >
            <div
              class="period-seg-glider pointer-events-none absolute rounded-lg bg-white shadow-[0_1px_2px_rgba(15,23,42,0.05)] ring-1 ring-slate-200/70"
              aria-hidden="true"
            />
            <div class="relative z-[1] flex gap-2">
              <button
                v-for="p in PERIODS"
                :key="p.code"
                type="button"
                role="tab"
                :aria-selected="activeCode === p.code"
                class="period-seg-btn relative min-h-[2.25rem] flex-1 rounded-lg py-2 text-sm font-medium transition-[color,transform] duration-300 ease-[cubic-bezier(0.25,0.46,0.45,0.94)] motion-safe:active:scale-[0.98]"
                :class="
                  activeCode === p.code
                    ? 'text-blue-700'
                    : 'text-slate-500 motion-safe:hover:text-slate-700'
                "
                @click="setActiveCode(p.code)"
              >
                <span class="relative z-[1]">{{ p.label }}</span>
                <span
                  v-if="periodSelectionCounts[p.code]"
                  class="absolute right-1 top-1 z-[2] flex h-4 min-w-4 items-center justify-center rounded-full bg-blue-600 px-1 text-[9px] font-semibold leading-none text-white ring-2 ring-white"
                >
                  {{ periodSelectionCounts[p.code] }}
                </span>
              </button>
            </div>
          </div>
        </div>
      </header>

      <!-- 加载 -->
      <div
        v-if="loading"
        class="flex min-h-0 flex-1 gap-0 overflow-hidden"
      >
        <div
          class="order-cat-rail order-cat-rail--scroll flex min-h-0 w-[5.5rem] shrink-0 flex-col gap-2 overflow-y-auto overflow-x-hidden overscroll-y-contain border-r border-slate-200 bg-white py-4 pl-2 pr-1"
        >
          <div
            v-for="n in 5"
            :key="n"
            class="h-9 animate-pulse rounded-md bg-slate-100"
          />
        </div>
        <div
          class="order-menu-scroll order-menu-scroll--above-dock min-h-0 min-w-0 flex-1 space-y-2 overflow-y-auto overscroll-y-contain bg-white px-4 py-4 [-webkit-overflow-scrolling:touch]"
        >
          <div class="h-3.5 w-20 animate-pulse rounded bg-slate-100" />
          <div
            v-for="n in 6"
            :key="n"
            class="flex gap-4 rounded-lg border border-slate-100 bg-white p-2 shadow-sm"
          >
            <div class="h-14 w-14 shrink-0 animate-pulse rounded-md bg-slate-100 ring-1 ring-slate-200/50 shadow-[0_1px_4px_rgba(15,23,42,0.05)]" />
            <div class="min-w-0 flex-1 space-y-2 py-1">
              <div class="h-3.5 w-3/4 animate-pulse rounded bg-slate-100" />
              <div class="h-3 w-1/2 animate-pulse rounded bg-slate-100" />
            </div>
          </div>
        </div>
      </div>

      <!-- 错误 -->
      <div
        v-else-if="loadError"
        class="flex min-h-0 flex-1 flex-col items-center justify-center gap-4 overflow-hidden px-6 py-16 text-center"
      >
        <p class="text-sm text-slate-600">
          {{ loadError }}
        </p>
        <button
          type="button"
          class="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-blue-700"
          @click="bootstrap"
        >
          重新加载
        </button>
      </div>

      <!-- 分类 + 菜品 -->
      <div
        v-else
        class="flex min-h-0 flex-1 gap-0 overflow-hidden"
      >
        <nav
          class="order-cat-rail order-cat-rail--scroll flex min-h-0 w-[5.5rem] shrink-0 flex-col overflow-y-auto overflow-x-hidden overscroll-y-contain border-r border-slate-200 bg-white py-2 pl-2 pr-0"
          aria-label="菜品分类"
        >
          <button
            type="button"
            class="order-cat-item mb-px w-full rounded-md px-2 py-2 text-left text-[12px] leading-snug text-slate-600 transition-colors duration-150"
            :class="
              activeCategoryId === null
                ? 'order-cat-item--active bg-blue-50/80 font-medium text-blue-800'
                : 'hover:bg-slate-50'
            "
            @click="activeCategoryId = null"
          >
            全部
          </button>
          <button
            v-for="c in categories"
            :key="c.id"
            type="button"
            class="order-cat-item mb-px w-full rounded-md px-2 py-2 text-left text-[12px] leading-snug text-slate-600 transition-colors duration-150"
            :class="
              activeCategoryId === c.id
                ? 'order-cat-item--active bg-blue-50/80 font-medium text-blue-800'
                : 'hover:bg-slate-50'
            "
            @click="activeCategoryId = c.id"
          >
            <span class="line-clamp-3 break-words">{{ c.name }}</span>
          </button>
        </nav>

        <section
          class="relative flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden bg-white"
        >
          <div
            class="flex shrink-0 items-center justify-between border-b border-slate-100 bg-white px-4 py-2"
          >
            <h2 class="text-sm font-medium text-slate-900">
              {{ rightHeading }}
            </h2>
            <span class="text-xs tabular-nums text-slate-400">
              {{ visibleDishes.length }} 道
            </span>
          </div>

          <div
            ref="menuScrollEl"
            class="order-menu-scroll order-menu-scroll--above-dock min-h-0 flex-1 overflow-y-auto overscroll-y-contain px-4 py-2 [-webkit-overflow-scrolling:touch]"
          >
            <Transition name="dish-panel" mode="out-in">
              <ul
                :key="dishPanelCategoryKey"
                class="space-y-2 pb-1"
              >
                <li
                  v-if="!visibleDishes.length"
                  key="empty"
                  class="py-20 text-center text-sm text-slate-400"
                >
                  当前餐段暂无菜品
                </li>
                <li
                  v-for="d in visibleDishes"
                  :key="d.id"
                >
                  <button
                    type="button"
                    class="order-dish-row flex w-full gap-4 rounded-lg border p-2 text-left shadow-sm transition-[border-color,background-color,box-shadow] duration-150"
                    :class="
                      isSelected(d.id)
                        ? 'border-blue-200/90 bg-blue-50/40 shadow-[0_2px_10px_rgba(59,130,246,0.12)]'
                        : 'border-slate-100 bg-white hover:border-slate-200 hover:bg-slate-50/80 hover:shadow-md'
                    "
                    @click="toggleDish(d)"
                  >
                    <div
                      class="relative h-14 w-14 shrink-0 overflow-hidden rounded-md bg-slate-100 ring-1 ring-slate-200/60 shadow-sm transition-[box-shadow,ring-color] duration-150"
                      :class="
                        isSelected(d.id)
                          ? 'ring-blue-200/70 shadow-[0_2px_8px_rgba(59,130,246,0.18)]'
                          : ''
                      "
                    >
                      <img
                        v-if="d.image_url"
                        :src="resolveMediaUrl(d.image_url)"
                        :alt="d.name"
                        class="h-full w-full object-cover"
                        loading="lazy"
                        decoding="async"
                      />
                      <span
                        v-else
                        class="flex h-full items-center justify-center text-xs font-medium text-slate-300"
                        aria-hidden="true"
                      >
                        无图
                      </span>
                      <span
                        v-if="isSelected(d.id)"
                        class="absolute bottom-0.5 right-0.5 flex h-5 w-5 items-center justify-center rounded-full bg-gradient-to-br from-blue-500 to-blue-700 text-white shadow-[0_2px_6px_rgba(37,99,235,0.45)] ring-[1.5px] ring-white"
                        aria-hidden="true"
                      >
                        <svg
                          class="h-2.5 w-2.5 shrink-0 translate-y-[0.5px]"
                          viewBox="0 0 24 24"
                          fill="none"
                          xmlns="http://www.w3.org/2000/svg"
                          aria-hidden="true"
                        >
                          <path
                            d="M20 6L9 17l-5-5"
                            stroke="currentColor"
                            stroke-width="2.75"
                            stroke-linecap="round"
                            stroke-linejoin="round"
                          />
                        </svg>
                      </span>
                    </div>
                    <div
                      class="flex min-w-0 flex-1 flex-col py-1"
                      :class="isSelected(d.id) ? 'min-h-[3.5rem]' : ''"
                    >
                      <div class="flex items-start justify-between gap-2">
                        <p class="truncate text-[15px] font-medium text-slate-900">
                          {{ d.name }}
                        </p>
                        <p
                          class="shrink-0 text-[11px] leading-none tracking-tight text-amber-500"
                          :aria-label="`推荐 ${d.recommendation_level} 级`"
                        >
                          {{ stars(d.recommendation_level) }}
                        </p>
                      </div>
                      <p class="mt-1 truncate text-xs text-slate-500">
                        {{ d.category_name }}
                      </p>
                      <div
                        v-if="isSelected(d.id)"
                        class="mt-auto flex justify-end pt-1"
                      >
                        <span
                          class="shrink-0 rounded px-2 py-1 text-[10px] font-medium text-blue-700"
                          @click.stop
                        >
                          已选
                        </span>
                      </div>
                    </div>
                  </button>
                </li>
              </ul>
            </Transition>
          </div>
        </section>
      </div>
      </div>
    </Transition>

    <!-- 底栏：固定叠在底部，可盖住列表；pointer-events 让空白处滑动/点击穿透到列表 -->
    <div
      class="order-submit-dock pointer-events-none fixed bottom-0 left-1/2 z-[38] w-full max-w-md -translate-x-1/2 overflow-hidden rounded-t-2xl border border-b-0 border-slate-200/90 bg-white px-4 pt-4 shadow-[0_-4px_24px_rgba(15,23,42,0.08)] pb-[max(0.65rem,env(safe-area-inset-bottom,0px))]"
    >
        <p
          v-if="submitTip"
          class="pointer-events-auto mb-2 text-center text-xs text-red-600"
        >
          {{ submitTip }}
        </p>
        <div class="pointer-events-auto mb-4 flex max-h-[5.5rem] flex-col gap-2 overflow-hidden">
          <p class="text-xs text-slate-500">
            已选 <span class="font-medium text-slate-700">{{ totalQty }}</span> 份
          </p>
          <div
            v-if="currentLines.length"
            class="flex gap-2 overflow-x-auto overflow-y-hidden pb-1 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
          >
            <div
              v-for="line in currentLines"
              :key="line.id"
              class="flex shrink-0 items-center gap-2 rounded-full border border-slate-200 bg-slate-50 py-1 pl-2 pr-1 text-[11px] text-slate-800"
            >
              <span class="max-w-[6.5rem] truncate">{{ line.dish.name }}</span>
              <div class="flex items-center rounded-full border border-slate-200 bg-white">
                <button
                  type="button"
                  class="flex h-7 w-7 items-center justify-center text-slate-600 transition-colors hover:bg-slate-50"
                  aria-label="减少"
                  @click="decLine(line.id)"
                >
                  −
                </button>
                <span class="min-w-[1.25rem] text-center text-[11px] font-medium tabular-nums text-slate-700">
                  {{ line.qty }}
                </span>
                <button
                  type="button"
                  class="flex h-7 w-7 items-center justify-center text-slate-600 transition-colors hover:bg-slate-50"
                  aria-label="增加"
                  @click="incLine(line.id)"
                >
                  +
                </button>
              </div>
            </div>
          </div>
          <p
            v-else
            class="text-xs text-slate-400"
          >
            点击菜品即可加入
          </p>
        </div>
        <div class="pointer-events-auto flex gap-2">
          <button
            type="button"
            class="flex h-12 shrink-0 items-center justify-center gap-2 rounded-lg border border-slate-200 bg-white px-3.5 text-sm font-medium text-slate-700 transition-colors hover:border-slate-300 hover:bg-slate-50"
            aria-label="返回首页"
            @click="router.push('/')"
          >
            <svg
              class="h-[18px] w-[18px] shrink-0 text-slate-500"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
              aria-hidden="true"
            >
              <path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
              <polyline points="9 22 9 12 15 12 15 22" />
            </svg>
            <span class="max-[360px]:sr-only">首页</span>
          </button>
          <button
            type="button"
            class="flex h-12 min-w-0 flex-1 items-center justify-center gap-2 rounded-lg bg-blue-600 text-sm font-medium text-white transition-colors hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-slate-200 disabled:text-slate-400"
            :disabled="submitting || !currentLines.length"
            @click="openSubmitModal"
          >
            提交点餐
          </button>
        </div>
    </div>

    <OrderSubmitNotesModal
      v-model="submitModalOpen"
      :lines="currentLines"
      :meal-period-label="submitModalPeriodLabel"
      :loading="submitting"
      :outcome="submitModalOutcome"
      :error-message="submitModalError"
      :initial-notes="submitModalInitialNotes"
      @confirm="onSubmitNotesConfirmed"
      @after-success="onSubmitModalAfterSuccess"
    />
    </template>
  </div>
</template>

<style scoped>
.order-shell {
  min-height: 0;
}

.order-menu-scroll {
  touch-action: pan-y;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.order-menu-scroll::-webkit-scrollbar {
  display: none;
  width: 0;
  height: 0;
}

.order-cat-rail--scroll {
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.order-cat-rail--scroll::-webkit-scrollbar {
  display: none;
  width: 0;
  height: 0;
}

/* 列表可延伸到固定底栏下方；留白保证最后一道菜能滚上来点击，空白处手势由底栏 pointer-events-none 穿透 */
.order-menu-scroll--above-dock {
  padding-bottom: max(10.5rem, calc(9rem + env(safe-area-inset-bottom, 0px)));
}

/*
 * 餐段 Tab：滑块用 left 定位（100% = 轨道宽度）。
 * 勿用 translateX(calc(...100%...))：在 transform 里 % 相对滑块自身，会导致错位。
 */
.period-seg {
  --seg-pad: 0.25rem;
  --seg-gap: 0.5rem;
  --period-i: 0;
}

.period-seg-glider {
  top: var(--seg-pad);
  bottom: var(--seg-pad);
  width: calc((100% - 2 * var(--seg-pad) - 2 * var(--seg-gap)) / 3);
  left: calc(
    var(--seg-pad) +
      var(--period-i) *
        ((100% - 2 * var(--seg-pad) - 2 * var(--seg-gap)) / 3 + var(--seg-gap))
  );
  transition:
    left 0.32s cubic-bezier(0.25, 0.46, 0.45, 0.94),
    box-shadow 0.25s ease;
}

@media (prefers-reduced-motion: reduce) {
  .period-seg-glider {
    transition-duration: 0.01ms;
  }

  .period-seg-btn {
    transition-duration: 0.01ms;
  }
}

.order-cat-item--active {
  box-shadow: inset 2px 0 0 0 var(--ls-accent);
}

/* 页面进入：轻量上浮 + 淡入 */
.order-mount-enter-active {
  transition:
    opacity 0.35s cubic-bezier(0.25, 0.46, 0.45, 0.94),
    transform 0.35s cubic-bezier(0.25, 0.46, 0.45, 0.94);
}

.order-mount-enter-from {
  opacity: 0;
  transform: translate3d(0, 8px, 0);
}

/* 分类切换：仅淡入淡出，避免横向位移抢戏 */
.dish-panel-enter-active,
.dish-panel-leave-active {
  transition: opacity 0.2s ease;
}

.dish-panel-enter-from,
.dish-panel-leave-to {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .order-mount-enter-active {
    transition-duration: 0.01ms;
  }

  .order-mount-enter-from {
    transform: none;
  }

  .dish-panel-enter-active,
  .dish-panel-leave-active {
    transition-duration: 0.01ms;
  }
}
</style>
