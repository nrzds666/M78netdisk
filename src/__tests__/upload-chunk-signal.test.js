import { describe, it, expect } from 'vitest'
import fs from 'fs'
import path from 'path'

describe('uploadChunk — abort signal', () => {
  const filePath = path.resolve(__dirname, '../api/file.js')

  it('RED: uploadChunk should accept signal parameter', () => {
    const content = fs.readFileSync(filePath, 'utf-8')
    // After fix: uploadChunk should have signal param and pass to axios config
    const match = content.match(/export function uploadChunk\([^)]+\)/)
    expect(match).not.toBeNull()
    expect(match[0]).toContain('signal')
    expect(content).toContain('config.signal = signal')
  })

  it('RED: chunkedUpload should pass signal to uploadChunk', () => {
    const content = fs.readFileSync(filePath, 'utf-8')
    // uploadChunk calls within chunkedUpload should pass the signal
    const calls = content.match(/uploadChunk\([^)]+\)/g) || []
    const hasSignal = calls.some(c => c.includes('signal'))
    expect(hasSignal).toBe(true)
  })
})
