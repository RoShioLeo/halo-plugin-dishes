<script setup lang="ts">
import { colorMode, colorModeLabels, cycleColorMode } from '@/utils/colorMode'
</script>

<template>
  <button
    type="button"
    class="ls-appearance"
    :title="`外观：${colorModeLabels[colorMode]}，点击切换`"
    :aria-label="`外观：${colorModeLabels[colorMode]}，点击切换`"
    @click="cycleColorMode"
  >
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">
      <template v-if="colorMode === 'light'">
        <circle cx="12" cy="12" r="4" />
        <path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5" stroke-linecap="round" />
      </template>
      <path v-else-if="colorMode === 'dark'" d="M20.5 14.5A9 9 0 0 1 9.5 3.5a9 9 0 1 0 11 11Z" stroke-linejoin="round" />
      <template v-else>
        <g stroke-linecap="round">
          <path d="M1.65 12.75 2.57 12.37M1.65 6.25 2.57 6.63M6.25 1.65 6.63 2.57M12.75 1.65 12.37 2.57" />
          <path d="M6.32 12.68a4.5 4.5 0 0 1 6.36-6.36" />
          <path d="M18.22 11.62a5.5 5.5 0 0 1-6.6 6.6 5.5 5.5 0 1 0 6.6-6.6Z" stroke-linejoin="round" />
          <path d="M4 20 20 4" />
        </g>
      </template>
    </svg>
  </button>
</template>
