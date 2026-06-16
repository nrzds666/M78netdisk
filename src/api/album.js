import request from './request'

/**
 * List albums (paginated)
 * @param {number} page - page number (1-based)
 * @param {number} size - page size
 * @returns {Promise} resolves to R<IPage<AlbumVO>>
 */
export function listAlbums(page = 1, size = 20) {
  return request.get('/albums', { params: { page, size } })
}

/**
 * Create a new album
 * @param {{ name: string, description?: string }} data
 * @returns {Promise} resolves to R<AlbumVO>
 */
export function createAlbum(data) {
  return request.post('/albums', data)
}

/**
 * Get album detail (with items)
 * @param {number|string} id - album ID
 * @param {number} page - page number (1-based)
 * @param {number} size - page size
 * @returns {Promise} resolves to R<AlbumVO>
 */
export function getAlbumDetail(id, page = 1, size = 20) {
  return request.get(`/albums/${id}`, { params: { page, size } })
}

/**
 * Delete an album
 * @param {number|string} id - album ID
 * @returns {Promise} resolves to R<Void>
 */
export function deleteAlbum(id) {
  return request.delete(`/albums/${id}`)
}

/**
 * Add files to an album
 * @param {number|string} id - album ID
 * @param {number[]} itemIds - file IDs to add
 * @returns {Promise} resolves to R<Void>
 */
export function addAlbumItems(id, itemIds) {
  return request.post(`/albums/${id}/items`, { itemIds })
}

/**
 * Remove files from an album
 * @param {number|string} id - album ID
 * @param {number[]} itemIds - file IDs to remove
 * @returns {Promise} resolves to R<Void>
 */
export function removeAlbumItems(id, itemIds) {
  return request.delete(`/albums/${id}/items`, {
    params: { itemIds }
  })
}

/**
 * Update album properties (name, description, sortOrder)
 * @param {number|string} id - album ID
 * @param {{ name?: string, description?: string, sortOrder?: number }} data
 * @returns {Promise} resolves to R<AlbumVO>
 */
export function updateAlbum(id, data) {
  return request.put(`/albums/${id}`, data)
}

/**
 * Set album cover from an album item
 * @param {number|string} id - album ID
 * @param {number} itemId - file ID to set as cover
 * @returns {Promise} resolves to R<AlbumVO>
 */
export function setAlbumCover(id, itemId) {
  return request.put(`/albums/${id}/cover`, null, {
    params: { itemId }
  })
}

/**
 * Create a share link for an album
 * @param {number|string} id - album ID
 * @param {number} [expireDays] - optional expiry in days
 * @returns {Promise} resolves to R<AlbumShareVO>
 */
export function createAlbumShare(id, expireDays) {
  return request.post(`/albums/${id}/share`, null, {
    params: { expireDays }
  })
}

/**
 * Get shared album by token (public, no auth)
 * @param {string} token - share token
 * @returns {Promise} resolves to R<AlbumVO>
 */
export function getSharedAlbum(token) {
  return request.get(`/albums/share-access/${token}`)
}
