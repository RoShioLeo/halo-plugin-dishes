import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import OrdersView from '../OrdersView.vue'
import DishesView from '../DishesView.vue'
import DishEditingModal from '@/components/DishEditingModal.vue'
import { Dialog } from '@halo-dev/components'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
  replace: vi.fn(),
  route: { query: { date: '2026-11-05' } as Record<string, string> },
}))
vi.mock('@/api/client', () => ({
  getData: mocks.get,
  postData: mocks.post,
  putData: mocks.put,
  deleteData: mocks.delete,
  getApiErrorMessage: (error: unknown, fallback: string) =>
    error instanceof Error ? error.message : fallback,
}))
vi.mock('@halo-dev/ui-shared', () => ({
  utils: { attachment: { getThumbnailUrl: (url: string) => url } },
}))
vi.mock('vue-router', () => ({
  useRoute: () => mocks.route,
  useRouter: () => ({ replace: mocks.replace }),
  onBeforeRouteLeave: vi.fn(),
}))
vi.mock('@halo-dev/components', async () => {
  const { defineComponent, h } = await import('vue')
  const box = defineComponent({
    setup(_, { slots }) {
      return () => h('div', [slots.header?.(), slots.default?.(), slots.footer?.()])
    },
  })
  const button = defineComponent({
    props: { disabled: Boolean, loading: Boolean },
    emits: ['click'],
    setup(props, { attrs, emit, slots }) {
      return () =>
        h(
          'button',
          { ...attrs, disabled: props.disabled || props.loading, onClick: () => emit('click') },
          slots.default?.(),
        )
    },
  })
  const toggle = defineComponent({
    props: { modelValue: Boolean, disabled: Boolean },
    emits: ['update:modelValue'],
    setup(props, { attrs, emit }) {
      return () =>
        h('input', {
          ...attrs,
          type: 'checkbox',
          checked: props.modelValue,
          disabled: props.disabled,
          onChange: (event: Event) =>
            emit('update:modelValue', (event.target as HTMLInputElement).checked),
        })
    },
  })
  return {
    VPageHeader: box,
    VCard: box,
    VModal: box,
    VButton: button,
    VSwitch: toggle,
    VSpace: box,
    VDropdown: box,
    VDropdownItem: box,
    VEmpty: box,
    VLoading: box,
    VPagination: box,
    IconAddCircle: box,
    Dialog: { warning: vi.fn(), error: vi.fn() },
    Toast: { warning: vi.fn(), success: vi.fn() },
  }
})

let wrapper: VueWrapper | undefined
let kitchen: ReturnType<typeof fixture>
type TestMenuItem = {
  dishId: number | null
  customId?: string | null
  name: string
  quantity: number
  imageUrl: string
  note: string
}
function fixture() {
  const item: TestMenuItem = {
    dishId: 1,
    name: '番茄汤',
    quantity: 1,
    imageUrl: '',
    note: '原菜单备注',
  }
  return {
    date: '2026-11-05',
    periods: [
      {
        date: '2026-11-05',
        period: 'lunch',
        periodName: '午餐',
        needsConfirmation: true,
        stateRevision: 1,
        activeBatchId: 'batch-1',
        chefParticipantId: null as string | null,
        chefDisplayName: '主厨',
        chefAvatarUrl: '/chef.png',
        hasDraftChanges: true,
        participants: [
          {
            id: 'person-a',
            displayName: '小明',
            avatarUrl: '',
            guest: true,
            revision: 1,
            batchId: 'batch-1',
            submitted: false,
            createdAt: '2026-11-05T01:00:00Z',
            items: [
              {
                dishId: 1,
                name: '番茄汤',
                quantity: 1,
                imageUrl: '',
                note: '少盐',
                tags: ['午餐'],
              },
            ],
            customItems: [] as TestMenuItem[],
            offMenu: '想吃水果',
            remark: '不要葱',
            updatedAt: '2026-11-05T01:00:00Z',
          },
        ],
        totals: [
          {
            dishId: 1,
            name: '番茄汤',
            imageUrl: '',
            quantity: 1,
            people: [{ id: 'person-a', displayName: '小明', quantity: 1 }],
          },
        ],
        contributionVersions: { 'person-a': 1 } as Record<string, number>,
        workingItems: [{ ...item }] as TestMenuItem[],
        menu: {
          status: 'draft',
          revision: 0,
          items: [{ ...item }] as TestMenuItem[],
          note: '',
          confirmedAt: null as string | null,
          confirmedBy: null as string | null,
        },
      },
    ],
  }
}
const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value))
const button = (scope: Pick<VueWrapper, 'findAll'>, text: string) => {
  const found = scope.findAll('button').find((item) => item.text().includes(text))
  if (!found) throw new Error(`Missing button: ${text}`)
  return found
}
async function openKitchen() {
  wrapper = mount(OrdersView)
  await flushPromises()
  return wrapper.get('[data-period="lunch"]')
}
async function refreshOnFocus() {
  window.dispatchEvent(new Event('focus'))
  await flushPromises()
}
function addParticipant() {
  const person = clone(kitchen.periods[0]!.participants[0]!)
  person.id = 'person-b'
  person.displayName = '小红'
  kitchen.periods[0]!.participants.push(person)
  kitchen.periods[0]!.contributionVersions['person-b'] = 1
}

