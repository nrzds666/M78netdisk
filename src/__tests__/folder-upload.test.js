import { describe, it, expect } from 'vitest'
import fs from 'fs'
import path from 'path'

describe('FileListView — Folder Upload', () => {
  const filePath = path.resolve(__dirname, '../views/file/FileListView.vue')

  it('RED: should have hidden input with webkitdirectory', () => {
    const content = fs.readFileSync(filePath, 'utf-8')
    // After implementation: there should be a hidden input with webkitdirectory
    expect(content).toContain('webkitdirectory')
  })

  it('RED: should call uploadFolder with real logic, not placeholder', () => {
    const content = fs.readFileSync(filePath, 'utf-8')
    // After implementation: uploadFolder should not show placeholder message
    expect(content).not.toContain('上传文件夹功能开发中')
  })
})
