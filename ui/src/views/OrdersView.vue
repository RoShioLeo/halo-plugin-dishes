<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { Dialog, Toast, VButton, VCard, VPageHeader } from '@halo-dev/components'
import { getApiErrorMessage, getData, postData } from '@/api/client'
import { allocateKitchenQuantities, type KitchenQuantitySource } from '@/utils/kitchenAllocation'
import RiFileList3Line from '~icons/ri/file-list-3-line'
import RiCloseLine from '~icons/ri/close-line'
import RiArrowLeftSLine from '~icons/ri/arrow-left-s-line'
import RiArrowRightSLine from '~icons/ri/arrow-right-s-line'
import RiRefreshLine from '~icons/ri/refresh-line'

type PeriodCode = 'breakfast' | 'lunch' | 'dinner'
type EditMode = 'requests' | 'menu'
type MenuItem = {
  dishId: number | null
  customId?: string | null
  name: string
  quantity: number
  imageUrl: string
  note: string
}
type Selection = MenuItem & { dishId: number }
type Participant = {
  id: string
  displayName: string
  avatarUrl?: string
  batchId?: string
  submitted?: boolean
  revision: number
  items: Selection[]
  customItems?: MenuItem[]
  offMenu: string
  remark: string
}
type Menu = { status: 'draft' | 'confirmed'; revision: number; items: MenuItem[]; note: string }
type KitchenPeriod = {
  date: string
  period: PeriodCode
  periodName: string
  needsConfirmation: boolean
  participants: Participant[]
  menu: Menu
  workingItems: MenuItem[]
  contributionVersions: Record<string, number>
  stateRevision: number
  activeBatchId: string
  chefParticipantId: string | null
  chefDisplayName: string
  chefAvatarUrl?: string
  hasDraftChanges?: boolean
}
type Dish = {
  id: number
  name: string
  imageUrl?: string | null
  isAvailable: boolean
  soldOut?: boolean
}
type DraftItem = MenuItem & { key: string }
type DraftSource = {
  sourceId: string
  participantId: string | null
  displayName: string
  avatarUrl?: string
  batchId?: string
  submitted?: boolean
  items: DraftItem[]
  offMenu: string
  remark: string
}
type Editor = {
  revision: number
  stateRevision: number
  batchId: string
  versions: Record<string, number>
  chefSourceId: string
  baseline: KitchenQuantitySource[]
  sources: DraftSource[]
  items: DraftItem[]
  metadata: Record<string, DraftItem>
  inputs: Record<string, string>
  inputLimits: Record<string, number>
  inputErrors: Record<string, string>
  note: string
  mode: EditMode | null
  dirty: boolean
  saving: boolean
  error: string
  catalogueId: string
}

const PERIODS: Array<{ code: PeriodCode; name: string }> = [
  { code: 'breakfast', name: '早餐' },
  { code: 'lunch', name: '午餐' },
  { code: 'dinner', name: '晚餐' },
]
const route = useRoute()
const router = useRouter()
const loading = ref(true)
const refreshing = ref(false)
const loadError = ref('')
const catalogue = ref<Dish[]>([])
const catalogueError = ref('')
const orders = ref<KitchenPeriod[]>([])
const editors = reactive<Record<PeriodCode, Editor>>({
  breakfast: emptyEditor(),
  lunch: emptyEditor(),
  dinner: emptyEditor(),
})
const selectedDate = ref(normalizeDate(route.query.date) || chinaToday())
const selectedPeriod = ref(normalizePeriod(route.query.period) || currentPeriod())
const failedAvatars = reactive(new Set<string>())
const anyDirty = computed(() => PERIODS.some(({ code }) => editors[code].dirty))
const anySaving = computed(() => PERIODS.some(({ code }) => editors[code].saving))
const availableDishes = computed(() => catalogue.value.filter((dish) => dish.isAvailable))
const selectedOrders = computed(() =>
  orders.value.filter((order) => order.period === selectedPeriod.value),
)
let periodChosen = !!normalizePeriod(route.query.period)
let requestSerial = 0
let disposed = false

