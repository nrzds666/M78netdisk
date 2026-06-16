import request from './request'

/**
 * Create a share link
 * @param {{ itemId: number, password?: string, permission?: string, expireType?: string, maxDownloads?: number }} data
 * @returns {Promise} resolves to R<ShareVO>
 */
export function createShare(data) {
  return request.post('/shares', data)
}

/**
 * List my shares (paginated)
 * @param {number} page - page number (1-based)
 * @param {number} size - page size
 * @returns {Promise} resolves to R<IPage<ShareVO>>
 */
export function listMyShares(page = 1, size = 20) {
  return request.get('/shares/mine', { params: { page, size } })
}

/**
 * Cancel a share
 * @param {string|number} id - share ID
 * @returns {Promise}
 */
export function cancelShare(id) {
  return request.post(`/shares/${id}/cancel`)
}

/**
 * Access a shared link (with optional password)
 * @param {string} token - share token
 * @param {string} [password] - extraction password
 * @returns {Promise} resolves to R<ShareVO>
 */
export function accessShare(token, password) {
  const params = {}
  if (password) params.password = password
  return request.get(`/shares/access/${token}`, { params })
}

/**
 * List received shares (paginated)
 * @param {number} page
 * @param {number} size
 * @returns {Promise} resolves to R<IPage<ShareVO>>
 */
export function listReceivedShares(page = 1, size = 20) {
  return request.get('/shares/received', { params: { page, size } })
}

/**
 * Browse items inside a shared folder
 * @param {string} token - share token
 * @param {string} [password] - extraction password
 * @param {number|null} [parentId] - parent folder id
 * @param {number} [page]
 * @param {number} [size]
 * @returns {Promise} resolves to R<IPage<ItemVO>>
 */
export function listShareItems(token, password, parentId, page = 1, size = 20) {
  const params = { page, size }
  if (password) params.password = password
  if (parentId) params.parentId = parentId
  return request.get(`/shares/access/${token}/items`, { params })
}

/**
 * Download a file from a share (returns blob)
 * @param {string} token - share token
 * @param {string} [password]
 * @param {number} itemId
 * @returns {Promise} resolves to Blob
 */
export function downloadShareFile(token, password, itemId) {
  const params = { itemId }
  if (password) params.password = password
  return request.get(`/shares/access/${token}/download`, {
    params,
    responseType: 'blob',
    timeout: 600000
  })
}

/**
 * Save shared files to current user's storage
 * @param {string} token - share token
 * @param {string} [password]
 * @param {number[]} itemIds
 * @returns {Promise} resolves to R<List<ItemVO>>
 */
export function saveShareFiles(token, password, itemIds) {
  const params = {}
  if (password) params.password = password
  return request.post(`/shares/access/${token}/save`, itemIds, { params, timeout: 600000 })
}
