import { describe, it, expect, vi, beforeEach } from 'vitest'
import fs from 'fs'
import path from 'path'

describe('AlbumDetailView — Gallery Preview', () => {
  const filePath = path.resolve(__dirname, '../views/album/AlbumDetailView.vue')

  it('RED: should NOT have old el-dialog preview', () => {
    const content = fs.readFileSync(filePath, 'utf-8')
    // After implementation: the old dialog-based preview should be removed
    expect(content).not.toContain('showPreview')
  })

  it('RED: should use preview-src-list on grid images', () => {
    const content = fs.readFileSync(filePath, 'utf-8')
    // After implementation: el-image in the grid should have preview-src-list
    expect(content).toContain('preview-src-list')
  })

  it('RED: should show file info in preview (name, size)', () => {
    const content = fs.readFileSync(filePath, 'utf-8')
    // After implementation: file metadata should be displayed
    expect(content).toContain('item.size')
  })
})