function emptyEditor(): Editor {
  return {
    revision: 0,
    stateRevision: 0,
    batchId: '',
    versions: {},
    chefSourceId: '__chef__',
    baseline: [],
    sources: [],
    items: [],
    metadata: {},
    inputs: {},
    inputLimits: {},
    inputErrors: {},
    note: '',
    mode: null,
    dirty: false,
    saving: false,
    error: '',
    catalogueId: '',
  }
}
function chinaToday() {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date())
  return ['year', 'month', 'day']
    .map((type) => parts.find((item) => item.type === type)?.value || '')
    .join('-')
}
function currentPeriod(): PeriodCode {
  const hour = Number(
    new Intl.DateTimeFormat('en-GB', {
      timeZone: 'Asia/Shanghai',
      hour: '2-digit',
      hourCycle: 'h23',
    }).format(new Date()),
  )
  return hour < 10 ? 'breakfast' : hour < 16 ? 'lunch' : 'dinner'
}
function normalizePeriod(value: unknown): PeriodCode | '' {
  return PERIODS.some((period) => period.code === value) ? (value as PeriodCode) : ''
}
function normalizeDate(value: unknown): string {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return ''
  const date = new Date(value + 'T12:00:00Z')
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value ? value : ''
}
function emptyPeriod(code: PeriodCode, name: string): KitchenPeriod {
  return {
    date: selectedDate.value,
    period: code,
    periodName: name,
    needsConfirmation: false,
    participants: [],
    contributionVersions: {},
    stateRevision: 0,
    activeBatchId: '',
    workingItems: [],
    chefParticipantId: null,
    chefDisplayName: '主厨',
    menu: { status: 'draft', revision: 0, items: [], note: '' },
  }
}
function draftItem(item: MenuItem): DraftItem {
  return {
    dishId: item.dishId ?? null,
    customId: item.customId ?? null,
    name: item.name || '',
    quantity: Number(item.quantity),
    imageUrl: item.imageUrl || '',
    note: item.note || '',
    key: item.dishId != null ? 'dish:' + item.dishId : 'custom:' + item.customId,
  }
}
function totalFor(sources: DraftSource[], key: string) {
  return sources.reduce(
    (sum, source) => sum + (source.items.find((item) => item.key === key)?.quantity || 0),
    0,
  )
}
function applyEditor(order: KitchenPeriod, mode: EditMode | null = null) {
  const chefSourceId = order.chefParticipantId || '__chef__'
  const sources: DraftSource[] = order.participants.map((person) => ({
    sourceId: person.id,
    participantId: person.id,
    displayName: person.displayName,
    avatarUrl: person.avatarUrl,
    batchId: person.batchId,
    submitted: person.submitted,
    offMenu: person.offMenu,
    remark: person.remark,
    items: [...person.items, ...(person.customItems || [])].map(draftItem),
  }))
  let chef = sources.find((source) => source.sourceId === chefSourceId)
  if (!chef) {
    chef = {
      sourceId: chefSourceId,
      participantId: null,
      displayName: order.chefDisplayName || '主厨',
      avatarUrl: order.chefAvatarUrl,
      batchId: order.activeBatchId,
      submitted: false,
      items: [],
      offMenu: '',
      remark: '',
    }
    sources.push(chef)
  }
  const items = order.workingItems.map(draftItem)
  const metadata: Record<string, DraftItem> = Object.fromEntries(
    items.map((item) => [item.key, item]),
  )
  for (const source of sources) for (const item of source.items) metadata[item.key] ||= { ...item }
  Object.assign(editors[order.period], {
    revision: order.menu.revision,
    stateRevision: order.stateRevision,
    batchId: order.activeBatchId,
    versions: { ...order.contributionVersions },
    chefSourceId,
    sources,
    items,
    metadata,
    baseline: sources.map((source) => ({
      sourceId: source.sourceId,
      items: source.items.map((item) => ({ key: item.key, quantity: item.quantity })),
    })),
    inputs: {},
    inputLimits: {},
    inputErrors: {},
    note: order.menu.note || '',
    mode,
    dirty: false,
    error: '',
    catalogueId: '',
  })
}
function sameVersions(left: Record<string, number>, right: Record<string, number>) {
  const keys = Object.keys(left)
  return keys.length === Object.keys(right).length && keys.every((key) => left[key] === right[key])
}
function menuChanged(order: KitchenPeriod) {
  return editors[order.period].revision !== order.menu.revision
}
function sourcesChanged(order: KitchenPeriod) {
  const editor = editors[order.period]
  return (
    editor.stateRevision !== order.stateRevision ||
    editor.batchId !== order.activeBatchId ||
    !sameVersions(editor.versions, order.contributionVersions)
  )
}
async function loadKitchen(reset = false) {
  const date = selectedDate.value,
    serial = ++requestSerial
  if (reset) loading.value = true
  else refreshing.value = true
  try {
    const data = await getData<{ date: string; periods: KitchenPeriod[] }>(
      '/kitchen?date=' + encodeURIComponent(date),
    )
    if (disposed || serial !== requestSerial || date !== selectedDate.value) return
    orders.value = PERIODS.map(
      ({ code, name }) =>
        data.periods.find((period) => period.period === code) || emptyPeriod(code, name),
    )
    for (const order of orders.value)
      if (reset || (!editors[order.period].mode && !editors[order.period].saving))
        applyEditor(order)
    if (!periodChosen) {
      selectedPeriod.value =
        orders.value.find(
          (order) =>
            order.participants.length &&
            (order.needsConfirmation || order.menu.status === 'draft' || order.hasDraftChanges),
        )?.period || selectedPeriod.value
      periodChosen = true
    }
    loadError.value = ''
  } catch (error) {
    if (!disposed && serial === requestSerial)
      loadError.value = getApiErrorMessage(error, '厨房工作台加载失败，请稍后重试')
  } finally {
    if (!disposed && serial === requestSerial) {
      loading.value = false
      refreshing.value = false
    }
  }
}
async function loadCatalogue() {
  try {
    const data = await getData<{ items: Dish[] }>('/dishes')
    if (!disposed) {
      catalogue.value = data.items || []
      catalogueError.value = ''
    }
  } catch (error) {
    if (!disposed) catalogueError.value = getApiErrorMessage(error, '菜谱暂未加载，可稍后刷新')
  }
}
function refresh() {
  void loadKitchen()
  void loadCatalogue()
}
function changeDate(value: string) {
  const date = normalizeDate(value)
  if (!date || date === selectedDate.value || anySaving.value) return false
  if (anyDirty.value && !window.confirm('菜单修改还未提交，切换日期会放弃这些修改。是否继续？'))
    return false
  selectedDate.value = date
  orders.value = []
  for (const { code } of PERIODS) Object.assign(editors[code], emptyEditor())
  void router.replace({ query: { ...route.query, date, period: selectedPeriod.value } })
  void loadKitchen(true)
  return true
}
function onDateInput(event: Event) {
  const input = event.target as HTMLInputElement,
    value = input.value
  input.value = selectedDate.value
  changeDate(value)
}
function moveDate(offset: number) {
  const date = new Date(selectedDate.value + 'T12:00:00Z')
  date.setUTCDate(date.getUTCDate() + offset)
  changeDate(date.toISOString().slice(0, 10))
}
function changePeriod(event: Event) {
  const period = normalizePeriod((event.target as HTMLSelectElement).value)
  if (!period || anySaving.value) return
  selectedPeriod.value = period
  periodChosen = true
  void router.replace({ query: { ...route.query, date: selectedDate.value, period } })
}
function beginEdit(order: KitchenPeriod, mode: EditMode) {
  if (anySaving.value || editors[order.period].mode) return
  applyEditor(order, mode)
}
function cancelEdit(order: KitchenPeriod) {
  if (anySaving.value) return
  if (!editors[order.period].dirty || window.confirm('放弃本次修改？')) applyEditor(order)
}
function markDirty(period: PeriodCode) {
  editors[period].dirty = true
  editors[period].error = ''
}
function isSoldOut(dishId: number | null) {
  return dishId != null && !!catalogue.value.find((dish) => dish.id === dishId)?.soldOut
}
function validQuantity(value: number, max: number) {
  return Number.isSafeInteger(value) && value >= 0 && value <= max
}
function inputValue(editor: Editor, key: string, quantity: number) {
  return editor.inputs[key] ?? quantity
}
function readQuantity(editor: Editor, key: string, event: Event, max: number) {
  const value = (event.target as HTMLInputElement).value
  editor.inputs[key] = value
  editor.inputLimits[key] = max
  editor.dirty = true
  editor.error = ''
  if (!value.trim() || !validQuantity(Number(value), max)) {
    editor.inputErrors[key] = '请输入有效的整数份数'
    return null
  }
  delete editor.inputErrors[key]
  return Number(value)
}
function sourceInputKey(source: DraftSource, item: DraftItem) {
  return source.sourceId + '/' + item.key
}
function sourceMaximum(editor: Editor, source: DraftSource, item: DraftItem) {
  return source.sourceId === editor.chefSourceId
    ? 9999
    : Math.max(
        99,
        editor.baseline
          .find((entry) => entry.sourceId === source.sourceId)
          ?.items.find((entry) => entry.key === item.key)?.quantity || 0,
      )
}
function rebuildTotals(editor: Editor) {
  const keys = new Set([
    ...editor.items.map((item) => item.key),
    ...editor.sources.flatMap((source) => source.items.map((item) => item.key)),
  ])
  editor.items = [...keys].map((key) => ({
    ...editor.metadata[key]!,
    key,
    quantity: totalFor(editor.sources, key),
  }))
}
function changeSourceQuantity(
  order: KitchenPeriod,
  source: DraftSource,
  item: DraftItem,
  event: Event,
) {
  const editor = editors[order.period]
  if (editor.mode !== 'requests' || anySaving.value) return
  const quantity = readQuantity(
    editor,
    sourceInputKey(source, item),
    event,
    sourceMaximum(editor, source, item),
  )
  if (quantity === null) return
  const original =
    editor.baseline
      .find((entry) => entry.sourceId === source.sourceId)
      ?.items.find((entry) => entry.key === item.key)?.quantity || 0
  if (isSoldOut(item.dishId) && quantity > original) {
    editor.error = '缺货菜品不能增加份数'
    editor.inputErrors[sourceInputKey(source, item)] = editor.error
    return
  }
  item.quantity = quantity
  rebuildTotals(editor)
}
function redistribute(editor: Editor) {
  try {
    const targets = Object.fromEntries(editor.items.map((item) => [item.key, item.quantity]))
    const allocation = allocateKitchenQuantities(editor.baseline, targets, editor.chefSourceId)
    editor.sources = allocation.map((entry) => {
      const source = editor.sources.find((value) => value.sourceId === entry.sourceId)!
      return {
        ...source,
        items: entry.items.map((item) => ({
          ...(source.items.find((value) => value.key === item.key) || editor.metadata[item.key]!),
          key: item.key,
          quantity: item.quantity,
        })),
      }
    })
    delete editor.inputErrors.allocation
  } catch (error) {
    editor.error = error instanceof RangeError ? error.message : '份数无法分配，请调整后重试'
    editor.inputErrors.allocation = editor.error
  }
}
function changeMenuQuantity(order: KitchenPeriod, item: DraftItem, event: Event) {
  const editor = editors[order.period]
  if (editor.mode !== 'menu' || anySaving.value) return
  const quantity = readQuantity(editor, 'menu/' + item.key, event, 9999)
  if (quantity === null) return
  const original = editor.baseline.reduce(
    (sum, source) => sum + (source.items.find((entry) => entry.key === item.key)?.quantity || 0),
    0,
  )
  if (isSoldOut(item.dishId) && quantity > original) {
    editor.error = '缺货菜品不能增加份数'
    editor.inputErrors['menu/' + item.key] = editor.error
    return
  }
  item.quantity = quantity
  redistribute(editor)
}
function changeCustomName(order: KitchenPeriod, item: DraftItem) {
  const editor = editors[order.period]
  editor.metadata[item.key] = { ...item }
  for (const source of editor.sources)
    for (const selection of source.items) if (selection.key === item.key) selection.name = item.name
  markDirty(order.period)
}
function addCatalogueDish(order: KitchenPeriod) {
  const editor = editors[order.period]
  if (editor.mode !== 'menu' || anySaving.value) return
  const dish = availableDishes.value.find((entry) => entry.id === Number(editor.catalogueId))
  if (!dish || dish.soldOut) return
  const key = 'dish:' + dish.id
  const existing = editor.items.find((item) => item.key === key)
  if (existing) {
    existing.quantity = Math.min(9999, existing.quantity + 1)
    editor.inputs['menu/' + key] = String(existing.quantity)
    delete editor.inputErrors['menu/' + key]
  } else {
    if (editor.items.length >= 200) {
      Toast.warning('每餐最多安排 200 道菜')
      return
    }
    const item = draftItem({
      dishId: dish.id,
      name: dish.name,
      imageUrl: dish.imageUrl || '',
      quantity: 1,
      note: '',
    })
    editor.items.push(item)
    editor.metadata[key] = { ...item }
  }
  editor.catalogueId = ''
  markDirty(order.period)
  redistribute(editor)
}
function addCustomDish(order: KitchenPeriod) {
  const editor = editors[order.period]
  if (editor.mode !== 'menu' || anySaving.value) return
  if (editor.items.length >= 200) {
    Toast.warning('每餐最多安排 200 道菜')
    return
  }
  const item = draftItem({
    dishId: null,
    customId: crypto.randomUUID(),
    name: '',
    quantity: 1,
    imageUrl: '',
    note: '',
  })
  editor.items.push(item)
  editor.metadata[item.key] = { ...item }
  markDirty(order.period)
  redistribute(editor)
}
function zeroMenuItem(order: KitchenPeriod, item: DraftItem) {
  const editor = editors[order.period]
  if (editor.mode !== 'menu' || anySaving.value) return
  item.quantity = 0
  editor.inputs['menu/' + item.key] = '0'
  delete editor.inputErrors['menu/' + item.key]
  markDirty(order.period)
  redistribute(editor)
}
function visibleSources(order: KitchenPeriod) {
  const editor = editors[order.period]
  return editor.sources.filter(
    (source) => source.participantId !== null || (editor.mode && source.items.length),
  )
}
function sourceGroupLabel(order: KitchenPeriod, index: number) {
  const sources = visibleSources(order),
    source = sources[index]!
  return index === 0 || source.batchId !== sources[index - 1]?.batchId
    ? source.submitted
      ? '已提交'
      : '待提交'
    : ''
}
function displayedMenu(order: KitchenPeriod) {
  const editor = editors[order.period]
  return !editor.mode && order.menu.status === 'confirmed'
    ? order.menu.items.map(draftItem)
    : editor.items
}
function validateEditor(editor: Editor) {
  if (
    Object.entries(editor.inputs).some(
      ([key, value]) =>
        !value.trim() || !validQuantity(Number(value), editor.inputLimits[key] || 9999),
    )
  )
    return '请输入有效的整数份数'
  const inputError = Object.values(editor.inputErrors)[0]
  if (inputError) return inputError
  if (editor.items.length > 200) return '每餐最多安排 200 道菜'
  if (editor.items.some((item) => !validQuantity(item.quantity, 9999)))
    return '份数须为 0 到 9999 之间的整数'
  if (
    editor.items.some(
      (item) => item.quantity > 0 && (!item.name.trim() || item.name.trim().length > 128),
    )
  )
    return '请填写每道菜的名称，最多 128 字'
  if (editor.items.some((item) => item.note.length > 255)) return '每道菜的备注最多 255 字'
  if (editor.note.length > 1000) return '本餐备注最多 1000 字'
  return ''
}
async function confirmMenu(order: KitchenPeriod) {
  const editor = editors[order.period]
  if (anySaving.value || !editor.mode || menuChanged(order) || sourcesChanged(order)) return
  editor.error = validateEditor(editor)
  if (editor.error) return
  if (!editor.items.some((item) => item.quantity > 0)) {
    Dialog.warning({
      title: '确认' + order.periodName + '暂无菜品',
      description: '这餐将确定为暂无菜品，待提交点菜会一并提交并锁定。',
      showCancel: true,
      onConfirm: () => performConfirm(order),
    })
    return
  }
  await performConfirm(order)
}
async function performConfirm(order: KitchenPeriod) {
  const editor = editors[order.period]
  if (anySaving.value || !editor.mode || menuChanged(order) || sourcesChanged(order)) return
  const date = selectedDate.value
  const payload = {
    date,
    period: order.period,
    batchId: editor.batchId,
    stateRevision: editor.stateRevision,
    revision: editor.revision,
    contributionVersions: { ...editor.versions },
    mode: editor.mode,
    ...(editor.mode === 'requests'
      ? {
          sourceQuantities: editor.sources
            .filter((source) => source.participantId !== null || source.items.length)
            .map((source) => ({
              participantId: source.participantId,
              items: source.items.map((item) => ({ key: item.key, quantity: item.quantity })),
            })),
        }
      : {}),
    items: editor.items.map((item) => ({
      dishId: item.dishId,
      customId: item.customId ?? null,
      name: item.name.trim(),
      quantity: item.quantity,
      imageUrl: item.imageUrl,
      note: item.note,
    })),
    note: editor.note.trim(),
  }
  editor.saving = true
  editor.error = ''
  try {
    const result = await postData<{ order: KitchenPeriod }>('/kitchen/commit', payload)
    if (disposed || date !== selectedDate.value) return
    orders.value = orders.value.map((entry) =>
      entry.period === result.order.period ? result.order : entry,
    )
    applyEditor(result.order)
    Toast.success(order.periodName + '菜单已确认')
    await loadKitchen()
  } catch (error) {
    if (!disposed && date === selectedDate.value) {
      editor.error = getApiErrorMessage(error, '提交失败，请稍后重试')
      await loadKitchen()
    }
  } finally {
    editor.saving = false
  }
}
function initial(name: string) {
  return Array.from(name.trim())[0] || '?'
}
function onVisible() {
  if (document.activeElement?.matches('.kitchen-source-quantity')) return
  if (!document.hidden && !loading.value && !refreshing.value && !anySaving.value) refresh()
}
watch(
  () => route.query.date,
  (value) => {
    const date = normalizeDate(value) || chinaToday()
    if (date !== selectedDate.value && !changeDate(date))
      void router.replace({
        query: { ...route.query, date: selectedDate.value, period: selectedPeriod.value },
      })
  },
)
watch(
  () => route.query.period,
  (value) => {
    const period = normalizePeriod(value)
    if (period && !anySaving.value) {
      selectedPeriod.value = period
      periodChosen = true
    }
  },
)
onBeforeRouteLeave(
  () => !anyDirty.value || window.confirm('菜单修改还未提交，离开会放弃这些修改。是否继续？'),
)
onMounted(() => {
  void loadKitchen(true)
  void loadCatalogue()
  document.addEventListener('visibilitychange', onVisible)
  window.addEventListener('focus', onVisible)
})
onBeforeUnmount(() => {
  disposed = true
  requestSerial += 1
  document.removeEventListener('visibilitychange', onVisible)
  window.removeEventListener('focus', onVisible)
})
</script>

