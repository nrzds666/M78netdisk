import { describe, it, expect, vi, beforeEach } from 'vitest'
import fs from 'fs'
import path from 'path'

const shareAccessPath = path.resolve(__dirname, '../views/share/ShareAccessView.vue')
const content = fs.readFileSync(shareAccessPath, 'utf-8')

describe('Share Access — Routes', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('router should have /share/ routes defined', async () => {
    const { default: appRouter } = await import('@/router/index.js')
    const shareRoutes = appRouter.getRoutes().filter(r => r.path.startsWith('/share/'))
    expect(shareRoutes.length).toBeGreaterThanOrEqual(2)
    const paths = shareRoutes.map(r => r.path)
    expect(paths).toContain('/share/:token')
    expect(paths).toContain('/share/:token/folder/:folderId')
  })

  // ─── 🟥 RED: 1a — Single file share should show file in list ───

  it('[GREEN] 1a: handleAccess should call loadItems() for single file shares', () => {
    // After fix: loadItems() is called unconditionally in handleAccess
    // The handleAccess function should NOT have `if (res.data.isDirectory) { loadItems() } else { items.value = [] }`
    expect(content).toContain('loadItems()')
    // Verify no conditional else that skips loading for single files in handleAccess
    // Only check the script section (after '<script setup>')
    const scriptSection = content.split('<script setup>')[1] || ''
    const handleAccessSection = scriptSection.split('function handleRowDblClick')[0] || ''
    expect(handleAccessSection).not.toMatch(/if.*isDirectory[\s\S]*?else[\s\S]*?items/)
  })

  // ─── 🟢 1b — Password gate should show when accessGranted=false ───

  it('[GREEN] 1b: handleAccess checks accessGranted before showing file list', () => {
    // After fix: handleAccess checks `res.data.accessGranted` before granting
    expect(content).toContain('accessGranted')
    expect(content).toContain('if (res.data.accessGranted)')
  })

  it('[GREEN] 1b: password gate template has password input for hasPassword shares', () => {
    expect(content).toContain('请输入提取码')
    expect(content).toContain('placeholder="请输入提取码"')
    expect(content).toContain('@keyup.enter="handleAccess"')
  })

  // ─── 🟢 1c — Owner info displayed on share page ───

  it('[GREEN] 1c: share page shows owner name and avatar', () => {
    expect(content).toContain('shareInfo?.ownerName')
    expect(content).toContain('shareInfo.ownerAvatar')
    expect(content).toContain('share-owner-name')
  })
})
