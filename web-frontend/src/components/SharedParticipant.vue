<script setup lang="ts">
import KitchenIcon from '@/components/KitchenIcon.vue'
import { computed, ref, watch } from 'vue'
import { resolveMediaUrl } from '@/utils/mediaUrl'
import type { Participant } from '@/api/kitchen'
import { normalizeCustomItems, quantityLabel, withinPublicCartLimit } from '@/api/kitchen'
import { canEditSubmitted, isPublicQuantity } from '@/utils/kitchenDraft'
const props = defineProps<{
  person: Participant
  editable: boolean
  busy: boolean
  hideSupplements?: boolean
  canIncrease: (dishId: number) => boolean
}>()
const avatarFailed = ref(false)
const customItems = computed(() => normalizeCustomItems(props.person.customItems))
const publicEditable = computed(() => props.editable && canEditSubmitted(props.person.submitted) && withinPublicCartLimit(props.person))
const avatarUrl = computed(() => resolveMediaUrl(props.person.avatarUrl))
const avatarLetter = computed(() => {
  const name = props.person.displayName.trim()
  if (!name) return '客'
  try { return Array.from(new Intl.Segmenter('zh-CN', { granularity: 'grapheme' }).segment(name))[0]?.segment || '客' }
  catch { return Array.from(name)[0] || '客' }
})
watch(() => props.person.avatarUrl, () => { avatarFailed.value = false })
const emit = defineEmits<{
  quantity: [person: Participant, dishId: number, quantity: number]
  invalid: [message: string]
}>()
function change(dishId: number, previous: number, event: Event) {
  const input = event.target as HTMLInputElement
  const raw = input.value.trim()
  const value = Number(raw)
  if (!raw || !isPublicQuantity(value)) {
    input.value = quantityLabel(previous)
    emit('invalid', '份数须为 0 至 99 的整数，0 表示移除。')
    return
  }
  if (value > previous && !props.canIncrease(dishId)) {
    input.value = quantityLabel(previous)
    emit('invalid', '这道菜当前只能减量或移除。')
    return
  }
  emit('quantity', props.person, dishId, value)
  input.value = quantityLabel(previous)
}
</script>

<template>
  <li class="k-shared-person">
    <span class="k-participant-avatar">
      <img v-if="avatarUrl && !avatarFailed" :src="avatarUrl" :alt="`${person.displayName}头像`" width="32" height="32" loading="lazy" @error="avatarFailed = true" />
      <span v-else aria-hidden="true">{{ avatarLetter }}</span>
    </span>
    <div class="k-participant-title">
      <h4>{{ person.displayName }} <span v-if="person.mine" class="k-mine-label">我</span></h4>
    </div>
    <div class="k-participant-content">
    <ul v-if="person.items.length || customItems.length" class="k-person-items">
      <li v-for="item in person.items" :key="item.dishId">
        <span class="k-person-dish-name">{{ item.name }}</span>
        <div v-if="publicEditable" class="k-catalogue-stepper k-drawer-stepper" role="group" :aria-label="`${person.displayName} 的 ${item.name} 份数`">
          <button type="button" :disabled="busy" :aria-label="`减少 ${person.displayName} 的 ${item.name} 份数，减到 0 移除`" @click="emit('quantity', person, item.dishId, Math.max(0, item.quantity - 1))"><KitchenIcon name="subtract-line" /></button>
          <input type="number" min="0" :max="canIncrease(item.dishId) ? 99 : item.quantity" step="1" :value="quantityLabel(item.quantity)" :disabled="busy" :aria-label="`${person.displayName} 的 ${item.name} 份数，0 表示移除`" @change="change(item.dishId, item.quantity, $event)" />
          <button type="button" :disabled="busy || item.quantity >= 99 || !canIncrease(item.dishId)" :aria-label="`增加 ${person.displayName} 的 ${item.name} 份数`" @click="emit('quantity', person, item.dishId, Math.min(99, item.quantity + 1))"><KitchenIcon name="add-line" /></button>
        </div>
        <span v-else class="k-quantity">× {{ quantityLabel(item.quantity) }}</span>
      </li>
      <li v-for="item in customItems" :key="`custom:${item.customId}`">
        <span class="k-person-dish-name">{{ item.name }}</span>
        <span class="k-quantity">× {{ quantityLabel(item.quantity) }}</span>
      </li>
    </ul>
    <p v-if="person.offMenu && !hideSupplements" class="k-person-wish"><span>想吃其他菜</span>{{ person.offMenu }}</p>
    <p v-if="person.remark && !hideSupplements" class="k-person-wish"><span>本餐备注</span>{{ person.remark }}</p>
    </div>
  </li>
</template>
