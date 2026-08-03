import request from './request'

/**
 * 发送聊天消息（POST）
 * @param {string} message 用户消息
 * @returns {Promise<{reply: string}>}
 */
export function sendMessage(message) {
  return request.post('/chat/message', { message })
}

/**
 * 发送测试消息（GET）
 * @param {string} message 用户消息
 * @returns {Promise<{reply: string}>}
 */
export function testMessage(message) {
  return request.get('/chat/test', { params: { message } })
}

/**
 * 搜索网盘文件
 * @param {string} query - 搜索关键词
 * @param {number} [limit=10] - 返回数量上限
 */
export function searchFiles(query, limit = 10) {
  return request.get('/chat/search-files', { params: { query, limit } })
}

/**
 * 读取文档全文
 * @param {number} fileId - 文件 ID
 */
export function readDocument(fileId) {
  return request.get(`/chat/read-document/${fileId}`)
}

/**
 * 生成图片
 * @param {Object} params
 * @param {string} params.prompt - 图片描述提示词（必填）
 * @param {string} [params.negativePrompt] - 负面提示词（可选）
 * @param {number} [params.width] - 图片宽度（默认512）
 * @param {number} [params.height] - 图片高度（默认512）
 * @returns {Promise<{fileId: number, fileName: string, fileUrl: string, fileSize: number, width: number, height: number}>}
 */
export function generateImage({ prompt, negativePrompt, width, height }) {
  return request.post('/chat/generate-image', { prompt, negativePrompt, width, height })
}
