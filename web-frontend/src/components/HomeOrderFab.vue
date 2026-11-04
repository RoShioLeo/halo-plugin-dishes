<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

/** 与 HomeView .home-body 的 padding-bottom 同步，滚到底不被挡住 */
const FAB_CSS_VAR = '--home-order-fab-h'

const rootRef = ref<HTMLElement | null>(null)
let ro: ResizeObserver | undefined

function publish() {
  const el = rootRef.value
  if (!el) return
  const h = Math.ceil(el.getBoundingClientRect().height)
  document.documentElement.style.setProperty(FAB_CSS_VAR, `${h}px`)
}

function clear() {
  document.documentElement.style.removeProperty(FAB_CSS_VAR)
}

onMounted(() => {
  ro = new ResizeObserver(() => publish())
  void nextTick(() => {
    publish()
    if (rootRef.value) ro?.observe(rootRef.value)
  })
  window.addEventListener('resize', publish)
})

onUnmounted(() => {
  ro?.disconnect()
  ro = undefined
  window.removeEventListener('resize', publish)
  clear()
})
</script>

<template>
  <div
    ref="rootRef"
    class="ls-fab-wrap pointer-events-none fixed bottom-0 left-1/2 z-40 flex w-full max-w-md -translate-x-1/2 justify-end"
    :style="{
      paddingBottom: `max(0.85rem, calc(0.5rem + env(safe-area-inset-bottom, 0px)))`,
      paddingRight: `max(1rem, env(safe-area-inset-right, 0px))`,
    }"
  >
    <RouterLink
      to="/order"
      class="home-order-fab pointer-events-auto inline-flex h-10 items-center gap-2 rounded-full border border-blue-500/35 bg-blue-600 pl-4 pr-3.5 text-[12.5px] font-medium leading-none tracking-tight text-white shadow-[0_2px_14px_-2px_rgba(37,99,235,0.45),0_1px_4px_rgba(15,23,42,0.12)] ring-1 ring-white/20 transition-[transform,box-shadow,background-color,border-color] duration-200 ease-out active:scale-[0.97] motion-safe:hover:border-blue-400/40 motion-safe:hover:bg-blue-700 motion-safe:hover:shadow-[0_4px_22px_-4px_rgba(37,99,235,0.5),0_2px_8px_-2px_rgba(15,23,42,0.14)] motion-reduce:active:scale-100"
      aria-label="去点菜"
    >
      <svg
        class="h-4 w-4 shrink-0 text-white"
        viewBox="0 0 1024 1024"
        xmlns="http://www.w3.org/2000/svg"
        fill="currentColor"
        aria-hidden="true"
      >
        <path
          d="M819.195776 512.056959l34.047532 426.618134a99.070638 99.070638 0 0 1-28.991601 62.399142 80.638891 80.638891 0 0 1-112.638452 0 99.326634 99.326634 0 0 1-28.991601-62.399142L716.797184 511.99296A285.756071 285.756071 0 0 1 597.310827 267.516322c0-147.581971 76.414949-267.260325 170.621654-267.260326 94.270704 0 170.685653 119.678354 170.685653 267.260326a285.692072 285.692072 0 0 1-119.486357 244.604636z m-449.913814-15.039793l-27.903616 15.039793 36.3515 456.057729a47.231351 47.231351 0 0 1-21.567703 39.487457 93.886709 93.886709 0 0 1-55.039243 16.255777h-60.927163a93.758711 93.758711 0 0 1-55.039243-16.255777 46.719358 46.719358 0 0 1-21.503704-39.295459L204.804224 511.99296l-27.583621-14.975794a85.438825 85.438825 0 0 1-91.902736-70.399032V34.303528A30.591579 30.591579 0 0 1 113.541479 0h6.52791a35.071518 35.071518 0 0 1 33.599538 34.239529v290.108011a40.063449 40.063449 0 0 0 38.143476 39.871452h6.463911a42.367417 42.367417 0 0 0 40.70344-39.871452V34.239529A32.639551 32.639551 0 0 1 270.019327 0h6.52791a32.127558 32.127558 0 0 1 30.719578 34.239529v290.108011a42.751412 42.751412 0 0 0 40.959437 39.871452h6.52791a39.679454 39.679454 0 0 0 37.759481-39.871452V34.239529A34.367527 34.367527 0 0 1 426.433177 0h6.52791A30.655578 30.655578 0 0 1 460.800704 34.239529v392.506603a85.05483 85.05483 0 0 1-91.518742 70.271034z m0 0"
        />
      </svg>
      <span class="text-white">去点菜</span>
    </RouterLink>
  </div>
</template>

<style scoped>
.home-order-fab:focus-visible {
  outline: 2px solid rgb(255 255 255 / 0.9);
  outline-offset: 2px;
}

@media (prefers-reduced-motion: reduce) {
  .home-order-fab {
    transition-duration: 0.01ms;
  }
}
</style>
