<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'

const emit = defineEmits<{
  resolved: [payload: { isScheduled: boolean; orderDate: string }]
}>()

const step = ref<'choose' | 'date'>('choose')
const pickDate = ref('')

function toYmd(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function localTodayStr(): string {
  return toYmd(new Date())
}

function defaultScheduledDate(): string {
  const d = new Date()
  d.setDate(d.getDate() + 1)
  return toYmd(d)
}

const minDate = computed(() => localTodayStr())

const maxDate = computed(() => {
  const d = new Date()
  d.setFullYear(d.getFullYear() + 1)
  return toYmd(d)
})

function onTodayOrder() {
  emit('resolved', { isScheduled: false, orderDate: localTodayStr() })
}

function goPickDate() {
  step.value = 'date'
  if (!pickDate.value || pickDate.value < minDate.value) {
    pickDate.value = defaultScheduledDate()
  }
}

function onConfirmScheduled() {
  const d = pickDate.value
  if (!d || d < minDate.value || d > maxDate.value) return
  emit('resolved', { isScheduled: true, orderDate: d })
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    e.preventDefault()
  }
}

onMounted(() => {
  pickDate.value = defaultScheduledDate()
  window.addEventListener('keydown', onKeydown)
})
onUnmounted(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <Teleport to="body">
    <div
      class="osp-root fixed inset-0 z-[95] flex items-end justify-center bg-slate-900/45 backdrop-blur-[2px] sm:items-center sm:p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="osp-title"
    >
      <Transition name="osp-sheet" appear>
        <div
          class="osp-panel relative flex w-full max-w-md flex-col overflow-hidden rounded-t-2xl border border-slate-200/90 bg-white shadow-[0_-12px_48px_rgba(15,23,42,0.15)] sm:rounded-2xl sm:shadow-[0_24px_64px_-16px_rgba(15,23,42,0.2)]"
        >
          <div class="mx-auto mt-2 h-1 w-10 shrink-0 rounded-full bg-slate-200/90 sm:hidden" />

          <div class="px-4 pb-2 pt-4 sm:pt-4">
            <p class="text-[10px] font-semibold uppercase tracking-[0.14em] text-slate-400">
              开始点菜
            </p>
            <h2 id="osp-title" class="mt-1 text-lg font-semibold tracking-tight text-slate-900">
              请选择点菜方式
            </h2>
            <p class="mt-2 text-xs leading-relaxed text-slate-500">
              今日点菜立即生效；预约将为所选日期备餐。
            </p>
          </div>

          <div class="min-h-[11rem] px-4 pb-4">
            <Transition name="osp-step" mode="out-in">
              <div v-if="step === 'choose'" key="choose" class="flex flex-col gap-2 pt-1">
                <button
                  type="button"
                  class="osp-btn osp-btn--primary flex h-12 w-full items-center justify-center rounded-xl text-sm font-semibold text-white transition-transform active:scale-[0.98]"
                  @click="onTodayOrder"
                >
                  立即点菜
                </button>
                <button
                  type="button"
                  class="osp-btn osp-btn--secondary flex h-12 w-full items-center justify-center rounded-xl border border-slate-200 bg-white text-sm font-medium text-slate-800 transition-colors hover:bg-slate-50 active:scale-[0.98]"
                  @click="goPickDate"
                >
                  预约点菜
                </button>
              </div>

              <div v-else key="date" class="pt-1">
                <label class="block">
                  <span class="mb-2 block text-xs font-medium text-slate-600">用餐日期</span>
                  <input
                    v-model="pickDate"
                    type="date"
                    class="osp-date-input w-full rounded-xl border border-slate-200 bg-slate-50/80 px-4 py-2 text-sm text-slate-900 focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-500/20"
                    :min="minDate"
                    :max="maxDate"
                  />
                </label>
                
                <div class="mt-4 flex gap-2">
                  <button
                    type="button"
                    class="flex h-12 min-w-0 flex-1 items-center justify-center rounded-xl border border-slate-200 bg-white text-sm font-medium text-slate-700 hover:bg-slate-50"
                    @click="step = 'choose'"
                  >
                    返回
                  </button>
                  <button
                    type="button"
                    class="flex h-12 min-w-0 flex-[1.2] items-center justify-center rounded-xl bg-blue-600 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-slate-300"
                    :disabled="!pickDate || pickDate < minDate || pickDate > maxDate"
                    @click="onConfirmScheduled"
                  >
                    确认日期
                  </button>
                </div>
              </div>
            </Transition>
          </div>
        </div>
      </Transition>
    </div>
  </Teleport>
</template>

<style scoped>
.osp-btn--primary {
  background: linear-gradient(180deg, #3b82f6 0%, #2563eb 100%);
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.35);
}

.osp-btn--primary:hover {
  filter: brightness(1.03);
}

.osp-sheet-enter-active {
  transition:
    transform 0.38s cubic-bezier(0.22, 1, 0.36, 1),
    opacity 0.32s cubic-bezier(0.25, 0.46, 0.45, 0.94);
}

.osp-sheet-enter-from {
  opacity: 0;
  transform: translate3d(0, 100%, 0);
}

@media (min-width: 640px) {
  .osp-sheet-enter-from {
    transform: translate3d(0, 0.6rem, 0) scale(0.98);
  }
}

.osp-step-enter-active,
.osp-step-leave-active {
  transition:
    opacity 0.22s cubic-bezier(0.25, 0.46, 0.45, 0.94),
    transform 0.24s cubic-bezier(0.25, 0.46, 0.45, 0.94);
}

.osp-step-enter-from {
  opacity: 0;
  transform: translate3d(0, 0.35rem, 0);
}

.osp-step-leave-to {
  opacity: 0;
  transform: translate3d(0, -0.2rem, 0);
}

@media (prefers-reduced-motion: reduce) {
  .osp-sheet-enter-active,
  .osp-step-enter-active,
  .osp-step-leave-active {
    transition-duration: 0.01ms;
  }

  .osp-sheet-enter-from,
  .osp-step-enter-from,
  .osp-step-leave-to {
    transform: none;
  }
}
</style>
