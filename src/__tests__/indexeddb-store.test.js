import { describe, it, expect } from 'vitest'
import fs from 'fs'
import path from 'path'

describe('IndexedDB upload file store', () => {
  const filePath = path.resolve(__dirname, '../utils/indexeddb.js')

  it('should export saveUploadFile / getUploadFile / removeUploadFile / getAllUploadFiles / getUploadFileByTaskId / updateTaskId', () => {
    const content = fs.readFileSync(filePath, 'utf-8')
    expect(content).toContain('export async function saveUploadFile')
    expect(content).toContain('export async function getUploadFile')
    expect(content).toContain('export async function removeUploadFile')
    expect(content).toContain('export async function getAllUploadFiles')
    expect(content).toContain('export async function getUploadFileByTaskId')
    expect(content).toContain('export async function updateTaskId')
  })

  it('should use item id as keyPath, not taskId', () => {
    const content = fs.readFileSync(filePath, 'utf-8')
    expect(content).toContain('keyPath')
    expect(content).toContain("'id'")
    expect(content).not.toMatch(/createObjectStore.*keyPath:\s*['"]taskId['"]/)
  })

  it('should create taskId index for lookup', () => {
    const content = fs.readFileSync(filePath, 'utf-8')
    expect(content).toContain('createIndex')
    expect(content).toContain('taskId')
  })

  it('should store file as ArrayBuffer with createdAt timestamp', () => {
    const content = fs.readFileSync(filePath, 'utf-8')
    expect(content).toContain('.arrayBuffer()')
    expect(content).toContain('createdAt')
  })

  it('should getAllUploadFiles sorted by createdAt', () => {
    const content = fs.readFileSync(filePath, 'utf-8')
    expect(content).toContain('getAllUploadFiles')
    expect(content).toMatch(/sort|createdAt/)
  })

  it('should reconstruct File from stored ArrayBuffer', () => {
    const content = fs.readFileSync(filePath, 'utf-8')
    expect(content).toContain('new File')
  })
})
