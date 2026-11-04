<script setup lang="ts">
import { onMounted, onUnmounted, reactive, watch } from 'vue'

import { resolveMediaUrl } from '@/utils/mediaUrl'

export interface SubmitModalDishLite {
  id: number
  name: string
  image_url: string | null
  category_id: number
  category_name: string
}

export interface SubmitModalLine {
  id: number
  dish: SubmitModalDishLite
  qty: number
}

const NOTE_MAX = 255

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    lines: SubmitModalLine[]
    mealPeriodLabel?: string
    loading?: boolean
    outcome?: 'idle' | 'success' | 'error'
    errorMessage?: string | null
    /** 打开弹窗时各菜已保存的备注（来自购物车） */
    initialNotes?: Record<number, string | undefined | null>
    /** 提交成功态停留时长（ms），之后再触发返回首页 */
    successRedirectDelayMs?: number
  }>(),
  {
    mealPeriodLabel: '',
    loading: false,
    outcome: 'idle',
    errorMessage: null,
    initialNotes: () => ({}),
    successRedirectDelayMs: 2800,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: [notes: Record<number, string>]
  'after-success': []
}>()

const draft = reactive<Record<number, string>>({})

function clearDraft() {
  for (const k of Object.keys(draft)) {
    delete draft[Number(k)]
  }
}

function syncDraftFromProps() {
  clearDraft()
  for (const l of props.lines) {
    const pre = props.initialNotes?.[l.id]
    draft[l.id] =
      pre != null && String(pre).trim() !== ''
        ? String(pre).slice(0, NOTE_MAX)
        : ''
  }
}

let successRedirectTimer: ReturnType<typeof setTimeout> | undefined

function clearSuccessRedirectTimer() {
  if (successRedirectTimer !== undefined) {
    window.clearTimeout(successRedirectTimer)
    successRedirectTimer = undefined
  }
}

watch(
  () => props.outcome,
  (outcome) => {
    clearSuccessRedirectTimer()
    if (outcome !== 'success') return
    successRedirectTimer = window.setTimeout(() => {
      successRedirectTimer = undefined
      emit('after-success')
    }, props.successRedirectDelayMs)
  },
)

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      syncDraftFromProps()
    } else {
      clearDraft()
      clearSuccessRedirectTimer()
    }
  },
)

function close() {
  if (props.loading || props.outcome === 'success') return
  emit('update:modelValue', false)
}

function onBackdrop() {
  close()
}

function onConfirm() {
  if (props.loading || props.outcome === 'success') return
  const out: Record<number, string> = {}
  for (const l of props.lines) {
    out[l.id] = (draft[l.id] ?? '').slice(0, NOTE_MAX)
  }
  emit('confirm', out)
}

function noteLen(id: number) {
  return (draft[id] ?? '').length
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && props.modelValue) {
    close()
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  clearSuccessRedirectTimer()
})
</script>

