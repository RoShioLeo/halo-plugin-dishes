<script setup lang="ts">
import { computed } from 'vue'
import { colorMode, colorModeLabels, cycleColorMode, resolvedColorScheme } from '@/utils/colorMode'
import AppearanceGlyph from './AppearanceGlyph.vue'
const title = computed(() => `外观：${colorModeLabels[colorMode.value]}${colorMode.value === 'system' ? `（当前${colorModeLabels[resolvedColorScheme.value]}）` : ''}`)
</script>
<template>
  <div class="ls-appearance-control">
    <button type="button" class="ls-appearance-toggle" :title="title" :aria-label="`外观模式：${colorModeLabels[colorMode]}`" @click="cycleColorMode"><AppearanceGlyph :mode="colorMode" /></button>
  </div>
</template>
