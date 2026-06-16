import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import fs from 'fs'
import path from 'path'

describe('pauseItem — abort order', () => {
  const filePath = path.resolve(__dirname, '../views/transfer/TransferView.vue')

  it('abortController.abort should be called before uploadPause', () => {
    const content = fs.readFileSync(filePath, 'utf-8')
    const pauseFn = extractPauseItem(content)

    // The abort should appear before the uploadPause call
    const abortIdx = pauseFn.indexOf('abortController.abort()')
    const pauseApiIdx = pauseFn.indexOf('uploadPause')

    expect(abortIdx).toBeGreaterThan(0)
    expect(pauseApiIdx).toBeGreaterThan(0)
    expect(abortIdx).toBeLessThan(pauseApiIdx)
  })
})

function extractPauseItem(content) {
  const match = content.match(/async function pauseItem[\s\S]*?\n  \}/)
  return match ? match[0] : ''
}
