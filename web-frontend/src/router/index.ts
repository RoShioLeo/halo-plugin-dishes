import { createRouter, createWebHistory } from 'vue-router'

import HomeView from '@/views/HomeView.vue'
import OrderView from '@/views/OrderView.vue'

function resolvePublicBase(): string {
  const envBase = import.meta.env.VITE_PUBLIC_BASE
  if (typeof envBase === 'string' && envBase.trim() !== '') {
    const raw = envBase.trim()
    if (!raw || raw === '/') return '/'
    const base = raw.startsWith('/') ? raw : `/${raw}`
    return base.endsWith('/') ? base : `${base}/`
  }
  // 未配置 VITE_PUBLIC_BASE：插件内嵌页由 Halo 注入 __DISHES_PUBLIC_BASE__；独立构建的 index.html 无注入，应用站点根 /
  try {
    const injected = (window as unknown as { __DISHES_PUBLIC_BASE__?: string }).__DISHES_PUBLIC_BASE__
    const s = injected == null ? '' : String(injected).trim()
    if (s !== '') {
      const base = s.startsWith('/') ? s : `/${s}`
      return base.endsWith('/') ? base : `${base}/`
    }
  } catch {
    // ignore
  }
  return '/'
}

const router = createRouter({
  history: createWebHistory(resolvePublicBase()),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
      meta: { title: '当日菜单' },
    },
    {
      path: '/order',
      name: 'order',
      component: OrderView,
      meta: { title: '看看吃啥' },
    },
    { path: '/dishes/manage', redirect: '/' },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior() {
    return { top: 0 }
  },
})

export default router
