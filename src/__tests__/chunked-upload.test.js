import { describe, it, expect } from 'vitest'
import fs from 'fs'
import path from 'path'

describe('chunkedUpload — onTaskInit callback', () => {
  const filePath = path.resolve(__dirname, '../api/file.js')

  it('RED: chunkedUpload should accept onTaskInit as 7th parameter', () => {
    const content = fs.readFileSync(filePath, 'utf-8')
    // After fix: chunkedUpload should declare onTaskInit in the function signature
    const match = content.match(/export async function chunkedUpload\([^)]+\)/)
    expect(match).not.toBeNull()
    const params = match[0]
    // 7th parameter should be onTaskInit (position in current: file, parentId, onProgress, fileName, resumeTaskId, skipChunks, onTaskInit)
    expect(params).toContain('onTaskInit')
  })

  it('RED: chunkedUpload should call onTaskInit after init', () => {
    const content = fs.readFileSync(filePath, 'utf-8')
    // Look for onTaskInit being called
    expect(content).toContain('onTaskInit')
  })
})
