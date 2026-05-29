import request from './request'

/**
 * List files/folders in a directory
 * @param {string|null} parentId - parent folder ID, null for root
 * @param {number} page - page number (1-based)
 * @param {number} size - page size
 * @returns {Promise}
 */
export function listItems(parentId, page = 1, size = 20) {
  const params = { page, size }
  if (parentId) {
    params.parentId = parentId
  }
  return request.get('/files/list', { params })
}

/**
 * Create a new folder
 * @param {string} name - folder name
 * @param {string|null} parentId - parent folder ID, null for root
 * @returns {Promise}
 */
export function createFolder(name, parentId) {
  return request.post('/files/folder', { name, parentId })
}

/**
 * Rename a file or folder
 * @param {string} itemId - file/folder ID
 * @param {string} newName - new name
 * @returns {Promise}
 */
export function rename(itemId, newName) {
  return request.put('/files/rename', { itemId, newName })
}

/**
 * Move items to trash
 * @param {string[]} ids - array of file/folder IDs
 * @returns {Promise}
 */
export function moveToTrash(ids) {
  return request.put('/files/trash', { ids })
}

/**
 * Restore items from trash
 * @param {string[]} ids - array of file/folder IDs
 * @returns {Promise}
 */
export function restore(ids) {
  return request.put('/files/restore', { ids })
}

/**
 * Permanently delete items
 * @param {string[]} ids - array of file/folder IDs
 * @returns {Promise}
 */
export function permanentDelete(ids) {
  return request.delete('/files', { data: { ids } })
}

/**
 * List trash items
 * @param {number} page - page number (1-based)
 * @param {number} size - page size
 * @returns {Promise}
 */
export function listTrash(page = 1, size = 20) {
  return request.get('/files/trash', { params: { page, size } })
}

/**
 * Upload a file
 * @param {File} file - the file to upload
 * @param {string|null} parentId - target folder ID, null for root
 * @returns {Promise}
 */
export function upload(file, parentId) {
  const formData = new FormData()
  formData.append('file', file)
  if (parentId) {
    formData.append('parentId', parentId)
  }
  return request.post('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * Download a file
 * @param {string} id - file ID
 * @returns {Promise} - returns a blob response
 */
export function download(id) {
  return request.get(`/files/${id}/download`, { responseType: 'blob' })
}

/**
 * Preview a file (get URL or metadata)
 * @param {string} id - file ID
 * @returns {Promise}
 */
export function preview(id) {
  return request.get(`/files/${id}/preview`)
}

/**
 * List recently accessed items
 * @param {number} days - look back days
 * @returns {Promise}
 */
export function recentItems(days = 7) {
  return request.get('/files/recent', { params: { days } })
}

/**
 * List recently saved items
 * @param {number} days - look back days
 * @returns {Promise}
 */
export function recentSaves(days = 7) {
  return request.get('/files/recent-saves', { params: { days } })
}
