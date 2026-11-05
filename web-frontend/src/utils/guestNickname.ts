interface NicknameStorage {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
}

export const GUEST_NICKNAME_KEY = 'lemonslice-kitchen-nickname'

export function normalizeGuestNickname(value: unknown): string {
  if (typeof value !== 'string') return ''
  const name = value.trim()
  return name && name.length <= 32 && !/[\u0000-\u001f\u007f-\u009f]/.test(name) ? name : ''
}

export function createGuestNicknamePreference(storage: () => NicknameStorage, random = Math.random) {
  let cachedName = ''
  let customNameInMemory = false
  const persist = (name: string) => {
    try { storage().setItem(GUEST_NICKNAME_KEY, name) } catch { /* The current page retains the name. */ }
  }
  return {
    resolve(serverName?: unknown, pendingName?: unknown): string {
      let storedName = ''
      try { storedName = normalizeGuestNickname(storage().getItem(GUEST_NICKNAME_KEY)) } catch { /* Use this page's memory. */ }
      const existingName = storedName || (customNameInMemory ? cachedName : '')
        || normalizeGuestNickname(serverName) || normalizeGuestNickname(pendingName)
      const name = existingName || cachedName || `访客${Math.floor(random() * 9000) + 1000}`
      cachedName = name
      if (existingName) customNameInMemory = true
      if (storedName !== name) persist(name)
      return name
    },
    set(value: string): string {
      const name = normalizeGuestNickname(value)
      if (!name) throw new Error('Invalid guest nickname')
      cachedName = name
      customNameInMemory = true
      persist(name)
      return name
    },
  }
}

// Storage is accessed lazily, only after the page knows that its viewer is anonymous.
export const guestNicknamePreference = createGuestNicknamePreference(() => window.localStorage)
