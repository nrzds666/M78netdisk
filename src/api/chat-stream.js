import request from './request'

/**
 * 发送聊天消息（POST，传统一次性返回）
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
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 *  SSE 流式聊天 API
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 *
 * 这个函数使用原生 fetch + ReadableStream 来消费后端 SSE 流。
 * 不使用 axios 的原因：axios 会等待完整响应才返回，无法流式处理。
 *
 * SSE 数据格式（后端每推一条数据，前端就收到一个 chunk）：
 *   data: {"chunk":"你","done":false}
 *   data: {"chunk":"好","done":false}
 *   data: {"chunk":"！","done":false}
 *   ...
 *   data: {"chunk":"","done":true}
 *
 * @param {string} message - 用户发送的消息
 * @param {object} options - 可选配置
 * @param {AbortSignal} options.signal - 用于中断请求的 AbortController.signal
 * @yields {StreamChunk} 每次 yield 一个 chunk: { text, done }
 *
 * 使用示例：
 *   const stream = streamChatMessage("你好")
 *   for await (const chunk of stream) {
 *     console.log(chunk.text)  // 逐字打印
 *   }
 */
/**
 * 保存 AI 生成的内容到网盘文档
 * @param {object} params
 * @param {string} params.content - AI 生成的全文内容
 * @param {string} params.fileName - 文件名（含扩展名）
 * @param {number} [params.parentId] - 目标父目录 ID，默认根目录
 * @returns {Promise<{fileId: number, fileName: string, fileSize: number}>}
 */
export async function saveDocument({ content, fileName, parentId }) {
  const baseUrl = import.meta.env.VITE_APP_BASE_API || ''
  const token = localStorage.getItem('m78_token')

  const response = await fetch(`${baseUrl}/chat/save-document`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json;charset=utf-8',
      'Authorization': `Bearer ${token}`,
    },
    body: JSON.stringify({ content, fileName, parentId }),
  })

  if (!response.ok) {
    const text = await response.text()
    throw new Error(`保存失败: HTTP ${response.status} ${text}`)
  }
  return response.json()
}

/**
 * 确认保存临时文档到网盘
 * @param {string} tempFileId - 临时文件 ID
 * @param {string} fileName - 文件名
 * @param {number} [parentId] - 目标父目录 ID
 */
export async function confirmTempDocument(tempFileId, parentId) {
  const baseUrl = import.meta.env.VITE_APP_BASE_API || ''
  const token = localStorage.getItem('m78_token')

  const params = new URLSearchParams()
  params.append('tempFileId', tempFileId)
  if (parentId != null) params.append('parentId', String(parentId))

  const response = await fetch(`${baseUrl}/chat/confirm-temp-document?${params}`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  })

  if (!response.ok) {
    const text = await response.text()
    throw new Error(`保存失败: HTTP ${response.status} ${text}`)
  }
  return response.json()
}

