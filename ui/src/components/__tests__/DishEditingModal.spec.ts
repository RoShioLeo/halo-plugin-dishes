import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import DishEditingModal from '../DishEditingModal.vue'

vi.mock('@halo-dev/components', async () => {
  const { defineComponent, h } = await import('vue')
  return {
    VModal: defineComponent({
      setup(_, { slots }) {
        return () => h('div', [slots.default?.(), slots.footer?.()])
      },
    }),
    VButton: defineComponent({
      emits: ['click'],
      setup(_, { emit, slots }) {
        return () => h('button', { onClick: () => emit('click') }, slots.default?.())
      },
    }),
    VSwitch: defineComponent({ render: () => null }),
  }
})

let wrapper: VueWrapper | undefined
afterEach(() => wrapper?.unmount())

function openModal(tags: string[] = []) {
  const form = reactive({
    categoryId: 1,
    name: '番茄汤',
    imageUrl: '',
    recommendationLevel: 4,
    description: '',
    isAvailable: true,
    soldOut: false,
    sortOrder: 0,
    tags: [...tags],
  })
  wrapper = mount(DishEditingModal, {
    props: {
      visible: true,
      mode: 'edit',
      saving: false,
      form,
      errors: {},
      availableTags: ['早餐', '午餐', '晚餐', '清淡', '素食'],
      categoryOptions: [{ value: 1, label: '汤羹' }],
    },
  })
  return { form, modal: wrapper }
}

describe('separate meal periods and custom dish tags', () => {
  it('switches between all-day and specific meals without changing custom tags', async () => {
    const { form, modal } = openModal(['清淡'])
    const allDay = modal.get<HTMLInputElement>('input[aria-label="全天"]')
    expect(allDay.element.checked).toBe(true)
    await modal.get('input[aria-label="早餐"]').setValue(true)
    expect(allDay.element.checked).toBe(false)
    await modal.get('input[aria-label="午餐"]').setValue(true)
    await modal.get('input[aria-label="晚餐"]').setValue(true)
    expect(form.tags).toEqual(['清淡'])
    expect(allDay.element.checked).toBe(true)
    expect(modal.get<HTMLInputElement>('input[aria-label="早餐"]').element.checked).toBe(false)
    await modal.get('input[aria-label="早餐"]').setValue(true)
    await allDay.setValue(true)
    expect(form.tags).toEqual(['清淡'])
    await modal.get('input[aria-label="午餐"]').setValue(true)
    await modal.get('input[aria-label="午餐"]').setValue(false)
    expect(allDay.element.checked).toBe(true)
    expect(form.tags).toEqual(['清淡'])
    await modal.get('input[aria-label="早餐"]').setValue(true)
    await modal.get('#dish-tag').setValue('全天')
    await modal.get('#dish-tag').trigger('keydown', { key: 'Enter' })
    expect(form.tags).toEqual(['清淡'])
    expect(allDay.element.checked).toBe(true)
  })

  it('loads all three meals as all-day and normalizes a full selection before the tag limit', async () => {
    const { form, modal } = openModal(['早餐', '午餐', '晚餐', '清淡'])
    expect(form.tags).toEqual(['清淡'])
    expect(modal.get<HTMLInputElement>('input[aria-label="全天"]').element.checked).toBe(true)
    const custom = Array.from({ length: 14 }, (_, index) => `口味${index}`)
    form.tags = [...custom, '早餐', '午餐']
    await modal.get('input[aria-label="晚餐"]').setValue(true)
    expect(form.tags).toEqual(custom)
    expect(modal.find('.plugin-error').exists()).toBe(false)
  })

  it('renders existing meal tags as checkboxes and keeps them out of custom chips and suggestions', () => {
    const { modal } = openModal(['早餐', '午餐', '清淡'])
    expect(modal.get<HTMLInputElement>('input[aria-label="早餐"]').element.checked).toBe(true)
    expect(modal.get<HTMLInputElement>('input[aria-label="午餐"]').element.checked).toBe(true)
    expect(modal.get<HTMLInputElement>('input[aria-label="晚餐"]').element.checked).toBe(false)
    expect(modal.findAll('.plugin-chip--selected').map((chip) => chip.text())).toEqual(['清淡 ×'])
    expect(modal.findAll('.plugin-tag-suggestions button').map((chip) => chip.text())).toEqual([
      '+ 素食',
    ])
  })

  it('routes a manually entered meal name to its checkbox without losing pending custom input', async () => {
    const { form, modal } = openModal()
    await modal.get('#dish-tag').setValue('  早餐  ')
    await modal.get('#dish-tag').trigger('keydown', { key: 'Enter' })
    expect(form.tags).toEqual(['早餐'])
    expect(modal.get<HTMLInputElement>('input[aria-label="早餐"]').element.checked).toBe(true)
    expect(modal.findAll('.plugin-chip--selected')).toHaveLength(0)
    await modal.get('#dish-tag').setValue('少油')
    await modal.get('input[aria-label="午餐"]').setValue(true)
    expect(modal.get<HTMLInputElement>('#dish-tag').element.value).toBe('少油')
    await modal.get('#dish-tag').trigger('keydown', { key: 'Enter' })
    expect(form.tags).toEqual(['早餐', '午餐', '少油'])
  })

  it('enforces the combined limit and preserves custom tags when selecting or clearing a meal', async () => {
    const tags = Array.from({ length: 16 }, (_, index) => `口味${index}`)
    const { form, modal } = openModal(tags)
    await modal.get('input[aria-label="早餐"]').setValue(true)
    expect(form.tags).toEqual(tags)
    expect(modal.get<HTMLInputElement>('input[aria-label="早餐"]').element.checked).toBe(false)
    expect(modal.text()).toContain('餐次和标签合计最多 16 项')
    await modal.get('button[aria-label="移除标签 口味0"]').trigger('click')
    await modal.get('input[aria-label="早餐"]').setValue(true)
    expect(form.tags).toEqual([...tags.slice(1), '早餐'])
    await modal.get('input[aria-label="早餐"]').setValue(false)
    expect(form.tags).toEqual(tags.slice(1))
  })

  it('saves a duplicate typed meal tag once even when all sixteen tag slots are occupied', async () => {
    const tags = ['早餐', ...Array.from({ length: 15 }, (_, index) => `口味${index}`)]
    const { form, modal } = openModal(tags)
    await modal.get('#dish-tag').setValue('早餐')
    await modal
      .findAll('button')
      .find((button) => button.text() === '保存')!
      .trigger('click')
    expect(form.tags).toEqual(tags)
    expect(modal.emitted('save')).toHaveLength(1)
    expect(modal.find('.plugin-error').exists()).toBe(false)
  })
})
