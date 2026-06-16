import { describe, it, expect } from 'vitest'
import fs from 'fs'
import path from 'path'

describe('chunkedUpload — merge progress', () => {
  const filePath = path.resolve(__dirname, '../api/file.js')

  it('RED: should call onProgress during merge poll', () => {
    const content = fs.readFileSync(filePath, 'utf-8')
    // After fix: during polling loop, progress should be updated with 99
    const pollSection = content.match(/\/\/ Poll status[\s\S]*?}\s+catch/g)
    expect(pollSection).not.toBeNull()
    expect(pollSection[0]).toContain('onProgress')
    expect(pollSection[0]).toContain('99')
  })
})
