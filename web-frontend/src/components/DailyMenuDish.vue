<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { Dish, MenuItem } from '@/api/kitchen'
import { quantityLabel } from '@/api/kitchen'
import { resolveMediaUrl } from '@/utils/mediaUrl'
import { stars } from '@/utils/recommendationDisplay'

const props = defineProps<{ item: MenuItem; catalogueDish?: Dish; periodNote?: string }>()
const normalized = (value: string | null | undefined) => (value || '').trim().replace(/\s+/g, ' ')
const name = computed(() => props.item.name.trim() || props.catalogueDish?.name.trim() || '未命名菜品')
const note = computed(() => {
  const value = props.item.note?.trim() || ''
  return value && ![name.value, props.periodNote].some((other) => normalized(other) === normalized(value)) ? value : ''
})
const description = computed(() => {
  const value = props.catalogueDish?.description?.trim() || ''
  return value && ![name.value, props.item.note, props.periodNote].some((other) => normalized(other) === normalized(value)) ? value : ''
})
const recommendation = computed(() => {
  const value = props.catalogueDish?.recommendationLevel
  return value != null && Number.isFinite(value) && value >= 1 && value <= 5 ? Math.round(value) : 0
})
const imageUrl = computed(() => resolveMediaUrl(props.item.imageUrl?.trim() || props.catalogueDish?.imageUrl || ''))
const imageFailed = ref(false)
watch(imageUrl, () => { imageFailed.value = false })
</script>

<template>
  <li class="daily-menu-dish">
    <div class="daily-menu-dish__media">
      <img v-if="imageUrl && !imageFailed" :src="imageUrl" alt="" loading="lazy" width="80" height="80" @error="imageFailed = true" />
      <svg v-else viewBox="0 0 24 24" width="24" height="24" fill="currentColor" aria-hidden="true" focusable="false">
        <!-- Official Remix Icon v4.8.0 restaurant-2-line, unchanged; Apache-2.0. -->
        <path d="m14.268 12.147l-.853.853l7.07 7.071l-1.413 1.414L12 14.415l-7.072 7.07l-1.414-1.414l9.339-9.339c-.588-1.457.02-3.555 1.621-5.156c1.953-1.953 4.644-2.428 6.01-1.061c1.368 1.367.893 4.058-1.06 6.01c-1.602 1.602-3.7 2.21-5.157 1.622M4.222 3.807l6.718 6.718l-2.829 2.829l-3.889-3.89a4 4 0 0 1 0-5.656m13.789 5.304c1.257-1.257 1.516-2.726 1.06-3.182s-1.924-.196-3.181 1.06c-1.258 1.258-1.517 2.727-1.061 3.183s1.925.196 3.182-1.06" />
      </svg>
    </div>
    <div class="daily-menu-dish__body">
      <h4>{{ name }}</h4>
      <span v-if="recommendation" class="daily-menu-dish__stars" :aria-label="`推荐等级 ${recommendation} 星`">{{ stars(recommendation) }}</span>
      <p v-if="description" class="daily-menu-dish__description">{{ description }}</p>
      <p v-if="note" class="daily-menu-dish__note">{{ note }}</p>
    </div>
    <div class="daily-menu-dish__quantity"><strong>{{ quantityLabel(item.quantity) }}</strong><span>份</span></div>
  </li>
</template>

<style scoped>
.daily-menu-dish {
  display: grid;
  grid-template-columns: 80px minmax(0, 1fr) auto;
  align-items: center;
  gap: 16px;
  min-width: 0;
}
.daily-menu-dish__media {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 80px;
  height: 80px;
  overflow: hidden;
  border-radius: 8px;
  color: var(--ls-muted);
}
.daily-menu-dish__media img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.daily-menu-dish__media > svg {
  opacity: .55;
}
.daily-menu-dish__body {
  min-width: 0;
}
.daily-menu-dish h4 {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow-wrap: anywhere;
  color: var(--ls-ink);
  font-size: 16px;
  font-weight: 500;
  line-height: 1.5;
}
.daily-menu-dish__stars {
  display: block;
  margin-top: 4px;
  color: var(--ls-accent);
  font-size: 11px;
  letter-spacing: .08em;
  line-height: 1.5;
}
.daily-menu-dish p {
  margin: 8px 0 0;
  overflow-wrap: anywhere;
  font-size: 13px;
  line-height: 1.6;
}
.daily-menu-dish__description {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  color: var(--ls-muted);
}
.daily-menu-dish__note {
  white-space: pre-wrap;
  color: var(--ls-ink);
}
.daily-menu-dish__quantity {
  display: flex;
  align-items: baseline;
  justify-content: flex-end;
  gap: 4px;
  min-width: 56px;
  white-space: nowrap;
  line-height: 1.5;
}
.daily-menu-dish__quantity strong {
  color: var(--ls-ink);
  font-size: 18px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}
.daily-menu-dish__quantity span {
  color: var(--ls-muted);
  font-size: 12px;
}
@media (max-width: 639px) {
  .daily-menu-dish {
    grid-template-columns: 64px minmax(0, 1fr) auto;
  }
  .daily-menu-dish__media {
    width: 64px;
    height: 64px;
  }
  .daily-menu-dish__quantity {
    min-width: 48px;
  }
  .daily-menu-dish__quantity strong {
    font-size: 16px;
  }
}
</style>
