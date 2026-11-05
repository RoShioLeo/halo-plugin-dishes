/** Serializes every write, including autosave and shared-drawer quantity actions. */
export function createWriteSerial() {
  let tail: Promise<unknown> = Promise.resolve()
  return {
    run<T>(action: () => Promise<T>): Promise<T> {
      const current = tail.catch(() => undefined).then(action)
      tail = current
      return current
    },
    async idle() { await tail.catch(() => undefined) },
  }
}
/** Coalesces edits while a save is in flight; a failed flush never reports success. */
export function createAutosave(worker: () => Promise<void>, delay = 300) {
  let pending = false
  let timer: ReturnType<typeof setTimeout> | undefined
  let running: Promise<void> | null = null
  let disposed = false
  const flush = async (): Promise<void> => {
    clearTimeout(timer)
    if (disposed) return
    if (running) return running
    if (!pending) return
    running = (async () => {
      while (pending && !disposed) {
        pending = false
        try { await worker() } catch (error) { pending = true; throw error }
      }
    })()
    try { await running } finally { running = null }
  }
  return {
    schedule() { if (disposed) return; pending = true; clearTimeout(timer); timer = setTimeout(() => { void flush().catch(() => { /* Worker exposes the retry state. */ }) }, delay) },
    flush,
    cancelPending() { pending = false; clearTimeout(timer) },
    dispose() { disposed = true; pending = false; clearTimeout(timer) },
  }
}