<template>
  <Teleport to="body">
    <Transition name="osm-fade">
      <div
        v-if="modelValue"
        class="osm-root fixed inset-0 z-[100] flex items-end justify-center sm:items-center sm:p-4"
        role="presentation"
      >
        <button
          type="button"
          class="osm-backdrop absolute inset-0 border-0 bg-slate-900/45 p-0 backdrop-blur-[2px]"
          aria-label="关闭"
          :disabled="loading || outcome === 'success'"
          @click="onBackdrop"
        />

        <Transition name="osm-sheet" appear>
          <div
            class="osm-panel relative z-[1] flex max-h-[min(88dvh,32rem)] w-full max-w-md flex-col overflow-hidden rounded-t-2xl border border-slate-200/90 bg-white shadow-[0_-8px_40px_rgba(15,23,42,0.12)] sm:max-h-[min(85dvh,36rem)] sm:rounded-2xl sm:shadow-[0_20px_50px_-12px_rgba(15,23,42,0.18)]"
            role="dialog"
            aria-modal="true"
            aria-labelledby="osm-title"
          >
            <div class="osm-handle mx-auto mt-2 h-1 w-10 shrink-0 rounded-full bg-slate-200/90 sm:hidden" />

            <header class="shrink-0 border-b border-slate-100 px-4 pb-4 pt-4 sm:pt-4">
              <div class="flex items-start justify-between gap-4">
                <div class="min-w-0">
                  <p class="text-[10px] font-semibold uppercase tracking-[0.14em] text-slate-400">
                    确认提交
                  </p>
                  <h2 id="osm-title" class="mt-1 text-base font-semibold tracking-tight text-slate-900">
                    {{ mealPeriodLabel ? `${mealPeriodLabel} · 备注` : '菜品备注' }}
                  </h2>
                  <p class="mt-1 text-xs leading-relaxed text-slate-500">
                    可选填口味、忌口等，厨师长大人会尽量留意的
                  </p>
                </div>
                <button
                  type="button"
                  class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600 disabled:pointer-events-none disabled:opacity-40"
                  :disabled="loading || outcome === 'success'"
                  aria-label="关闭"
                  @click="close"
                >
                  <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                    <path d="M18 6 6 18M6 6l12 12" stroke-linecap="round" />
                  </svg>
                </button>
              </div>
            </header>

            <div class="min-h-0 flex-1 overflow-y-auto overscroll-contain px-4 py-4 [-webkit-overflow-scrolling:touch]">
              <Transition name="osm-step" mode="out-in">
                <div v-if="outcome !== 'success'" key="form" class="space-y-4">
                  <p
                    v-if="outcome === 'error' && errorMessage"
                    class="rounded-lg border border-red-100 bg-red-50/90 px-4 py-2 text-xs text-red-700"
                  >
                    {{ errorMessage }}
                  </p>

                  <div
                    v-for="l in lines"
                    :key="l.id"
                    class="rounded-xl border border-slate-100 bg-slate-50/50 p-4 shadow-[0_1px_2px_rgba(15,23,42,0.03)]"
                  >
                    <div class="mb-2 flex items-center gap-2">
                      <div
                        class="h-10 w-10 shrink-0 overflow-hidden rounded-md bg-slate-100 ring-1 ring-slate-200/70"
                      >
                        <img
                          v-if="l.dish.image_url"
                          :src="resolveMediaUrl(l.dish.image_url)"
                          :alt="l.dish.name"
                          class="h-full w-full object-cover"
                          loading="lazy"
                          decoding="async"
                        />
                      </div>
                      <div class="min-w-0 flex-1">
                        <p class="truncate text-sm font-medium text-slate-900">
                          {{ l.dish.name }}
                        </p>
                        <p class="text-[11px] text-slate-500">
                          ×{{ l.qty }}
                        </p>
                      </div>
                    </div>
                    <label class="block">
                      <span class="sr-only">{{ l.dish.name }} 备注</span>
                      <textarea
                        v-model="draft[l.id]"
                        :maxlength="NOTE_MAX"
                        rows="2"
                        class="osm-textarea w-full resize-none rounded-lg border border-slate-200 bg-white px-2 py-2 text-[13px] leading-snug text-slate-800 placeholder:text-slate-400 focus:border-blue-300 focus:outline-none focus:ring-2 focus:ring-blue-500/20"
                        placeholder="如：少盐、不要香菜…"
                        :disabled="loading"
                      />
                    </label>
                    <p class="mt-1 text-right text-[10px] tabular-nums text-slate-400">
                      {{ noteLen(l.id) }} / {{ NOTE_MAX }}
                    </p>
                  </div>
                </div>

                <div
                  v-else
                  key="success"
                  class="flex flex-col items-center justify-center gap-4 py-14 text-center"
                >
                  <div
                    class="flex h-14 w-14 items-center justify-center rounded-full bg-emerald-50 text-emerald-600 ring-1 ring-emerald-200/80"
                  >
                    <svg class="h-7 w-7" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                      <path
                        d="M20 6L9 17l-5-5"
                        stroke="currentColor"
                        stroke-width="2.25"
                        stroke-linecap="round"
                        stroke-linejoin="round"
                      />
                    </svg>
                  </div>
                  <p class="text-lg font-semibold text-slate-900">
                    提交成功
                  </p>
                  <p class="max-w-[16rem] text-sm leading-relaxed text-slate-500">
                    已通知厨师长，正在带你回首页…
                  </p>
                </div>
              </Transition>
            </div>

            <footer
              v-if="outcome !== 'success'"
              class="shrink-0 border-t border-slate-100 bg-white px-4 py-4 pb-[max(0.75rem,env(safe-area-inset-bottom,0px))]"
            >
              <div class="flex gap-2">
                <button
                  type="button"
                  class="flex h-12 min-w-0 flex-1 items-center justify-center rounded-lg border border-slate-200 bg-white text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
                  :disabled="loading"
                  @click="close"
                >
                  再想想
                </button>
                <button
                  type="button"
                  class="flex h-12 min-w-0 flex-[1.2] items-center justify-center gap-2 rounded-lg bg-blue-600 text-sm font-medium text-white transition-colors hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-slate-300"
                  :disabled="loading || !lines.length"
                  @click="onConfirm"
                >
                  <span
                    v-if="loading"
                    class="inline-block h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white"
                  />
                  {{ loading ? '提交中…' : '确认提交' }}
                </button>
              </div>
            </footer>
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.osm-fade-enter-active,
.osm-fade-leave-active {
  transition: opacity 0.28s cubic-bezier(0.25, 0.46, 0.45, 0.94);
}

.osm-fade-enter-from,
.osm-fade-leave-to {
  opacity: 0;
}

.osm-sheet-enter-active {
  transition:
    transform 0.38s cubic-bezier(0.22, 1, 0.36, 1),
    opacity 0.32s cubic-bezier(0.25, 0.46, 0.45, 0.94);
}

.osm-sheet-leave-active {
  transition:
    transform 0.26s cubic-bezier(0.4, 0, 1, 1),
    opacity 0.22s ease;
}

.osm-sheet-enter-from {
  opacity: 0;
  transform: translate3d(0, 100%, 0);
}

@media (min-width: 640px) {
  .osm-sheet-enter-from {
    opacity: 0;
    transform: translate3d(0, 0.75rem, 0) scale(0.98);
  }
}

.osm-sheet-leave-to {
  opacity: 0;
  transform: translate3d(0, 0.5rem, 0);
}

.osm-step-enter-active,
.osm-step-leave-active {
  transition:
    opacity 0.24s cubic-bezier(0.25, 0.46, 0.45, 0.94),
    transform 0.26s cubic-bezier(0.25, 0.46, 0.45, 0.94);
}

.osm-step-enter-from {
  opacity: 0;
  transform: translate3d(0, 0.35rem, 0);
}

.osm-step-leave-to {
  opacity: 0;
  transform: translate3d(0, -0.25rem, 0);
}

@media (prefers-reduced-motion: reduce) {
  .osm-fade-enter-active,
  .osm-fade-leave-active,
  .osm-sheet-enter-active,
  .osm-sheet-leave-active,
  .osm-step-enter-active,
  .osm-step-leave-active {
    transition-duration: 0.01ms;
  }

  .osm-sheet-enter-from,
  .osm-sheet-leave-to,
  .osm-step-enter-from,
  .osm-step-leave-to {
    transform: none;
  }
}
</style>
