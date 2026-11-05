export interface KitchenQuantityItem {
  key: string
  quantity: number
}

export interface KitchenQuantitySource {
  sourceId: string
  items: KitchenQuantityItem[]
}

type QuantityBaseline = readonly {
  sourceId: string
  items: readonly KitchenQuantityItem[]
}[]

function quantity(value: number, maximum: number): number {
  if (!Number.isSafeInteger(value) || value < 0 || value > maximum) {
    throw new RangeError('Quantity must be a nonnegative integer within the limit')
  }
  return value
}

function checkKey(key: string) {
  if (!/^dish:[1-9]\d*$/.test(key) && !(key.startsWith('custom:') && key.slice(7).trim())) {
    throw new Error('An allocation key must be dish:N or a distinct custom:key')
  }
}

/** Recompute from the frozen baseline in supplied UI order; zero rows remain until commit. */
export function allocateKitchenQuantities(
  baseline: QuantityBaseline,
  targets: Readonly<Record<string, number>>,
  chefSourceId: string,
  options: { maxQuantity?: number } = {},
): KitchenQuantitySource[] {
  const maximum = quantity(options.maxQuantity ?? 9999, 9999)
  if (!maximum) throw new RangeError('maxQuantity must be positive')
  const sourceIds = new Set<string>()
  const baselineTotals = new Map<string, number>()
  const rows = baseline.map((source) => {
    if (!source.sourceId.trim() || sourceIds.has(source.sourceId)) {
      throw new Error('Every allocation source must have a distinct, nonempty ID')
    }
    sourceIds.add(source.sourceId)
    const itemKeys = new Set<string>()
    return {
      sourceId: source.sourceId,
      items: source.items.map((item) => {
        checkKey(item.key)
        if (itemKeys.has(item.key))
          throw new Error('Allocation keys must be distinct within a source')
        itemKeys.add(item.key)
        const value = quantity(item.quantity, maximum)
        const total = (baselineTotals.get(item.key) ?? 0) + value
        if (!Number.isSafeInteger(total))
          throw new RangeError('Baseline total exceeds safe precision')
        baselineTotals.set(item.key, total)
        return { key: item.key, quantity: value }
      }),
    }
  })
  const chef = rows.find((source) => source.sourceId === chefSourceId)
  if (!chef) throw new Error('The baseline must contain the chef allocation source')

  for (const [key, value] of Object.entries(targets)) {
    checkKey(key)
    const target = quantity(value, maximum)
    const original = baselineTotals.get(key) ?? 0
    if (target >= original) {
      const increase = target - original
      const chefItem = chef.items.find((item) => item.key === key)
      if (increase > 0 && chefItem) chefItem.quantity += increase
      else if (increase > 0 || !baselineTotals.has(key))
        chef.items.push({ key, quantity: increase })
      continue
    }
    let decrease = original - target
    for (let index = rows.length - 1; index >= 0 && decrease > 0; index -= 1) {
      const item = rows[index]!.items.find((candidate) => candidate.key === key)
      if (!item) continue
      const removed = Math.min(item.quantity, decrease)
      item.quantity -= removed
      decrease -= removed
    }
  }
  return rows
}
