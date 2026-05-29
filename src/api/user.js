import request from './request'

/**
 * User login
 * @param {string} username
 * @param {string} password
 * @returns {Promise}
 */
export function login(username, password) {
  return request.post('/user/login', { username, password })
}

/**
 * User registration
 * @param {string} username
 * @param {string} password
 * @param {string} email
 * @returns {Promise}
 */
export function register(username, password, email) {
  return request.post('/user/register', { username, password, email })
}

/**
 * Get current user info
 * @returns {Promise}
 */
export function getUserInfo() {
  return request.get('/user/info')
}

/**
 * Refresh auth token
 * @returns {Promise}
 */
export function refreshToken() {
  return request.post('/user/refresh')
}
