import { describe, it, expect } from 'vitest'
import fs from 'fs'
import path from 'path'

describe('TransferView — filter active upload tasks', () => {
  const filePath = path.resolve(__dirname, '../views/transfer/TransferView.vue')

  it('loadUnfinishedTasks should filter out active queue tasks', () => {
    const content = fs.readFileSync(filePath, 'utf-8')
    expect(content).toContain('activeTaskIds')
    expect(content).toContain('.taskId)')
  })

  it('should import getAllUploadFiles and getUploadFileByTaskId for auto-resume', () => {
    const content = fs.readFileSync(filePath, 'utf-8')
    expect(content).toContain('getAllUploadFiles')
    expect(content).toContain('getUploadFileByTaskId')
  })

  it('should filter pending items too in loadUnfinishedTasks', () => {
    const content = fs.readFileSync(filePath, 'utf-8')
    expect(content).toContain("status === 'uploading' || i.status === 'pending'")
  })

  it('should have pendingItems section in template', () => {
    const content = fs.readFileSync(filePath, 'utf-8')
    expect(content).toContain('等待上传')
    expect(content).toContain('pendingItems')
  })
})
