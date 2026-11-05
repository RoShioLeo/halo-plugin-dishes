<script setup lang="ts">
defineProps<{ mode: 'light' | 'dark' | 'system' }>()
</script>
<template>
  <!-- The site's existing custom sun/moon design, shared with the LemonSlice theme. -->
  <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <template v-if="mode === 'light'">
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.5 1.5M17.5 17.5 19 19M5 19l1.5-1.5M17.5 6.5 19 5" />
    </template>
    <path v-else-if="mode === 'dark'" d="M20.8 14A9 9 0 0 1 10 3.2 9 9 0 1 0 20.8 14Z" />
    <template v-else>
      <path d="M1.65 12.75 2.57 12.37M1.65 6.25 2.57 6.63M6.25 1.65 6.63 2.57M12.75 1.65 12.37 2.57" />
      <path d="M6.32 12.68a4.5 4.5 0 0 1 6.36-6.36" />
      <path d="M18.22 11.62a5.5 5.5 0 0 1-6.6 6.6 5.5 5.5 0 1 0 6.6-6.6Z" />
      <path d="M4 20 20 4" />
    </template>
  </svg>
</template>
