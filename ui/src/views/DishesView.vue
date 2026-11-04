<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  Dialog,
  IconAddCircle,
  Toast,
  VButton,
  VCard,
  VDropdown,
  VDropdownItem,
  VEmpty,
  VLoading,
  VPageHeader,
  VPagination,
  VSpace,
  VSwitch,
} from '@halo-dev/components'
import { utils } from '@halo-dev/ui-shared'
import { deleteData, getApiErrorMessage, getData, postData, putData } from '@/api/client'
import CategoryList from '@/components/CategoryList.vue'
import CategoryEditingModal from '@/components/CategoryEditingModal.vue'
import DishEditingModal from '@/components/DishEditingModal.vue'
import RiRestaurant2Line from '~icons/ri/restaurant-2-line'

type Dish = {
  id: number
  categoryId: number
  categoryName: string
  name: string
  imageUrl?: string | null
  recommendationLevel: number
  description?: string | null
  isAvailable: boolean
  sortOrder: number
  mealPeriodIds: number[]
}

type Category = { id: number; name: string; slug: string; sortOrder: number }
type Period = { id: number; code: 'breakfast' | 'lunch' | 'dinner'; name: string }

const PERIODS: Period[] = [
  { id: 1, code: 'breakfast', name: '早餐' },
  { id: 2, code: 'lunch', name: '午餐' },
  { id: 3, code: 'dinner', name: '晚餐' },
]

const loading = ref(true)
const error = ref<string | null>(null)
const dishes = ref<Dish[]>([])
const categories = ref<Category[]>([])

const selectedCategoryId = ref<number | undefined>(undefined)
const q = ref('')
const filterAvailable = ref<'all' | 'on' | 'off'>('all')
const page = ref(1)
const size = ref(20)

const dishModalOpen = ref(false)
const dishModalMode = ref<'create' | 'edit'>('create')
const editingDishId = ref<number | null>(null)
const dishSaving = ref(false)
const attachmentModalOpen = ref(false)
const checkedAll = ref(false)
const selectedDishIds = ref<Set<number>>(new Set())
const availabilityUpdating = ref<Set<number>>(new Set())

const categoryModalOpen = ref(false)
const categoryModalMode = ref<'create' | 'edit'>('create')
const editingCategoryId = ref<number | null>(null)
const categorySaving = ref(false)

const dishForm = reactive({
  categoryId: 0,
  name: '',
  imageUrl: '',
  recommendationLevel: 3,
  description: '',
  isAvailable: true,
  sortOrder: 0,
  mealPeriodIds: [] as number[],
})

const dishErrors = reactive<{
  name?: string
  categoryId?: string
  recommendationLevel?: string
  mealPeriodIds?: string
  imageUrl?: string
}>({})

const categoryForm = reactive({
  name: '',
  slug: '',
  sortOrder: 0,
})

const categoryErrors = reactive<{ name?: string; slug?: string }>({})

const sortedCategories = computed(() =>
  categories.value.slice().sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.id - b.id),
)

const hasCategories = computed(() => sortedCategories.value.length > 0)

const categoryOptions = computed(() => sortedCategories.value.map((c) => ({ value: c.id, label: c.name })))

const categoryCountMap = computed(() => {
  const map = new Map<number, number>()
  dishes.value.forEach((d) => {
    map.set(d.categoryId, (map.get(d.categoryId) ?? 0) + 1)
  })
  return map
})

function dishCountForCategory(categoryId: number) {
  return categoryCountMap.value.get(categoryId) ?? 0
}

async function handleCategoriesSorted(rows: { id: number; name: string; slug: string; sortOrder: number }[]) {
  // 更新本地顺序
  categories.value = rows.map((c, index) => ({
    ...c,
    sortOrder: index,
  }))

  try {
    await Promise.all(
      categories.value.map((c, index) =>
        putData(`/categories/${c.id}`, {
          name: c.name,
          slug: c.slug,
          sortOrder: index,
        }),
      ),
    )
    Toast.success('分类排序已保存')
    await load()
  } catch (e) {
    Dialog.error({
      title: '保存排序失败',
      description: getApiErrorMessage(e, '保存排序失败，请稍后重试', {
        DISHES_BAD_REQUEST: '分类参数不合法，请检查后重试',
      }),
    })
  }
}

