import { describe, it, expect } from 'vitest'
import fs from 'fs'
import path from 'path'

describe('AlbumDetailView — Slideshow (方案C)', () => {
  const filePath = path.resolve(__dirname, '../views/album/AlbumDetailView.vue')

  const content = fs.readFileSync(filePath, 'utf-8')

  it('RED: should have slideshow button in template', () => {
    expect(content).toContain('slideshow')
    expect(content).toContain('幻灯片')
  })

  it('RED: should have slideshowVisible ref', () => {
    expect(content).toContain('slideshowVisible')
  })

  it('RED: should have keyboard event listeners', () => {
    expect(content).toContain('keydown')
  })
})
