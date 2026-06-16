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

import { getStatus, setup, unlock, lock, listVaultItems, uploadToVault, removeFromVault } from '@/api/vault'

describe('api/vault.js — endpoint correctness', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('setup sends loginPassword, vaultPassword, confirmPassword', async () => {
    await setup('login123', 'vault456', 'vault456')
    expect(mockRequest.post).toHaveBeenCalledWith('/vault/setup', {
      loginPassword: 'login123',
      vaultPassword: 'vault456',
      confirmPassword: 'vault456'
    })
  })

  it('unlock sends password in body', async () => {
    await unlock('vault456')
    expect(mockRequest.post).toHaveBeenCalledWith('/vault/unlock', {
      password: 'vault456'
    })
  })

  it('lock sends no body', async () => {
    await lock()
    expect(mockRequest.post).toHaveBeenCalledWith('/vault/lock')
  })

  it('getStatus sends GET', async () => {
    await getStatus()
    expect(mockRequest.get).toHaveBeenCalledWith('/vault/status')
  })
})
