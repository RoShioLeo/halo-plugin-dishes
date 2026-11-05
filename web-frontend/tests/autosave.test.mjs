import test from 'node:test'
import assert from 'node:assert/strict'
import { createAutosave, createWriteSerial } from '../src/utils/autosave.ts'
const deferred = () => { let resolve; const promise = new Promise((done) => { resolve = done }); return { promise, resolve } }

test('rapid changes are coalesced to the latest current draft before a forced flush', async () => {
  let value = 1
  const saved = []
  const autosave = createAutosave(async () => { saved.push(value) }, 5000)
  autosave.schedule()
  value = 2
  autosave.schedule()
  value = 3
  autosave.schedule()
  await autosave.flush()
  assert.deepEqual(saved, [3])
  autosave.dispose()
})

test('edits made during an in-flight save are serialized and coalesced into one follow-up write', async () => {
  let value = 1, active = 0, maximum = 0
  const saved = [], entered = deferred(), release = deferred()
  const autosave = createAutosave(async () => {
    active++; maximum = Math.max(maximum, active)
    saved.push(value)
    if (saved.length === 1) { entered.resolve(); await release.promise }
    active--
  }, 5000)
  autosave.schedule()
  const completed = autosave.flush()
  await entered.promise
  value = 2; autosave.schedule()
  value = 3; autosave.schedule()
  release.resolve()
  await completed
  assert.deepEqual(saved, [1, 3])
  assert.equal(maximum, 1)
  autosave.dispose()
})

test('drawer writes and autosave share a serial writer and a failed write does not poison later retries', async () => {
  const serial = createWriteSerial(), entered = deferred(), release = deferred()
  const calls = []
  const main = serial.run(async () => { calls.push('main-start'); entered.resolve(); await release.promise; calls.push('main-end') })
  await entered.promise
  const peer = serial.run(async () => { calls.push('peer') })
  assert.deepEqual(calls, ['main-start'])
  release.resolve()
  await Promise.all([main, peer])
  assert.deepEqual(calls, ['main-start', 'main-end', 'peer'])
  await assert.rejects(serial.run(async () => { throw new Error('network') }))
  await serial.run(async () => { calls.push('retry') })
  await serial.idle()
  assert.equal(calls.at(-1), 'retry')
})

test('failed autosave blocks the subsequent lock action; explicit retry must finish successfully first', async () => {
  let fail = true, locked = false, saved = false
  const autosave = createAutosave(async () => { if (fail) throw new Error('network'); saved = true }, 5000)
  autosave.schedule()
  await assert.rejects(async () => { await autosave.flush(); locked = true })
  assert.equal(locked, false)
  assert.equal(saved, false)
  fail = false
  await autosave.flush()
  assert.equal(saved, true)
  // Cancelling a reviewed submission never calls the publication action.
  assert.equal(locked, false)
  autosave.dispose()
})

test('immediate context navigation flushes the outgoing draft before switching date or meal', async () => {
  let context = '2026-11-05:lunch', quantity = 1
  const saved = new Map()
  const autosave = createAutosave(async () => { saved.set(context, quantity) }, 5000)
  quantity = 4; autosave.schedule()
  await autosave.flush()
  context = '2026-11-05:dinner'
  assert.equal(saved.get('2026-11-05:lunch'), 4)
  assert.equal(saved.has(context), false)
  context = '2026-11-05:lunch'
  assert.equal(saved.get(context), 4)
  autosave.dispose()
})
