<script setup lang="ts">
import KitchenIcon from '@/components/KitchenIcon.vue'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { apiGet, getApiErrorMessage } from '@/api/http'
import { isDate, isPeriod, kitchenViewer, loadViewer, mealContext, mealPeriods, normalizeDish, orderPeriod, shanghaiToday } from '@/api/kitchen'
import type { DailyMenu, Dish } from '@/api/kitchen'
import KitchenBrand from '@/components/KitchenBrand.vue'
import KitchenFooter from '@/components/KitchenFooter.vue'
import DailyMenuDish from '@/components/DailyMenuDish.vue'

const route = useRoute()
const router = useRouter()
const date = ref(isDate(route.query.date) ? route.query.date : mealContext.value.date || shanghaiToday())
const menu = ref<DailyMenu | null>(null)
const loading = ref(true)
const error = ref('')
const catalogueById = ref(new Map<number, Dish>())
let alive = true
let sequence = 0
let refreshing = false
let catalogueRefreshing = false
const visiblePeriods = computed(() => mealPeriods.flatMap((period) => {
  const found = menu.value?.periods.find((entry) => entry.code === period.code)
  return found?.status === 'confirmed' && found.items.length ? [found] : []
}))
const orderLink = computed(() => ({ path: '/order', query: { date: date.value, period: orderPeriod(route.query.period) } }))
async function refresh(quiet = false, refreshViewer = true) {
  if (quiet && refreshing) return
  if (refreshViewer) void loadViewer(true).catch(() => { /* Keep the current context when the optional refresh fails. */ })
  void refreshCatalogue()
  refreshing = true
  const current = ++sequence
  if (!quiet) { loading.value = true; error.value = '' }
  try {
    const data = await apiGet<DailyMenu>(`/daily-menu?date=${encodeURIComponent(date.value)}`)
    if (alive && current === sequence) { menu.value = data; error.value = '' }
  } catch (e) {
    if (alive && current === sequence && !quiet) error.value = getApiErrorMessage(e, '菜单暂时无法加载')
  } finally {
    if (alive && current === sequence) { loading.value = false; refreshing = false }
  }
}
async function refreshCatalogue() {
  if (catalogueRefreshing) return
  catalogueRefreshing = true
  try {
    const data = await apiGet<{ items: unknown[] }>('/dishes')
    if (!alive) return
    const dishes = Array.isArray(data.items) ? data.items.map(normalizeDish) : []
    catalogueById.value = new Map(dishes.filter((dish) => Number.isFinite(dish.id) && dish.id > 0).map((dish) => [dish.id, dish]))
  } catch {
    // Catalogue details are optional: confirmed snapshots remain usable without them.
  } finally {
    catalogueRefreshing = false
  }
}
function changeDate(event: Event) {
  const value = (event.target as HTMLInputElement).value
  if (isDate(value)) void router.replace({ path: '/', query: { date: value } })
}
watch(() => route.query.date, (value) => {
  const next = isDate(value) ? value : kitchenViewer.value?.today || shanghaiToday()
  if (next === date.value && menu.value) return
  date.value = next
  mealContext.value = { ...mealContext.value, date: next }
  void refresh()
})
watch(() => route.query.period, (value) => {
  if (isPeriod(value)) mealContext.value = { ...mealContext.value, period: value }
}, { immediate: true })
function onVisible() { if (!document.hidden) void refresh(true) }
onMounted(async () => {
  try {
    const viewer = await loadViewer(true)
    if (!alive) return
    if (!isDate(route.query.date)) date.value = viewer.today
    mealContext.value = { ...mealContext.value, date: date.value }
  } catch { /* The menu request below reports its own failure. */ }
  if (!alive) return
  void refresh(false, false)
  window.addEventListener('focus', onVisible)
  document.addEventListener('visibilitychange', onVisible)
})
onUnmounted(() => { alive = false; ++sequence; window.removeEventListener('focus', onVisible); document.removeEventListener('visibilitychange', onVisible) })
</script>

<template>
  <div class="k-page-scroll">
    <KitchenBrand />
    <div class="k-page-content k-menu-content">
      <section class="daily-menu-heading">
        <div class="daily-menu-heading__title"><p class="k-eyebrow">THE DAILY TABLE</p><h2>当日菜单</h2></div>
        <input class="daily-menu-date" type="date" :value="date" :max="kitchenViewer?.maxDate" aria-label="用餐日期" @change="changeDate" />
        <button type="button" class="k-icon-button daily-menu-refresh" :disabled="loading" :aria-busy="loading" aria-label="刷新" title="刷新" @click="refresh()"><KitchenIcon name="refresh-line" /></button>
      </section>
      <div v-if="loading" class="k-empty" role="status">菜单加载中…</div>
      <div v-else-if="error" class="k-notice k-notice--error" role="alert"><p>{{ error }}</p><button class="k-button k-button--secondary" @click="refresh()">重新加载</button></div>
      <template v-else>
        <p v-if="!visiblePeriods.length" class="k-empty daily-menu-empty">还没有点餐</p>
        <section v-for="period in visiblePeriods" :key="period.code" class="k-meal-section daily-menu-section">
          <div class="k-section-heading">
            <h3>{{ period.name }}</h3>
          </div>
          <ul class="daily-menu-items">
            <DailyMenuDish v-for="(item, index) in period.items" :key="`${item.dishId ?? 'custom'}-${index}`" :item="item" :catalogue-dish="item.dishId === null ? undefined : catalogueById.get(item.dishId)" :period-note="period.note" />
          </ul>
          <p v-if="period.note" class="k-menu-note">{{ period.note }}</p>
        </section>
      </template>

      <RouterLink class="k-button k-button--primary k-menu-action" :to="orderLink">我要点餐 <KitchenIcon name="arrow-right-s-line" /></RouterLink>
      <KitchenFooter />
    </div>
  </div>
</template>

<style scoped>
.daily-menu-heading {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 136px 40px;
  align-items: center;
  gap: 16px;
  padding: 16px 0;
  border-top: 1px solid var(--ls-line);
}
.daily-menu-heading__title {
  min-width: 0;
}
.daily-menu-heading .k-eyebrow {
  white-space: nowrap;
}
.daily-menu-heading h2 {
  margin: 4px 0 0;
  color: var(--ls-ink);
  font-size: 24px;
  font-weight: 500;
  line-height: 1.4;
}
.daily-menu-date {
  width: 136px;
  min-width: 0;
  height: 40px;
  padding: 8px;
  border: 1px solid var(--ls-line);
  border-radius: 8px;
  color: var(--ls-ink);
  background: var(--ls-field);
  font-size: 13px;
  line-height: 1.5;
}
.daily-menu-refresh {
  width: 40px;
  height: 40px;
  background: transparent;
}
.daily-menu-refresh:hover:not(:disabled) {
  color: var(--ls-ink);
}
.daily-menu-section {
  padding-block: 16px;
}
.daily-menu-items {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 16px;
  padding: 0;
  list-style: none;
}
.daily-menu-empty {
  min-height: 128px;
  padding: 16px;
}
@media (max-width: 639px) {
  .daily-menu-heading h2 {
    font-size: 20px;
  }
}
@media (max-width: 359px) {
  .daily-menu-heading {
    grid-template-columns: minmax(0, 1fr) 40px;
  }
  .daily-menu-heading__title {
    grid-column: 1 / -1;
  }
  .daily-menu-date {
    grid-column: 1;
    grid-row: 2;
  }
  .daily-menu-refresh {
    grid-column: 2;
    grid-row: 2;
  }
}
</style>
