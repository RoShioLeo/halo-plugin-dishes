import { ref } from 'vue'

export type ColorMode = 'system' | 'light' | 'dark'

const storageKey = 'lemonslice-color-mode'
const systemScheme = window.matchMedia('(prefers-color-scheme: dark)')
const normalize = (value: string | null): ColorMode =>
  value === 'light' || value === 'dark' ? value : 'system'

function readMode(): ColorMode {
  try {
    return normalize(window.localStorage.getItem(storageKey))
  } catch {
    return 'system'
  }
}

export const colorMode = ref<ColorMode>(readMode())
export const resolvedColorScheme = ref<'light' | 'dark'>('light')
export const colorModeLabels = { system: '跟随系统', light: '浅色', dark: '深色' }

function applyMode() {
  const root = document.documentElement
  const scheme = colorMode.value === 'system'
    ? (systemScheme.matches ? 'dark' : 'light')
    : colorMode.value
  resolvedColorScheme.value = scheme
  root.classList.add('lemon-dishes')
  root.dataset.colorMode = colorMode.value
  root.dataset.colorScheme = scheme
  root.style.colorScheme = scheme
  const meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]')
  if (meta) meta.content = scheme === 'dark' ? '#15171b' : '#ffffff'
}

export function setColorMode(mode: ColorMode) {
  colorMode.value = mode
  try { window.localStorage.setItem(storageKey, mode) } catch { /* Apply without persistence. */ }
  applyMode()
}

export function cycleColorMode() {
  const modes: ColorMode[] = ['light', 'dark', 'system']
  setColorMode(modes[(modes.indexOf(colorMode.value) + 1) % modes.length] ?? 'light')
}

systemScheme.addEventListener('change', applyMode)
window.addEventListener('storage', (event) => {
  if (event.key !== storageKey && event.key !== null) return
  colorMode.value = readMode()
  applyMode()
})

applyMode()