function batchFixture() {
  addParticipant()
  const order = kitchen.periods[0]!
  Object.assign(order, {
    activeBatchId: 'batch-2',
    submittedBatches: [{ id: 'batch-1', submittedAt: '2026-11-05T01:30:00Z', submittedBy: '小明' }],
    hasDraftChanges: true,
    draftContributionVersions: { 'person-b': 1 },
    mine: { revision: 0, items: [], offMenu: '', remark: '', nickname: '主厨' },
  })
  Object.assign(order.participants[0]!, {
    batchId: 'batch-1',
    submitted: true,
    submittedAt: '2026-11-05T01:30:00Z',
  })
  Object.assign(order.participants[1]!, { batchId: 'batch-2', submitted: false, submittedAt: null })
  return order
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.put.mockResolvedValue({})
  kitchen = fixture()
  mocks.route.query = { date: '2026-11-05' }
  Object.defineProperty(document, 'hidden', { configurable: true, value: false })
  vi.spyOn(window, 'confirm').mockReturnValue(true)
  mocks.get.mockImplementation(async (path: string) =>
    path.startsWith('/kitchen')
      ? clone(kitchen)
      : { items: [{ id: 1, name: '番茄汤', isAvailable: true }] },
  )
  mocks.post.mockImplementation(
    async (path: string, body: { revision: number; items: TestMenuItem[]; note: string }) => {
      if (path !== '/kitchen/commit') return { id: 1 }
      const order = clone(kitchen.periods[0]!)
      Object.assign(order.menu, {
        status: 'confirmed',
        revision: body.revision + 1,
        items: clone(body.items.filter((item) => item.quantity > 0)),
        note: body.note,
      })
      order.workingItems = clone(order.menu.items)
      order.stateRevision++
      order.hasDraftChanges = false
      order.needsConfirmation = false
      kitchen.periods[0] = order
      return { order }
    },
  )
})
afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  document.body.innerHTML = ''
  vi.restoreAllMocks()
})

async function editMenu() {
  const lunch = await openKitchen()
  await button(lunch, '调整菜单').trigger('click')
  return lunch
}
async function editRequests() {
  const lunch = await openKitchen()
  await button(lunch, '调整点餐').trigger('click')
  return lunch
}
const source = (id: string) => wrapper!.get('[data-source="' + id + '"]')
const menuRow = (key: string) => wrapper!.get('[data-menu-item="' + key + '"]')
const sourceRow = (id: string, key: string) => source(id).get('[data-item="' + key + '"]')

function threeSources(chefFirst = false) {
  const order = kitchen.periods[0]!
  const a = order.participants[0]!
  a.items[0]!.quantity = 2
  const b = clone(a)
  b.id = 'person-b'
  b.displayName = '小红'
  b.items[0]!.quantity = 3
  const chef = clone(a)
  chef.id = 'person-chef'
  chef.displayName = '主厨'
  chef.items[0]!.quantity = 1
  chef.offMenu = ''
  chef.remark = ''
  order.participants = chefFirst ? [chef, a, b] : [a, b, chef]
  order.chefParticipantId = 'person-chef'
  order.contributionVersions = { 'person-a': 1, 'person-b': 1, 'person-chef': 1 }
  order.workingItems[0]!.quantity = 6
  order.menu.items[0]!.quantity = 6
  return order
}

