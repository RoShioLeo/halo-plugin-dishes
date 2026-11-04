<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { VButton, VCard, VEmpty, VModal, VPageHeader, VPagination } from '@halo-dev/components'
import { getApiErrorMessage, getData } from '@/api/client'
import RiFileList3Line from '~icons/ri/file-list-3-line'

type PeriodCode = 'breakfast' | 'lunch' | 'dinner'

type Order = {
  id: number
  orderDate: string
  mealPeriodCode: PeriodCode
  mealPeriodName?: string
  itemCount: number
}

type OrdersResp = {
  range: { from: string; to: string }
  items: Order[]
  meta: { total: number; limit: number; offset: number }
}

type OrderDetailResp = {
  id: number
  orderDate: string
  mealPeriod: { id: number; code: string; name: string }
  order: {
    id: number
    remark: string | null
    items: Array<{
      line_id: number
      dish_id: number
      quantity: number
      note: string | null
      dish: { name: string; image_url: string | null; category_id: number; category_name: string }
    }>
    item_count: number
    created_at: string
    updated_at: string
  }
}

type DayCard = {
  date: string
  periodMap: Record<PeriodCode, Order | null>
  totalCount: number
  isScheduled: boolean
}

const PERIODS: Array<{ code: PeriodCode; name: string }> = [
  { code: 'breakfast', name: '早餐' },
  { code: 'lunch', name: '午餐' },
  { code: 'dinner', name: '晚餐' },
]

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const error = ref<string | null>(null)
const allOrders = ref<Order[]>([])
const dayCards = ref<DayCard[]>([])

const queryDate = ref('')
const page = ref(1)
const size = ref(10)

const detailOpen = ref(false)
const detailLoading = ref(false)
const detailError = ref<string | null>(null)
const detail = ref<OrderDetailResp | null>(null)
const detailRequestSeq = ref(0)
const detailOpening = ref(false)

