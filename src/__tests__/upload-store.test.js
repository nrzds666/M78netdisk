import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUploadStore } from '@/stores/upload'

describe('useUploadStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('RED: addFiles items should have taskId field', () => {
    const store = useUploadStore()
    const file = new File(['hello'], 'test.txt', { type: 'text/plain' })
    const items = store.addFiles([file])
    expect(items[0]).toHaveProperty('taskId')
    expect(items[0].taskId).toBeNull()
  })

  it('RED: active queue items should expose taskId for filtering', () => {
    const store = useUploadStore()
    const file = new File(['hello'], 'test.txt', { type: 'text/plain' })
    store.addFiles([file])
    expect(store.queue[0]).toHaveProperty('taskId')
  })

  it('RED: addFiles items should have pulseTrigger field', () => {
    const store = useUploadStore()
    const file = new File(['hello'], 'test.txt', { type: 'text/plain' })
    const items = store.addFiles([file])
    expect(items[0]).toHaveProperty('pulseTrigger')
    expect(items[0].pulseTrigger).toBe(false)
  })

  it('RED: addFiles items should have status "pending" by default', () => {
    const store = useUploadStore()
    const file = new File(['hello'], 'test.txt', { type: 'text/plain' })
    const items = store.addFiles([file])
    expect(items[0].status).toBe('pending')
  })

  it('RED: addFiles items should have parentId field', () => {
    const store = useUploadStore()
    const file = new File(['hello'], 'test.txt', { type: 'text/plain' })
    const items = store.addFiles([file])
    expect(items[0]).toHaveProperty('parentId')
    expect(items[0].parentId).toBeNull()
  })

  it('RED: startNext should pick first pending item and set status to uploading', () => {
    const store = useUploadStore()
    const a = new File(['a'], 'a.txt', { type: 'text/plain' })
    const b = new File(['b'], 'b.txt', { type: 'text/plain' })
    store.addFiles([a])
    store.addFiles([b])
    const item = store.startNext()
    expect(item).not.toBeNull()
    expect(item.status).toBe('uploading')
    expect(item.name).toBe('a.txt')
  })

  it('RED: startNext should return null when no pending items', () => {
    const store = useUploadStore()
    expect(store.startNext()).toBeNull()
  })

  it('RED: beginProcessing should return true on first call and false on second', () => {
    const store = useUploadStore()
    expect(store.beginProcessing()).toBe(true)
    expect(store.beginProcessing()).toBe(false)
  })

  it('RED: endProcessing should release the lock', () => {
    const store = useUploadStore()
    store.beginProcessing()
    store.endProcessing()
    expect(store.beginProcessing()).toBe(true)
  })

  it('RED: pendingItems should return items with status pending', () => {
    const store = useUploadStore()
    const a = new File(['a'], 'a.txt', { type: 'text/plain' })
    const b = new File(['b'], 'b.txt', { type: 'text/plain' })
    store.addFiles([a])
    store.addFiles([b])
    store.startNext() // a → uploading
    expect(store.pendingItems.length).toBe(1)
    expect(store.pendingItems[0].name).toBe('b.txt')
  })
})
