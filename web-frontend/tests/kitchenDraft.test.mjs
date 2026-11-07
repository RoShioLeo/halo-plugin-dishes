import test from 'node:test'
import assert from 'node:assert/strict'
import { ContextDraftStore, createRequestTracker, draftContent, draftKey, applicableToMeal, mealTagNames, isCurrentBatch, canEditSubmitted, updateSavedQuantity, freezeSubmissionPayload, catalogueIncludesDish, canIncreaseCatalogueDish, stockOrdered, isPublicQuantity } from '../src/utils/kitchenDraft.ts'
import { createGuestNicknamePreference, GUEST_NICKNAME_KEY } from '../src/utils/guestNickname.ts'
import { mealPeriodAt, preferredMealPeriod } from '../src/utils/mealSchedule.ts'

const draft = (name = '番茄炒蛋', id = 1) => ({ revision: 0, items: [{ dishId: id, name, imageUrl: '', quantity: 1, note: '', tags: ['家常'] }], offMenu: '', remark: '', nickname: '小柠檬' })

test('default meal cutoffs use Shanghai minutes at 10:00 and 15:00', () => {
  const period = (time) => mealPeriodAt(new Date(`2026-11-05T${time}:00+08:00`))
  assert.equal(period('09:59'), 'breakfast')
  assert.equal(period('10:00'), 'lunch')
  assert.equal(period('14:59'), 'lunch')
  assert.equal(period('15:00'), 'dinner')
})

test('configured cutoffs preserve minute boundaries and an invalid configuration falls back to defaults', () => {
  const schedule = { lunchStart: '09:30', dinnerStart: '14:30' }
  const at = (time) => new Date(`2026-11-05T${time}:00+08:00`)
  assert.equal(mealPeriodAt(at('09:29'), schedule), 'breakfast')
  assert.equal(mealPeriodAt(at('09:30'), schedule), 'lunch')
  assert.equal(mealPeriodAt(at('14:29'), schedule), 'lunch')
  assert.equal(mealPeriodAt(at('14:30'), schedule), 'dinner')
  assert.equal(mealPeriodAt(at('14:30'), { lunchStart: '15:00', dinnerStart: '10:00' }), 'lunch')
})

test('explicit and already selected meal contexts take priority over a refreshed default', () => {
  assert.equal(preferredMealPeriod('breakfast', 'dinner'), 'breakfast')
  assert.equal(preferredMealPeriod(undefined, 'lunch'), 'lunch')
  assert.equal(preferredMealPeriod('invalid', 'dinner'), 'dinner')
  assert.equal(preferredMealPeriod(undefined, undefined), undefined)
})
test('public quantities accept only integer counts from zero through 99', () => {
  for (const value of [0, 1, 99]) assert.equal(isPublicQuantity(value), true)
  for (const value of [0.015, 1.5, -1, 100, Number.NaN, Number.POSITIVE_INFINITY]) {
    assert.equal(isPublicQuantity(value), false)
    assert.throws(() => updateSavedQuantity(draft(), 1, value))
  }
})
function memoryStorage() {
  const values = new Map()
  return { values, getItem: (key) => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: (key) => values.delete(key) }
}

test('an anonymous default nickname is generated once and remembered across page instances', () => {
  const memory = memoryStorage()
  let randomCalls = 0
  const random = () => { randomCalls++; return 0.5 }
  const preference = createGuestNicknamePreference(() => memory, random)
  const name = preference.resolve()
  assert.match(name, /^访客\d{4}$/)
  assert.equal(preference.resolve(), name)
  assert.equal(createGuestNicknamePreference(() => memory, random).resolve(), name)
  assert.equal(memory.getItem(GUEST_NICKNAME_KEY), name)
  assert.equal(randomCalls, 1)
})

test('existing local, server and pending-draft nicknames take priority over a random default', () => {
  const noRandom = () => { throw new Error('Existing names must not be replaced') }
  const local = memoryStorage()
  local.setItem(GUEST_NICKNAME_KEY, '小柠檬')
  assert.equal(createGuestNicknamePreference(() => local, noRandom).resolve('服务端昵称', '草稿昵称'), '小柠檬')
  assert.equal(createGuestNicknamePreference(() => memoryStorage(), noRandom).resolve('服务端昵称', '草稿昵称'), '服务端昵称')
  assert.equal(createGuestNicknamePreference(() => memoryStorage(), noRandom).resolve('', '草稿昵称'), '草稿昵称')
})