function ymd(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function localTodayYmd(): string {
  return ymd(new Date())
}

function setFromQuery() {
  const q = route.query
  const qDate = typeof q.date === 'string' ? q.date : ''
  const qPage = typeof q.page === 'string' ? parseInt(q.page, 10) : NaN
  const qSize = typeof q.size === 'string' ? parseInt(q.size, 10) : NaN

  queryDate.value = qDate || ''
  if (!Number.isNaN(qPage) && qPage > 0) page.value = qPage
  if (!Number.isNaN(qSize) && qSize > 0) size.value = qSize
}

function syncQuery() {
  const nextQuery: Record<string, string> = {
    page: String(page.value),
    size: String(size.value),
  }
  if (queryDate.value) nextQuery.date = queryDate.value
  void router.replace({
    query: nextQuery,
  })
}

function buildDayCards(rows: Order[]): DayCard[] {
  const today = localTodayYmd()
  const map = new Map<string, DayCard>()
  for (const row of rows) {
    const key = row.orderDate
    if (!map.has(key)) {
      map.set(key, {
        date: key,
        periodMap: { breakfast: null, lunch: null, dinner: null },
        totalCount: 0,
        isScheduled: key > today,
      })
    }
    const card = map.get(key)!
    card.periodMap[row.mealPeriodCode] = row
    card.totalCount += Number(row.itemCount || 0)
    card.isScheduled = card.date > today
  }
  return [...map.values()].sort((a, b) => {
    if (a.isScheduled !== b.isScheduled) return a.isScheduled ? -1 : 1
    if (a.isScheduled && b.isScheduled) return a.date.localeCompare(b.date, 'zh-CN')
    return b.date.localeCompare(a.date, 'zh-CN')
  })
}

async function fetchOrdersByRange(fromDate: string, toDate: string): Promise<Order[]> {
  const merged: Order[] = []
  const chunk = 200
  let total = 0
  let currentPage = 1
  // 安全上限，避免异常 total 导致死循环
  const maxPages = 20
  do {
    const qs = new URLSearchParams()
    qs.set('from', fromDate)
    qs.set('to', toDate)
    qs.set('page', String(currentPage))
    qs.set('limit', String(chunk))
    const data = await getData<OrdersResp>(`/orders?${qs.toString()}`)
    total = Number(data.meta?.total ?? 0)
    merged.push(...(data.items ?? []))
    currentPage += 1
    if (currentPage > maxPages + 1) break
  } while (merged.length < total)
  return merged
}

async function load() {
  loading.value = true
  error.value = null
  try {
    // 管理端默认展示所有点餐记录，预约与普通记录都在同一数据集中展示
    const rows = await fetchOrdersByRange('2000-01-01', '2099-12-31')
    allOrders.value = rows
    dayCards.value = buildDayCards(rows)
    const maxPage = Math.max(1, Math.ceil(filteredDayCards.value.length / size.value))
    if (page.value > maxPage) page.value = maxPage
    syncQuery()
  } catch (e) {
    error.value = getApiErrorMessage(e, '加载点菜记录失败，请稍后重试', {
      DISHES_ACCESS_DENIED: '当前账号无权访问点菜记录',
    })
  } finally {
    loading.value = false
  }
}

const filteredDayCards = computed(() => {
  const d = queryDate.value.trim()
  if (!d) return dayCards.value
  return dayCards.value.filter((x) => x.date === d)
})

const pagedDayCards = computed(() => {
  const start = (page.value - 1) * size.value
  return filteredDayCards.value.slice(start, start + size.value)
})

function periodName(code: PeriodCode) {
  return PERIODS.find((x) => x.code === code)?.name ?? code
}

function onSearch() {
  page.value = 1
  syncQuery()
}

function clearDateFilter() {
  queryDate.value = ''
  page.value = 1
  syncQuery()
}

function onRefresh() {
  void load()
}

async function openDetail(o: Order) {
  if (detailOpening.value) return
  detailOpening.value = true
  const requestId = ++detailRequestSeq.value
  detailOpen.value = true
  detailLoading.value = true
  detailError.value = null
  detail.value = null
  try {
    const next = await getData<OrderDetailResp>(`/orders/${o.id}`)
    // 只接收最后一次点击的返回，避免连续点击导致竞态覆盖
    if (requestId !== detailRequestSeq.value) return
    detail.value = next
  } catch (e) {
    if (requestId !== detailRequestSeq.value) return
    detailError.value = getApiErrorMessage(e, '加载明细失败，请稍后重试', {
      DISHES_NOT_FOUND: '该记录不存在，可能已被删除',
    })
  } finally {
    if (requestId !== detailRequestSeq.value) return
    detailLoading.value = false
    detailOpening.value = false
  }
}

function closeDetail() {
  detailRequestSeq.value += 1
  detailOpening.value = false
  detailOpen.value = false
  detailLoading.value = false
  detailError.value = null
  detail.value = null
}

const detailItems = computed(() => detail.value?.order?.items ?? [])
const detailReady = computed(() => !detailLoading.value && !detailError.value && !!detail.value)

onMounted(() => {
  setFromQuery()
  void load()
})

watch(
  () => route.query,
  () => {
    setFromQuery()
  },
)
</script>

<template>
  <VPageHeader title="点菜记录">
    <template #icon>
      <RiFileList3Line />
    </template>
  </VPageHeader>

  <div class="orders-page :uno: p-4">
    <VCard class="orders-main-card" :body-class="[':uno: !p-0']">
      <template #header>
        <div class="orders-toolbar :uno: block w-full px-4 py-4">
          <div class=":uno: flex flex-wrap items-end justify-between gap-3">
            <div class=":uno: flex flex-wrap items-end gap-3">
              <div>
                <div class="orders-label">按日期查询</div>
                <input v-model="queryDate" type="date" class="orders-input" />
              </div>
              <VButton type="primary" class="orders-search-btn" :disabled="loading" @click="onSearch">查询</VButton>
              <VButton :disabled="loading || !queryDate" @click="clearDateFilter">清空</VButton>
            </div>

            <div class=":uno: flex items-center gap-2">
              <div class="orders-meta">共 {{ filteredDayCards.length }} 天 / {{ allOrders.length }} 条</div>
              <VButton :disabled="loading" @click="onRefresh">刷新</VButton>
            </div>
          </div>
        </div>
      </template>

      <div class=":uno: p-4">
        <div v-if="error" class="orders-error">{{ error }}</div>
        <div v-else-if="loading" class="orders-loading">加载中…</div>
        <VEmpty v-else-if="dayCards.length === 0" title="暂无点餐记录" description="当前日期范围内没有可展示的数据。" />

        <div v-else class="orders-day-list">
          <div
            v-for="card in pagedDayCards"
            :key="card.date"
            class="orders-day-card"
            :class="{ 'orders-day-card--scheduled': card.isScheduled }"
          >
            <div class="orders-day-head">
              <div class="orders-day-date-wrap">
                <div class="orders-day-date">{{ card.date }}</div>
                <span v-if="card.isScheduled" class="orders-scheduled-badge">预约</span>
              </div>
              <div class="orders-day-count">{{ card.isScheduled ? '预约共' : '当日共' }} {{ card.totalCount }} 样菜品</div>
            </div>

            <div class="orders-period-grid">
              <div v-for="p in PERIODS" :key="p.code" class="orders-period-col">
                <div class="orders-period-title">{{ p.name }}</div>
                <template v-if="card.periodMap[p.code]">
                  <div class="orders-period-value">{{ card.periodMap[p.code]?.itemCount }} 样菜品</div>
                  <VButton size="sm" :disabled="detailLoading" @click="openDetail(card.periodMap[p.code]!)">查看明细</VButton>
                </template>
                <template v-else>
                  <div class="orders-period-empty">未点餐</div>
                </template>
              </div>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <div class=":uno: px-4 py-3">
          <VPagination
            v-model:page="page"
            v-model:size="size"
            :total="filteredDayCards.length"
            :size-options="[10, 20, 30]"
            @change="syncQuery"
          />
        </div>
      </template>
    </VCard>

    <VModal :visible="detailOpen" title="点餐明细" @close="closeDetail">
      <div class="orders-modal-body">
        <div v-show="!!detailError" class="orders-error">{{ detailError }}</div>
        <div v-show="detailLoading" class="orders-loading">加载中…</div>
        <div v-show="detailReady" class=":uno: space-y-3">
          <div class="orders-detail-meta">
            <div><span class="orders-detail-label">日期：</span>{{ detail?.orderDate ?? '' }}</div>
            <div><span class="orders-detail-label">餐段：</span>{{ detail?.mealPeriod?.name ?? '' }}</div>
            <div><span class="orders-detail-label">样数：</span>{{ detail?.order?.item_count ?? 0 }}</div>
            <div v-if="detail?.order?.remark"><span class="orders-detail-label">备注：</span>{{ detail?.order?.remark }}</div>
          </div>
          <div class=":uno: overflow-x-auto">
            <table class="orders-detail-table">
              <thead>
                <tr>
                  <th>菜品</th>
                  <th>分类</th>
                  <th>数量</th>
                  <th>备注</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="it in detailItems" :key="it.line_id">
                  <td>
                    <div class="orders-dish-cell">
                      <img
                        v-if="it.dish.image_url"
                        :src="it.dish.image_url"
                        :alt="it.dish.name"
                        class="orders-dish-thumb"
                      />
                      <div v-else class="orders-dish-thumb orders-dish-thumb--placeholder">
                        {{ it.dish.name.slice(0, 1) }}
                      </div>
                      <div class="orders-dish-name">{{ it.dish.name }}</div>
                    </div>
                  </td>
                  <td>{{ it.dish.category_name }}</td>
                  <td>{{ it.quantity }}</td>
                  <td>{{ it.note ?? '' }}</td>
                </tr>
                <tr v-if="detailItems.length === 0">
                  <td colspan="4" class="orders-detail-empty">无明细</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
        <div v-show="!detailLoading && !detailError && !detail" class="orders-loading">
          暂无可展示明细
        </div>
      </div>
    </VModal>
  </div>
</template>

<style scoped>
.orders-page :deep(.halo-card) {
  border-radius: 8px;
}

.orders-main-card {
  box-shadow: none;
}

.orders-main-card :deep(.card-header),
.orders-main-card :deep(.card-footer) {
  padding: 0;
}

.orders-toolbar {
  background: var(--ls-dishes-soft, #f7f7f8);
}

.orders-label {
  margin-bottom: 0.25rem;
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--ls-dishes-ink, #29292e);
}

.orders-input {
  height: 2.25rem;
  border: 1px solid rgb(212 212 216);
  border-radius: 6px;
  padding: 0 0.75rem;
  font-size: 0.875rem;
  border-color: var(--ls-dishes-line, #e8e8eb);
  background: var(--ls-dishes-surface, #fff);
  color: var(--ls-dishes-ink, #29292e);
}

.orders-search-btn {
  background: rgb(24 24 27) !important;
  border-color: rgb(24 24 27) !important;
  color: #fff !important;
}

.orders-meta {
  font-size: 0.875rem;
  color: var(--ls-dishes-muted, #6b6b75);
}

.orders-error {
  margin-bottom: 0.75rem;
  border: 1px solid rgb(254 202 202);
  background: rgb(254 242 242);
  color: rgb(153 27 27);
  border-radius: 8px;
  padding: 0.75rem;
  font-size: 0.875rem;
}

.orders-loading {
  padding: 1.25rem 0;
  color: var(--ls-dishes-muted, #6b6b75);
  font-size: 0.875rem;
}

.orders-day-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.orders-day-card {
  border: 1px solid var(--ls-dishes-line, #e8e8eb);
  border-radius: 8px;
  background: var(--ls-dishes-surface, #fff);
  overflow: hidden;
}

.orders-day-card--scheduled {
  border-inline-start: 4px solid var(--ls-dishes-highlight, #e7b63e);
}

.orders-day-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid var(--ls-dishes-line, #e8e8eb);
  background: var(--ls-dishes-soft, #f7f7f8);
}

.orders-day-date {
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--ls-dishes-ink, #29292e);
}

.orders-day-date-wrap {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.orders-scheduled-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 1.25rem;
  padding: 0 0.5rem;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--ls-dishes-gold, #946d12);
  background: var(--ls-dishes-gold-soft, #fff8e5);
}

.orders-day-count {
  font-size: 0.8125rem;
  color: var(--ls-dishes-muted, #6b6b75);
}

.orders-period-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0;
}

.orders-period-col {
  padding: 1rem;
  border-right: 1px solid var(--ls-dishes-line, #e8e8eb);
}

.orders-period-col:last-child {
  border-right: none;
}

.orders-period-title {
  font-size: 0.8125rem;
  color: var(--ls-dishes-muted, #6b6b75);
  margin-bottom: 0.5rem;
}

.orders-period-value {
  font-size: 1rem;
  font-weight: 600;
  color: var(--ls-dishes-ink, #29292e);
  margin-bottom: 0.5rem;
}

.orders-period-empty {
  font-size: 0.875rem;
  color: var(--ls-dishes-muted, #6b6b75);
}

.orders-modal-body {
  min-height: 5rem;
}

.orders-detail-meta {
  border: 1px solid rgb(228 228 231);
  border-radius: 8px;
  padding: 0.75rem;
  font-size: 0.875rem;
  display: grid;
  gap: 0.375rem;
}

.orders-detail-label {
  color: var(--ls-dishes-muted, #6b6b75);
}

.orders-detail-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
}

.orders-detail-table thead tr {
  border-bottom: 1px solid rgb(228 228 231);
  color: var(--ls-dishes-muted, #6b6b75);
}

.orders-detail-table th,
.orders-detail-table td {
  text-align: left;
  padding: 0.625rem 0.5rem;
}

.orders-detail-table tbody tr {
  border-bottom: 1px solid rgb(244 244 245);
}

.orders-detail-empty {
  text-align: center;
  color: var(--ls-dishes-muted, #6b6b75);
  padding: 1rem 0;
}

.orders-dish-cell {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
}

.orders-dish-thumb {
  width: 2rem;
  height: 2rem;
  border-radius: 0.375rem;
  object-fit: cover;
  background: rgb(244 244 245);
  flex: 0 0 auto;
}

.orders-dish-thumb--placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ls-dishes-muted, #6b6b75);
  font-size: 0.75rem;
  font-weight: 600;
}

.orders-dish-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 900px) {
  .orders-day-list {
    grid-template-columns: 1fr;
  }

  .orders-period-grid {
    grid-template-columns: 1fr;
  }

  .orders-period-col {
    border-right: none;
    border-bottom: 1px solid var(--ls-dishes-line, #e8e8eb);
  }

  .orders-period-col:last-child {
    border-bottom: none;
  }
}
</style>