export async function* streamChatMessage(message, options = {}, history = [], docContext = null) {
  const baseUrl = (import.meta.env.VITE_APP_BASE_API || '')
  const url = `${baseUrl}/chat/stream`

  // 从 localStorage 读取 token（与 request.js 中的 getToken 逻辑一致）
  const token = localStorage.getItem('m78_token')

  const headers = {
    'Content-Type': 'application/json;charset=utf-8',
  }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const response = await fetch(url, {
    method: 'POST',
    headers,
    body: JSON.stringify({ message, history, ...(docContext ? { docContext } : {}) }),
    signal: options.signal,  // 支持用户取消
  })

  if (!response.ok) {
    throw new Error(`SSE 请求失败: HTTP ${response.status}`)
  }

  // 检查 Content-Type 是否是 SSE
  const contentType = response.headers.get('Content-Type') || ''
  if (!contentType.includes('text/event-stream')) {
    throw new Error('后端未返回 SSE 流，请确认接口正确配置')
  }

  // ── ReadableStream 解码逻辑 ──────────────────────────
  // 后端 SSE 格式：每行以 "data: " 开头，以 "\n\n" 结尾
  // 我们需要：
  //   1. 将二进制流解码为文本
  //   2. 按 "\n\n" 分割成独立的 SSE 事件
  //   3. 去掉每行的 "data: " 前缀
  //   4. 解析 JSON 拿到 chunk 内容
  // ──────────────────────────────────────────────────────

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''  // 存放未处理的残余文本（跨 chunk 边界）

  try {
    while (true) {
      const { done: streamDone, value } = await reader.read()

      if (streamDone) {
        // 流结束：处理剩余的 buffer 内容
        for await (const chunk of processBuffer(buffer)) {
          yield chunk
        }
        break
      }

      // 将新收到的字节解码并追加到 buffer
      const newText = decoder.decode(value, { stream: true })
      console.log('[SSE] 收到原始数据:', JSON.stringify(newText))
      buffer += newText

      // 按 "\n\n" 分割（SSE 事件分隔符）
      const events = buffer.split('\n\n')
      // 最后一个元素可能是不完整的片段，保留到下一次
      buffer = events.pop() || ''

      // 处理每个完整的事件
      for (const event of events) {
        if (!event.trim()) continue

        let eventType = null
        const lines = event.split('\n')
        for (const line of lines) {
          if (line.startsWith('event:')) {
            eventType = line.slice(6).trim()
          }
        }

        // 提取 "data:" 开头的行（兼容 "data: JSON" 有空格 和 "data:JSON" 无空格 两种格式）
        // Spring SseEmitter 发送格式为 "data:VALUE"（无空格），手动拼接为 "data: VALUE"（有空格）
        for (const line of lines) {
          let jsonStr = null
          // 【修复】兼容 "data:" 无空格 和 "data: " 有空格 两种 SSE 格式
          if (line.startsWith('data: ')) {
            jsonStr = line.slice(6)
          } else if (line.startsWith('data:')) {
            jsonStr = line.slice(5)
          }
          if (!jsonStr || !jsonStr.trim()) continue

          try {
            const parsed = JSON.parse(jsonStr)
            console.log('[SSE] 解析成功:', parsed)
            if (eventType === 'rag-status') {
              // RAG 状态事件，不拼入消息文本
              yield { ragStatus: parsed.ragActive }
            } else if (eventType === 'doc-generated') {
              // 文档生成事件，不拼入消息文本
              yield { docCard: parsed }
            } else if (eventType === 'img-generated') {
              // 图片生成事件
              yield { imageRef: parsed }
            } else {
              yield {
                text: parsed.chunk || '',   // 当前片段文本
                done: parsed.done || false, // 是否已结束
              }
            }
          } catch (e) {
            // JSON 解析失败时跳过（可能是后端拼接错误）
            console.warn('SSE JSON 解析失败:', jsonStr, e)
          }
        }
      }
    }
  } finally {
    reader.releaseLock()
  }

  /**
   * 处理 buffer 中剩余的事件（流结束时调用）
   */
  async function* processBuffer(buf) {
    if (!buf.trim()) return
    const events = buf.split('\n\n')
    for (const event of events) {
      let eventType = null
      const lines = event.split('\n')
      for (const line of lines) {
        if (line.startsWith('event:')) {
          eventType = line.slice(6).trim()
        }
        // 【修复】兼容 "data:" 无空格 和 "data: " 有空格 两种 SSE 格式
        let jsonStr = null
        if (line.startsWith('data: ')) {
          jsonStr = line.slice(6)
        } else if (line.startsWith('data:')) {
          jsonStr = line.slice(5)
        }
        if (!jsonStr || !jsonStr.trim()) continue
        if (eventType === 'rag-status') {
          // RAG 状态事件：{"ragActive": true/false}
          try {
            const parsed = JSON.parse(jsonStr)
            if (parsed.ragActive !== undefined) {
              yield { ragStatus: parsed.ragActive }
            }
          } catch { /* 忽略 */ }
        } else if (eventType === 'doc-generated') {
          // 文档生成事件
          try {
            const parsed = JSON.parse(jsonStr)
            yield { docCard: parsed }
          } catch { /* 忽略 */ }
        } else if (eventType === 'img-generated') {
          // 图片生成事件
          try {
            const parsed = JSON.parse(jsonStr)
            yield { imageRef: parsed }
          } catch { /* 忽略 */ }
        } else {
          try {
            const parsed = JSON.parse(jsonStr)
            yield {
              text: parsed.chunk || '',
              done: parsed.done || false,
            }
          } catch { /* 忽略 */ }
        }
      }
    }
  }
}

/**
 * 生成图片（同步 API）
 * @param {Object} params
 * @param {string} params.prompt - 图片描述提示词（必填）
 * @param {string} [params.negativePrompt] - 负面提示词（可选）
 * @param {number} [params.width] - 图片宽度（默认512）
 * @param {number} [params.height] - 图片高度（默认512）
 * @returns {Promise<{fileId: number, fileName: string, fileUrl: string, fileSize: number, width: number, height: number}>}
 */
export async function generateImage({ prompt, negativePrompt, width, height }) {
  const baseUrl = import.meta.env.VITE_APP_BASE_API || ''
  const token = localStorage.getItem('m78_token')

  const response = await fetch(`${baseUrl}/chat/generate-image`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
    body: JSON.stringify({ prompt, negativePrompt, width, height }),
  })

  if (!response.ok) {
    const text = await response.text()
    throw new Error(`图片生成失败: HTTP ${response.status} ${text}`)
  }

  return response.json()
}
