<script setup lang="ts">
import KitchenIcon from '@/components/KitchenIcon.vue'
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { ApiError, apiGet, apiPost, getApiErrorMessage } from '@/api/http'
import { countSharedDishes, defaultPeriod, isDate, isPeriod, kitchenViewer, loadViewer, mealContext, mealPeriods, normalizeCustomItems, normalizeDish, periodName, quantityLabel, shanghaiToday, withinPublicCartLimit } from '@/api/kitchen'
import type { Dish, Participant, PeriodCode, SharedOrder } from '@/api/kitchen'
import { resolveMediaUrl } from '@/utils/mediaUrl'
import { stars } from '@/utils/recommendationDisplay'
import KitchenBrand from '@/components/KitchenBrand.vue'
import KitchenFooter from '@/components/KitchenFooter.vue'
import SharedParticipant from '@/components/SharedParticipant.vue'
import KitchenModal from '@/components/KitchenModal.vue'
import KitchenSupplementEditor from '@/components/KitchenSupplementEditor.vue'
import { createAutosave, createWriteSerial } from '@/utils/autosave'
import { ContextDraftStore, createRequestTracker, draftContent, draftKey, mealTagNames, isCurrentBatch, canEditSubmitted, updateSavedQuantity, freezeSubmissionPayload, catalogueIncludesDish, canIncreaseCatalogueDish, stockOrdered, isPublicQuantity } from '@/utils/kitchenDraft'
import type { KitchenDraft } from '@/utils/kitchenDraft'
import { guestNicknamePreference, normalizeGuestNickname } from '@/utils/guestNickname'

type Draft = KitchenDraft
const draftStore = new ContextDraftStore(() => window.sessionStorage)
const requests = createRequestTracker()
const route = useRoute()
const router = useRouter()
const date = ref(shanghaiToday())
const period = ref<PeriodCode>(defaultPeriod())
const dishes = ref<Dish[]>([])
const tags = ref<string[]>([])
const shared = ref<SharedOrder | null>(null)
const draftBatchId = ref('')
const batchChanged = ref(false)
const draft = reactive<Draft>({ revision: 0, items: [], offMenu: '', remark: '', nickname: '' })
const baseline = ref('')
const initialized = ref(false)
const loading = ref(true)
const saving = ref(false)
const syncing = ref(false)
const loadError = ref('')
const saveError = ref('')
const conflict = ref(false)
const sharedStale = ref(false)
const search = ref('')
const selectedTags = ref<string[]>([])
const sharedPanel = ref<HTMLElement | null>(null)
const catalogueListScroll = ref<HTMLElement | null>(null)
const categoryByContext = reactive<Record<string, string>>({})
const activeCategory = computed({
  get: () => categoryByContext[`${date.value}:${period.value}`] || '',
  set: (value: string) => { categoryByContext[`${date.value}:${period.value}`] = value },
})
const closeSharedButton = ref<HTMLButtonElement | null>(null)
const sharedOpen = ref(false)
const wideScreenQuery = window.matchMedia('(min-width: 960px)')
const wideScreen = ref(wideScreenQuery.matches)
const desktopSupplementEditor = ref<InstanceType<typeof KitchenSupplementEditor> | null>(null)
const drawerSupplementEditor = ref<InstanceType<typeof KitchenSupplementEditor> | null>(null)
let drawerReturnFocus: HTMLElement | null = null
let appInertBeforeDrawer: boolean | undefined
let alive = true
let refreshTimer: ReturnType<typeof setInterval> | undefined
let activeKey = ''
let shareMessageTimer: ReturnType<typeof setTimeout> | undefined
const shareMessage = ref('')
const drawerError = ref('')
const drawerNotice = ref('')
const drawerWriteFailed = ref(false)
const guestNickname = ref('')
const actorName = computed(() => kitchenViewer.value?.authenticated ? kitchenViewer.value.displayName : guestNickname.value.trim())
const validActor = computed(() => !!actorName.value && actorName.value.length <= 32)
const nicknameOpen = ref(false)
const nicknameInput = ref('')
const nicknameError = ref('')
let identityPromise: Promise<boolean> | null = null
let identityResolve: ((accepted: boolean) => void) | null = null
let nicknameFromDrawer = false
const confirmOpen = ref(false)
const preparingSubmission = ref(false)
const locking = ref(false)
const busyInteraction = computed(() => preparingSubmission.value || locking.value)
const writes = createWriteSerial()
const autosave = createAutosave(saveActiveDraft, 300)
let hydrating = false
interface SubmissionReview {
  payload: { date: string; period: PeriodCode; nickname: string; batchId: string; draftContributionVersions: Record<string, number> }
  participants: { displayName: string; items: { name: string; quantity: number }[]; offMenu: string; remark: string }[]
  fromDrawer: boolean
}
const submissionReview = ref<SubmissionReview | null>(null)
function applyDraftState(action: () => void) { hydrating = true; try { action() } finally { hydrating = false } }

