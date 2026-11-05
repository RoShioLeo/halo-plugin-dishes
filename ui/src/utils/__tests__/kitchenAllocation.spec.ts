import { describe, expect, it } from 'vitest'
import { allocateKitchenQuantities, type KitchenQuantitySource } from '../kitchenAllocation'

const chef = 'chef:batch-2'
const baseline = (): KitchenQuantitySource[] => [
  { sourceId: 'person-a:batch-1', items: [{ key: 'dish:1', quantity: 2 }] },
  { sourceId: 'person-b:batch-1', items: [{ key: 'dish:1', quantity: 3 }] },
  { sourceId: chef, items: [{ key: 'dish:1', quantity: 1 }] },
]
const quantities = (sources: KitchenQuantitySource[], key = 'dish:1') =>
  sources.map((source) => source.items.find((item) => item.key === key)?.quantity ?? 0)

describe('integer kitchen quantity allocation', () => {
  it('restores original ownership for 6 → 4 → 6 using the same frozen baseline', () => {
    const sources = baseline()
    expect(quantities(allocateKitchenQuantities(sources, { 'dish:1': 4 }, chef))).toEqual([2, 2, 0])
    expect(quantities(allocateKitchenQuantities(sources, { 'dish:1': 6 }, chef))).toEqual([2, 3, 1])
    expect(sources).toEqual(baseline())
  })

  it('deducts in reverse UI order without sorting source IDs or prioritizing the chef', () => {
    const sources = [baseline()[2]!, baseline()[0]!, baseline()[1]!]
    expect(quantities(allocateKitchenQuantities(sources, { 'dish:1': 4 }, chef))).toEqual([1, 2, 1])
  })

  it('adds only the excess to an existing chef record', () => {
    expect(quantities(allocateKitchenQuantities(baseline(), { 'dish:1': 8 }, chef))).toEqual([
      2, 3, 3,
    ])
  })

  it('assigns a newly added catalogue dish to the chef', () => {
    const result = allocateKitchenQuantities(baseline(), { 'dish:2': 2 }, chef)
    expect(quantities(result)).toEqual([2, 3, 1])
    expect(quantities(result, 'dish:2')).toEqual([0, 0, 2])
  })

  it('keeps separate batches belonging to the same person distinct', () => {
    const sources = [
      { sourceId: 'same-person:batch-1', items: [{ key: 'dish:1', quantity: 2 }] },
      { sourceId: 'same-person:batch-2', items: [{ key: 'dish:1', quantity: 3 }] },
      { sourceId: chef, items: [] },
    ]
    const result = allocateKitchenQuantities(sources, { 'dish:1': 3 }, chef)
    expect(result.map((source) => source.sourceId)).toEqual(
      sources.map((source) => source.sourceId),
    )
    expect(quantities(result)).toEqual([2, 1, 0])
  })

  it('allocates dishes independently and preserves keys not present in targets', () => {
    const sources = baseline()
    sources[0]!.items.push({ key: 'dish:2', quantity: 4 })
    sources[1]!.items.push({ key: 'dish:2', quantity: 1 }, { key: 'dish:3', quantity: 2 })
    const result = allocateKitchenQuantities(sources, { 'dish:1': 4, 'dish:2': 6 }, chef)
    expect(quantities(result)).toEqual([2, 2, 0])
    expect(quantities(result, 'dish:2')).toEqual([4, 1, 1])
    expect(quantities(result, 'dish:3')).toEqual([0, 2, 0])
  })

  it('keeps distinct custom keys independent and assigns new custom dishes to the chef', () => {
    const sources = [
      {
        sourceId: chef,
        items: [
          { key: 'custom:one', quantity: 2 },
          { key: 'custom:two', quantity: 3 },
        ],
      },
    ]
    const result = allocateKitchenQuantities(sources, { 'custom:one': 1, 'custom:three': 4 }, chef)
    expect(result[0]!.items).toEqual([
      { key: 'custom:one', quantity: 1 },
      { key: 'custom:two', quantity: 3 },
      { key: 'custom:three', quantity: 4 },
    ])
  })

  it('retains zero rows and source identities until confirmation', () => {
    const result = allocateKitchenQuantities(baseline(), { 'dish:1': 0, 'dish:2': 0 }, chef)
    expect(result.map((source) => source.items[0])).toEqual([
      { key: 'dish:1', quantity: 0 },
      { key: 'dish:1', quantity: 0 },
      { key: 'dish:1', quantity: 0 },
    ])
    expect(result[2]!.items[1]).toEqual({ key: 'dish:2', quantity: 0 })
  })

  it('does not mutate a frozen baseline or attached notes and returns independent rows', () => {
    const note = Object.freeze({ text: '不放葱' })
    const sources = baseline().map((source) =>
      Object.freeze({
        ...source,
        note,
        items: Object.freeze(source.items.map((item) => Object.freeze(item))),
      }),
    )
    const result = allocateKitchenQuantities(Object.freeze(sources), { 'dish:1': 4 }, chef)
    result[0]!.items[0]!.quantity = 9
    expect(sources[0]!.items[0]!.quantity).toBe(2)
    expect(sources[0]!.note).toBe(note)
  })

  it.each([-1, Number.NaN, Number.POSITIVE_INFINITY, 10000, 1.5])(
    'rejects invalid target %s',
    (value) => {
      expect(() => allocateKitchenQuantities(baseline(), { 'dish:1': value }, chef)).toThrow()
    },
  )

  it('rejects invalid baselines, limits, ambiguous keys, duplicate sources and a missing chef', () => {
    const sources = baseline()
    sources[0]!.items[0]!.quantity = 1.5
    expect(() => allocateKitchenQuantities(sources, {}, chef)).toThrow()
    expect(() =>
      allocateKitchenQuantities(baseline(), { 'dish:1': 100 }, chef, { maxQuantity: 99 }),
    ).toThrow()
    expect(() => allocateKitchenQuantities(baseline(), {}, chef, { maxQuantity: 0 })).toThrow()
    expect(() => allocateKitchenQuantities(baseline(), {}, chef, { maxQuantity: 1.5 })).toThrow()
    expect(() => allocateKitchenQuantities([...baseline(), baseline()[0]!], {}, chef)).toThrow()
    expect(() => allocateKitchenQuantities(baseline(), {}, 'missing-chef')).toThrow()
    expect(() => allocateKitchenQuantities(baseline(), { custom: 2 }, chef)).toThrow()
  })
})
