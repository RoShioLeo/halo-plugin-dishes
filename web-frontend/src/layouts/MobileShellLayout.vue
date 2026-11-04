<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import AppearanceControl from '@/components/AppearanceControl.vue'

const route = useRoute()

const mainBottomPad = computed(() =>
  route.name === 'order'
    ? 'pb-[max(0.5rem,env(safe-area-inset-bottom,0px))]'
    : '',
)
</script>

<template>
  <div
    class="ls-shell mx-auto flex h-dvh max-h-dvh min-h-0 w-full flex-col overflow-hidden text-slate-900 antialiased"
  >
    <nav class="ls-site-nav" aria-label="站点导航">
      <a class="ls-back-blog" href="https://blog.lemonslice.cloud/">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><path d="m10 6-6 6 6 6M4 12h16" stroke-linecap="round" stroke-linejoin="round" /></svg>
        <span>柠檬汽水池</span>
      </a>
      <div class="ls-site-nav__actions">
        <RouterLink to="/" :aria-current="route.name === 'home' ? 'page' : undefined">私厨首页</RouterLink>
        <RouterLink to="/order" :aria-current="route.name === 'order' ? 'page' : undefined">点菜</RouterLink>
        <AppearanceControl />
      </div>
    </nav>
    <main
      class="flex min-h-0 flex-1 flex-col overflow-hidden px-4"
      :class="mainBottomPad"
    >
      <slot />
    </main>
  </div>
</template>