function clone<T>(value: T): T { return JSON.parse(JSON.stringify(value)) as T }
function content(value: Draft): string {
  return draftContent(value, true)
}
const dirty = computed(() => !!baseline.value && (content(draft) !== baseline.value || ownRenameNeeded.value))
const canEdit = computed(() => !!kitchenViewer.value && date.value >= kitchenViewer.value.today && date.value <= kitchenViewer.value.maxDate)
const canEditDraft = computed(() => canEdit.value && !batchChanged.value)
const filterTags = computed(() => tags.value.filter((tag) => !mealTagNames.includes(tag)))
function canIncreaseDish(dish: Dish) { return canIncreaseCatalogueDish(dish, periodName(period.value)) }
const catalogueDishes = computed(() => {
  const selectedIds = new Set(draft.items.map((item) => item.dishId))
  const listedIds = new Set(dishes.value.map((dish) => dish.id))
  const list = dishes.value.filter((dish) => catalogueIncludesDish(dish, selectedIds, periodName(period.value)))
  for (const item of draft.items) {
    if (listedIds.has(item.dishId)) continue
    list.push({ id: item.dishId, name: item.name, imageUrl: item.imageUrl, categoryId: 0, categoryName: '已点菜品', recommendationLevel: 0, description: '', isAvailable: false, soldOut: false, tags: [...item.tags] })
  }
  return stockOrdered(list)
})
const visibleDishes = computed(() => catalogueDishes.value.filter((dish) => {
  // A refresh must not hide an already selected dish that can now only be reduced.
  if (!canIncreaseDish(dish) && draft.items.some((item) => item.dishId === dish.id)) return true
  const term = search.value.trim().toLocaleLowerCase()
  return (!term || `${dish.name} ${dish.description} ${dish.tags.join(' ')}`.toLocaleLowerCase().includes(term))
    && selectedTags.value.every((tag) => dish.tags.includes(tag))
}))
const catalogueGroups = computed(() => {
  const groups = new Map<string, { key: string; name: string; dishes: Dish[] }>()
  for (const dish of visibleDishes.value) {
    const name = dish.categoryName || '其他菜品'
    const key = `${dish.categoryId}:${name}`
    if (!groups.has(key)) groups.set(key, { key, name, dishes: [] })
    groups.get(key)!.dishes.push(dish)
  }
  return [...groups.values()]
})
const categoryDishes = computed(() => activeCategory.value ? catalogueGroups.value.find((group) => group.key === activeCategory.value)?.dishes ?? [] : visibleDishes.value)
const categoryTitle = computed(() => catalogueGroups.value.find((group) => group.key === activeCategory.value)?.name || '全部菜品')
function chooseCategory(key: string) {
  activeCategory.value = key
  catalogueListScroll.value?.scrollTo({ top: 0, behavior: 'auto' })
}
watch(catalogueGroups, (groups) => { if (activeCategory.value && !groups.some((group) => group.key === activeCategory.value)) activeCategory.value = '' })
const activeParticipants = computed(() => shared.value?.participants.filter((person) => !person.submitted && person.batchId === shared.value?.activeBatchId) ?? [])
const ownRenameNeeded = computed(() => !kitchenViewer.value?.authenticated && validActor.value && activeParticipants.value.some((person) => person.mine) && actorName.value !== shared.value?.mine.nickname)
const displayedActiveParticipants = computed(() => activeParticipants.value.map((person) => person.mine && !batchChanged.value && withinPublicCartLimit(person) ? { ...person, items: draft.items, offMenu: draft.offMenu, remark: draft.remark } : person))
const sharedDishCount = computed(() => countSharedDishes(shared.value))
const currentOrderTotals = computed(() => {
  const items = new Map<string, { key: string; name: string; quantity: number }>()
  const add = (key: string, name: string, quantity: number) => {
    const current = items.get(key)
    if (current) current.quantity += quantity
    else items.set(key, { key, name, quantity })
  }
  const selections = displayedActiveParticipants.value.map((person) => person.items)
  if (!batchChanged.value && !activeParticipants.value.some((person) => person.mine)) selections.push(draft.items)
  for (const selection of selections) {
    for (const item of selection) {
      add(`dish:${item.dishId}`, item.name, item.quantity)
    }
  }
  for (const person of displayedActiveParticipants.value) {
    for (const item of normalizeCustomItems(person.customItems)) add(`custom:${item.customId}`, item.name, item.quantity)
  }
  return [...items.values()]
})
const submittedGroups = computed(() => (shared.value?.submittedBatches ?? []).map((batch, index) => ({ ...batch, number: index + 1, participants: shared.value?.participants.filter((person) => person.submitted && person.batchId === batch.id) ?? [] })).filter((batch) => batch.participants.length > 0))
const canPrepareSubmission = computed(() => canEdit.value && !loading.value && !busyInteraction.value && !conflict.value && !batchChanged.value && (activeParticipants.value.length > 0 || dirty.value))
const draftValid = computed(() => draft.items.length <= 50 && draft.items.every((item) => isPublicQuantity(item.quantity) && item.quantity > 0))
function persistDraft() {
  if (!activeKey || !initialized.value || !baseline.value) return
  draftStore.save(activeKey, draft, baseline.value, dirty.value)
}
function editorName(order: SharedOrder): string {
  return actorName.value || order.mine.nickname
}
function serverDraft(order: SharedOrder): Draft {
  const source = order.mine
  return { revision: source.revision, items: clone(source.items), offMenu: source.offMenu, remark: source.remark, nickname: editorName(order) }
}
function restoreOwnDraft(order: SharedOrder) {
  draftBatchId.value = order.activeBatchId
  batchChanged.value = false
  activeKey = draftKey(date.value, period.value, order.activeBatchId)
  const stored = draftStore.load(activeKey)
  if (!kitchenViewer.value?.authenticated) {
    guestNickname.value = guestNicknamePreference.resolve(order.mine.nickname, stored?.draft.nickname)
  }
  const source = serverDraft(order)
  applyDraftState(() => {
    Object.assign(draft, stored?.draft || source, { nickname: editorName(order) })
    baseline.value = stored?.baseline || content(source)
  })
  conflict.value = !!stored && stored.draft.revision !== source.revision
  saveError.value = ''
}
function rebaseDraft() {
  if (!shared.value || batchChanged.value) return
  const saved = serverDraft(shared.value)
    draft.revision = saved.revision
  baseline.value = content(saved)
  conflict.value = false
  saveError.value = ''
  persistDraft()
  void scheduleAutosave()
}
function normalizeContext() {
  const viewer = kitchenViewer.value
  const requestedDate = isDate(route.query.date) ? route.query.date : viewer?.today || shanghaiToday()
  return { date: requestedDate, period: isPeriod(route.query.period) ? route.query.period : defaultPeriod() }
}
async function loadContext() {
  closeShared(false)
  autosave.cancelPending()
  const next = normalizeContext()
  persistDraft()
  date.value = next.date
  period.value = next.period
  mealContext.value = next
  loading.value = true
  loadError.value = ''
  saveError.value = ''
  conflict.value = false
  shared.value = null
  const current = requests.begin()
  try {
    const order = await apiGet<SharedOrder>(`/order?date=${encodeURIComponent(next.date)}&period=${next.period}`)
    if (!alive || !requests.isCurrent(current)) return
    // Bind the draft only after this meal has loaded successfully.
    shared.value = order
    sharedStale.value = false
    restoreOwnDraft(order)
    initialized.value = true
    persistDraft()
  } catch (e) {
    if (alive && requests.isCurrent(current)) loadError.value = getApiErrorMessage(e, '这一餐暂时无法加载，请重试')
  } finally {
    if (alive && requests.isCurrent(current)) {
      loading.value = false
      if (dirty.value && validActor.value && !conflict.value && !batchChanged.value) autosave.schedule()
    }
  }
}
function acceptShared(order: SharedOrder) {
  const rotated = !isCurrentBatch(draftBatchId.value, order.activeBatchId)
  const hadPendingEdits = dirty.value
  if (rotated) persistDraft()
  shared.value = order
  if (rotated) {
    if (hadPendingEdits) {
      batchChanged.value = true
    } else {
      restoreOwnDraft(order)
    }
    return
  }
  const saved = serverDraft(order)
  if (!dirty.value) { applyDraftState(() => { Object.assign(draft, saved); baseline.value = content(draft) }) }
  else if (saved.revision !== draft.revision) conflict.value = true
}
function startAddition() {
  if (!shared.value || saving.value) return
  persistDraft()
  autosave.cancelPending()
  requests.begin()
  restoreOwnDraft(shared.value)
}
function supplementsAreBeingEdited(person: Participant): boolean {
  return !wideScreen.value && person.mine && !person.submitted
}
function participantEditable(person: Participant) {
  return canEdit.value && withinPublicCartLimit(person) && canEditSubmitted(person.submitted)
}
function batchTime(value: string): string {
  const instant = new Date(value)
  return Number.isNaN(instant.getTime()) ? '' : new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(instant)
}
function applyCatalogue(catalogue: { items: unknown[]; tags: string[] }) {
  dishes.value = catalogue.items.map(normalizeDish)
  tags.value = [...new Set([...(catalogue.tags ?? []), ...dishes.value.flatMap((dish) => dish.tags)])]
}
async function refreshShared(manual = false) {
  if (!initialized.value || !shared.value || loadError.value || loading.value || saving.value || syncing.value || preparingSubmission.value || confirmOpen.value || nicknameOpen.value || document.hidden) return
  const current = requests.current()
  syncing.value = true
  try {
    const [orderResult, catalogueResult] = await Promise.allSettled([
      apiGet<SharedOrder>(`/order?date=${encodeURIComponent(date.value)}&period=${period.value}`),
      apiGet<{ items: unknown[]; tags: string[] }>('/dishes'),
    ])
    if (!alive || !requests.isCurrent(current)) return
    if (catalogueResult.status === 'fulfilled') applyCatalogue(catalogueResult.value)
    sharedStale.value = orderResult.status === 'rejected' || catalogueResult.status === 'rejected'
    if (orderResult.status === 'fulfilled') {
      acceptShared(orderResult.value)
      if (manual) { drawerWriteFailed.value = false; drawerError.value = ''; drawerNotice.value = '' }
    }
  } finally { if (alive) syncing.value = false }
}
async function bootstrap() {
  loading.value = true
  loadError.value = ''
  try {
    const [, catalogue] = await Promise.all([loadViewer(), apiGet<{ items: unknown[]; tags: string[] }>('/dishes')])
    if (!alive) return
    applyCatalogue(catalogue)
    await loadContext()
    if (!route.query.date || !isPeriod(route.query.period)) void router.replace({ path: '/order', query: { date: date.value, period: period.value } })
  } catch (e) {
    if (alive) { loadError.value = getApiErrorMessage(e, '菜谱暂时无法加载'); loading.value = false }
  }
}
async function changeContext(field: 'date' | 'period', event: Event) {
  const element = event.target as HTMLInputElement
  const value = element.value
  element.value = field === 'date' ? date.value : period.value
  if (field === 'date' && !isDate(value)) return
  if (field === 'period' && !isPeriod(value)) return
  if (!batchChanged.value && !await flushPending()) return
  persistDraft()
  void router.replace({ path: '/order', query: { date: date.value, period: period.value, [field]: value } })
}
function toggleTag(tag: string) {
  selectedTags.value = [tag]
}
function selected(id: number) { return draft.items.find((item) => item.dishId === id) }
function applyDishQuantity(dish: Dish, quantity: number) {
  if (!canEditDraft.value || busyInteraction.value) return
  const index = draft.items.findIndex((item) => item.dishId === dish.id)
  const previous = index >= 0 ? draft.items[index]!.quantity : 0
  if (!canIncreaseDish(dish) && quantity > previous) { saveError.value = dish.soldOut ? '这道菜已售罄，只能减量或移除。' : dish.isAvailable ? '这道菜不适用于当前餐次，只能减量或移除。' : '这道菜已下架，只能减量或移除。'; return }
  if (quantity === 0) { if (index >= 0) draft.items.splice(index, 1); return }
  if (!isPublicQuantity(quantity) || quantity < 1) return
  if (index >= 0) draft.items[index]!.quantity = quantity
  else if (draft.items.length < 50) {
    draft.items.push({ dishId: dish.id, name: dish.name, imageUrl: dish.imageUrl, quantity, note: '', tags: [...dish.tags] })
  } else saveError.value = '每次最多选择 50 道菜。'
}
function adjustDish(dish: Dish, delta: number) {
  const quantity = Number(selected(dish.id)?.quantity ?? 0)
  applyDishQuantity(dish, Math.max(0, Math.min(99, quantity + delta)))
}
function inputDishQuantity(dish: Dish, event: Event) {
  const input = event.target as HTMLInputElement
  const previous = selected(dish.id)?.quantity ?? 0
  if (input.value.trim() === '') { input.value = quantityLabel(previous); saveError.value = '份数不能为空；输入 0 或点击减号可移除。'; return }
  const quantity = Number(input.value)
  if (!isPublicQuantity(quantity)) {
    input.value = quantityLabel(previous)
    saveError.value = '份数须为 0 至 99 的整数，0 表示移除。'
    return
  }
  applyDishQuantity(dish, quantity)
  input.value = quantityLabel(selected(dish.id)?.quantity ?? 0)
}
async function ensureIdentity(force = false): Promise<boolean> {
  if (kitchenViewer.value?.authenticated) return true
  if (!force) {
    if (!validActor.value) guestNickname.value = guestNicknamePreference.resolve(shared.value?.mine.nickname, draft.nickname)
    return true
  }
  if (identityPromise) return identityPromise
  nicknameFromDrawer = sharedOpen.value
  closeShared(false)
  nicknameInput.value = guestNickname.value
  nicknameError.value = ''
  nicknameOpen.value = true
  identityPromise = new Promise<boolean>((resolve) => { identityResolve = resolve })
  return identityPromise
}
function completeNickname() {
  const value = normalizeGuestNickname(nicknameInput.value)
  if (!value) { nicknameError.value = '昵称需要 1 至 32 个字符。'; return }
  guestNickname.value = guestNicknamePreference.set(value)
  const resolve = identityResolve
  identityResolve = null
  identityPromise = null
  nicknameOpen.value = false
  void nextTick(() => {
    if (nicknameFromDrawer) openShared()
    resolve?.(true)
    if (dirty.value && canEditDraft.value && !conflict.value) autosave.schedule()
  })
}
function cancelNickname() {
  const resolve = identityResolve
  identityResolve = null
  identityPromise = null
  nicknameOpen.value = false
  void nextTick(() => { if (nicknameFromDrawer) openShared(); resolve?.(false) })
}
async function scheduleAutosave() {
  if (!initialized.value || hydrating || loading.value || !dirty.value || !canEditDraft.value || conflict.value) return
  if (!await ensureIdentity()) { saveError.value = '设置点餐昵称后才能同步，当前选择仍保留。'; return }
  autosave.schedule()
}
async function flushPending(): Promise<boolean> {
  if (batchChanged.value) { persistDraft(); return false }
  if (!dirty.value) { await writes.idle(); return true }
  if (!await ensureIdentity()) return false
  autosave.schedule()
  try { await autosave.flush(); await writes.idle(); return !dirty.value && !conflict.value && !batchChanged.value } catch { return false }
}
async function saveActiveDraft(): Promise<void> {
  await writes.run(async () => {
    if (!dirty.value) return
    if (!canEditDraft.value || !draftValid.value || conflict.value || !shared.value || !validActor.value) throw new Error('当前点菜暂时不能同步')
    saving.value = true
    saveError.value = ''
    const current = requests.begin()
    const submittedKey = activeKey
    const snapshot = clone(draft)
    const fingerprint = content(snapshot)
    const payload = { date: date.value, period: period.value, nickname: actorName.value, revision: snapshot.revision, batchId: draftBatchId.value, items: snapshot.items.map((item) => ({ dishId: item.dishId, quantity: Number(item.quantity), note: item.note })), offMenu: snapshot.offMenu, remark: snapshot.remark }
    try {
      await loadViewer(true)
      const response = await apiPost<{ order: SharedOrder }>('/cart', payload)
      draftStore.clear(submittedKey)
      if (!alive || !requests.isCurrent(current)) return
      shared.value = response.order
      const saved = serverDraft(response.order)
      applyDraftState(() => {
        if (content(draft) === fingerprint) Object.assign(draft, saved)
        else draft.revision = saved.revision
        baseline.value = content(saved)
      })
      conflict.value = false
      sharedStale.value = false
      persistDraft()
    } catch (e) {
      if (alive && requests.isCurrent(current)) {
        saveError.value = getApiErrorMessage(e, '同步失败，当前修改仍保留，请重试。')
        if (e instanceof ApiError && e.status === 409) {
          conflict.value = true
          try { acceptShared(await apiGet<SharedOrder>(`/order?date=${encodeURIComponent(payload.date)}&period=${payload.period}`)) } catch { /* Preserve local fields. */ }
          saveError.value = '这份点菜有新的变化，请核对后继续同步；不会自动覆盖或追加到新批次。'
        }
      }
      throw e
    } finally { if (alive) saving.value = false }
  })
}
async function prepareSubmission(fromDrawer = false) {
  if (!canPrepareSubmission.value) return
  if (!await ensureIdentity()) return
  if (drawerWriteFailed.value) { saveError.value = '还有份数调整未成功，请重试该调整，或刷新并核对服务器内容后再提交。'; return }
  preparingSubmission.value = true
  saveError.value = ''
  try {
    if (!await flushPending()) return
    const order = await apiGet<SharedOrder>(`/order?date=${encodeURIComponent(date.value)}&period=${period.value}`)
    acceptShared(order)
    if (batchChanged.value || conflict.value || dirty.value || !activeParticipants.value.length) { saveError.value = '请核对当前待提交内容，再提交点餐。'; return }
    submissionReview.value = {
      payload: freezeSubmissionPayload({ date: date.value, period: period.value, nickname: actorName.value, batchId: order.activeBatchId, draftContributionVersions: clone(order.draftContributionVersions) }),
      participants: activeParticipants.value.map((person) => ({ displayName: person.displayName, items: person.items.map((item) => ({ name: item.name, quantity: item.quantity })), offMenu: person.offMenu, remark: person.remark })),
      fromDrawer,
    }
    closeShared(false)
    confirmOpen.value = true
  } catch (e) { saveError.value = getApiErrorMessage(e, '无法准备提交，请刷新后重试。') }
  finally { preparingSubmission.value = false }
}
function cancelSubmission() {
  if (locking.value) return
  const fromDrawer = submissionReview.value?.fromDrawer
  confirmOpen.value = false
  submissionReview.value = null
  if (fromDrawer) void nextTick(() => { openShared() })
}
async function confirmSubmission() {
  const review = submissionReview.value
  if (!review || locking.value) return
  locking.value = true
  const current = requests.begin()
  const submittedKey = activeKey
  try {
    const result = await writes.run(() => apiPost<{ order: SharedOrder }>('/submit', clone(review.payload)))
    draftStore.clear(submittedKey)
    if (!alive || !requests.isCurrent(current)) return
    shared.value = result.order
    activeKey = ''
    restoreOwnDraft(result.order)
    autosave.cancelPending()
    saveError.value = ''
    conflict.value = false
    confirmOpen.value = false
    submissionReview.value = null
    if (review.fromDrawer) void nextTick(() => { openShared() })
  } catch (e) {
    if (!alive || !requests.isCurrent(current)) return
    saveError.value = getApiErrorMessage(e, '提交失败，请重新核对共享单。')
    if (e instanceof ApiError && e.status === 409) {
      try { acceptShared(await apiGet<SharedOrder>(`/order?date=${encodeURIComponent(review.payload.date)}&period=${review.payload.period}`)) } catch { /* Review is still invalidated. */ }
      saveError.value = '共享单已有变化，请重新核对并确认提交；本次没有锁单。'
    }
    confirmOpen.value = false
    submissionReview.value = null
    if (review.fromDrawer) void nextTick(() => { openShared() })
  } finally { if (alive) locking.value = false }
}
function canIncreaseSharedDish(dishId: number): boolean {
  const dish = dishes.value.find((entry) => entry.id === dishId)
  return !!dish && canIncreaseDish(dish)
}
async function saveParticipantQuantity(person: Participant, dishId: number, quantity: number) {
  if (!shared.value || saving.value || busyInteraction.value || !participantEditable(person) || !isPublicQuantity(quantity)) return
  drawerError.value = ''
  drawerNotice.value = ''
  if (!await ensureIdentity()) { drawerError.value = '设置昵称后才能调整，服务器数量没有改变。'; return }
  const originalItem = person.items.find((item) => item.dishId === dishId)
  if (!originalItem || (quantity > originalItem.quantity && !canIncreaseSharedDish(dishId))) return
  if (person.mine && !batchChanged.value) {
    const updated = updateSavedQuantity(clone(draft), dishId, quantity)
    Object.assign(draft, updated)
    if (await flushPending()) drawerNotice.value = ''
    else drawerError.value = saveError.value || '同步失败，修改仍保留。'
    return
  }
  const source: Draft = { revision: person.revision, items: clone(person.items), offMenu: person.offMenu || '', remark: person.remark || '', nickname: actorName.value }
  const updated = updateSavedQuantity(source, dishId, quantity)
  const payload = { date: date.value, period: period.value, batchId: shared.value.activeBatchId, targetParticipantId: person.id, revision: person.revision, nickname: actorName.value, items: updated.items.map((item) => ({ dishId: item.dishId, quantity: item.quantity, note: item.note })), offMenu: person.offMenu || '', remark: person.remark || '' }
  const hadOwnPendingEdits = dirty.value
  await writes.run(async () => {
      const current = requests.begin()
      saving.value = true
      try {
        const result = await apiPost<{ order: SharedOrder }>('/cart', payload)
        if (!alive || !requests.isCurrent(current)) return
        acceptShared(result.order)
        drawerWriteFailed.value = false
        drawerNotice.value = hadOwnPendingEdits && conflict.value ? '主表单存在冲突，请核对后继续。' : ''
      } catch (e) {
        if (alive && requests.isCurrent(current)) {
          drawerWriteFailed.value = true
          drawerError.value = getApiErrorMessage(e, '份数未同步，仍显示服务器原值，请重试。')
          if (e instanceof ApiError && e.status === 409) {
            try { acceptShared(await apiGet<SharedOrder>(`/order?date=${encodeURIComponent(payload.date)}&period=${payload.period}`)) } catch { /* Keep saved values. */ }
            drawerError.value = '这份点菜已有新版本，请核对后再调整。'
          }
        }
      } finally { if (alive) saving.value = false }
  })
}
async function shareMeal() {
  const target = router.resolve({ path: '/order', query: { date: date.value, period: period.value } })
  const url = new URL(target.href, window.location.origin).href
  try {
    await navigator.clipboard.writeText(url)
    shareMessage.value = '这一餐的链接已复制'
  } catch {
    shareMessage.value = '可复制地址栏链接，邀请大家一起点菜。'
  }
  clearTimeout(shareMessageTimer)
  shareMessageTimer = setTimeout(() => { shareMessage.value = '' }, 4000)
}
function openShared() {
  if (sharedOpen.value) return
  if (!drawerWriteFailed.value) drawerError.value = ''
  drawerNotice.value = ''
  drawerReturnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
  const app = document.getElementById('app')
  if (app) { appInertBeforeDrawer = app.inert; app.inert = true }
  sharedOpen.value = true
  void nextTick(() => { closeSharedButton.value?.focus() })
}
function closeShared(restoreFocus = true) {
  sharedOpen.value = false
  const app = document.getElementById('app')
  if (app && appInertBeforeDrawer !== undefined) app.inert = appInertBeforeDrawer
  appInertBeforeDrawer = undefined
  if (restoreFocus && drawerReturnFocus?.isConnected) {
    const target = drawerReturnFocus
    void nextTick(() => { if (target.isConnected) target.focus({ preventScroll: true }) })
  }
}
function onDrawerKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); closeShared(); return }
  if (event.key !== 'Tab' || !sharedPanel.value) return
  const controls = [...sharedPanel.value.querySelectorAll<HTMLElement>('button:not([disabled]), a[href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])')].filter((element) => element.getClientRects().length > 0)
  const first = controls[0]
  const last = controls[controls.length - 1]
  if (!first || !last) { event.preventDefault(); sharedPanel.value.focus(); return }
  if (event.shiftKey && (document.activeElement === first || !sharedPanel.value.contains(document.activeElement))) { event.preventDefault(); last.focus() }
  else if (!event.shiftKey && (document.activeElement === last || !sharedPanel.value.contains(document.activeElement))) { event.preventDefault(); first.focus() }
}
watch(draft, persistDraft, { deep: true })
watch(() => content(draft), () => { if (!hydrating) void scheduleAutosave() }, { flush: 'sync' })
onBeforeRouteUpdate(async (to) => {
  if (to.query.date === route.query.date && to.query.period === route.query.period) return true
  const targetDate = isDate(to.query.date) ? to.query.date : kitchenViewer.value?.today || shanghaiToday()
  const targetPeriod = isPeriod(to.query.period) ? to.query.period : defaultPeriod()
  if (targetDate === date.value && targetPeriod === period.value) return true
  return batchChanged.value ? (persistDraft(), true) : await flushPending()
})
onBeforeRouteLeave(async () => batchChanged.value ? (persistDraft(), true) : await flushPending())
watch(() => [route.query.date, route.query.period], () => {
  if (!initialized.value) return
  const next = normalizeContext()
  if (shared.value && shared.value.date === next.date && shared.value.period === next.period) return
  void loadContext()
})
function scheduleRefresh() {
  clearInterval(refreshTimer)
  refreshTimer = undefined
  if (!document.hidden && alive) refreshTimer = setInterval(() => { void refreshShared() }, 10_000)
}
function onVisible() {
  scheduleRefresh()
  if (!document.hidden) void refreshShared()
}
function onViewportChange(event: MediaQueryListEvent) {
  const focused = document.activeElement
  const field = focused instanceof HTMLTextAreaElement ? focused.dataset.kSupplementField : undefined
  const selection = focused instanceof HTMLTextAreaElement ? [focused.selectionStart, focused.selectionEnd] as const : undefined
  wideScreen.value = event.matches
  if (!field) return
  // Move the active editor with its text selection; the draft and save queue stay in this view.
  if (event.matches) closeShared(false)
  else openShared()
  void nextTick(() => {
    const input = (event.matches ? desktopSupplementEditor.value : drawerSupplementEditor.value)?.field(field)
    if (!input || input.disabled) return
    input.focus({ preventScroll: true })
    if (selection) input.setSelectionRange(selection[0], selection[1])
  })
}
onMounted(() => { void bootstrap(); scheduleRefresh(); window.addEventListener('focus', onVisible); document.addEventListener('visibilitychange', onVisible); wideScreenQuery.addEventListener('change', onViewportChange) })
onUnmounted(() => { persistDraft(); autosave.dispose(); identityResolve?.(false); closeShared(false); alive = false; requests.begin(); clearInterval(refreshTimer); clearTimeout(shareMessageTimer); window.removeEventListener('focus', onVisible); document.removeEventListener('visibilitychange', onVisible); wideScreenQuery.removeEventListener('change', onViewportChange) })
</script>

