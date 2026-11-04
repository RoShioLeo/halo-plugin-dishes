<script lang="ts" setup>
import { IconList, VButton, VCard, VDropdownItem, VEmpty, VEntity, VEntityField, VLoading, VSpace } from '@halo-dev/components'
import { ref, watch } from 'vue'
import { VueDraggable } from 'vue-draggable-plus'

export type CategoryRow = {
  id: number
  name: string
  slug: string
  sortOrder: number
}

const props = defineProps<{
  loading: boolean
  error?: string | null
  categories: CategoryRow[]
  /** undefined 表示「全部分类」 */
  selectedCategoryId?: number
  totalDishes: number
  dishCount: (categoryId: number) => number
}>()

const emit = defineEmits<{
  select: [categoryId?: number]
  create: []
  edit: [category: CategoryRow]
  delete: [category: CategoryRow]
  refresh: []
  /** 拖拽排序后触发，传出最新顺序 */
  sorted: [categories: CategoryRow[]]
}>()

const innerCategories = ref<CategoryRow[]>([])

watch(
  () => props.categories,
  (val) => {
    innerCategories.value = [...val]
  },
  { immediate: true },
)

function handleSorted() {
  emit('sorted', innerCategories.value)
}
</script>
<template>
  <VCard :body-class="[':uno: !p-0']" class="category-card" title="分类">
    <VLoading v-if="loading" />
    <template v-else>
      <div v-if="error" class=":uno: m-3 rounded border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700">
        {{ error }}
      </div>
      <Transition v-else-if="!innerCategories.length" appear name="fade">
        <VEmpty message="你可以尝试刷新或者新建分类" title="当前没有分类">
          <template #actions>
            <VSpace>
              <VButton size="sm" @click="emit('refresh')">刷新</VButton>
              <VButton size="sm" type="primary" @click="emit('create')">新建分类</VButton>
            </VSpace>
          </template>
        </VEmpty>
      </Transition>
      <Transition v-else appear name="fade">
        <div class=":uno: w-full overflow-x-auto">
          <table class="category-table :uno: w-full border-spacing-0">
            <tbody class=":uno: divide-y divide-gray-100">
              <VEntity
                :is-selected="selectedCategoryId === undefined"
                class="category-entity :uno: group"
                @click="emit('select', undefined)"
              >
                <template #start>
                  <VEntityField description="全部菜品" title="全部分类"></VEntityField>
                </template>
                <template #end>
                  <VEntityField>
                    <template #description>
                      <span class="count-badge">{{ totalDishes }} 道</span>
                    </template>
                  </VEntityField>
                </template>
              </VEntity>
            </tbody>
            <VueDraggable
              v-model="innerCategories"
              class=":uno: divide-y divide-gray-100"
              handle=".drag-element"
              item-key="id"
              tag="tbody"
              @update="handleSorted"
            >
              <VEntity
                v-for="c in innerCategories"
                :key="c.id"
                :is-selected="selectedCategoryId === c.id"
                class="category-entity :uno: group"
                @click="emit('select', c.id)"
              >
                <template #prepend>
                  <div
                    class=":uno: drag-element absolute inset-y-0 left-0 hidden w-3.5 cursor-move items-center bg-gray-100 transition-all group-hover:flex hover:bg-gray-200"
                  >
                    <IconList class=":uno: size-3.5" />
                  </div>
                </template>
                <template #start>
                  <VEntityField :description="c.slug" :title="c.name"></VEntityField>
                </template>
                <template #end>
                  <VEntityField>
                    <template #description>
                      <span class="count-badge">{{ dishCount(c.id) }} 道</span>
                    </template>
                  </VEntityField>
                </template>
                <template #dropdownItems>
                  <VDropdownItem @click.stop="emit('edit', c)">修改</VDropdownItem>
                  <VDropdownItem type="danger" @click.stop="emit('delete', c)">删除</VDropdownItem>
                </template>
              </VEntity>
            </VueDraggable>
          </table>
        </div>
      </Transition>
    </template>
    <template v-if="!loading && innerCategories.length" #footer>
      <VButton block class="new-category-btn" type="secondary" @click="emit('create')">新增分类</VButton>
    </template>
  </VCard>
</template>

<style scoped>
.category-card {
  box-shadow: none;
  max-width: 100%;
}

.category-entity {
  transition: background-color 0.2s ease;
}

.category-entity:hover {
  background: var(--ls-dishes-soft, #f7f7f8);
}

.category-card :deep(table.category-table) {
  table-layout: fixed;
  width: 100%;
}

/* 名称列占满剩余宽度；不要用 width:1%，在 fixed 表格里会把文字列压到几乎为 0 */
.category-card :deep(.entity-start-wrapper) {
  width: auto;
  vertical-align: middle;
  padding-right: 8px;
}

/* 右侧「�」固定宽度，避免再出现大块留白，也不挤压名称 */
.category-card :deep(.entity-end-wrapper) {
  width: 6.5rem;
  max-width: 6.5rem;
  vertical-align: middle;
  white-space: nowrap;
}

.category-card :deep(.entity-start) {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.category-card :deep(.entity-field-wrapper) {
  min-width: 0;
  flex: 1;
}

.category-card :deep(.entity-field-title-body),
.category-card :deep(.entity-field-description-body) {
  min-width: 0;
}

.category-card :deep(.entity-field-title),
.category-card :deep(.entity-field-description) {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}

.category-card :deep(.entity-end) {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.category-card :deep(.entity-dropdown) {
  margin-left: 0;
  flex-shrink: 0;
}

.count-badge {
  display: inline-flex;
  align-items: center;
  background: var(--ls-dishes-soft, #f7f7f8);
  border-radius: 4px;
  color: var(--ls-dishes-muted, #6b6b75);
  font-size: 11px;
  padding: 4px 8px;
}

.new-category-btn {
  border-radius: 8px;
  font-weight: 500;
}
</style>
