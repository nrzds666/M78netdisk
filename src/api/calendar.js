import request from './request'

/**
 * Get calendar data for today
 * @returns {Promise}
 */
export function getToday() {
  return request.get('/calendar/today')
}