<template>
  <VPageHeader title="厨房工作台">
    <template #icon><RiFileList3Line /></template>
    <template #actions
      ><VButton
        class="kitchen-refresh-button"
        aria-label="刷新厨房工作台"
        title="刷新"
        :disabled="loading || refreshing || anySaving"
        @click="refresh"
        ><RiRefreshLine class="kitchen-action-icon" aria-hidden="true" /></VButton
    ></template>
  </VPageHeader>
  <div class="orders-page kitchen-page">
    <div class="kitchen-date-bar">
      <div class="kitchen-date-controls">
        <VButton :disabled="anySaving" aria-label="前一天" @click="moveDate(-1)"
          ><RiArrowLeftSLine class="kitchen-action-icon" aria-hidden="true"
        /></VButton>
        <input
          type="date"
          :value="selectedDate"
          :disabled="anySaving"
          aria-label="菜单日期"
          class="kitchen-control kitchen-date-input"
          @change="onDateInput"
        />
        <VButton :disabled="anySaving" aria-label="后一天" @click="moveDate(1)"
          ><RiArrowRightSLine class="kitchen-action-icon" aria-hidden="true"
        /></VButton>
        <VButton
          :disabled="anySaving || selectedDate === chinaToday()"
          @click="changeDate(chinaToday())"
          >今天</VButton
        >
      </div>
      <select
        class="kitchen-control kitchen-period-select"
        :value="selectedPeriod"
        aria-label="餐次"
        :disabled="loading || anySaving"
        @change="changePeriod"
      >
        <option v-for="period in PERIODS" :key="period.code" :value="period.code">
          {{ period.name }}
        </option>
      </select>
    </div>
    <p v-if="loadError" class="kitchen-alert kitchen-alert--error" role="alert">
      {{ loadError }}<span v-if="orders.length">；下方保留上次加载的内容。</span>
    </p>
    <p v-if="loading" class="kitchen-empty" role="status">加载中…</p>
    <p v-if="catalogueError" class="kitchen-alert" role="status">{{ catalogueError }}</p>
    <section
      v-for="order in selectedOrders"
      v-show="!loading"
      :key="order.period"
      class="kitchen-columns"
      :data-period="order.period"
      :aria-label="order.periodName"
    >
      <div class="kitchen-requests" data-kitchen-requests>
        <div class="kitchen-menu-heading">
          <h3 class="kitchen-region-heading">点餐记录</h3>
          <VButton
            v-if="editors[order.period].mode === 'requests'"
            size="sm"
            :disabled="anySaving"
            @click="cancelEdit(order)"
            >取消修改</VButton
          >
          <VButton
            v-else
            size="sm"
            :disabled="anySaving || !!editors[order.period].mode"
            @click="beginEdit(order, 'requests')"
            >调整点餐</VButton
          >
        </div>
        <p v-if="!visibleSources(order).length" class="kitchen-empty">还没有点餐</p>
        <template v-for="(source, sourceIndex) in visibleSources(order)" :key="source.sourceId">
          <h4 v-if="sourceGroupLabel(order, sourceIndex)" class="kitchen-group-heading">
            {{ sourceGroupLabel(order, sourceIndex) }}
          </h4>
          <article class="kitchen-person" :data-source="source.sourceId">
            <div class="kitchen-person-heading">
              <img
                v-if="source.avatarUrl && !failedAvatars.has(source.sourceId)"
                :src="source.avatarUrl"
                alt=""
                width="32"
                height="32"
                class="kitchen-avatar"
                @error="failedAvatars.add(source.sourceId)"
              />
              <span v-else class="kitchen-avatar kitchen-avatar--fallback" aria-hidden="true">{{
                initial(source.displayName)
              }}</span>
              <h5>{{ source.displayName }}</h5>
            </div>
            <ul v-if="source.items.length" class="kitchen-request-items">
              <li v-for="item in source.items" :key="item.key" :data-item="item.key">
                <span class="kitchen-request-name"
                  >{{ item.name
                  }}<small v-if="isSoldOut(item.dishId)" class="kitchen-muted"> · 缺货</small></span
                >
                <label
                  v-if="editors[order.period].mode === 'requests'"
                  class="kitchen-quantity-label"
                >
                  <input
                    class="kitchen-control kitchen-source-quantity"
                    type="number"
                    min="0"
                    :max="sourceMaximum(editors[order.period], source, item)"
                    step="1"
                    inputmode="numeric"
                    :value="
                      inputValue(editors[order.period], sourceInputKey(source, item), item.quantity)
                    "
                    :aria-label="'调整' + source.displayName + '的' + item.name + '份数'"
                    :disabled="anySaving"
                    @input="changeSourceQuantity(order, source, item, $event)"
                  />
                  份
                </label>
                <span v-else class="kitchen-quantity-label">{{ item.quantity }} 份</span>
              </li>
            </ul>
            <p v-if="source.offMenu" class="kitchen-person-note">
              <span>想吃其他菜：</span>{{ source.offMenu }}
            </p>
            <p v-if="source.remark" class="kitchen-person-note">
              <span>本餐备注：</span>{{ source.remark }}
            </p>
          </article>
        </template>
      </div>
      <VCard class="kitchen-menu-card" :body-class="[':uno: !p-0']">
        <div class="kitchen-menu-body">
          <div class="kitchen-menu-heading">
            <h3 class="kitchen-region-heading">
              最终菜单
              <span v-if="order.needsConfirmation" class="kitchen-pending" role="status"
                >待确认</span
              >
              <span
                v-else-if="order.menu.status === 'confirmed' && !editors[order.period].mode"
                class="kitchen-confirmed"
                >已确认</span
              >
            </h3>
            <VButton
              v-if="editors[order.period].mode === 'menu'"
              size="sm"
              :disabled="anySaving"
              @click="cancelEdit(order)"
              >取消修改</VButton
            >
            <VButton
              v-else
              size="sm"
              :disabled="anySaving || !!editors[order.period].mode"
              @click="beginEdit(order, 'menu')"
              >调整菜单</VButton
            >
          </div>
          <div
            v-if="editors[order.period].mode && (menuChanged(order) || sourcesChanged(order))"
            class="kitchen-alert"
            role="status"
          >
            点餐或菜单已更新，重新载入后再提交。
            <button type="button" class="kitchen-text-button" @click="cancelEdit(order)">
              载入最新
            </button>
          </div>
          <p v-if="!displayedMenu(order).length" class="kitchen-empty">尚未安排菜品</p>
          <div
            v-for="(item, index) in displayedMenu(order)"
            :key="item.key"
            class="kitchen-menu-item"
            :data-menu-item="item.key"
          >
            <input
              v-if="item.dishId === null && editors[order.period].mode === 'menu'"
              v-model="item.name"
              class="kitchen-control kitchen-item-name"
              maxlength="128"
              :aria-label="order.periodName + '第 ' + (index + 1) + ' 道菜名称'"
              placeholder="菜品名称"
              :disabled="anySaving"
              @input="changeCustomName(order, item)"
            />
            <span v-else class="kitchen-item-name">{{ item.name }}</span>
            <label v-if="editors[order.period].mode === 'menu'" class="kitchen-quantity-label">
              <input
                type="number"
                min="0"
                max="9999"
                step="1"
                inputmode="numeric"
                class="kitchen-control kitchen-menu-quantity"
                :value="inputValue(editors[order.period], 'menu/' + item.key, item.quantity)"
                :aria-label="(item.name || '菜品') + '份数'"
                :disabled="anySaving"
                @input="changeMenuQuantity(order, item, $event)"
              />
              份
            </label>
            <span v-else class="kitchen-quantity-label">{{ item.quantity }} 份</span>
            <button
              v-if="editors[order.period].mode === 'menu'"
              type="button"
              class="kitchen-remove"
              :aria-label="'移除' + (item.name || '菜品')"
              :disabled="anySaving"
              @click="zeroMenuItem(order, item)"
            >
              <RiCloseLine class="kitchen-action-icon" aria-hidden="true" />
            </button>
          </div>
          <div v-if="editors[order.period].mode === 'menu'" class="kitchen-add-row">
            <select
              v-model="editors[order.period].catalogueId"
              class="kitchen-control kitchen-catalogue-select"
              :aria-label="'为' + order.periodName + '添加菜谱里的菜'"
              :disabled="anySaving"
            >
              <option value="">从菜谱添加</option>
              <option
                v-for="dish in availableDishes"
                :key="dish.id"
                :value="String(dish.id)"
                :disabled="!!dish.soldOut"
              >
                {{ dish.name }}{{ dish.soldOut ? ' · 缺货' : '' }}
              </option>
            </select>
            <VButton
              size="sm"
              :disabled="
                anySaving ||
                !editors[order.period].catalogueId ||
                isSoldOut(Number(editors[order.period].catalogueId))
              "
              @click="addCatalogueDish(order)"
              >添加</VButton
            >
            <VButton size="sm" :disabled="anySaving" @click="addCustomDish(order)"
              >手写一道</VButton
            >
          </div>
          <label v-if="editors[order.period].mode === 'menu'" class="kitchen-note-label"
            >菜单备注<textarea
              v-model="editors[order.period].note"
              class="kitchen-control kitchen-note"
              maxlength="1000"
              rows="2"
              aria-label="最终菜单备注"
              :disabled="anySaving"
              @input="markDirty(order.period)"
            />
          </label>
          <p v-else-if="editors[order.period].note" class="kitchen-final-note">
            {{ editors[order.period].note }}
          </p>
          <p v-if="editors[order.period].error" class="kitchen-form-error" role="alert">
            {{ editors[order.period].error }}
          </p>
          <template v-if="editors[order.period].mode">
            <p v-if="order.hasDraftChanges" class="kitchen-lock-notice">
              确认时将一并提交并锁定待提交点菜。
            </p>
            <div class="kitchen-menu-actions">
              <VButton
                type="primary"
                :loading="editors[order.period].saving"
                :disabled="anySaving || menuChanged(order) || sourcesChanged(order)"
                @click="confirmMenu(order)"
                >确认{{ order.periodName }}菜单</VButton
              >
            </div>
          </template>
        </div>
      </VCard>
    </section>
  </div>
