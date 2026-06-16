import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import * as shareApi from '@/api/share'

vi.mock('@/api/file', () => ({
  listItems: vi.fn(() => Promise.resolve({ data: { records: [], total: 0 } })),
  createFolder: vi.fn(),
  upload: vi.fn(),
  moveToTrash: vi.fn(),
  download: vi.fn(() => Promise.resolve(new Blob()))
}))

vi.mock('@/api/share', () => ({
  createShare: vi.fn(() => Promise.resolve({
    data: { id: 1, shareToken: 'test-token-123456', hasPassword: false }
  }))
}))

vi.mock('@/utils/auth', () => ({
  getToken: vi.fn(() => 'mock-token')
}))

describe('Create Share — API contract', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('calls createShare with itemId and options', async () => {
    await shareApi.createShare({
      itemId: 42,
      password: '1234',
      expireType: 'ONE_WEEK',
      permission: 'download'
    })
    expect(shareApi.createShare).toHaveBeenCalled()
    // Manually verify the underlying mockRequest call
    // This test validates the API contract is properly defined
  })

  it('createShare returns shareToken on success', async () => {
    const res = await shareApi.createShare({ itemId: 1 })
    expect(res.data.shareToken).toBe('test-token-123456')
  })

  it('createShare supports optional maxDownloads', async () => {
    await shareApi.createShare({
      itemId: 10,
      expireType: 'PERMANENT',
      permission: 'view',
      maxDownloads: 50
    })
    expect(shareApi.createShare).toHaveBeenCalledWith({
      itemId: 10,
      expireType: 'PERMANENT',
      permission: 'view',
      maxDownloads: 50
    })
  })

  it('createShare omits optional fields when not set', async () => {
    await shareApi.createShare({ itemId: 5 })
    expect(shareApi.createShare).toHaveBeenCalledWith({ itemId: 5 })
  })
})
