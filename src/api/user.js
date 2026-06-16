import request from './request'

export function getCaptcha() {
  return request.get('/users/captcha')
}

export function login(username, password, captchaKey, captchaCode) {
  return request.post('/users/login', { username, password, captchaKey, captchaCode })
}

export function register(username, password, email, captchaKey, captchaCode) {
  return request.post('/users/register', { username, password, email, captchaKey, captchaCode })
}

export function getUserInfo() {
  return request.get('/users')
}

export function refreshToken() {
  return request.post('/users/refresh')
}

export function updateProfile(username) {
  return request.put('/users/profile', null, { params: { username } })
}

export function updatePassword(oldPassword, newPassword) {
  return request.put('/users/password', null, { params: { oldPassword, newPassword } })
}

export function updateAvatar(avatarUrl) {
  return request.put('/users/avatar', null, { params: { avatarUrl } })
}

export function uploadAvatarTemp(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/users/avatar/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function updateAvatarByKey(storageKey) {
  return request.put('/users/avatar/by-key', null, { params: { storageKey } })
}
