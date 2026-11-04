<template>
  <div class="ls-dishes-console">
    <router-view />
  </div>
</template>

<style scoped>
/* Keep Halo's page-header and workspace geometry; only this plugin inherits the palette. */
.ls-dishes-console {
  display: contents;
  --ls-dishes-surface: #fff;
  --ls-dishes-soft: #f7f7f8;
  --ls-dishes-line: #e8e8eb;
  --ls-dishes-ink: #29292e;
  --ls-dishes-muted: #6b6b75;
  --ls-dishes-gold: #946d12;
  --ls-dishes-highlight: #e7b63e;
  --ls-dishes-gold-soft: #fff8e5;
  --ls-dishes-danger: #b91c1c;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Noto Sans CJK SC', sans-serif;
}

/* Follow an explicit host dark mode, rather than making one panel dark inside a light Console. */
html.dark .ls-dishes-console,
html[data-theme='dark'] .ls-dishes-console {
  --ls-dishes-surface: #202125;
  --ls-dishes-soft: #27282d;
  --ls-dishes-line: #35363d;
  --ls-dishes-ink: #ebebef;
  --ls-dishes-muted: #a5a5af;
  --ls-dishes-gold: #e7c56e;
  --ls-dishes-highlight: #e7b63e;
  --ls-dishes-gold-soft: #322c1f;
  --ls-dishes-danger: #fca5a5;
}

.ls-dishes-console :deep(.dishes-page),
.ls-dishes-console :deep(.orders-page),
.ls-dishes-console :deep(.settings-page) {
  min-width: 0;
  padding: 32px;
  color: var(--ls-dishes-ink);
}

.ls-dishes-console :deep(.dishes-main-card),
.ls-dishes-console :deep(.category-card),
.ls-dishes-console :deep(.orders-main-card),
.ls-dishes-console :deep(.settings-card) {
  border-radius: 8px;
  border-color: var(--ls-dishes-line);
  background: var(--ls-dishes-surface);
  box-shadow: none;
}

@media (max-width: 767px) {
  .ls-dishes-console :deep(.dishes-page),
  .ls-dishes-console :deep(.orders-page),
  .ls-dishes-console :deep(.settings-page) {
    padding: 16px;
  }
}
</style>