</template>

<style scoped>
.kitchen-action-icon {
  width: 16px;
  height: 16px;
}
.kitchen-page {
  min-width: 0;
}
.kitchen-refresh-button {
  width: 32px;
  height: 32px;
  padding: 0;
}
.kitchen-date-bar,
.kitchen-date-controls,
.kitchen-person-heading,
.kitchen-menu-heading,
.kitchen-add-row,
.kitchen-menu-actions,
.kitchen-quantity-label {
  display: flex;
  align-items: center;
  gap: 8px;
}
.kitchen-date-bar {
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 32px;
}
.kitchen-date-controls {
  flex-wrap: wrap;
}
.kitchen-control {
  min-width: 0;
  box-sizing: border-box;
  border: 1px solid var(--ls-dishes-line, #e8e8eb);
  border-radius: 8px;
  background: var(--ls-dishes-surface, #fff);
  color: var(--ls-dishes-ink, #29292e);
  font: inherit;
  font-size: 14px;
  line-height: 1.5;
  padding: 8px;
}
.kitchen-control:focus-visible,
.kitchen-text-button:focus-visible,
.kitchen-remove:focus-visible {
  outline: 2px solid var(--ls-dishes-gold, #946d12);
  outline-offset: 2px;
}
.kitchen-control:disabled {
  opacity: 0.65;
}
.kitchen-date-input {
  width: 152px;
  height: 32px;
  padding-block: 4px;
}
.kitchen-period-select {
  width: 96px;
  height: 32px;
  padding-block: 4px;
}
.kitchen-columns {
  display: grid;
  grid-template-columns: minmax(256px, 1fr) minmax(320px, 1.3fr);
  align-items: start;
  gap: 32px;
}
.kitchen-requests {
  min-width: 0;
}
.kitchen-region-heading {
  margin: 0;
  color: var(--ls-dishes-ink, #29292e);
  font-size: 16px;
  font-weight: 500;
  line-height: 1.5;
}
.kitchen-request-group {
  margin-top: 16px;
}
.kitchen-group-heading {
  margin: 0;
  padding-bottom: 8px;
  color: var(--ls-dishes-muted, #6b6b75);
  font-size: 12px;
  font-weight: 400;
}
.kitchen-person {
  padding-block: 16px;
  border-top: 1px solid var(--ls-dishes-line, #e8e8eb);
}
.kitchen-person-heading {
  min-width: 0;
  margin-bottom: 8px;
}
.kitchen-person-heading h5 {
  min-width: 0;
  margin: 0;
  color: var(--ls-dishes-ink, #29292e);
  font-size: 14px;
  font-weight: 500;
  overflow-wrap: anywhere;
}
.kitchen-avatar {
  display: block;
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
}
.kitchen-avatar--fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--ls-dishes-soft, #f7f7f8);
  color: var(--ls-dishes-muted, #6b6b75);
  font-size: 13px;
  font-weight: 400;
}
.kitchen-request-items {
  margin: 0;
  padding: 0;
  list-style: none;
}
.kitchen-request-items li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 8px;
}
.kitchen-request-name {
  min-width: 0;
  overflow-wrap: anywhere;
  font-size: 14px;
}
.kitchen-muted {
  color: var(--ls-dishes-muted, #6b6b75);
  font-size: 12px;
}
.kitchen-quantity-label {
  flex-shrink: 0;
  gap: 4px;
  color: var(--ls-dishes-muted, #6b6b75);
  font-size: 12px;
  white-space: nowrap;
}
.kitchen-source-quantity,
.kitchen-menu-quantity {
  width: 80px;
  height: 32px;
  padding-block: 4px;
}
.kitchen-person-note {
  margin: 8px 0 0;
  color: var(--ls-dishes-ink, #29292e);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.kitchen-person-note > span {
  color: var(--ls-dishes-muted, #6b6b75);
}
.kitchen-menu-card {
  min-width: 0;
  border: 1px solid var(--ls-dishes-line, #e8e8eb);
  border-radius: 8px;
  background: var(--ls-dishes-surface, #fff);
  box-shadow: none;
}
.kitchen-menu-body {
  padding: 16px;
}
.kitchen-region-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.kitchen-menu-heading {
  justify-content: space-between;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.kitchen-confirmed {
  margin-left: 8px;
  color: var(--ls-dishes-muted, #6b6b75);
  font-size: 12px;
  font-weight: 400;
}
.kitchen-pending {
  display: inline-flex;
  align-items: center;
  margin-left: 8px;
  padding: 4px 8px;
  border-radius: 4px;
  background: var(--ls-dishes-gold-soft, #fff8e5);
  color: var(--ls-dishes-gold, #946d12);
  font-size: 12px;
  font-weight: 400;
  line-height: 16px;
  vertical-align: middle;
}
.kitchen-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-block: 8px;
  border-bottom: 1px solid var(--ls-dishes-line, #e8e8eb);
}
.kitchen-item-name {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
  font-size: 14px;
}
.kitchen-remove {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  padding: 0;
  border: 0;
  border-radius: 4px;
  color: var(--ls-dishes-muted, #6b6b75);
  background: transparent;
  font: inherit;
  font-size: 20px;
  cursor: pointer;
}
.kitchen-remove:hover:not(:disabled) {
  color: var(--ls-dishes-danger, #b91c1c);
  background: var(--ls-dishes-soft, #f7f7f8);
}
.kitchen-add-row {
  flex-wrap: wrap;
  margin-block: 16px;
}
.kitchen-catalogue-select {
  flex: 1;
  width: 160px;
}
.kitchen-note-label {
  display: block;
  margin-top: 16px;
  color: var(--ls-dishes-ink, #29292e);
  font-size: 13px;
}
.kitchen-note {
  display: block;
  width: 100%;
  margin-top: 8px;
  resize: vertical;
}
.kitchen-lock-notice {
  margin: 8px 0 0;
  color: var(--ls-dishes-muted, #6b6b75);
  font-size: 12px;
  line-height: 1.6;
}
.kitchen-menu-actions {
  justify-content: flex-end;
  margin-top: 16px;
}
.kitchen-alert {
  margin: 0 0 16px;
  padding: 8px 16px;
  border-left: 2px solid var(--ls-dishes-highlight, #e7b63e);
  background: var(--ls-dishes-gold-soft, #fff8e5);
  color: var(--ls-dishes-ink, #29292e);
  font-size: 13px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.kitchen-alert--error,
.kitchen-form-error {
  color: var(--ls-dishes-danger, #b91c1c);
}
.kitchen-form-error {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.6;
}
.kitchen-text-button {
  border: 0;
  background: transparent;
  color: var(--ls-dishes-gold, #946d12);
  padding: 4px;
  text-decoration: underline;
  font: inherit;
  cursor: pointer;
}
.kitchen-empty {
  padding: 16px 0;
  margin: 0;
  color: var(--ls-dishes-muted, #6b6b75);
  font-size: 13px;
  line-height: 1.6;
}
.kitchen-final-row {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 16px;
  padding-block: 8px;
  color: var(--ls-dishes-ink, #29292e);
  font-size: 14px;
}
.kitchen-final-row > span:first-child {
  min-width: 0;
  overflow-wrap: anywhere;
}
.kitchen-final-row > span:last-child {
  flex-shrink: 0;
}
.kitchen-final-note {
  margin: 16px 0 0;
  padding-top: 16px;
  border-top: 1px solid var(--ls-dishes-line, #e8e8eb);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-size: 13px;
}
@media (max-width: 1023px) {
  .kitchen-columns {
    grid-template-columns: minmax(0, 1fr);
    gap: 32px;
  }
}
@media (max-width: 767px) {
  .kitchen-date-bar {
    margin-bottom: 16px;
  }
  .kitchen-source-quantity,
  .kitchen-menu-quantity {
    width: 64px;
  }
}
</style>
