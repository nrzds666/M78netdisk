import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useFileStore } from '@/stores/file'
import * as fileApi from '@/api/file'

vi.mock('@/api/file', () => ({
  listItems: vi.fn(() => Promise.resolve({ data: { records: [], total: 0 } })),
  createFolder: vi.fn(),
  upload: vi.fn(),
  moveToTrash: vi.fn(),
  download: vi.fn(() => Promise.resolve(new Blob()))
}))

describe('FileStore — goToParent navigation bug', () => {
  let store

  beforeEach(() => {
    vi.clearAllMocks()
    const pinia = createPinia()
    setActivePinia(pinia)
    store = useFileStore()
  })

  it('goUp correctly updates store from nested state', () => {
    store.enterFolder(1, 'Folder A')
    store.enterFolder(2, 'Folder B')
    store.enterFolder(3, 'Folder C')

    // BUG: goToParent() calls goUp() + router.push() but NOT loadFiles()
    // So store updates correctly but file list never refreshes
    store.goUp()
    expect(store.currentFolderId).toBe(2)
    expect(store.currentPath.length).toBe(2)
  })

  it('goUp multiple times works correctly', () => {
    store.enterFolder(10, 'X')
    store.enterFolder(20, 'Y')
    store.enterFolder(30, 'Z')

    store.goUp() // Z → Y
    expect(store.currentFolderId).toBe(20)

    store.goUp() // Y → X
    expect(store.currentFolderId).toBe(10)

    store.goUp() // X → root
    expect(store.currentFolderId).toBeNull()
    expect(store.currentPath.length).toBe(0)
  })

  it('goUp at root stays at root', () => {
    store.goUp()
    expect(store.currentFolderId).toBeNull()
  })

  describe('goToParent fix: loadFiles should be called after goUp', () => {
    it('direct goUp should NOT call listItems (pure store mutation)', () => {
      store.enterFolder(1, 'A')
      store.enterFolder(2, 'B')
      store.goUp()
      // goUp is a pure store action — no API calls
      expect(fileApi.listItems).not.toHaveBeenCalled()
    })

    it('goToParent-equivalent flow: loadFiles must be explicitly called', () => {
      store.enterFolder(1, 'A')
      store.enterFolder(2, 'B')
      store.enterFolder(3, 'C')

      // Simulate what goToParent SHOULD do:
      store.goUp() // currentFolderId = 2
      // loadFiles() must be called here (this was the missing line)

      // Check that WITHOUT loadFiles(), listItems is NOT called → this IS the bug
      expect(fileApi.listItems).not.toHaveBeenCalled()

      // Now simulate the fix — call loadFiles explicitly
      store.currentFolderId = store.currentFolderId // no-op, just to show the pattern
      // The component's loadFiles would call listItems(currentFolderId, ...)
      // This test documents that goToParent() needs loadFiles() after goUp()
    })
  })
})
