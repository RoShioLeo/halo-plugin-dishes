<script setup lang="ts">
import KitchenIcon from '@/components/KitchenIcon.vue'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import AppearanceControl from '@/components/AppearanceControl.vue'
import { kitchenViewer, loadViewer, mealContext } from '@/api/kitchen'
const route = useRoute()
const homeLink = computed(() => ({ path: '/', query: { date: mealContext.value.date } }))
const orderLink = computed(() => ({ path: '/order', query: { ...mealContext.value } }))
const header = ref<HTMLElement | null>(null)
const menuToggle = ref<HTMLButtonElement | null>(null)
const navOpen = ref(false)
const mobile = window.matchMedia('(max-width: 760px)')
function closeNav(returnFocus = false) { navOpen.value = false; if (returnFocus) menuToggle.value?.focus() }
function toggleNav() { navOpen.value = !navOpen.value }
function outside(event: MouseEvent) { if (event.target instanceof Node && !header.value?.contains(event.target)) closeNav() }
function escape(event: KeyboardEvent) { if (event.key === 'Escape' && navOpen.value) { event.preventDefault(); closeNav(true) } }
function resized() { if (!mobile.matches) closeNav() }
watch(() => route.fullPath, () => closeNav())
onMounted(() => {
  void loadViewer().catch(() => { /* Each page provides its API retry. */ })
  document.addEventListener('click', outside)
  document.addEventListener('keydown', escape)
  mobile.addEventListener('change', resized)
})
onUnmounted(() => { document.removeEventListener('click', outside); document.removeEventListener('keydown', escape); mobile.removeEventListener('change', resized) })
</script>
<template>
  <div class="ls-shell">
    <header ref="header" class="ls-site-nav">
      <a class="ls-back-blog" href="https://blog.lemonslice.cloud/"><KitchenIcon name="arrow-left-s-line" /><span>主站</span></a>
      <nav id="kitchen-navigation" class="ls-site-links" :class="navOpen ? 'is-open' : ''" aria-label="主导航">
        <RouterLink :to="homeLink" :aria-current="route.name === 'home' ? 'page' : undefined" @click="closeNav()">当日菜单</RouterLink>
        <RouterLink :to="orderLink" :aria-current="route.name === 'order' ? 'page' : undefined" @click="closeNav()">看看吃啥</RouterLink>
        <a v-if="kitchenViewer?.canManage" href="/console/dishes/orders" @click="closeNav()">厨房工作台</a>
      </nav>
      <div class="ls-site-tools"><AppearanceControl /><button ref="menuToggle" type="button" class="ls-mobile-menu" :aria-expanded="navOpen" aria-controls="kitchen-navigation" :aria-label="navOpen ? '收起导航' : '展开导航'" @click="toggleNav"><KitchenIcon name="menu-line" /></button></div>
    </header>
    <main class="ls-main"><slot /></main>
  </div>
</template>
