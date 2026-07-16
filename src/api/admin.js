import request from './request'

export function getStatsOverview() {
  return request.get('/admin/stats/overview')
}

export function getUsers(page, size, keyword) {
  return request.get('/admin/users', { params: { page, size, keyword } })
}

export function updateUserStatus(id, status) {
  return request.put(`/admin/users/${id}/status`, { status })
}

export function updateUserQuota(id, quotaBytes) {
  return request.put(`/admin/users/${id}/quota`, { quotaBytes })
}

export function getNodes() {
  return request.get('/admin/nodes')
}

export function createNode(data) {
  return request.post('/admin/nodes', data)
}

export function updateNode(id, data) {
  return request.put(`/admin/nodes/${id}`, data)
}

export function deleteNode(id) {
  return request.delete(`/admin/nodes/${id}`)
}

export function getLogs(page, size, params) {
  return request.get('/admin/logs', { params: { page, size, ...params } })
}
