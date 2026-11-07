import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Dialog } from '@halo-dev/components'
import SettingsView from '../SettingsView.vue'

const mocks = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn() }))
vi.mock('@/api/client', () => ({
  getData: mocks.get,
  putData: mocks.put,
  getApiErrorMessage: (error: unknown, fallback: string) =>
    error instanceof Error ? error.message : fallback,
}))
vi.mock('@halo-dev/ui-shared', () => ({
  utils: { attachment: { getThumbnailUrl: (url: string) => url } },
}))
vi.mock('@halo-dev/components', async () => {
  const { defineComponent, h } = await import('vue')
  const box = defineComponent({
    setup(_, { slots }) {
      return () => h('div', [slots.header?.(), slots.default?.(), slots.footer?.()])
    },
  })
  const button = defineComponent({
    props: { disabled: Boolean },
    emits: ['click'],
    setup(props, { attrs, emit, slots }) {
      return () =>
        h(
          'button',
          { ...attrs, disabled: props.disabled, onClick: () => emit('click') },
          slots.default?.(),
        )
    },
  })
  return {
    VPageHeader: box,
    VCard: box,
    VButton: button,
    Dialog: { warning: vi.fn(), error: vi.fn() },
    Toast: { success: vi.fn() },
  }
})

function fixture() {
  return {
    basic: {
      accessMode: 'password',
      accessPassword: 'keep-test-password',
      accessPasswordSet: true,
      publicAccessUrl: '/dishes',
      defaultPublicAccessUrl: '/dishes',
      publicLogoUrl: '',
      publicSiteTitle: '测试标题',
      publicBrandTitle: '测试私厨',
      publicBrandSubtitle: '今天吃什么',
      publicDomainWhitelist: 'blog.example.com',
      mealSchedule: { lunchStart: '10:00', dinnerStart: '15:00' },
    },
  }
}
const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value))
let settings: ReturnType<typeof fixture>
let wrapper: VueWrapper | undefined
beforeEach(() => {
  vi.clearAllMocks()
  settings = fixture()
  mocks.get.mockImplementation(async () => clone(settings))
  mocks.put.mockImplementation(
    async (_path: string, body: { basic: Partial<ReturnType<typeof fixture>['basic']> }) => {
      settings.basic = { ...settings.basic, ...body.basic }
      return { updated: true }
    },
  )
})
afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})
async function openSettings() {
  wrapper = mount(SettingsView, { global: { stubs: { AttachmentSelectorModal: true } } })
  await flushPromises()
  return wrapper
}
function save() {
  return wrapper!
    .findAll('button')
    .find((button) => button.text() === '保存')!
    .trigger('click')
}

describe('default meal schedule settings', () => {
  it('loads the canonical defaults with minute controls and one derived schedule summary', async () => {
    const view = await openSettings()
    expect(view.findAll('input[type="time"]')).toHaveLength(2)
    expect(view.get<HTMLInputElement>('input[aria-label="午餐开始"]').element.value).toBe('10:00')
    expect(view.get<HTMLInputElement>('input[aria-label="晚餐开始"]').element.value).toBe('15:00')
    expect(view.get('input[aria-label="晚餐开始"]').attributes('step')).toBe('60')
    expect(view.text()).toContain(
      '早餐 00:00–09:59；午餐 10:00–14:59；晚餐 15:00–23:59（上海时间）',
    )
  })

  it('saves edited boundaries together with the existing password and branding fields', async () => {
    settings.basic.mealSchedule = { lunchStart: '09:30', dinnerStart: '14:45' }
    const view = await openSettings()
    expect(view.get<HTMLInputElement>('input[aria-label="午餐开始"]').element.value).toBe('09:30')
    await view.get('input[aria-label="午餐开始"]').setValue('10:30')
    await view.get('input[aria-label="晚餐开始"]').setValue('15:45')
    await save()
    await flushPromises()
    expect(mocks.put).toHaveBeenCalledWith('/settings', {
      basic: {
        accessMode: 'password',
        accessPassword: 'keep-test-password',
        publicAccessUrl: '/dishes',
        publicLogoUrl: '',
        publicSiteTitle: '测试标题',
        publicBrandTitle: '测试私厨',
        publicBrandSubtitle: '今天吃什么',
        publicDomainWhitelist: 'blog.example.com',
        mealSchedule: { lunchStart: '10:30', dinnerStart: '15:45' },
      },
    })
    expect(view.get<HTMLInputElement>('input[aria-label="晚餐开始"]').element.value).toBe('15:45')
    expect(view.text()).toContain('午餐 10:30–15:44')
  })

  it.each([
    ['00:00', '15:00'],
    ['10:00', '10:00'],
    ['16:00', '15:00'],
    ['10:00:30', '15:00'],
    ['10:00', '24:00'],
  ])('rejects invalid boundary order or format %s / %s before saving', async (lunch, dinner) => {
    const view = await openSettings()
    await view.get('input[aria-label="午餐开始"]').setValue(lunch)
    await view.get('input[aria-label="晚餐开始"]').setValue(dinner)
    await save()
    await flushPromises()
    expect(mocks.put).not.toHaveBeenCalled()
    expect(view.find('[role="alert"]').exists()).toBe(true)
    expect(view.get('input[aria-label="午餐开始"]').attributes('aria-invalid')).toBe('true')
    expect(settings.basic.accessPassword).toBe('keep-test-password')
  })

  it.each([
    ['00:01', '00:02'],
    ['23:58', '23:59'],
  ])('accepts the valid minute boundary %s / %s', async (lunch, dinner) => {
    const view = await openSettings()
    await view.get('input[aria-label="午餐开始"]').setValue(lunch)
    await view.get('input[aria-label="晚餐开始"]').setValue(dinner)
    await save()
    await flushPromises()
    expect(mocks.put).toHaveBeenCalledWith(
      '/settings',
      expect.objectContaining({
        basic: expect.objectContaining({
          mealSchedule: { lunchStart: lunch, dinnerStart: dinner },
        }),
      }),
    )
    expect(view.find('[role="alert"]').exists()).toBe(false)
  })

  it('keeps the entered boundaries when the server rejects a save', async () => {
    const view = await openSettings()
    await view.get('input[aria-label="午餐开始"]').setValue('11:00')
    await view.get('input[aria-label="晚餐开始"]').setValue('16:30')
    mocks.put.mockRejectedValueOnce(new Error('保存失败'))
    await save()
    await flushPromises()
    expect(Dialog.error).toHaveBeenCalledWith(expect.objectContaining({ description: '保存失败' }))
    expect(view.get<HTMLInputElement>('input[aria-label="午餐开始"]').element.value).toBe('11:00')
    expect(view.get<HTMLInputElement>('input[aria-label="晚餐开始"]').element.value).toBe('16:30')
  })
})
