// LemonSlice edition, modified 2026-11-05. GPL-3.0; see LICENSE.
// Apply the saved appearance before CSS paints, using the blog's shared key.
(() => {
  let mode = 'system'
  try {
    const saved = localStorage.getItem('lemonslice-color-mode')
    if (saved === 'light' || saved === 'dark') mode = saved
  } catch { /* Storage is optional. */ }
  const scheme = mode === 'system'
    ? (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
    : mode
  const root = document.documentElement
  root.classList.add('lemon-dishes')
  root.dataset.colorMode = mode
  root.dataset.colorScheme = scheme
  root.style.colorScheme = scheme
  const meta = document.querySelector('meta[name="theme-color"]')
  if (meta) meta.content = scheme === 'dark' ? '#15171b' : '#ffffff'
})()
