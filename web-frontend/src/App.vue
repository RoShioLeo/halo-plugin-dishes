<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterView } from 'vue-router'
import { apiGet, apiPost, getApiErrorMessage, setAccessToken } from '@/api/http'

import MobileShellLayout from './layouts/MobileShellLayout.vue'

type AccessStatus = {
  access_mode: 'none' | 'password'
  granted: boolean
  password_required: boolean
}

const authChecking = ref(true)
const accessGranted = ref(false)
const accessMode = ref<'none' | 'password'>('none')
const passwordInput = ref('')
const authError = ref('')
const submitting = ref(false)

const showPasswordModal = computed(
  () => !authChecking.value && !accessGranted.value && accessMode.value === 'password',
)
const showGuardFallback = computed(
  () =>
    !authChecking.value &&
    !accessGranted.value &&
    accessMode.value === 'none' &&
    !showPasswordModal.value,
)

async function refreshAccessStatus() {
  authChecking.value = true
  authError.value = ''
  try {
    const s = await apiGet<AccessStatus>('/access/status')
    accessMode.value = s.access_mode
    accessGranted.value = !!s.granted
  } catch (e) {
    authError.value = getApiErrorMessage(e, '鉴权状态加载失败', {
      DISHES_ACCESS_DENIED: '需要输入访问密码',
    })
    // 不放行页面，避免后续业务请求持续报 HTML 非 JSON
    accessMode.value = 'none'
    accessGranted.value = false
  } finally {
    authChecking.value = false
  }
}

async function submitPasswordAccess() {
  if (!passwordInput.value.trim()) {
    authError.value = '请输入访问密码'
    return
  }
  submitting.value = true
  authError.value = ''
  try {
    const res = await apiPost<{ granted: boolean; token: string }>('/access/password-verify', {
      password: passwordInput.value,
    })
    if (res.granted && res.token) {
      setAccessToken(res.token)
      passwordInput.value = ''
      await refreshAccessStatus()
      return
    }
    authError.value = '密码验证失败'
  } catch (e) {
    authError.value = getApiErrorMessage(e, '密码验证失败', {
      DISHES_PASSWORD_INVALID: '密码错误，请重试',
    })
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  void refreshAccessStatus()
})
</script>

<template>
  <MobileShellLayout>
    <RouterView v-if="accessGranted" v-slot="{ Component, route }">
      <!--
        宿主占满 main 剩余高度；各路由层 absolute 叠在同一块区域里只做 opacity，
        避免两个 flex-1 并排把布局撑出「上方一大块空白再挤 header」。
      -->
      <!-- 勿 overflow-hidden：会裁掉子页面 -mx-4 对 main px-4 的拉满边距 -->
      <div class="route-view-host relative min-h-0 w-full min-w-0 flex-1">
        <Transition name="route-view">
          <!-- 用 path 而非 fullPath：同一路由下仅 query 变化（如点菜页切换餐段）时不整页重挂载，避免状态丢失 -->
          <div
            :key="route.path"
            class="route-view-layer absolute inset-0 flex min-h-0 min-w-0 flex-col"
          >
            <component :is="Component" />
          </div>
        </Transition>
      </div>
    </RouterView>

    <div v-if="authChecking" class="access-mask">
      <div class="access-card">正在校验访问权限...</div>
    </div>

    <div v-if="showPasswordModal" class="access-mask">
      <div class="access-card" role="dialog" aria-modal="true" aria-labelledby="dishes-access-title">
        <h3 id="dishes-access-title" class="access-title">欢迎来到柠檬私厨</h3>
        <p class="access-desc">请输入前台访问密码，验证通过后本地缓存生效。</p>
        <label for="dishes-access-password" class="sr-only">访问密码</label>
        <input
          id="dishes-access-password"
          v-model="passwordInput"
          type="password"
          autocomplete="current-password"
          class="access-input"
          placeholder="请输入访问密码"
          @keyup.enter="submitPasswordAccess"
        />
        <p v-if="authError" class="access-error">{{ authError }}</p>
        <button class="access-btn" :disabled="submitting" @click="submitPasswordAccess">验证并进入</button>
      </div>
    </div>

    <div v-if="showGuardFallback" class="access-mask">
      <div class="access-card">
        <h3 class="access-title">访问校验异常</h3>
        <p class="access-desc">未能正确获取鉴权状态，请重试。</p>
        <p v-if="authError" class="access-error">{{ authError }}</p>
        <button class="access-btn" @click="refreshAccessStatus">重试</button>
      </div>
    </div>
  </MobileShellLayout>
</template>

<style>
/*
 * 仅用 opacity：勿用 transform/scale 过渡整页（会破坏子级 position:fixed 的参照）。
 * enter 叠在 leave 之上，交叉淡入更干净。
 */
.route-view-enter-active,
.route-view-leave-active {
  pointer-events: none;
  transition: opacity 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}

.route-view-enter-active {
  z-index: 2;
}

.route-view-leave-active {
  z-index: 1;
}

.route-view-enter-from,
.route-view-leave-to {
  opacity: 0;
}

.route-view-enter-to,
.route-view-leave-from {
  opacity: 1;
}

@media (prefers-reduced-motion: reduce) {
  .route-view-enter-active,
  .route-view-leave-active {
    transition-duration: 0.01ms;
  }
}

.access-mask {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgb(15 23 42 / 0.35);
  padding: 1rem;
}

.access-card {
  width: min(100%, 24rem);
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 12px 40px rgb(15 23 42 / 0.2);
  padding: 1rem;
}

.access-title {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
}

.access-desc {
  margin: 0.5rem 0 0.75rem;
  color: #64748b;
  font-size: 0.875rem;
}

.access-input {
  width: 100%;
  height: 2.5rem;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  padding: 0 0.75rem;
  outline: none;
}

.access-btn {
  margin-top: 0.75rem;
  width: 100%;
  height: 2.5rem;
  border: none;
  border-radius: 8px;
  background: #0f172a;
  color: #fff;
  font-size: 0.875rem;
}

.access-error {
  margin: 0.5rem 0 0;
  font-size: 0.8125rem;
  color: #dc2626;
}
</style>
