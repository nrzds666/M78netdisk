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
