<script setup lang="ts">
import { computed } from 'vue'
import { getPublicBrandSubtitle, getPublicBrandTitle } from '@/utils/publicBranding'
import { resolveMediaUrl } from '@/utils/mediaUrl'
import lemonUrl from '@/assets/lemon.svg'
const customLogo = (window as unknown as { __DISHES_PUBLIC_LOGO__?: string }).__DISHES_PUBLIC_LOGO__
const logo = computed(() => resolveMediaUrl(customLogo?.trim() || lemonUrl))
</script>

<template>
  <header class="ls-brand">
    <img :src="logo" alt="" class="ls-brand__logo" width="48" height="48" />
    <div>
      <p class="ls-brand__kicker">LEMONSLICE CLOUD</p>
      <h1>{{ getPublicBrandTitle() }}</h1>
      <p class="ls-brand__subtitle">{{ getPublicBrandSubtitle() }}</p>
    </div>
  </header>
</template>
