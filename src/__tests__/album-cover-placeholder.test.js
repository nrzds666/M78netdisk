import { describe, it, expect } from 'vitest'
import fs from 'fs'
import path from 'path'

describe('AlbumView — Cover Placeholder', () => {
  const filePath = path.resolve(__dirname, '../views/album/AlbumView.vue')
  const content = fs.readFileSync(filePath, 'utf-8')

  it('RED: el-image should have v-if cover condition so v-else sibling works', () => {
    // The bug: v-else on line 51 has no matching v-if sibling (el-image lacks v-if).
    // Expected: <el-image v-if="album.coverThumbnailKey || album.coverItemId" ...>
    expect(content).toContain('v-if="album.coverThumbnailKey || album.coverItemId"')
  })

  it('RED: should render v-else placeholder when no cover exists', () => {
    // After fix: v-else div should exist after </el-image> for the "更换封面" placeholder
    expect(content).toContain('v-else class="cover-placeholder"')
  })

  it('RED: should not have orphaned v-else (template slot closed before v-else)', () => {
    // Verify the structural order: </el-image> comes before v-else div
    const elImageCloseIdx = content.indexOf('</el-image>')
    const vElseIdx = content.indexOf('v-else class="cover-placeholder"')
    expect(elImageCloseIdx).toBeGreaterThan(0)
    expect(vElseIdx).toBeGreaterThan(elImageCloseIdx)
  })
})