describe('atomic shared kitchen draft', () => {
  it('marks newly submitted changes as pending once and clears the tag after confirmation', async () => {
    kitchen.periods[0]!.menu.status = 'confirmed'
    kitchen.periods[0]!.needsConfirmation = false
    const lunch = await openKitchen()
    expect(lunch.find('.kitchen-pending').exists()).toBe(false)
    expect(lunch.find('.kitchen-confirmed').exists()).toBe(true)
    kitchen.periods[0]!.needsConfirmation = true
    kitchen.periods[0]!.stateRevision++
    await refreshOnFocus()
    expect(lunch.findAll('.kitchen-pending')).toHaveLength(1)
    expect(lunch.get('.kitchen-pending').text()).toBe('待确认')
    expect(lunch.find('.kitchen-confirmed').exists()).toBe(false)
    expect(lunch.text()).not.toContain('有新点菜，调整后重新确认')
    await button(lunch, '调整菜单').trigger('click')
    await button(lunch, '确认午餐菜单').trigger('click')
    await flushPromises()
    expect(lunch.find('.kitchen-pending').exists()).toBe(false)
    expect(lunch.find('.kitchen-confirmed').exists()).toBe(true)
  })

  it('starts with both sides read-only and removes duplicate logs, forms and aggregate lists', async () => {
    Object.assign(kitchen.periods[0]!.participants[0]!, {
      avatarUrl: '/avatar.png',
      lastEditedBy: 'Administrator',
      lastEditedAt: '2026-11-05T02:00:00Z',
      submittedAt: '2026-11-05T01:30:00Z',
    })
    const lunch = await openKitchen()
    expect(wrapper!.findAll('[data-period]')).toHaveLength(1)
    expect(lunch.findAll('input[type="number"]')).toHaveLength(0)
    expect(lunch.findAll('textarea')).toHaveLength(0)
    const records = lunch.get('[data-kitchen-requests]')
    expect(records.text().match(/小明/g)).toHaveLength(1)
    expect(records.text().match(/想吃水果/g)).toHaveLength(1)
    expect(records.text().match(/不要葱/g)).toHaveLength(1)
    expect(lunch.text()).not.toMatch(
      /最近由|Administrator|11\/05|点菜汇总|主厨加菜|修改点餐|仅主厨可修改/,
    )
    expect(mocks.post).not.toHaveBeenCalled()
  })

  it('locks adjustment to one side until cancel or successful commit', async () => {
    const lunch = await editMenu()
    expect(button(lunch, '调整点餐').attributes('disabled')).toBeDefined()
    expect(lunch.find('input[aria-label="调整小明的番茄汤份数"]').exists()).toBe(false)
    await button(lunch, '取消修改').trigger('click')
    await button(lunch, '调整点餐').trigger('click')
    expect(button(lunch, '调整菜单').attributes('disabled')).toBeDefined()
    expect(lunch.find('input[aria-label="番茄汤份数"]').exists()).toBe(false)
    expect(lunch.find('input[aria-label="调整小明的番茄汤份数"]').exists()).toBe(true)
    expect(mocks.post).not.toHaveBeenCalled()
  })

  it('stages personal quantities and totals locally, then commits a single versioned request without editable notes', async () => {
    const lunch = await editRequests()
    await source('person-a').get('input').setValue(8)
    expect(menuRow('dish:1').text()).toContain('8 份')
    expect(mocks.post).not.toHaveBeenCalled()
    expect(lunch.findAll('textarea')).toHaveLength(0)
    await button(lunch, '确认午餐菜单').trigger('click')
    await flushPromises()
    expect(mocks.post).toHaveBeenCalledTimes(1)
    expect(mocks.post).toHaveBeenCalledWith(
      '/kitchen/commit',
      expect.objectContaining({
        mode: 'requests',
        stateRevision: 1,
        batchId: 'batch-1',
        revision: 0,
        contributionVersions: { 'person-a': 1 },
        sourceQuantities: [{ participantId: 'person-a', items: [{ key: 'dish:1', quantity: 8 }] }],
        items: [expect.objectContaining({ dishId: 1, quantity: 8 })],
      }),
    )
    expect(JSON.stringify(mocks.post.mock.calls[0]![1])).not.toMatch(/想吃水果|不要葱/)
    expect(mocks.post.mock.calls.some(([path]) => path === '/kitchen/cart')).toBe(false)
  })

  it('keeps source native steps whole and synchronizes without an immediate save', async () => {
    kitchen.periods[0]!.participants[0]!.items[0]!.quantity = 3
    kitchen.periods[0]!.workingItems[0]!.quantity = 3
    await editRequests()
    const input = source('person-a').get<HTMLInputElement>('input')
    input.element.stepUp()
    await input.trigger('input')
    expect(input.element.value).toBe('4')
    expect(menuRow('dish:1').text()).toContain('4 份')
    expect(mocks.post).not.toHaveBeenCalled()
  })

  it('keeps final native steps whole and assigns only the surplus to the current chef', async () => {
    kitchen.periods[0]!.participants[0]!.items[0]!.quantity = 3
    kitchen.periods[0]!.workingItems[0]!.quantity = 3
    const lunch = await editMenu()
    const input = lunch.get<HTMLInputElement>('input[aria-label="番茄汤份数"]')
    input.element.stepUp()
    await input.trigger('input')
    expect(input.element.value).toBe('4')
    expect(sourceRow('person-a', 'dish:1').text()).toContain('3 份')
    expect(sourceRow('__chef__', 'dish:1').text()).toContain('1 份')
    expect(mocks.post).not.toHaveBeenCalled()
    await button(lunch, '确认午餐菜单').trigger('click')
    await flushPromises()
    expect(mocks.post).toHaveBeenCalledWith(
      '/kitchen/commit',
      expect.objectContaining({ mode: 'menu', items: [expect.objectContaining({ quantity: 4 })] }),
    )
    expect(mocks.post.mock.calls[0]![1]).not.toHaveProperty('sourceQuantities')
  })

  it('decreases sources in exact visible reverse order and restores the frozen baseline when a total is restored', async () => {
    threeSources()
    const lunch = await editMenu()
    const input = lunch.get('input[aria-label="番茄汤份数"]')
    await input.setValue(4)
    expect(sourceRow('person-a', 'dish:1').text()).toContain('2 份')
    expect(sourceRow('person-b', 'dish:1').text()).toContain('2 份')
    expect(sourceRow('person-chef', 'dish:1').text()).toContain('0 份')
    await input.setValue(6)
    expect(sourceRow('person-b', 'dish:1').text()).toContain('3 份')
    expect(sourceRow('person-chef', 'dish:1').text()).toContain('1 份')
    await input.setValue(7)
    expect(sourceRow('person-chef', 'dish:1').text()).toContain('2 份')
    expect(mocks.post).not.toHaveBeenCalled()
  })

  it('does not force the chef to the end or reorder same-nickname sources', async () => {
    threeSources(true)
    kitchen.periods[0]!.participants[1]!.displayName = '主厨'
    const lunch = await editMenu()
    expect(lunch.findAll('[data-source]').map((row) => row.attributes('data-source'))).toEqual([
      'person-chef',
      'person-a',
      'person-b',
    ])
    await lunch.get('input[aria-label="番茄汤份数"]').setValue(4)
    expect(sourceRow('person-chef', 'dish:1').text()).toContain('1 份')
    expect(sourceRow('person-b', 'dish:1').text()).toContain('1 份')
  })

  it('retains zero source rows until the atomic empty commit succeeds', async () => {
    const lunch = await editRequests()
    await source('person-a').get('input').setValue(0)
    expect(sourceRow('person-a', 'dish:1').element).toBeDefined()
    expect(menuRow('dish:1').text()).toContain('0 份')
    expect(mocks.post).not.toHaveBeenCalled()
    await button(lunch, '确认午餐菜单').trigger('click')
    expect(mocks.post).not.toHaveBeenCalled()
    const empty = clone(kitchen.periods[0]!)
    empty.participants[0]!.items = []
    empty.workingItems = []
    empty.menu.items = []
    empty.stateRevision = 2
    empty.menu.revision = 1
    empty.menu.status = 'confirmed'
    mocks.post.mockImplementationOnce(async () => {
      kitchen.periods[0] = empty
      return { order: clone(empty) }
    })
    await vi.mocked(Dialog.warning).mock.calls.at(-1)?.[0]?.onConfirm?.()
    await flushPromises()
    expect(mocks.post).toHaveBeenCalledWith(
      '/kitchen/commit',
      expect.objectContaining({
        sourceQuantities: [{ participantId: 'person-a', items: [{ key: 'dish:1', quantity: 0 }] }],
        items: [expect.objectContaining({ quantity: 0 })],
      }),
    )
    expect(wrapper!.find('[data-item="dish:1"]').exists()).toBe(false)
  })

  it('keeps a final zero row and its source identities while editing and can restore them', async () => {
    threeSources()
    const lunch = await editMenu()
    const input = lunch.get('input[aria-label="番茄汤份数"]')
    await input.setValue(0)
    expect(lunch.findAll('[data-source]')).toHaveLength(3)
    expect(lunch.findAll('[data-item="dish:1"]')).toHaveLength(3)
    expect(menuRow('dish:1').element).toBeDefined()
    await input.setValue(6)
    expect(sourceRow('person-a', 'dish:1').text()).toContain('2 份')
    expect(sourceRow('person-b', 'dish:1').text()).toContain('3 份')
    expect(sourceRow('person-chef', 'dish:1').text()).toContain('1 份')
    expect(mocks.post).not.toHaveBeenCalled()
  })

  it('keeps null-ID custom dishes distinct and attaches new custom dishes to the chef', async () => {
    const order = kitchen.periods[0]!
    const a: TestMenuItem = {
      dishId: null,
      customId: 'custom-a',
      name: '水果',
      quantity: 1,
      imageUrl: '',
      note: '',
    }
    const b: TestMenuItem = { ...a, customId: 'custom-b', name: '甜点' }
    order.participants[0]!.items = []
    order.participants[0]!.customItems = [a, b]
    order.workingItems = [a, b]
    const lunch = await editMenu()
    await lunch.get('input[aria-label="水果份数"]').setValue(3)
    expect(sourceRow('person-a', 'custom:custom-b').text()).toContain('1 份')
    expect(sourceRow('__chef__', 'custom:custom-a').text()).toContain('2 份')
    await button(lunch, '手写一道').trigger('click')
    await lunch.get('input[aria-label="午餐第 3 道菜名称"]').setValue('白粥')
    expect(source('__chef__').text()).toContain('白粥')
    await button(lunch, '确认午餐菜单').trigger('click')
    await flushPromises()
    expect(mocks.post).toHaveBeenCalledWith(
      '/kitchen/commit',
      expect.objectContaining({
        items: [
          expect.objectContaining({ customId: 'custom-a', quantity: 3 }),
          expect.objectContaining({ customId: 'custom-b', quantity: 1 }),
          expect.objectContaining({ name: '白粥', customId: expect.any(String), quantity: 1 }),
        ],
      }),
    )
  })

  it('adds catalogue selections to the chef and uses zero rather than deleting menu rows locally', async () => {
    const lunch = await editMenu()
    await lunch.get('select[aria-label="为午餐添加菜谱里的菜"]').setValue('1')
    await button(lunch, '添加').trigger('click')
    expect(sourceRow('__chef__', 'dish:1').text()).toContain('1 份')
    await lunch.get('button[aria-label="移除番茄汤"]').trigger('click')
    expect(menuRow('dish:1').element).toBeDefined()
    expect(sourceRow('person-a', 'dish:1').text()).toContain('0 份')
    expect(mocks.post).not.toHaveBeenCalled()
  })

  it('permits a blank new custom row only when its staged quantity is zero', async () => {
    const lunch = await editMenu()
    await button(lunch, '手写一道').trigger('click')
    await button(lunch, '确认午餐菜单').trigger('click')
    expect(lunch.text()).toContain('请填写每道菜的名称')
    expect(mocks.post).not.toHaveBeenCalled()
    await lunch.get('input[aria-label="菜品份数"]').setValue(0)
    await button(lunch, '确认午餐菜单').trigger('click')
    await flushPromises()
    expect(mocks.post).toHaveBeenCalledWith(
      '/kitchen/commit',
      expect.objectContaining({
        items: expect.arrayContaining([
          expect.objectContaining({ dishId: null, name: '', quantity: 0 }),
        ]),
      }),
    )
  })

  it('rejects blank and fractional input without treating them as a staged zero', async () => {
    const lunch = await editRequests()
    const input = source('person-a').get('input')
    await input.setValue('')
    expect(menuRow('dish:1').text()).toContain('1 份')
    await button(lunch, '确认午餐菜单').trigger('click')
    expect(mocks.post).not.toHaveBeenCalled()
    expect(lunch.text()).toContain('请输入有效的整数份数')
    await input.setValue(1.5)
    await button(lunch, '确认午餐菜单').trigger('click')
    expect(mocks.post).not.toHaveBeenCalled()
    await input.setValue(2)
    await button(lunch, '确认午餐菜单').trigger('click')
    await flushPromises()
    expect(mocks.post).toHaveBeenCalledTimes(1)
  })

  it('clears an invalid quantity when the explicit zero action is used', async () => {
    const lunch = await editMenu()
    await lunch.get('input[aria-label="番茄汤份数"]').setValue('')
    await lunch.get('button[aria-label="移除番茄汤"]').trigger('click')
    await button(lunch, '确认午餐菜单').trigger('click')
    expect(Dialog.warning).toHaveBeenCalledWith(
      expect.objectContaining({ title: '确认午餐暂无菜品' }),
    )
    expect(mocks.post).not.toHaveBeenCalled()
  })

  it('preserves a staged draft when only the server header revision changes and requires explicit reload', async () => {
    const lunch = await editRequests()
    await source('person-a').get('input').setValue(8)
    kitchen.periods[0]!.stateRevision = 2
    kitchen.periods[0]!.participants[0]!.items[0]!.quantity = 4
    await refreshOnFocus()
    expect(source('person-a').get<HTMLInputElement>('input').element.value).toBe('8')
    expect(lunch.text()).toContain('重新载入后再提交')
    expect(button(lunch, '确认午餐菜单').attributes('disabled')).toBeDefined()
    await button(lunch, '载入最新').trigger('click')
    expect(window.confirm).toHaveBeenCalled()
    expect(lunch.find('input[aria-label="调整小明的番茄汤份数"]').exists()).toBe(false)
    expect(sourceRow('person-a', 'dish:1').text()).toContain('4 份')
  })

  it('never silently retries a commit conflict and preserves staged input', async () => {
    const lunch = await editMenu()
    await lunch.get('input[aria-label="番茄汤份数"]').setValue(7)
    mocks.post.mockImplementationOnce(async () => {
      kitchen.periods[0]!.stateRevision++
      throw new Error('点菜已更新，请重新载入')
    })
    await button(lunch, '确认午餐菜单').trigger('click')
    await flushPromises()
    expect(lunch.get<HTMLInputElement>('input[aria-label="番茄汤份数"]').element.value).toBe('7')
    expect(lunch.text()).toContain('点菜已更新，请重新载入')
    expect(mocks.post).toHaveBeenCalledTimes(1)
    expect(button(lunch, '确认午餐菜单').attributes('disabled')).toBeDefined()
  })

  it('permits retrying a network failure without discarding an otherwise valid draft', async () => {
    const lunch = await editMenu()
    await lunch.get('input[aria-label="番茄汤份数"]').setValue(3)
    mocks.post.mockRejectedValueOnce(new Error('网络连接失败'))
    await button(lunch, '确认午餐菜单').trigger('click')
    await flushPromises()
    expect(lunch.text()).toContain('网络连接失败')
    await button(lunch, '确认午餐菜单').trigger('click')
    await flushPromises()
    expect(mocks.post).toHaveBeenCalledTimes(2)
  })

  it('keeps the published snapshot until adjustment starts and edits from working sources', async () => {
    kitchen.periods[0]!.menu.status = 'confirmed'
    kitchen.periods[0]!.menu.revision = 3
    kitchen.periods[0]!.menu.items[0]!.quantity = 99
    const lunch = await openKitchen()
    expect(menuRow('dish:1').text()).toContain('99 份')
    await button(lunch, '调整菜单').trigger('click')
    expect(lunch.get<HTMLInputElement>('input[aria-label="番茄汤份数"]').element.value).toBe('1')
    await lunch.get('input[aria-label="番茄汤份数"]').setValue(5)
    await button(lunch, '确认午餐菜单').trigger('click')
    await flushPromises()
    expect(mocks.post).toHaveBeenCalledWith(
      '/kitchen/commit',
      expect.objectContaining({
        revision: 3,
        items: [expect.objectContaining({ quantity: 5, note: '原菜单备注' })],
      }),
    )
  })

  it('keeps independent meal drafts and explicit side locks when switching the meal selector', async () => {
    const lunch = await editMenu()
    await lunch.get('input[aria-label="番茄汤份数"]').setValue(7)
    const select = wrapper!.get('select[aria-label="餐次"]')
    await select.setValue('breakfast')
    await button(wrapper!.get('[data-period]'), '调整点餐').trigger('click')
    await select.setValue('lunch')
    expect(wrapper!.get<HTMLInputElement>('input[aria-label="番茄汤份数"]').element.value).toBe('7')
    expect(button(wrapper!.get('[data-period]'), '调整点餐').attributes('disabled')).toBeDefined()
    expect(window.confirm).not.toHaveBeenCalled()
    expect(mocks.post).not.toHaveBeenCalled()
  })

  it('keeps date-change protection and an explicit route meal', async () => {
    mocks.route.query = { date: '2026-11-05', period: 'dinner' }
    wrapper = mount(OrdersView)
    await flushPromises()
    expect(wrapper.get('[data-period]').attributes('data-period')).toBe('dinner')
    await button(wrapper.get('[data-period]'), '调整菜单').trigger('click')
    await button(wrapper.get('[data-period]'), '手写一道').trigger('click')
    await wrapper.get('input[aria-label="晚餐第 1 道菜名称"]').setValue('面条')
    vi.mocked(window.confirm).mockReturnValueOnce(false)
    await wrapper.get('input[aria-label="菜单日期"]').setValue('2026-11-06')
    expect(wrapper.get<HTMLInputElement>('input[aria-label="菜单日期"]').element.value).toBe(
      '2026-11-05',
    )
    expect(
      wrapper.get<HTMLInputElement>('input[aria-label="晚餐第 1 道菜名称"]').element.value,
    ).toBe('面条')
  })
})

