import { describe, it, expect } from 'vitest'
import fs from 'fs'
import path from 'path'

describe('FileListView — folder upload creates root folder', () => {
  const filePath = path.resolve(__dirname, '../views/file/FileListView.vue')

  it('RED: handleFolderSelected should create root folder before uploading files', () => {
    const content = fs.readFileSync(filePath, 'utf-8')
    // After fix: should call createFolder for the root folder name
    const lines = content.split('\n')
    // Find the section after addFolder call, before the for loop
    const addFolderIdx = lines.findIndex(l => l.includes('uploadStore.addFolder'))
    // The for loop starts with `for (let i = 0; i < fileEntries.length; i++)`
    const forLoopIdx = lines.findIndex(l => l.includes('for (let i = 0; i < fileEntries.length; i++)'))
    const between = lines.slice(addFolderIdx, forLoopIdx).join('\n')
    // Should contain createFolder call for the root folder
    expect(between).toContain('createFolder(folderName')
    // Should use the created folder ID as base parentId
    expect(between).toContain('rootFolderId')
  })
})
