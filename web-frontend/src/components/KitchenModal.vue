<script setup lang="ts">
import KitchenIcon from '@/components/KitchenIcon.vue'
import { nextTick, onUnmounted, ref, watch } from 'vue'
const props = defineProps<{ modelValue: boolean; title: string; busy?: boolean }>()
const emit = defineEmits<{ cancel: [] }>()
const panel = ref<HTMLElement | null>(null)
let returnFocus: HTMLElement | null = null
let previousInert: boolean | undefined
function restore() {
  const app = document.getElementById('app')
  if (app && previousInert !== undefined) app.inert = previousInert
  previousInert = undefined
}
watch(() => props.modelValue, async (open) => {
  if (open) {
    returnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const app = document.getElementById('app')
    if (app) { previousInert = app.inert; app.inert = true }
    await nextTick()
    panel.value?.querySelector<HTMLElement>('[data-autofocus]')?.focus()
  } else {
    restore()
    const target = returnFocus
    await nextTick()
    if (target?.isConnected) target.focus({ preventScroll: true })
  }
}, { flush: 'post' })
function keydown(event: KeyboardEvent) {
  if (event.key === 'Escape') { event.preventDefault(); if (!props.busy) emit('cancel'); return }
  if (event.key !== 'Tab' || !panel.value) return
  const controls = [...panel.value.querySelectorAll<HTMLElement>('button:not([disabled]), input:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])')].filter((item) => item.getClientRects().length)
  const first = controls[0], last = controls[controls.length - 1]
  if (!first || !last) { event.preventDefault(); panel.value.focus(); return }
  if (event.shiftKey && (document.activeElement === first || !panel.value.contains(document.activeElement))) { event.preventDefault(); last.focus() }
  if (!event.shiftKey && (document.activeElement === last || !panel.value.contains(document.activeElement))) { event.preventDefault(); first.focus() }
}
onUnmounted(restore)
</script>
<template>
  <Teleport to="body">
    <div v-if="modelValue" class="k-modal-root">
      <button class="k-modal-backdrop" type="button" tabindex="-1" aria-label="关闭弹窗" :disabled="busy" @click="emit('cancel')" />
      <section ref="panel" class="k-modal-panel" role="dialog" aria-modal="true" :aria-label="title" tabindex="-1" @keydown="keydown">
        <header><h3>{{ title }}</h3><button type="button" class="k-icon-button" :disabled="busy" aria-label="关闭" @click="emit('cancel')"><KitchenIcon name="close-line" /></button></header>
        <div class="k-modal-body"><slot /></div>
        <footer><slot name="actions" /></footer>
      </section>
    </div>
  </Teleport>
</template>
