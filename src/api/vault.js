import request from './request'

/**
 * Get vault status (setup + unlocked flags)
 * @returns {Promise} resolves to R<VaultStatusVO> { setup: boolean, unlocked: boolean }
 */
export function getStatus() {
  return request.get('/vault/status')
}

/**
 * Set up vault with a password (first-time setup)
 * @param {string} loginPassword - the user's login password
 * @param {string} vaultPassword - the vault password
 * @param {string} confirmPassword - confirm vault password
 * @returns {Promise} resolves to R<Void>
 */
export function setup(loginPassword, vaultPassword, confirmPassword) {
  return request.post('/vault/setup', {
    loginPassword,
    vaultPassword,
    confirmPassword
  })
}

/**
 * Unlock the vault with the password
 * @param {string} password - the vault password
 * @returns {Promise} resolves to R<Void>
 */
export function unlock(password) {
  return request.post('/vault/unlock', { password })
}

/**
 * Lock the vault
 * @returns {Promise} resolves to R<Void>
 */
export function lock() {
  return request.post('/vault/lock')
}

/**
 * List items inside the vault
 * @param {string|null} parentId - parent folder ID, null for root
 * @param {number} page - page number (1-based)
 * @param {number} size - page size
 * @returns {Promise} resolves to R<IPage<VaultItemVO>>
 */
export function listVaultItems(parentId, page = 1, size = 20) {
  const params = { page, size }
  if (parentId) {
    params.parentId = parentId
  }
  return request.get('/vault/files/list', { params })
}

/**
 * Upload a file to the vault
 * @param {File} file - the file to upload
 * @param {string|null} parentId - target folder ID
 * @returns {Promise} resolves to R<VaultItemVO>
 */
export function uploadToVault(file, parentId) {
  const formData = new FormData()
  formData.append('file', file)
  if (parentId) {
    formData.append('parentId', parentId)
  }
  return request.post('/vault/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * Remove a file from the vault (move out)
 * @param {string} itemId - vault item ID
 * @returns {Promise} resolves to R<Void>
 */
export function removeFromVault(itemId) {
  return request.put('/vault/files/remove', null, {
    params: { itemId }
  })
}
