<script setup lang="ts">
// Official, unmodified Remix Icon v4.8.0 paths, Apache-2.0.
// Source: @iconify-json/ri 1.2.10; see THIRD_PARTY_NOTICES.md and third-party-licenses/.
const paths = {
  "refresh-line": "<path fill=\"currentColor\" d=\"M5.463 4.433A9.96 9.96 0 0 1 12 2c5.523 0 10 4.477 10 10c0 2.136-.67 4.116-1.81 5.74L17 12h3A8 8 0 0 0 6.46 6.228zm13.074 15.134A9.96 9.96 0 0 1 12 22C6.477 22 2 17.523 2 12c0-2.136.67-4.116 1.81-5.74L7 12H4a8 8 0 0 0 13.54 5.772z\"/>",
  "share-line": "<path fill=\"currentColor\" d=\"m13.12 17.023l-4.199-2.29a4 4 0 1 1 0-5.465l4.2-2.29a4 4 0 1 1 .958 1.755l-4.2 2.29a4 4 0 0 1 0 1.954l4.2 2.29a4 4 0 1 1-.959 1.755M6 14a2 2 0 1 0 0-4a2 2 0 0 0 0 4m11-6a2 2 0 1 0 0-4a2 2 0 0 0 0 4m0 12a2 2 0 1 0 0-4a2 2 0 0 0 0 4\"/>",
  "close-line": "<path fill=\"currentColor\" d=\"m12 10.587l4.95-4.95l1.414 1.414l-4.95 4.95l4.95 4.95l-1.415 1.414l-4.95-4.95l-4.949 4.95l-1.414-1.415l4.95-4.95l-4.95-4.95L7.05 5.638z\"/>",
  "search-line": "<path fill=\"currentColor\" d=\"m18.031 16.617l4.283 4.282l-1.415 1.415l-4.282-4.283A8.96 8.96 0 0 1 11 20c-4.968 0-9-4.032-9-9s4.032-9 9-9s9 4.032 9 9a8.96 8.96 0 0 1-1.969 5.617m-2.006-.742A6.98 6.98 0 0 0 18 11c0-3.867-3.133-7-7-7s-7 3.133-7 7s3.133 7 7 7a6.98 6.98 0 0 0 4.875-1.975z\"/>",
  "edit-line": "<path fill=\"currentColor\" d=\"M6.414 15.89L16.556 5.748l-1.414-1.414L5 14.476v1.414zm.829 2H3v-4.243L14.435 2.212a1 1 0 0 1 1.414 0l2.829 2.829a1 1 0 0 1 0 1.414zM3 19.89h18v2H3z\"/>",
  "add-line": "<path fill=\"currentColor\" d=\"M11 11V5h2v6h6v2h-6v6h-2v-6H5v-2z\"/>",
  "subtract-line": "<path fill=\"currentColor\" d=\"M5 11v2h14v-2z\"/>",
  "arrow-left-s-line": "<path fill=\"currentColor\" d=\"m10.828 12l4.95 4.95l-1.414 1.415L8 12l6.364-6.364l1.414 1.414z\"/>",
  "arrow-right-s-line": "<path fill=\"currentColor\" d=\"m13.172 12l-4.95-4.95l1.414-1.413L16 12l-6.364 6.364l-1.414-1.415z\"/>",
  "group-line": "<path fill=\"currentColor\" d=\"M2 22a8 8 0 1 1 16 0h-2a6 6 0 0 0-12 0zm8-9c-3.315 0-6-2.685-6-6s2.685-6 6-6s6 2.685 6 6s-2.685 6-6 6m0-2c2.21 0 4-1.79 4-4s-1.79-4-4-4s-4 1.79-4 4s1.79 4 4 4m8.284 3.703A8 8 0 0 1 23 22h-2a6 6 0 0 0-3.537-5.473zm-.688-11.29A5.5 5.5 0 0 1 21 8.5a5.5 5.5 0 0 1-5 5.478v-2.013a3.5 3.5 0 0 0 1.041-6.609z\"/>",
  "send-plane-line": "<path fill=\"currentColor\" d=\"m21.727 2.957l-5.454 19.086c-.15.529-.475.553-.717.07L11 13L1.923 9.37c-.51-.205-.503-.51.034-.689L21.043 2.32c.529-.176.832.12.684.638m-2.692 2.14L6.812 9.17l5.637 2.255l3.04 6.08z\"/>",
  "menu-line": "<path fill=\"currentColor\" d=\"M3 4h18v2H3zm0 7h18v2H3zm0 7h18v2H3z\"/>",
  "check-line": "<path fill=\"currentColor\" d=\"m10 15.17l9.192-9.191l1.414 1.414L10 17.999l-6.364-6.364l1.414-1.414z\"/>"
} as const
withDefaults(defineProps<{ name: keyof typeof paths; size?: 16 | 24 }>(), { size: 16 })
</script>
<template>
  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" :width="size" :height="size" fill="currentColor" aria-hidden="true" focusable="false" v-html="paths[name]" />
</template>
