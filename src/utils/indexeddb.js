const DB_NAME = 'm78netdisk-uploads'
const DB_VERSION = 2
const STORE_NAME = 'upload-files'

function openDB() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)
    request.onerror = () => reject(request.error)
    request.onsuccess = () => resolve(request.result)
    request.onupgradeneeded = (event) => {
      const db = event.target.result
      let store
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        store = db.createObjectStore(STORE_NAME, { keyPath: 'id' })
        store.createIndex('taskId', 'taskId', { unique: false })
      } else if (event.oldVersion < 2) {
        // Upgrade from v1 (keyPath='taskId') to v2 (keyPath='id')
        db.deleteObjectStore(STORE_NAME)
        store = db.createObjectStore(STORE_NAME, { keyPath: 'id' })
        store.createIndex('taskId', 'taskId', { unique: false })
      }
    }
  })
}

/**
 * 将 File 以 ArrayBuffer 存入 IndexedDB
 * @param {number} id - store item id
 * @param {File} file
 * @param {number} parentId
 */
export async function saveUploadFile(id, file, parentId) {
  const buffer = await file.arrayBuffer()
  const db = await openDB()
  const tx = db.transaction(STORE_NAME, 'readwrite')
  const store = tx.objectStore(STORE_NAME)
  await new Promise((resolve, reject) => {
    const req = store.put({
      id,
      taskId: null,
      name: file.name,
      size: file.size,
      type: file.type,
      parentId,
      buffer,
      createdAt: Date.now()
    })
    req.onsuccess = resolve
    req.onerror = () => reject(req.error)
  })
  db.close()
}

/**
 * 按 id 从 IndexedDB 读取并重建 File
 */
export async function getUploadFile(id) {
  const db = await openDB()
  const tx = db.transaction(STORE_NAME, 'readonly')
  const store = tx.objectStore(STORE_NAME)
  const result = await new Promise((resolve, reject) => {
    const req = store.get(id)
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
  db.close()
  if (!result) return null
  const file = new File([result.buffer], result.name, { type: result.type })
  return { id: result.id, file, parentId: result.parentId, name: result.name, size: result.size, taskId: result.taskId }
}

/**
 * 按 taskId 索引查找
 */
export async function getUploadFileByTaskId(taskId) {
  const db = await openDB()
  const tx = db.transaction(STORE_NAME, 'readonly')
  const store = tx.objectStore(STORE_NAME)
  const index = store.index('taskId')
  const result = await new Promise((resolve, reject) => {
    const req = index.get(taskId)
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
  db.close()
  if (!result) return null
  const file = new File([result.buffer], result.name, { type: result.type })
  return { id: result.id, file, parentId: result.parentId, name: result.name, size: result.size, taskId: result.taskId }
}

/**
 * 获取所有存储的上传文件，按 createdAt 排序
 */
export async function getAllUploadFiles() {
  const db = await openDB()
  const tx = db.transaction(STORE_NAME, 'readonly')
  const store = tx.objectStore(STORE_NAME)
  const results = await new Promise((resolve, reject) => {
    const req = store.getAll()
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
  db.close()
  results.sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0))
  return results.map(r => {
    const file = new File([r.buffer], r.name, { type: r.type })
    return { id: r.id, file, parentId: r.parentId, name: r.name, size: r.size, taskId: r.taskId }
  })
}

/**
 * 更新记录的 taskId
 */
export async function updateTaskId(id, taskId) {
  const db = await openDB()
  const tx = db.transaction(STORE_NAME, 'readwrite')
  const store = tx.objectStore(STORE_NAME)
  const record = await new Promise((resolve) => {
    const req = store.get(id)
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => resolve(null)
  })
  if (record) {
    record.taskId = taskId
    await new Promise((resolve) => {
      const req = store.put(record)
      req.onsuccess = resolve
      req.onerror = () => resolve() // best-effort
    })
  }
  db.close()
}

/**
 * 按 id 删除
 */
export async function removeUploadFile(id) {
  const db = await openDB()
  const tx = db.transaction(STORE_NAME, 'readwrite')
  const store = tx.objectStore(STORE_NAME)
  await new Promise((resolve, reject) => {
    const req = store.delete(id)
    req.onsuccess = resolve
    req.onerror = () => reject(req.error)
  })
  db.close()
}
