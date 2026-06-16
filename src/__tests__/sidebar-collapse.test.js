import { describe, it, expect } from 'vitest'
import fs from 'fs'
import path from 'path'

describe('MainLayout — Sidebar collapse', () => {
  const filePath = path.resolve(__dirname, '../views/layout/MainLayout.vue')
  const content = fs.readFileSync(filePath, 'utf-8')

  it('RED: sidebar-header should have dynamic is-collapsed class', () => {
    // Fix: add :class="{ 'is-collapsed': isCollapsed }" to sidebar-header
    // So when collapsed, the button can be centered without being clipped
    expect(content).toContain('class=\"sidebar-header\"')
    expect(content).toContain('is-collapsed')
  })

  it('RED: should have CSS for .sidebar-header.is-collapsed', () => {
    // When collapsed, center the button with reduced padding
    expect(content).toContain('.sidebar-header.is-collapsed')
  })
})