function onCategorySelect(categoryId?: number) {
  selectedCategoryId.value = categoryId
}

function dishThumbSrc(url: string | null | undefined) {
  const raw = (url || '').trim()
  if (!raw) return ''
  return utils.attachment.getThumbnailUrl(raw, 'M')
}

const filteredDishes = computed(() => {
  const keyword = q.value.trim().toLowerCase()
  const avail = filterAvailable.value
  return dishes.value
    .slice()
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.id - b.id)
    .filter((d) => {
      if (selectedCategoryId.value != null && d.categoryId !== selectedCategoryId.value) return false
      if (keyword && !d.name.toLowerCase().includes(keyword)) return false
      if (avail === 'on' && !d.isAvailable) return false
      if (avail === 'off' && d.isAvailable) return false
      return true
    })
})

const pagedDishes = computed(() => {
  const start = (page.value - 1) * size.value
  return filteredDishes.value.slice(start, start + size.value)
})

const filterAvailableLabel = computed(() => {
  if (filterAvailable.value === 'on') return '已上架'
  if (filterAvailable.value === 'off') return '已下架'
  return '全部状态'
})

watch([q, selectedCategoryId, filterAvailable], () => {
  page.value = 1
})

watch(
  () => categoryForm.name,
  (v) => {
    if (categoryModalMode.value === 'create' && (!categoryForm.slug || categoryForm.slug.trim() === '')) {
      const next = normalizeSlug(v || '')
      if (next) categoryForm.slug = next
    }
    if (categoryErrors.name) categoryErrors.name = undefined
  },
)

watch(
  () => categoryForm.slug,
  () => {
    if (categoryErrors.slug) categoryErrors.slug = undefined
  },
)

function periodNames(ids: number[]) {
  const set = new Set(ids || [])
  const names = PERIODS.filter((p) => set.has(p.id)).map((p) => p.name)
  return names.length ? names.join(' / ') : '未设置'
}

function normalizeSlug(slug: string) {
  return slug
    .trim()
    .toLowerCase()
    .replace(/[\s_]+/g, '-')
    .replace(/[^a-z0-9-]/g, '')
    .replace(/-+/g, '-')
    .replace(/^-+|-+$/g, '')
}

function resetDishErrors() {
  dishErrors.name = undefined
  dishErrors.categoryId = undefined
  dishErrors.recommendationLevel = undefined
  dishErrors.mealPeriodIds = undefined
  dishErrors.imageUrl = undefined
}

function resetCategoryErrors() {
  categoryErrors.name = undefined
  categoryErrors.slug = undefined
}

type AttachmentLike = {
  status?: Record<string, unknown>
  spec?: Record<string, unknown>
  permalink?: string
  url?: string
  path?: string
}

function asString(v: unknown) {
  return typeof v === 'string' ? v : ''
}