test('denied local storage keeps the default and a manually chosen nickname usable in page memory', () => {
  let randomCalls = 0
  const preference = createGuestNicknamePreference(() => { throw new Error('Storage denied') }, () => { randomCalls++; return 0 })
  assert.equal(preference.resolve(), '访客1000')
  assert.equal(preference.resolve(), '访客1000')
  assert.equal(preference.set('薄荷汽水'), '薄荷汽水')
  assert.equal(preference.resolve(), '薄荷汽水')
  assert.equal(randomCalls, 1)
})

test('switching meal contexts never copies A selections into B, and returning restores A pending edits', () => {
  const storage = memoryStorage()
  const store = new ContextDraftStore(() => storage)
  const a = draft()
  a.items[0].note = '不要香菜'
  const aContext = '2026-11-05:lunch'
  const bContext = '2026-11-05:dinner'
  store.save(aContext, a, 'previously saved A', true)
  assert.equal(store.load(bContext), null)
  const b = draft('清蒸鱼', 2)
  store.save(bContext, b, 'previously saved B', true)
  assert.deepEqual(store.load(aContext).draft, a)
  assert.deepEqual(store.load(bContext).draft, b)
  store.clear(bContext)
  assert.equal(store.load(bContext), null)
  assert.deepEqual(store.load(aContext).draft.items[0].note, '不要香菜')
})

test('draft snapshots are independent objects and clean state removes only the current context', () => {
  const store = new ContextDraftStore(() => memory)
  const memory = memoryStorage()
  const a = draft()
  store.save('a', a, 'baseline', true)
  const restored = store.load('a')
  restored.draft.items[0].quantity = 99
  assert.equal(store.load('a').draft.items[0].quantity, 1)
  store.save('b', draft('鱼', 2), 'b', true)
  store.save('a', a, 'baseline', false)
  assert.equal(store.load('a'), null)
  assert.equal(store.load('b').draft.items[0].dishId, 2)
})

test('a save invalidates an earlier in-flight shared GET and a context switch invalidates the save response', () => {
  const requests = createRequestTracker()
  const contextA = requests.begin()
  const oldSharedGet = requests.current()
  assert.equal(oldSharedGet, contextA)
  const save = requests.begin()
  assert.equal(requests.isCurrent(oldSharedGet), false)
  assert.equal(requests.isCurrent(save), true)
  requests.begin()
  assert.equal(requests.isCurrent(save), false)
})

test('denied storage and malformed stored state do not prevent current-page editing', () => {
  const denied = new ContextDraftStore(() => { throw new Error('Storage denied') })
  assert.doesNotThrow(() => denied.save('a', draft(), 'a', true))
  assert.equal(denied.load('a'), null)
  assert.doesNotThrow(() => denied.clear('a'))
  const memory = memoryStorage()
  const store = new ContextDraftStore(() => memory)
  memory.setItem('lemonslice-kitchen-draft:a', '{bad json')
  assert.equal(store.load('a'), null)
  memory.setItem('lemonslice-kitchen-draft:a', JSON.stringify({ draft: { items: [] }, baseline: '' }))
  assert.equal(store.load('a'), null)
})

test('pending comparison includes own notes and wishes but ignores ordering, revision and signed-in display name', () => {
  const a = draft()
  const b = structuredClone(a)
  b.revision = 8
  assert.equal(draftContent(a, false), draftContent(b, false))
  b.nickname = '账号昵称'
  assert.equal(draftContent(a, true), draftContent(b, true))
  assert.notEqual(draftContent(a, false), draftContent(b, false))
  b.nickname = a.nickname
  b.offMenu = '想吃汤圆'
  assert.notEqual(draftContent(a, false), draftContent(b, false))
  b.offMenu = ''
  b.items[0].note = '少盐'
  assert.notEqual(draftContent(a, false), draftContent(b, false))
  const two = structuredClone(a)
  two.items.push(draft('鱼', 2).items[0])
  const reversed = structuredClone(two)
  reversed.items.reverse()
  assert.equal(draftContent(two, false), draftContent(reversed, false))
})


test('catalogue uses current meal automatically while untagged dishes remain all-day options', () => {
  assert.equal(applicableToMeal(['早餐', '午餐', '家常'], '午餐'), true)
  assert.equal(applicableToMeal(['早餐', '午餐', '家常'], '晚餐'), false)
  assert.equal(applicableToMeal(['家常', '清淡'], '晚餐'), true)
  assert.equal(applicableToMeal([], '早餐'), true)
  for (const meal of mealTagNames) {
    assert.equal(applicableToMeal([...mealTagNames, '清淡'], meal), applicableToMeal(['清淡'], meal))
    assert.equal(applicableToMeal([...mealTagNames], meal), applicableToMeal([], meal))
  }
  assert.deepEqual(['早餐', '家常', '午餐', '清淡', '晚餐'].filter((tag) => !mealTagNames.includes(tag)), ['家常', '清淡'])
})