describe('dish tags', () => {
  it('shows empty and complete meal selections as all-day while keeping custom tags separate', async () => {
    mocks.get.mockImplementation(async (path: string) =>
      path === '/categories'
        ? { items: [{ id: 1, name: '家常', slug: 'home', sortOrder: 0 }] }
        : {
            items: [
              {
                id: 1,
                categoryId: 1,
                name: '甲',
                recommendationLevel: 3,
                isAvailable: true,
                soldOut: false,
                tags: ['清淡'],
              },
              {
                id: 2,
                categoryId: 1,
                name: '乙',
                recommendationLevel: 3,
                isAvailable: true,
                soldOut: false,
                tags: ['早餐', '午餐', '晚餐', '素食'],
              },
              {
                id: 3,
                categoryId: 1,
                name: '丙',
                recommendationLevel: 3,
                isAvailable: true,
                soldOut: false,
                tags: ['早餐'],
              },
            ],
          },
    )
    wrapper = mount(DishesView, {
      global: {
        stubs: { CategoryList: true, CategoryEditingModal: true, AttachmentSelectorModal: true },
      },
    })
    await flushPromises()
    const rows = wrapper.findAll('tbody tr')
    expect(rows.map((row) => row.findAll('td')[4]!.text())).toEqual(['全天', '全天', '早餐'])
    expect(rows[1]!.findAll('td')[5]!.text()).toBe('素食')
  })

  it('keeps one dish with multiple meal/custom tags and retains its recommendation level', async () => {
    const form = {
      categoryId: 1,
      name: '番茄汤',
      imageUrl: '',
      recommendationLevel: 4,
      description: '',
      isAvailable: true,
      soldOut: false,
      sortOrder: 0,
      tags: [] as string[],
    }
    wrapper = mount(DishEditingModal, {
      props: {
        visible: true,
        mode: 'create',
        saving: false,
        form,
        errors: {},
        availableTags: ['早餐', '午餐', '晚餐'],
        categoryOptions: [{ value: 1, label: '汤羹' }],
      },
    })
    await wrapper.get('input[aria-label="早餐"]').setValue(true)
    await wrapper.get('input[aria-label="午餐"]').setValue(true)
    await wrapper.get('#dish-tag').setValue('清淡')
    await wrapper.get('#dish-tag').trigger('keydown', { key: 'Enter' })
    await button(wrapper, '保存').trigger('click')
    expect(form.tags).toEqual(['早餐', '午餐', '清淡'])
    expect(form.recommendationLevel).toBe(4)
    expect(wrapper.emitted('save')).toHaveLength(1)
  })
})

