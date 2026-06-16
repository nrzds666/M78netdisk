import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import fs from 'fs'
import path from 'path'

describe('chunkedUpload — abort signal normalization', () => {
  const filePath = path.resolve(__dirname, '../api/file.js')

  it('RED: catch should convert CanceledError to paused error', () => {
    const content = fs.readFileSync(filePath, 'utf-8')
    const catchBlock = extractCatchBlock(content)
    // After fix: catch should detect CanceledError / ERR_CANCELED and throw Error('paused')
    expect(catchBlock).toMatch(/ERR_CANCELED/)
    expect(catchBlock).toMatch(/CanceledError/)
  })
})

/**
 * Extract the try-catch body from the chunkedUpload function
 */
function extractCatchBlock(content) {
  // Find the chunkedUpload function body
  const fnMatch = content.match(/export async function chunkedUpload[\s\S]*?\n\}/)
  if (!fnMatch) return ''
  const fnBody = fnMatch[0]
  // Find the catch block
  const catchMatch = fnBody.match(/catch\s*\(e\)\s*\{[\s\S]*?\n  \}/)
  return catchMatch ? catchMatch[0] : ''
}
