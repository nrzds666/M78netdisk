import { describe, it, expect } from 'vitest'
import fs from 'fs'
import path from 'path'

describe('FileListView — confirmUpload taskId tracking', () => {
  const filePath = path.resolve(__dirname, '../views/file/FileListView.vue')

  it('RED: confirmUpload should set item.taskId via callback', () => {
    const content = fs.readFileSync(filePath, 'utf-8')
    expect(content).toContain('item.taskId = taskId')
  })

  it('RED: resume logic moved to TransferView (not in FileListView anymore)', () => {
    const content = fs.readFileSync(filePath, 'utf-8')
    // handleResumeUpload was removed; resume is now in TransferView
    const matches = content.match(/item\.taskId\s*=\s*taskId/g)
    expect(matches).not.toBeNull()
    // Only confirmUpload tracks taskId now
    expect(matches.length).toBe(1)
  })
})
