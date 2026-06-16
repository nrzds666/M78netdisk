import { describe, it, expect, vi, beforeEach } from 'vitest'
import fs from 'fs'
import path from 'path'

describe('MySharesView — Bug fixes and enhancements', () => {
  const filePath = path.resolve(__dirname, '../views/share/MySharesView.vue')

  it('RED: should use downloadCount instead of accessCount', () => {
    const content = fs.readFileSync(filePath, 'utf-8')
    // The bug: currently uses non-existent accessCount/maxAccessCount
    // After fix: should use downloadCount/maxDownloads
    expect(content).toContain('row.downloadCount')
  })

  it('RED: should have received shares tab', () => {
    const content = fs.readFileSync(filePath, 'utf-8')
    // After fix: should have an el-tabs or received shares section
    expect(content).toContain('收到的分享')
  })

  it('RED: should have copy share link button', () => {
    const content = fs.readFileSync(filePath, 'utf-8')
    expect(content).toContain('复制链接')
  })
})