<template>
  <div class="k-page-scroll">
    <KitchenBrand />
    <div class="k-page-content k-order-content">
      <section class="k-page-heading k-order-heading">
        <div><p class="k-eyebrow">WHAT'S ON THE MENU</p><h2>看看吃啥</h2><p class="k-order-subtitle">让我看看，有什么好吃的？</p></div>
        <button type="button" class="k-share-action" @click="shareMeal"><KitchenIcon name="share-line" />分享</button>
      </section>
      <p v-if="shareMessage" class="k-hint k-share-feedback" role="status">{{ shareMessage }}</p>
      <div class="k-context-bar">
        <label class="k-context-field"><span>用餐日期</span><input type="date" :value="date" :min="kitchenViewer?.today" :max="kitchenViewer?.maxDate" :disabled="busyInteraction || loading" @change="changeContext('date', $event)" /></label>
        <label class="k-context-field"><span>餐次</span><select :value="period" :disabled="busyInteraction || loading" @change="changeContext('period', $event)"><option v-for="meal in mealPeriods" :key="meal.code" :value="meal.code">{{ meal.name }}</option></select></label>
        <div class="k-actor-line"><span>点餐人：</span><span v-if="kitchenViewer?.authenticated">{{ actorName }}</span><button v-else type="button" class="k-text-link" :disabled="busyInteraction || loading" @click="ensureIdentity(true)">{{ actorName || '设置昵称' }}<KitchenIcon name="edit-line" /></button></div>
      </div>
      <div v-if="loading" class="k-empty" role="status">正在准备这一餐…</div>
      <div v-else-if="loadError" class="k-notice k-notice--error" role="alert"><p>{{ loadError }}</p><button class="k-button k-button--secondary" @click="bootstrap">重新加载</button></div>
      <template v-else-if="shared">
        <p v-if="!canEdit" class="k-notice">这个日期只能查看。请选择今天至 {{ kitchenViewer?.maxDate }} 之间的日期来点菜。</p>
        <div class="k-workspace">
          <section class="k-catalogue" aria-labelledby="catalogue-title" tabindex="-1">
            <div class="k-section-heading"><h3 id="catalogue-title">菜谱</h3></div>
            <div class="k-catalogue-tools">
              <label class="k-search"><KitchenIcon name="search-line" /><span class="sr-only">搜索菜名、描述或标签</span><input v-model="search" type="search" placeholder="搜索想吃的菜…" /></label>

            </div>
            <div v-if="filterTags.length" class="k-tags" role="group" aria-label="按标签筛选，单选">
              <button type="button" class="k-tag" :aria-pressed="!selectedTags.length" @click="selectedTags = []">全部</button>
              <button v-for="tag in filterTags" :key="tag" type="button" class="k-tag" :aria-pressed="selectedTags.includes(tag)" @click="toggleTag(tag)">{{ tag }}</button>
            </div>
            <div class="k-category-menu">
              <nav class="k-category-rail" aria-label="菜品分类，单选">
                <button type="button" :class="!activeCategory ? 'is-active' : ''" :aria-pressed="!activeCategory" @click="chooseCategory('')"><span>全部</span></button>
                <button v-for="group in catalogueGroups" :key="group.key" type="button" :class="activeCategory === group.key ? 'is-active' : ''" :aria-pressed="activeCategory === group.key" @click="chooseCategory(group.key)"><span>{{ group.name }}</span></button>
              </nav>
              <div ref="catalogueListScroll" class="k-category-dishes">
                <div class="k-category-dishes__heading"><span>{{ categoryTitle }}</span><small>{{ categoryDishes.length }} 道</small></div>
                <p v-if="!catalogueDishes.length" class="k-empty k-empty--small">暂无菜品</p>
                <p v-else-if="!categoryDishes.length" class="k-empty k-empty--small">没有找到符合条件的菜品。<button class="k-text-link" @click="search = ''; selectedTags = []; chooseCategory('')">清除筛选</button></p>
                <ul class="k-catalogue-list">
              <li v-for="dish in categoryDishes" :key="dish.id">
                <div class="k-catalogue-dish" :class="selected(dish.id) ? 'is-selected' : ''">
                  <img v-if="dish.imageUrl" :src="resolveMediaUrl(dish.imageUrl)" :alt="dish.name" loading="lazy" width="80" height="80" />
                  <span v-else class="k-dish-placeholder" aria-hidden="true">食</span>
                  <div class="k-grow"><span class="k-dish-name" :title="dish.name">{{ dish.name }}</span><span v-if="dish.recommendationLevel > 0" class="k-stars" :aria-label="`推荐等级 ${dish.recommendationLevel} 星`">{{ stars(dish.recommendationLevel) }}</span><span v-if="!dish.soldOut && !canIncreaseDish(dish)" class="k-dish-unavailable">{{ dish.isAvailable ? '当前餐次不可新增，仅可减量或移除' : '已下架，仅可减量或移除' }}</span><span v-if="dish.description" class="k-dish-description" :title="dish.description">{{ dish.description }}</span><span v-if="dish.tags.some((tag) => !mealTagNames.includes(tag))" class="k-dish-tags" :title="dish.tags.filter((tag) => !mealTagNames.includes(tag)).join(' · ')">{{ dish.tags.filter((tag) => !mealTagNames.includes(tag)).join(' · ') }}</span></div>
                  <div class="k-catalogue-stepper" role="group" :aria-label="`${dish.name} 份数`">
                    <button v-if="selected(dish.id)" type="button" :disabled="!canEditDraft || busyInteraction" :aria-label="`减少 ${dish.name} 份数，减到 0 移除`" @click="adjustDish(dish, -1)"><KitchenIcon name="subtract-line" /></button>
                    <input v-if="selected(dish.id)" type="number" min="0" :max="canIncreaseDish(dish) ? 99 : selected(dish.id)?.quantity ?? 0" step="1" :value="quantityLabel(selected(dish.id)?.quantity ?? 0)" :disabled="!canEditDraft || busyInteraction" :aria-label="`${dish.name} 份数，0 表示移除`" @change="inputDishQuantity(dish, $event)" />
                    <span v-if="dish.soldOut" class="k-stock-state">售罄</span><button v-else type="button" :disabled="!canEditDraft || busyInteraction || !canIncreaseDish(dish) || (selected(dish.id)?.quantity ?? 0) >= 99" :aria-label="`增加 ${dish.name} 份数`" @click="adjustDish(dish, 1)"><KitchenIcon name="add-line" /></button>
                  </div>
                </div>
              </li>
                </ul>
              </div>
            </div>
          </section>
          <section class="k-own-panel" aria-label="当前点餐">
            <div v-if="wideScreen || batchChanged || saveError || conflict" class="k-own-body">
              <section v-if="wideScreen && currentOrderTotals.length" class="k-current-summary" aria-label="当前点餐"><h3>当前点餐</h3><ul><li v-for="item in currentOrderTotals" :key="item.key"><span :title="item.name">{{ item.name }}</span><span>× {{ quantityLabel(item.quantity) }}</span></li></ul></section>
              <KitchenSupplementEditor v-if="wideScreen" ref="desktopSupplementEditor" v-model:off-menu="draft.offMenu" v-model:remark="draft.remark" :disabled="busyInteraction || !canEditDraft" @blur="flushPending()" />
              <div v-if="batchChanged" class="k-notice k-notice--warning"><p>这个批次已提交，不能再修改。旧批次本地修改不会自动带入加点。</p><button type="button" class="k-text-link" :disabled="saving" @click="startAddition">开始新的待提交加点</button></div>
              <div v-if="saveError" class="k-notice k-notice--error" role="alert"><p>{{ saveError }}</p><button v-if="!conflict && !batchChanged" type="button" class="k-text-link" @click="flushPending()">重试同步</button></div>
              <div v-if="conflict && !batchChanged" class="k-notice k-notice--warning"><p>这份点菜已有新版本，本地修改已保留。</p><button type="button" class="k-text-link" @click="rebaseDraft">核对后，按最新版本同步这些修改</button></div>
            </div>
            <footer class="k-own-footer">
              <button type="button" class="k-button k-button--primary k-save-button k-wide-submit" :disabled="!canPrepareSubmission" @click="prepareSubmission(false)"><KitchenIcon name="send-plane-line" />提交点餐</button>
              <button type="button" class="k-button k-button--primary k-save-button k-mobile-submit" :disabled="busyInteraction" @click="openShared">去提交 <KitchenIcon name="arrow-right-s-line" /></button>
            </footer>
          </section>

        </div>
        <button type="button" class="k-shared-fab" :aria-label="`查看点餐单，共 ${sharedDishCount} 道菜`" :aria-expanded="sharedOpen" aria-controls="k-shared-drawer" title="查看点餐单" @click="openShared">
          <KitchenIcon name="group-line" :size="24" />
          <span class="k-shared-fab__count" aria-hidden="true">{{ sharedDishCount > 99 ? '99+' : sharedDishCount }}</span>
        </button>
        <Teleport to="body">
          <Transition name="k-shared-drawer">
            <div v-if="sharedOpen" class="k-drawer-root">
              <button type="button" class="k-drawer-backdrop" aria-label="点击遮罩关闭" tabindex="-1" @click="closeShared()" />
          <section id="k-shared-drawer" ref="sharedPanel" class="k-shared-panel k-drawer-panel" role="dialog" aria-modal="true" aria-labelledby="shared-title" tabindex="-1" @keydown="onDrawerKeydown">
            <div class="k-section-heading"><h3 id="shared-title">点餐单</h3><div class="k-shared-actions"><button type="button" class="k-icon-button" :disabled="syncing || saving || busyInteraction" :aria-busy="syncing" aria-label="刷新" title="刷新" @click="refreshShared(true)"><KitchenIcon name="refresh-line" /></button><button ref="closeSharedButton" type="button" class="k-icon-button" aria-label="关闭点餐单" title="关闭" @click="closeShared()"><KitchenIcon name="close-line" /></button></div></div>
            <div class="k-drawer-content">
              <p v-if="sharedStale" class="k-hint k-pending-hint">菜谱或共享内容暂未更新，你的修改仍保留。</p>

              <p v-if="drawerError" class="k-notice k-notice--error" role="alert">{{ drawerError }}</p>
              <p v-if="drawerNotice" class="k-save-feedback" role="status">{{ drawerNotice }}</p>
              <p v-if="!shared.participants.length" class="k-hint k-shared-empty">还没有点餐</p>

              <section v-if="activeParticipants.length" class="k-batch-group k-batch-group--pending"><div class="k-section-heading"><h4>待提交</h4></div><ul class="k-participants"><SharedParticipant v-for="person in displayedActiveParticipants" :key="person.id" :person="person" :editable="participantEditable(person)" :busy="saving || busyInteraction" :can-increase="canIncreaseSharedDish" :hide-supplements="supplementsAreBeingEdited(person)" @quantity="saveParticipantQuantity" @invalid="drawerError = $event" /></ul></section>
              <KitchenSupplementEditor v-if="!wideScreen" ref="drawerSupplementEditor" v-model:off-menu="draft.offMenu" v-model:remark="draft.remark" :disabled="busyInteraction || !canEditDraft" @blur="flushPending()" />
              <section v-for="batch in submittedGroups" :key="batch.id" class="k-batch-group k-batch-group--submitted"><div class="k-section-heading"><h4>第 {{ batch.number }} 批</h4><span class="k-hint">{{ batchTime(batch.submittedAt) }}</span></div><ul class="k-participants"><SharedParticipant v-for="person in batch.participants" :key="person.id" :person="person" :editable="participantEditable(person)" :busy="saving || busyInteraction" :can-increase="canIncreaseSharedDish" :hide-supplements="supplementsAreBeingEdited(person)" @quantity="saveParticipantQuantity" @invalid="drawerError = $event" /></ul></section>


            </div>
            <footer v-if="activeParticipants.length || (dirty && !batchChanged)" class="k-drawer-footer"><p v-if="saveError" class="k-notice k-notice--error" role="alert">{{ saveError }}</p><button type="button" class="k-button k-button--primary k-save-button" :disabled="!canPrepareSubmission" @click="prepareSubmission(true)"><KitchenIcon name="send-plane-line" />提交点餐</button></footer>
          </section>
            </div>
          </Transition>
        </Teleport>
      </template>
      <KitchenFooter />
    </div>
    <KitchenModal :model-value="nicknameOpen" title="设置点餐昵称" @cancel="cancelNickname">
      <label class="k-field k-modal-nickname"><span>昵称</span><input v-model="nicknameInput" data-autofocus maxlength="32" autocomplete="nickname" placeholder="1 至 32 个字符" @keyup.enter="completeNickname" /></label>
      <p v-if="nicknameError" class="k-notice k-notice--error" role="alert">{{ nicknameError }}</p>
      <template #actions><button type="button" class="k-button k-button--secondary" @click="cancelNickname">取消</button><button type="button" class="k-button k-button--primary" @click="completeNickname">确定</button></template>
    </KitchenModal>
    <KitchenModal :model-value="confirmOpen" title="提交点餐单" :busy="locking" @cancel="cancelSubmission">
      <p class="k-submit-explanation">提交后，本餐所有人的点菜仅主厨可修改。继续点菜将作为加点。</p>
      <p v-if="submissionReview" class="k-hint">{{ submissionReview.payload.date }} · {{ periodName(submissionReview.payload.period) }} · {{ submissionReview.participants.length }} 份点菜</p>
      <ul v-if="submissionReview" class="k-submit-review"><li v-for="(person, index) in submissionReview.participants" :key="index"><h4>{{ person.displayName }}</h4><p v-for="(item, itemIndex) in person.items" :key="itemIndex">{{ item.name }} <span>× {{ quantityLabel(item.quantity) }}</span></p><p v-if="person.offMenu">想吃其他菜：{{ person.offMenu }}</p><p v-if="person.remark">本餐备注：{{ person.remark }}</p></li></ul>
      <template #actions><button type="button" class="k-button k-button--secondary" data-autofocus :disabled="locking" @click="cancelSubmission">取消</button><button type="button" class="k-button k-button--primary" :disabled="locking" @click="confirmSubmission">{{ locking ? '正在提交…' : '确认提交点餐' }}</button></template>
    </KitchenModal>
  </div>
</template>