function onAttachmentSelect(attachments: AttachmentLike[]) {
  const first = attachments?.[0]
  const status = first?.status ?? {}
  const spec = first?.spec ?? {}
  const url =
    asString(status.permalink) ||
    asString(status.url) ||
    asString(status.link) ||
    asString(status.publicUrl) ||
    asString(status.downloadUrl) ||
    asString(spec.permalink) ||
    asString(spec.url) ||
    asString(spec.link) ||
    asString(spec.path) ||
    asString(first?.permalink) ||
    asString(first?.url) ||
    asString(first?.path) ||
    ''

  if (typeof url === 'string' && url.trim() !== '') {
    dishForm.imageUrl = url.trim()
    if (dishErrors.imageUrl) dishErrors.imageUrl = undefined
    return
  }

  const specKeys = first?.spec ? Object.keys(first.spec) : []
  const statusKeys = first?.status ? Object.keys(first.status) : []
  Dialog.warning({
    title: '提示',
    description: `未获取到附件 URL，请检查附件字段（spec: ${specKeys.join(', ') || '-'}；status: ${statusKeys.join(', ') || '-'}）`,
  })
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const [dishRes, catRes] = await Promise.all([
      getData<{ items: Dish[] }>('/dishes'),
      getData<{ items: Category[] }>('/categories'),
    ])
    dishes.value = dishRes.items ?? []
    categories.value = catRes.items ?? []
  } catch (e) {
    error.value = getApiErrorMessage(e, '加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function openCreateDish() {
  if (!hasCategories.value) {
    Dialog.warning({ title: '提示', description: '请先创建至少一个分类' })
    return
  }
  dishModalMode.value = 'create'
  editingDishId.value = null
  dishForm.categoryId = selectedCategoryId.value ?? categoryOptions.value[0]?.value ?? 0
  dishForm.name = ''
  dishForm.imageUrl = ''
  dishForm.recommendationLevel = 3
  dishForm.description = ''
  dishForm.isAvailable = true
  dishForm.sortOrder = 0
  dishForm.mealPeriodIds = [2, 3]
  resetDishErrors()
  dishModalOpen.value = true
}

function openCreateDishWithAttachment() {
  openCreateDish()
  if (dishModalOpen.value) attachmentModalOpen.value = true
}

function openEditDish(d: Dish) {
  dishModalMode.value = 'edit'
  editingDishId.value = d.id
  dishForm.categoryId = d.categoryId
  dishForm.name = d.name
  dishForm.imageUrl = d.imageUrl ?? ''
  dishForm.recommendationLevel = d.recommendationLevel ?? 3
  dishForm.description = d.description ?? ''
  dishForm.isAvailable = !!d.isAvailable
  dishForm.sortOrder = d.sortOrder ?? 0
  dishForm.mealPeriodIds = Array.isArray(d.mealPeriodIds) ? [...d.mealPeriodIds] : []
  resetDishErrors()
  dishModalOpen.value = true
}

function isChecked(d: Dish) {
  return selectedDishIds.value.has(d.id)
}

function toggleDishSelection(d: Dish) {
  if (selectedDishIds.value.has(d.id)) selectedDishIds.value.delete(d.id)
  else selectedDishIds.value.add(d.id)
}

async function updateDishAvailability(d: Dish, isAvailable: boolean) {
  if (availabilityUpdating.value.has(d.id)) return
  availabilityUpdating.value.add(d.id)
  try {
    await putData(`/dishes/${d.id}`, {
      categoryId: Number(d.categoryId),
      name: d.name,
      imageUrl: d.imageUrl || null,
      recommendationLevel: Number(d.recommendationLevel) || 3,
      description: d.description || null,
      isAvailable,
      sortOrder: Number(d.sortOrder) || 0,
      mealPeriodIds: (d.mealPeriodIds || []).map((x) => Number(x)).filter((x) => x === 1 || x === 2 || x === 3),
    })
    d.isAvailable = isAvailable
    Toast.success(isAvailable ? '已上架' : '已下架')
  } catch (e) {
    Dialog.error({
      title: '更新状态失败',
      description: getApiErrorMessage(e, '更新状态失败，请稍后重试', {
        DISHES_BAD_REQUEST: '状态更新参数不合法，请刷新后重试',
      }),
    })
  } finally {
    availabilityUpdating.value.delete(d.id)
  }
}

function handleCheckAllChange(e: Event) {
  const { checked } = e.target as HTMLInputElement
  handleCheckAll(checked)
}

function handleCheckAll(checkAll: boolean) {
  if (checkAll) {
    pagedDishes.value.forEach((d) => selectedDishIds.value.add(d.id))
    return
  }
  selectedDishIds.value.clear()
}

function setFilterAvailable(value: 'all' | 'on' | 'off') {
  filterAvailable.value = value
}

function togglePeriod(id: number) {
  const set = new Set(dishForm.mealPeriodIds)
  if (set.has(id)) set.delete(id)
  else set.add(id)
  dishForm.mealPeriodIds = [...set].sort((a, b) => a - b)
}

async function saveDish() {
  const name = dishForm.name.trim()
  resetDishErrors()
  if (!name) dishErrors.name = '请填写菜品名称'
  if (!dishForm.categoryId) dishErrors.categoryId = '请选择分类'
  const rl = Number(dishForm.recommendationLevel)
  if (!Number.isFinite(rl) || rl < 1 || rl > 5) dishErrors.recommendationLevel = '推荐等级需为 1-5'
  if (!Array.isArray(dishForm.mealPeriodIds) || dishForm.mealPeriodIds.length === 0) dishErrors.mealPeriodIds = '请至少选择一个餐段'
  const rawImage = (dishForm.imageUrl || '').trim()
  if (rawImage && !/^https?:\/\//i.test(rawImage) && !rawImage.startsWith('/')) {
    dishErrors.imageUrl = '图片 URL 需为 http(s) 链接或站内相对路径（以 / 开头）'
  }
  if (Object.values(dishErrors).some(Boolean)) return

  dishSaving.value = true
  try {
    const payload = {
      categoryId: Number(dishForm.categoryId),
      name,
      imageUrl: dishForm.imageUrl.trim() || null,
      recommendationLevel: Number(dishForm.recommendationLevel) || 3,
      description: dishForm.description.trim() || null,
      isAvailable: !!dishForm.isAvailable,
      sortOrder: Number(dishForm.sortOrder) || 0,
      mealPeriodIds: dishForm.mealPeriodIds.map((x) => Number(x)).filter((x) => x === 1 || x === 2 || x === 3),
    }
    if (dishModalMode.value === 'create') await postData<{ id: number }>('/dishes', payload)
    else if (editingDishId.value != null) await putData(`/dishes/${editingDishId.value}`, payload)
    dishModalOpen.value = false
    Toast.success('保存成功')
    await load()
  } catch (e) {
    const msg = getApiErrorMessage(e, '保存失败，请稍后重试')
    if (msg.includes('菜品名称')) dishErrors.name = msg
    else if (msg.includes('category_id') || msg.includes('分类')) dishErrors.categoryId = msg
    else if (msg.includes('推荐等级') || msg.includes('1-5')) dishErrors.recommendationLevel = msg
    else if (msg.includes('餐段')) dishErrors.mealPeriodIds = msg
    else if (msg.includes('图片') || msg.toLowerCase().includes('url')) dishErrors.imageUrl = msg
    else Dialog.error({ title: '保存失败', description: msg })
  } finally {
    dishSaving.value = false
  }
}

function removeDish(d: Dish, closeModal = false) {
  Dialog.warning({
    title: '确认删除',
    description: `删除菜品「${d.name}」？`,
    showCancel: true,
    onConfirm: async () => {
      try {
        await deleteData(`/dishes/${d.id}`)
        if (closeModal) dishModalOpen.value = false
        Toast.success('已删除')
        await load()
      } catch (e) {
        Dialog.error({
          title: '删除失败',
          description: getApiErrorMessage(e, '删除失败，请稍后重试', {
            DISHES_NOT_FOUND: '菜品不存在，可能已被删除',
          }),
        })
      }
    },
  })
}

function removeSelectedDishes() {
  if (!selectedDishIds.value.size) return
  Dialog.warning({
    title: '确认批量删除',
    description: `将删除已勾选的 ${selectedDishIds.value.size} 个菜品，此操作不可恢复。`,
    showCancel: true,
    onConfirm: async () => {
      try {
        await Promise.all([...selectedDishIds.value].map((id) => deleteData(`/dishes/${id}`)))
        selectedDishIds.value.clear()
        Toast.success('已删除')
        await load()
      } catch (e) {
        Dialog.error({
          title: '删除失败',
          description: getApiErrorMessage(e, '删除失败，请稍后重试', {
            DISHES_NOT_FOUND: '部分菜品不存在，建议刷新列表',
          }),
        })
      }
    },
  })
}

function removeEditingDish() {
  if (dishModalMode.value !== 'edit' || editingDishId.value == null) return
  const target = dishes.value.find((x) => x.id === editingDishId.value)
  if (!target) return
  removeDish(target, true)
}

function openCreateCategory() {
  categoryModalMode.value = 'create'
  editingCategoryId.value = null
  categoryForm.name = ''
  categoryForm.slug = ''
  categoryForm.sortOrder = 0
  resetCategoryErrors()
  categoryModalOpen.value = true
}

function openEditCategory(c: Category) {
  categoryModalMode.value = 'edit'
  editingCategoryId.value = c.id
  categoryForm.name = c.name
  categoryForm.slug = c.slug
  categoryForm.sortOrder = c.sortOrder ?? 0
  resetCategoryErrors()
  categoryModalOpen.value = true
}

async function saveCategory() {
  const name = categoryForm.name.trim()
  const slug = normalizeSlug(categoryForm.slug)
  categoryForm.slug = slug
  resetCategoryErrors()
  if (!name) categoryErrors.name = '请填写分类名称'
  if (!slug) categoryErrors.slug = '请填写 slug（用于标识）'
  if (Object.values(categoryErrors).some(Boolean)) return

  categorySaving.value = true
  try {
    const payload = { name, slug, sortOrder: Number(categoryForm.sortOrder) || 0 }
    if (categoryModalMode.value === 'create') await postData('/categories', payload)
    else if (editingCategoryId.value != null) await putData(`/categories/${editingCategoryId.value}`, payload)
    categoryModalOpen.value = false
    Toast.success('分类已保存')
    await load()
  } catch (e) {
    const msg = getApiErrorMessage(e, '保存失败，请稍后重试')
    if (msg.includes('名称')) categoryErrors.name = msg
    else if (msg.includes('slug')) categoryErrors.slug = msg
    else Dialog.error({ title: '保存失败', description: msg })
  } finally {
    categorySaving.value = false
  }
}

function removeCategory(c: Category) {
  if ((categoryCountMap.value.get(c.id) ?? 0) > 0) {
    Dialog.warning({ title: '无法删除', description: '该分类下仍有菜品，请先迁移或删除菜品。' })
    return
  }
  Dialog.warning({
    title: '确认删除',
    description: `删除分类「${c.name}」？`,
    showCancel: true,
    onConfirm: async () => {
      try {
        await deleteData(`/categories/${c.id}`)
        if (selectedCategoryId.value === c.id) selectedCategoryId.value = undefined
        if (editingCategoryId.value === c.id) categoryModalOpen.value = false
        Toast.success('分类已删除')
        await load()
      } catch (e) {
        Dialog.error({
          title: '删除失败',
          description: getApiErrorMessage(e, '删除失败，请稍后重试', {
            DISHES_CATEGORY_DELETE_CONFLICT: '分类下仍有菜品，请先迁移或删除菜品',
            DISHES_NOT_FOUND: '分类不存在，可能已被删除',
          }),
        })
      }
    },
  })
}

onMounted(() => void load())

watch(
  () => pagedDishes.value.map((d) => d.id),
  (ids) => {
    const valid = new Set(ids)
    selectedDishIds.value.forEach((id) => {
      if (!valid.has(id)) selectedDishIds.value.delete(id)
    })
    checkedAll.value = !!ids.length && ids.every((id) => selectedDishIds.value.has(id))
  },
  { immediate: true },
)
</script>

<template>
  <DishEditingModal
    :visible="dishModalOpen"
    :mode="dishModalMode"
    :saving="dishSaving"
    :form="dishForm"
    :errors="dishErrors"
    :periods="PERIODS"
    :category-options="categoryOptions"
    @close="dishModalOpen = false"
    @save="saveDish"
    @delete="removeEditingDish"
    @toggle-period="togglePeriod"
    @open-attachment="attachmentModalOpen = true"
  />

  <CategoryEditingModal
    :visible="categoryModalOpen"
    :mode="categoryModalMode"
    :saving="categorySaving"
    :form="categoryForm"
    :errors="categoryErrors"
    @close="categoryModalOpen = false"
    @save="saveCategory"
    @delete="removeCategory({ id: editingCategoryId!, name: categoryForm.name, slug: categoryForm.slug, sortOrder: categoryForm.sortOrder })"
  />

  <AttachmentSelectorModal v-model:visible="attachmentModalOpen" :max="1" :accepts="['image/*']" @select="onAttachmentSelect" />

  <VPageHeader title="菜品管理">
    <template #icon>
      <RiRestaurant2Line />
    </template>
  </VPageHeader>

  <div class="dishes-page :uno: p-4">
    <div class="dishes-layout">
      <div class="dishes-sidebar">
        <CategoryList
          :loading="loading"
          :error="error"
          :categories="sortedCategories"
          :selected-category-id="selectedCategoryId"
          :total-dishes="dishes.length"
          :dish-count="dishCountForCategory"
          @select="onCategorySelect"
          @create="openCreateCategory"
          @edit="openEditCategory"
          @delete="removeCategory"
          @refresh="load"
          @sorted="handleCategoriesSorted"
        />
      </div>

      <div class="dishes-main-column">
        <VCard class="dishes-main-card" :body-class="[':uno: !p-0']">
          <template #header>
            <div class="dishes-toolbar :uno: block w-full px-4 py-4">
              <div class="dishes-toolbar-row :uno: flex items-center justify-between gap-4">
                <div class=":uno: flex items-center gap-2 box-border border border-gray-300 rounded-base">
                  <input
                    v-model="q"
                    class=":uno: h-9 w-48 rounded-md border border-gray-200 bg-white px-3 text-sm outline-none focus:border-primary"
                    placeholder="搜索菜品名称"
                  />
                </div>

                <div class="dishes-toolbar-actions :uno: flex items-center gap-2">
                  <VDropdown>
                    <VButton size="sm" type="secondary" class="toolbar-btn">{{ filterAvailableLabel }}</VButton>
                    <template #popper>
                      <VDropdownItem @click="setFilterAvailable('all')">全部状态</VDropdownItem>
                      <VDropdownItem @click="setFilterAvailable('on')">已上架</VDropdownItem>
                      <VDropdownItem @click="setFilterAvailable('off')">已下架</VDropdownItem>
                    </template>
                  </VDropdown>
                  <VButton v-if="selectedDishIds.size" size="sm" type="danger" class="toolbar-btn" @click="removeSelectedDishes">
                    删除已选 {{ selectedDishIds.size }}
                  </VButton>
                  <VButton size="sm" type="secondary" class="toolbar-btn" @click="openCreateDish()">新增菜品</VButton>
                </div>
              </div>

            </div>
          </template>

          <VLoading v-if="loading" />
          <Transition v-else-if="!hasCategories" appear name="fade">
            <VEmpty message="请先在左侧新建分类" title="暂无分类">
              <template #actions>
                <VSpace>
                  <VButton @click="load">刷新</VButton>
                  <VButton type="primary" @click="openCreateCategory">
                    <template #icon>
                      <IconAddCircle class=":uno: size-full" />
                    </template>
                    新建分类
                  </VButton>
                </VSpace>
              </template>
            </VEmpty>
          </Transition>
          <Transition v-else-if="!dishes.length" appear name="fade">
            <VEmpty message="你可以尝试刷新或者新建菜品" title="当前没有菜品">
              <template #actions>
                <VSpace>
                  <VButton @click="load">刷新</VButton>
                  <VButton type="primary" @click="openCreateDish()">
                    <template #icon>
                      <IconAddCircle class=":uno: size-full" />
                    </template>
                    新增菜品
                  </VButton>
                </VSpace>
              </template>
            </VEmpty>
          </Transition>
          <Transition v-else-if="!filteredDishes.length" appear name="fade">
            <VEmpty message="请调整筛选或搜索条件" title="无匹配结果" />
          </Transition>
          <Transition v-else appear name="fade">
            <div class=":uno:  overflow-x-auto">
              <table class=":uno: min-w-full table-fixed border-collapse">
                <colgroup>
                  <col class=":uno: w-12" />
                  <col class=":uno: min-w-[14rem] w-56" />
                  <col class=":uno: w-24" />
                  <col class=":uno: w-30" />
                  <col class=":uno: w-42" />
                  <col class=":uno: w-24" />
                  <col class=":uno: w-40" />
                </colgroup>
                <thead class="dishes-table-head :uno: border-y border-gray-200 bg-gray-50 text-sm text-gray-600 font-semibold">
                  <tr>
                    <th class=":uno: px-3 py-2 text-left">
                      <input v-model="checkedAll" type="checkbox" @change="handleCheckAllChange" />
                    </th>
                    <th class=":uno: px-3 py-2 text-left">菜品名称</th>
                    <th class=":uno: px-3 py-2 text-left">菜品分类</th>
                    <th class=":uno: px-3 py-2 text-left">推荐星级</th>
                    <th class=":uno: px-3 py-2 text-left">适用餐段</th>
                    <th class=":uno: px-3 py-2 text-left">上架/下架</th>
                    <th class=":uno: px-3 py-2 text-left">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="d in pagedDishes"
                    :key="d.id"
                    class="dish-row :uno: border-b border-gray-100 text-sm transition-colors hover:bg-gray-50"
                  >
                    <td class=":uno: px-3 py-3">
                      <input :checked="isChecked(d)" type="checkbox" @change="toggleDishSelection(d)" />
                    </td>
                    <td class=":uno: px-3 py-3">
                      <div class=":uno: min-w-0 flex items-center gap-3">
                        <div v-if="d.imageUrl" class="dish-thumb">
                          <img class="dish-thumb__img" :src="dishThumbSrc(d.imageUrl)" :alt="d.name" loading="lazy" />
                        </div>
                        <div v-else class="dish-thumb dish-thumb--placeholder" aria-hidden="true">
                          <span class="dish-thumb__letter">{{ d.name.slice(0, 1) }}</span>
                        </div>
                        <div class=":uno: min-w-0">
                          <p class="dish-name :uno: truncate text-sm font-medium">{{ d.name }}</p>
                          <p class="dish-meta :uno: text-xs">ID {{ d.id }}</p>
                        </div>
                      </div>
                    </td>
                    <td class="dish-meta :uno: px-3 py-3">{{ d.categoryName }}</td>
                    <td class=":uno: px-3 py-3 text-amber-500 tracking-wide">
                      {{ '★'.repeat(Math.max(1, Math.min(5, d.recommendationLevel || 1))) }}
                    </td>
                    <td class="dish-meta :uno: px-3 py-3">{{ periodNames(d.mealPeriodIds) }}</td>
                    <td class=":uno: px-3 py-3">
                      <VSwitch
                        :model-value="d.isAvailable"
                        :disabled="availabilityUpdating.has(d.id)"
                        @update:model-value="(value:boolean) => updateDishAvailability(d, value)"
                      />
                    </td>
                    <td class=":uno: px-3 py-3">
                      <VSpace class=":uno: gap-2">
                        <VButton size="xs" type="secondary" class="op-btn" @click="openEditDish(d)">编辑</VButton>
                        <VButton size="xs" type="danger" class="op-btn" @click="removeDish(d)">删除</VButton>
                      </VSpace>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </Transition>

          <template #footer>
            <div class=":uno: px-4 py-3">
              <VPagination v-model:page="page" v-model:size="size" :total="filteredDishes.length" :size-options="[10, 20, 30, 50, 100]" />
            </div>
          </template>
        </VCard>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 不依赖 Uno 原子类：控制台里 w-68等可能未生成，侧栏会撑满整行导致右侧列表被挤出视口 */
.dishes-layout {
  display: flex;
  flex-flow: row nowrap;
  align-items: flex-start;
  gap: 16px;
  width: 100%;
  box-sizing: border-box;
}

.dishes-sidebar {
  flex: 0 0 256px;
  width: 256px;
  max-width: 256px;
  min-width: 0;
  box-sizing: border-box;
}

.dishes-main-column {
  flex: 1 1 0;
  min-width: 0;
  box-sizing: border-box;
}

.dishes-page :deep(.halo-card) {
  border-radius: 8px;
}

.dishes-sidebar :deep(.halo-card) {
  box-shadow: none;
}

.dishes-main-card {
  box-shadow: none;
}

.dishes-main-card :deep(.card-header),
.dishes-main-card :deep(.card-footer) {
  padding: 0;
}

.toolbar-btn {
  border-radius: 8px;
  font-weight: 500;
}

.dish-row td {
  vertical-align: middle;
}

.op-btn {
  border-radius: 4px;
}

.dish-thumb {
  flex: 0 0 auto;
  width: 48px;
  height: 48px;
  border-radius: 8px;
  overflow: hidden;
  background: var(--ls-dishes-soft, #f7f7f8);
  border: 1px solid var(--ls-dishes-line, #e8e8eb);
  box-sizing: border-box;
}

.dish-thumb__img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center;
}

.dish-thumb--placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
}

.dish-thumb__letter {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--ls-dishes-muted, #6b6b75);
  line-height: 1;
}

.dishes-toolbar {
  background: var(--ls-dishes-soft, #f7f7f8);
}

.dishes-table-head {
  background: var(--ls-dishes-soft, #f7f7f8);
  border-color: var(--ls-dishes-line, #e8e8eb);
  color: var(--ls-dishes-muted, #6b6b75);
}

.dish-name {
  color: var(--ls-dishes-ink, #29292e);
}

.dish-meta {
  color: var(--ls-dishes-muted, #6b6b75);
}

.dish-row:hover {
  background: var(--ls-dishes-soft, #f7f7f8);
}

.dishes-toolbar-row,
.dishes-toolbar-actions {
  flex-wrap: wrap;
}

@media (max-width: 1023px) {
  .dishes-layout {
    flex-direction: column;
  }

  .dishes-sidebar,
  .dishes-main-column {
    flex: 0 1 auto;
    width: 100%;
    max-width: 100%;
  }
}
</style>
