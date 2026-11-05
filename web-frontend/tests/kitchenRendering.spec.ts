import { describe, expect, it } from 'vitest'
import { countSharedDishes, normalizeCustomItems, normalizeDish, withinPublicCartLimit } from '../src/api/kitchen'
import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import SharedParticipant from '../src/components/SharedParticipant.vue'
import type { Participant } from '../src/api/kitchen'

describe('public kitchen rendering', () => {
  it('reads the current dish DTO and requires boolean availability and stock fields', () => {
    const dish = { id: 1, categoryId: 2, categoryName: '家常', name: '番茄汤', imageUrl: '/tomato.png', description: '清淡', recommendationLevel: 4, isAvailable: true, soldOut: false, tags: ['午餐'] }
    expect(normalizeDish(dish)).toEqual(dish)
    expect(normalizeDish({ ...dish, isAvailable: 1, soldOut: '1' })).toMatchObject({ isAvailable: false, soldOut: false })
    expect(normalizeDish(null)).toMatchObject({ categoryId: 0, name: '', isAvailable: false, soldOut: false, tags: [] })
  })
  it('preserves independent custom identities and quantities without mutating the server snapshot', () => {
    const items = Object.freeze([
      Object.freeze({ dishId: null, customId: 'first', name: '家常汤', quantity: 1, imageUrl: '', note: '' }),
      Object.freeze({ dishId: null, customId: 'second', name: '家常汤', quantity: 2, imageUrl: '', note: '少盐' }),
    ])
    const result = normalizeCustomItems(items)
    expect(result).toEqual(items)
    expect(result[0]).not.toBe(items[0])
    expect(result[1]?.customId).toBe('second')
  })

  it('handles absent optional fields and rejects malformed, fractional or non-custom rows', () => {
    expect(normalizeCustomItems(undefined)).toEqual([])
    expect(normalizeCustomItems(null)).toEqual([])
    expect(normalizeCustomItems([
      null, { dishId: 1, customId: 'catalogue', name: '菜谱菜', quantity: 1 },
      { dishId: null, customId: '', name: '无编号', quantity: 1 },
      { dishId: null, customId: 'bad', name: '无效份数', quantity: Number.NaN },
      { dishId: null, customId: 'zero', name: '零份', quantity: 0 },
      { dishId: null, customId: 'large', name: '超量', quantity: 10000 },
      { dishId: null, customId: 'fraction', name: '非整数', quantity: 1.5 },
      { dishId: null, customId: 'valid', name: '手写菜', quantity: 2 },
    ])).toEqual([{ dishId: null, customId: 'valid', name: '手写菜', quantity: 2, imageUrl: '', note: '' }])
  })

  it('counts custom identities once, separately from catalogue IDs, including empty custom totals', () => {
    const catalogue = { dishId: 1, name: '菜谱菜', imageUrl: '', quantity: 2, people: [] }
    const custom = { dishId: null, customId: '1', name: '手写菜', quantity: 3, imageUrl: '', note: '' } as const
    expect(countSharedDishes(null)).toBe(0)
    expect(countSharedDishes({ totals: [catalogue] })).toBe(1)
    expect(countSharedDishes({ totals: [catalogue], customTotals: [custom, { ...custom, name: '同一记录' }, { ...custom, customId: '2' }] })).toBe(3)
  })

  it('keeps submitted and over-limit sources read-only while pending quantity controls remain usable', async () => {
    const person: Participant = {
      id: 'chef-source', displayName: '主厨', guest: false, mine: false, revision: 1,
      items: [{ dishId: 1, name: '大份菜', quantity: 150, imageUrl: '', note: '', tags: [] }, { dishId: 2, name: '普通菜', quantity: 2, imageUrl: '', note: '', tags: [] }],
      customItems: [{ dishId: null, customId: 'one', name: '手写菜', quantity: 3, imageUrl: '', note: '' }],
      offMenu: '', remark: '', updatedAt: '', batchId: 'first', submitted: true, submittedAt: '',
    }
    expect(withinPublicCartLimit(person)).toBe(false)
    const render = (value: Participant) => renderToString(createSSRApp(SharedParticipant, { person: value, editable: true, busy: false, canIncrease: () => true }))
    const html = await render(person)
    expect(html).toContain('大份菜')
    expect(html).toContain('× 150')
    expect(html).toContain('手写菜')
    expect(html).not.toContain('<input')
    expect(html).not.toContain('title="编辑点菜"')
    const normal = { ...person, submitted: false, items: person.items.map((item) => ({ ...item, quantity: 2 })) }
    expect(withinPublicCartLimit(normal)).toBe(true)
    expect(withinPublicCartLimit({ items: normal.items.map((item) => ({ ...item, quantity: 1.5 })) })).toBe(false)
    const editableHtml = await render(normal)
    expect(editableHtml).toContain('<input')
    expect(editableHtml).not.toContain('编辑点菜')
    expect(await render({ ...normal, submitted: true })).not.toContain('<input')
    const customRow = editableHtml.slice(editableHtml.indexOf('手写菜'))
    expect(customRow).not.toContain('<input')
  })
})
