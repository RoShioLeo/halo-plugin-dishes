export interface DraftItem {
  dishId: number
  name: string
  imageUrl: string
  quantity: number
  note: string
  tags: string[]
}
export interface KitchenDraft {
  revision: number
  items: DraftItem[]
  offMenu: string
  remark: string
  nickname: string
}
export interface StoredKitchenDraft { draft: KitchenDraft; baseline: string }
interface StorageAdapter {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
  removeItem(key: string): void
}
export function isPublicQuantity(value: number): boolean {
  return Number.isSafeInteger(value) && value >= 0 && value <= 99
}
export function draftContent(value: KitchenDraft, authenticated: boolean): string {
  return JSON.stringify({
    items: value.items.map((item) => ({ dishId: item.dishId, quantity: Number(item.quantity), note: item.note || '' })).sort((a, b) => a.dishId - b.dishId),
    offMenu: value.offMenu.trim(), remark: value.remark.trim(), nickname: authenticated || (!value.items.length && !value.offMenu.trim() && !value.remark.trim()) ? '' : value.nickname.trim(),
  })
}
function validStored(value: unknown): value is StoredKitchenDraft {
  if (!value || typeof value !== 'object') return false
  const parsed = value as StoredKitchenDraft
  if (!Array.isArray(parsed.draft?.items) || typeof parsed.baseline !== 'string') return false
  const draft = parsed.draft
  return Number.isFinite(draft.revision) && typeof draft.offMenu === 'string' && typeof draft.remark === 'string' && typeof draft.nickname === 'string'
    && draft.items.every((item) => !!item && Number.isSafeInteger(item.dishId) && isPublicQuantity(item.quantity) && item.quantity > 0 && typeof item.name === 'string' && typeof item.imageUrl === 'string' && typeof item.note === 'string' && Array.isArray(item.tags))
}
/** Inline drawer updates are built only from the participant's saved snapshot. */
export function updateSavedQuantity(source: KitchenDraft, dishId: number, quantity: number): KitchenDraft {
  if (!source.items.some((item) => item.dishId === dishId)) throw new Error('Dish is not in this saved selection')
  if (!isPublicQuantity(quantity)) throw new Error('Invalid quantity')
  return {
    ...source,
    items: source.items.flatMap((item) => item.dishId === dishId && quantity === 0 ? [] : [{ ...item, tags: [...item.tags], quantity: item.dishId === dishId ? quantity : item.quantity }]),
  }
}
export const mealTagNames = ['早餐', '午餐', '晚餐']
export function applicableToMeal(tags: string[], selectedMealName: string): boolean {
  return !tags.some((tag) => mealTagNames.includes(tag)) || tags.includes(selectedMealName)
}
interface CatalogueAvailability { id: number; isAvailable: boolean; soldOut?: boolean; tags: string[] }
export function catalogueIncludesDish(dish: CatalogueAvailability, selectedIds: ReadonlySet<number>, mealName: string): boolean {
  return (dish.isAvailable && applicableToMeal(dish.tags, mealName)) || selectedIds.has(dish.id)
}
export function canIncreaseCatalogueDish(dish: CatalogueAvailability, mealName: string): boolean {
  return dish.isAvailable && !dish.soldOut && applicableToMeal(dish.tags, mealName)
}
export function stockOrdered<T extends { soldOut?: boolean }>(dishes: readonly T[]): T[] {
  return [...dishes.filter((dish) => !dish.soldOut), ...dishes.filter((dish) => dish.soldOut)]
}
export function draftKey(date: string, period: string, batchId: string): string {
  return `${date}:${period}:${batchId}`
}
export function freezeSubmissionPayload<T extends { batchId: string; draftContributionVersions: Record<string, number> }>(payload: T): T {
  const frozen = structuredClone(payload)
  Object.freeze(frozen.draftContributionVersions)
  return Object.freeze(frozen)
}
export function isCurrentBatch(boundBatchId: string, activeBatchId: string): boolean {
  return !!boundBatchId && boundBatchId === activeBatchId
}
export function canEditSubmitted(submitted: boolean): boolean {
  return !submitted
}
/** A pending draft belongs to exactly one meal and batch; switching never copies it. */
export class ContextDraftStore {
  private readonly storage: () => StorageAdapter
  constructor(storage: () => StorageAdapter) { this.storage = storage }
  private key(context: string) { return `lemonslice-kitchen-draft:${context}` }
  save(context: string, draft: KitchenDraft, baseline: string, pending: boolean) {
    try {
      if (pending) this.storage().setItem(this.key(context), JSON.stringify({ draft, baseline }))
      else this.clear(context)
    } catch { /* Current-page edits remain usable when storage is unavailable. */ }
  }
  load(context: string): StoredKitchenDraft | null {
    try {
      const value: unknown = JSON.parse(this.storage().getItem(this.key(context)) ?? 'null')
      return validStored(value) ? value : null
    } catch { return null }
  }
  clear(context: string) { try { this.storage().removeItem(this.key(context)) } catch { /* Best effort. */ } }
}
/** A write or context switch invalidates every older in-flight read. */
export function createRequestTracker() {
  let revision = 0
  return {
    begin: () => ++revision,
    current: () => revision,
    isCurrent: (token: number) => token === revision,
  }
}