describe('sold-out dishes', () => {
  function soldOutCatalogue() {
    mocks.get.mockImplementation(async (path: string) =>
      path.startsWith('/kitchen')
        ? clone(kitchen)
        : { items: [{ id: 1, name: '番茄汤', isAvailable: true, soldOut: true }] },
    )
  }
  it('blocks sold-out increases in either adjustment mode and keeps reductions valid', async () => {
    soldOutCatalogue()
    kitchen.periods[0]!.participants[0]!.items[0]!.quantity = 3
    kitchen.periods[0]!.workingItems[0]!.quantity = 3
    const lunch = await editMenu()
    const input = lunch.get('input[aria-label="番茄汤份数"]')
    await input.setValue(4)
    await button(lunch, '确认午餐菜单').trigger('click')
    expect(lunch.text()).toContain('缺货菜品不能增加份数')
    expect(mocks.post).not.toHaveBeenCalled()
    await input.setValue(2)
    await button(lunch, '确认午餐菜单').trigger('click')
    await flushPromises()
    expect(mocks.post).toHaveBeenCalledWith(
      '/kitchen/commit',
      expect.objectContaining({ items: [expect.objectContaining({ quantity: 2 })] }),
    )
  })
  async function openDishes(soldOut = false) {
    mocks.get.mockImplementation(async (path: string) =>
      path === '/categories'
        ? { items: [{ id: 1, name: '汤羹', slug: 'soup', sortOrder: 0 }] }
        : {
            items: [
              {
                id: 1,
                categoryId: 1,
                categoryName: '汤羹',
                name: '番茄汤',
                recommendationLevel: 4,
                isAvailable: true,
                soldOut,
                tags: ['午餐', '清淡'],
              },
              {
                id: 2,
                categoryId: 1,
                categoryName: '汤羹',
                name: '豆腐汤',
                recommendationLevel: 3,
                isAvailable: true,
                soldOut: !soldOut,
                tags: [],
              },
            ],
          },
    )
    wrapper = mount(DishesView, {
      global: {
        stubs: { CategoryList: true, CategoryEditingModal: true, AttachmentSelectorModal: true },
      },
    })
    await flushPromises()
  }

  it('uses a positive in-stock switch and saves its inverted sold-out flag', async () => {
    await openDishes()
    await button(wrapper!, '新增菜品').trigger('click')
    const modal = wrapper!.getComponent(DishEditingModal)
    expect(modal.props('form').soldOut).toBe(false)
    const stock = modal.get<HTMLInputElement>('input[aria-label="菜品有货"]')
    expect(stock.element.checked).toBe(true)
    await stock.setValue(false)
    expect(modal.props('form').soldOut).toBe(true)
    await stock.setValue(true)
    expect(modal.props('form').soldOut).toBe(false)
    await stock.setValue(false)
    await modal.get('#dish-name').setValue('新汤')
    await button(modal, '保存').trigger('click')
    await flushPromises()
    expect(mocks.post).toHaveBeenCalledWith(
      '/dishes',
      expect.objectContaining({ name: '新汤', isAvailable: true, soldOut: true }),
    )
  })

  it('presents availability and stock as separate boolean icons and changes them only in the editor', async () => {
    await openDishes(true)
    const table = wrapper!.get('table')
    expect(table.find('[role="img"][aria-label="已上架"]').exists()).toBe(true)
    expect(table.find('[role="img"][aria-label="缺货"]').exists()).toBe(true)
    expect(table.get('[role="img"][aria-label="缺货"]').classes()).toContain('is-false')
    expect(table.get('[role="img"][aria-label="缺货"]').attributes('title')).toBe('缺货')
    expect(table.get('[role="img"][aria-label="有货"]').classes()).not.toContain('is-false')
    expect(table.get('[role="img"][aria-label="有货"]').attributes('title')).toBe('有货')
    expect(table.find('input[aria-label="番茄汤上架"]').exists()).toBe(false)
    expect(table.text()).toContain('午餐')
    expect(table.text()).toContain('清淡')
    expect(wrapper!.findAll('button').some((item) => item.text() === '恢复供应')).toBe(false)
    await button(table, '编辑').trigger('click')
    const modal = wrapper!.getComponent(DishEditingModal)
    await modal.get('input[aria-label="菜品上架"]').setValue(false)
    await button(modal, '保存').trigger('click')
    await flushPromises()
    expect(mocks.put).toHaveBeenLastCalledWith(
      '/dishes/1',
      expect.objectContaining({ isAvailable: false, soldOut: true }),
    )
  })
})