test('submitted batches cannot leak own pending drafts into a new addition batch', () => {
  const storage = memoryStorage(), store = new ContextDraftStore(() => storage)
  const oldOwn = draftKey('2026-11-05', 'lunch', 'batch-one')
  const newOwn = draftKey('2026-11-05', 'lunch', 'batch-two')
  store.save(oldOwn, draft(), 'baseline', true)
  assert.notEqual(oldOwn, newOwn)
  assert.equal(store.load(newOwn), null)
  assert.equal(store.load(oldOwn).draft.items[0].dishId, 1)
  assert.equal(isCurrentBatch('batch-one', 'batch-two'), false)
  assert.equal(canEditSubmitted(true), false)
  assert.equal(canEditSubmitted(false), true)
})

test('drawer quantity updates preserve saved participant content and cannot pull unrelated main-form text into the request', () => {
  const source = draft()
  source.revision = 8
  source.offMenu = '服务器菜单外意愿'
  source.remark = '服务器备注'
  source.items[0].note = '原始菜品备注'
  const mainDraft = draft('主表单别的菜', 99)
  mainDraft.offMenu = '尚在编辑的菜单外意愿'
  mainDraft.remark = '尚在编辑的备注'
  const changed = updateSavedQuantity(source, 1, 3)
  assert.equal(changed.items[0].quantity, 3)
  assert.equal(changed.items[0].note, source.items[0].note)
  assert.equal(changed.offMenu, source.offMenu)
  assert.equal(changed.remark, source.remark)
  assert.equal(source.items[0].quantity, 1)
  assert.equal(mainDraft.items[0].dishId, 99)
  assert.equal(mainDraft.remark, '尚在编辑的备注')
  assert.deepEqual(updateSavedQuantity(source, 1, 0).items, [])
  assert.throws(() => updateSavedQuantity(source, 99, 1))
})

test('submission review freezes the exact batch and participant versions rather than adopting later refresh values', () => {
  const source = { batchId: 'batch-one', draftContributionVersions: { a: 2, b: 5 }, nickname: '小柠檬' }
  const reviewed = freezeSubmissionPayload(source)
  source.batchId = 'batch-two'
  source.draftContributionVersions.a = 9
  assert.equal(reviewed.batchId, 'batch-one')
  assert.deepEqual(reviewed.draftContributionVersions, { a: 2, b: 5 })
  assert.throws(() => { reviewed.draftContributionVersions.a = 10 })
  assert.equal(reviewed.nickname, '小柠檬')
})


test('sold-out catalogue dishes remain visible and move stably after available dishes', () => {
  const dishes = [
    { id: 1, soldOut: false }, { id: 2, soldOut: true },
    { id: 3, soldOut: false }, { id: 4, soldOut: true }, { id: 5 },
  ]
  assert.deepEqual(stockOrdered(dishes).map((dish) => dish.id), [1, 3, 5, 2, 4])
  assert.deepEqual(dishes.map((dish) => dish.id), [1, 2, 3, 4, 5])
  const dish = { id: 2, soldOut: true, isAvailable: true, tags: ['午餐', '家常'] }
  assert.equal(catalogueIncludesDish(dish, new Set(), '午餐'), true)
  assert.equal(catalogueIncludesDish(dish, new Set(), '晚餐'), false)
  assert.equal(catalogueIncludesDish({ ...dish, isAvailable: false }, new Set(), '午餐'), false)
  assert.equal(catalogueIncludesDish({ ...dish, isAvailable: false }, new Set([2]), '午餐'), true)
})

test('sold-out dishes reject additions or increases while existing saved selections can decrease or be removed', () => {
  const dish = { id: 1, soldOut: true, isAvailable: true, tags: [] }
  assert.equal(canIncreaseCatalogueDish(dish, '午餐'), false)
  assert.equal(canIncreaseCatalogueDish({ ...dish, soldOut: false }, '午餐'), true)
  assert.equal(canIncreaseCatalogueDish({ id: 1, isAvailable: true, tags: [] }, '午餐'), true)
  const selected = draft()
  selected.items[0].quantity = 3
  assert.equal(updateSavedQuantity(selected, 1, 2).items[0].quantity, 2)
  assert.deepEqual(updateSavedQuantity(selected, 1, 0).items, [])
})
