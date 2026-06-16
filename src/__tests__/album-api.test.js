import { describe, it, expect, vi, beforeEach } from 'vitest'

const { mockRequest } = vi.hoisted(() => ({
  mockRequest: {
    get: vi.fn(() => Promise.resolve({ data: {} })),
    post: vi.fn(() => Promise.resolve({ data: {} })),
    put: vi.fn(() => Promise.resolve({ data: {} })),
    delete: vi.fn(() => Promise.resolve({ data: {} }))
  }
}))

vi.mock('@/api/request', () => ({
  default: mockRequest
}))

// These imports will include updateAlbum and setAlbumCover after they're added
// For RED phase, import only what exists
describe('api/album.js — endpoint correctness', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('RED: updateAlbum should be importable', async () => {
    // This should fail because updateAlbum isn't exported yet
    const mod = await import('@/api/album')
    expect(mod.updateAlbum).toBeDefined()
  })

  it('RED: setAlbumCover should be importable', async () => {
    const mod = await import('@/api/album')
    expect(mod.setAlbumCover).toBeDefined()
  })

  it('RED: updateAlbum sends PUT /albums/{id} with name', async () => {
    const mod = await import('@/api/album')
    await mod.updateAlbum(5, { name: '新相册名' })
    expect(mockRequest.put).toHaveBeenCalledWith('/albums/5', { name: '新相册名' })
  })

  it('RED: setAlbumCover sends PUT /albums/{id}/cover with itemId param', async () => {
    const mod = await import('@/api/album')
    await mod.setAlbumCover(5, 42)
    expect(mockRequest.put).toHaveBeenCalledWith('/albums/5/cover', null, {
      params: { itemId: 42 }
    })
  })
})
