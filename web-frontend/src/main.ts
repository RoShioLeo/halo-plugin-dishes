import { createApp } from 'vue'

import App from './App.vue'
import router from './router'
import { getPublicSiteTitle } from '@/utils/publicBranding'

import './styles/main.css'

const app = createApp(App)

app.use(router)

router.afterEach((to) => {
  const base = getPublicSiteTitle()
  const piece = typeof to.meta.title === 'string' ? to.meta.title.trim() : ''
  document.title = piece ? `${piece} - ${base}` : base
})

app.mount('#app')
