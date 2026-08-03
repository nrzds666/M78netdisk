import axios from 'axios'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { getToken, getRefreshToken, setToken, setRefreshToken, clearAuth } from '@/utils/auth'
import cache from '@/plugins/cache'
import { refreshToken as apiRefreshToken } from '@/api/user'

// 是否正在显示重新登录弹窗
let isRelogin = { show: false }

// Token 刷新状态
let isRefreshing = false
let refreshFailed = false
let pendingRequests = []

/**
 * 取消所有挂起的请求（页面关闭时调用）
 */
export function cancelAllPendingRequests() {
  pendingRequests.forEach(({ config, reject }) => {
    if (reject) {
      reject(new axios.Cancel('页面关闭，取消请求'))
    }
  })
  pendingRequests = []
}

// 页面关闭/刷新时自动取消所有活跃请求，避免 401 风暴
window.addEventListener('beforeunload', () => {
  cancelAllPendingRequests()
})

/**
 * 刷新 token，成功后重试挂起的请求
 */
async function tryRefreshToken() {
  const refreshTokenValue = getRefreshToken()
  if (!refreshTokenValue) return false

  try {
    const res = await axios.post(
      (import.meta.env.VITE_APP_BASE_API || '') + '/users/refresh',
      null,
      { headers: { 'X-Refresh-Token': refreshTokenValue } }
    )
    // 后端可能返回 HTTP 200 但 code !== 200（如 BizException），必须检查
    if (res.data?.code !== 200) {
      throw new Error(res.data?.msg || '刷新令牌失败')
    }
    const body = res.data
    if (!body.data?.accessToken) {
      clearAuth()
      setTimeout(() => { window.location.href = '/login' }, 200)
      return false
    }
    setToken(body.data.accessToken)
    setRefreshToken(body.data.refreshToken)
    return true
  } catch {
    clearAuth()
    // 刷新失败直接跳登录页，不依赖弹框
    setTimeout(() => { window.location.href = '/login' }, 200)
    return false
  }
}

/**
 * 执行挂起的请求重试 — 修复：重试时重新读取最新 Token
 */
function retryPendingRequests() {
  const requests = [...pendingRequests]
  pendingRequests = []
  requests.forEach(({ config }) => {
    // 重新读取最新 Token 并添加到 header
    config.headers['Authorization'] = 'Bearer ' + getToken()
    // 通过原始 axios 实例重试
    service(config).catch(() => {})
  })
}

/**
 * 拒绝所有挂起的请求
 */
function rejectPendingRequests(error) {
  const requests = [...pendingRequests]
  pendingRequests = []
  requests.forEach(({ config, reject }) => {
    if (reject) reject(error)
  })
}

const service = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json;charset=utf-8' },
  paramsSerializer: {
    serialize: (params) => {
      const parts = []
      for (const [key, val] of Object.entries(params)) {
        if (val === undefined || val === null) continue
        if (Array.isArray(val)) {
          val.forEach(v => parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(v)}`))
        } else {
          parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(val)}`)
        }
      }
      return parts.join('&')
    }
  }
})

// ==================== 请求拦截器 ====================
service.interceptors.request.use(
  (config) => {
    // Token
    const isToken = (config.headers || {}).isToken === false
    if (getToken() && !isToken) {
      config.headers['Authorization'] = 'Bearer ' + getToken()
    }

    // 防重复提交（仅 POST/PUT，跳过 FormData 上传）
    const isRepeatSubmit = (config.headers || {}).repeatSubmit === false
    if (!isRepeatSubmit && (config.method === 'post' || config.method === 'put')) {
      // FormData 序列化总是 "{}"，跳过防重复检查
      if (config.data instanceof FormData) return config

      const requestObj = {
        url: config.url,
        data: typeof config.data === 'object' ? JSON.stringify(config.data) : config.data,
        time: new Date().getTime()
      }
      const requestSize = new Blob([JSON.stringify(requestObj)]).size
      if (requestSize >= 5 * 1024 * 1024) return config // 大文件跳过

      const sessionObj = cache.session.getJSON('sessionObj')
      if (!sessionObj) {
        cache.session.setJSON('sessionObj', requestObj)
      } else {
        const interval = 1000
        if (
          sessionObj.data === requestObj.data &&
          requestObj.time - sessionObj.time < interval &&
          sessionObj.url === requestObj.url
        ) {
          const msg = '数据正在处理，请勿重复提交'
          console.warn(`[${sessionObj.url}]: ${msg}`)
          return Promise.reject(new Error(msg))
        }
        cache.session.setJSON('sessionObj', requestObj)
      }
    }
    return config
  },
  (error) => Promise.reject(error)
)

// ==================== 响应拦截器 ====================
service.interceptors.response.use(
  (response) => {
    // 二进制数据（下载）直接返回
    if (response.request.responseType === 'blob' || response.request.responseType === 'arraybuffer') {
      return response.data
    }

    const res = response.data   // R<T> { code, msg, data }

    // 未登录 / token 过期 → 尝试静默刷新
    if (res.code === 401) {
      // 刷新已失败过 → 直接跳登录页，不再尝试
      if (refreshFailed) {
        window.location.href = '/login'
        return Promise.reject(new Error(res.msg || '请重新登录'))
      }
      if (!isRefreshing) {
        isRefreshing = true
        tryRefreshToken().then((ok) => {
          if (ok) {
            retryPendingRequests()
          } else {
            refreshFailed = true
            rejectPendingRequests(new Error(res.msg || '请重新登录'))
          }
          isRefreshing = false
        })
      }
      // 将当前请求加入等待队列
      return new Promise((resolve, reject) => {
        pendingRequests.push({
          config: response.config,
          resolve,
          reject
        })
      })
    }

    // 无权限
    if (res.code === 403) {
      ElMessage({ message: res.msg || '无权限访问', type: 'warning' })
      return Promise.reject(new Error(res.msg || '无权限访问'))
    }

    // 服务端错误
    if (res.code === 500) {
      ElMessage({ message: res.msg || '服务器内部错误', type: 'error' })
      return Promise.reject(new Error(res.msg || '服务器内部错误'))
    }

    // 业务错误（400 等）
    if (res.code !== 200) {
      ElNotification.error({ title: '操作失败', message: res.msg })
      return Promise.reject(new Error(res.msg))
    }

    return Promise.resolve(res)
  },
  (error) => {
    // axios 取消请求（暂停/刷新）静默返回，不弹错误
    if (axios.isCancel(error) || error.code === 'ERR_CANCELED' || error.name === 'CanceledError') {
      return Promise.reject(error)
    }

    // HTTP 401（拦截器直接返回的）→ 尝试静默刷新
    if (error.response && error.response.status === 401) {
      // 刷新已失败过 → 直接拒绝，不再尝试
      if (refreshFailed) {
        return Promise.reject(error)
      }

      const msg = error.response.data?.msg || ''
      // refresh token 失效 → 直接弹登录框，不刷新
      if (msg.indexOf('刷新令牌') !== -1) {
        refreshFailed = true
        rejectPendingRequests(new Error(msg))
        showReloginDialog()
        return Promise.reject(error)
      }
      // access token 失效 → 走静默刷新流程
      if (!isRefreshing) {
        isRefreshing = true
        tryRefreshToken().then((ok) => {
          if (ok) {
            retryPendingRequests()
          } else {
            refreshFailed = true
            rejectPendingRequests(new Error(msg || '请重新登录'))
          }
          isRefreshing = false
        }).catch(() => {
          refreshFailed = true
          isRefreshing = false
        })
      }
      // 将当前请求加入等待队列
      return new Promise((resolve, reject) => {
        pendingRequests.push({
          config: error.config,
          resolve,
          reject
        })
      })
    }

    let message = error.message || ''
    if (message === 'Network Error') {
      message = '后端接口连接异常'
    } else if (message.includes('timeout')) {
      message = '系统接口请求超时'
    } else if (message.includes('Request failed with status code')) {
      const status = message.substr(message.length - 3)
      const statusMap = {
        401: '登录已过期，请重新登录',
        403: '无权限访问',
        404: '请求的资源不存在',
        500: '服务器内部错误'
      }
      message = statusMap[status] || `系统接口${status}异常`
      if (status === '401') {
        showReloginDialog()
      }
    }
    ElMessage({ message, type: 'error', duration: 5000 })
    return Promise.reject(error)
  }
)

function showReloginDialog() {
  if (!isRelogin.show) {
    isRelogin.show = true
    ElMessageBox.confirm('登录状态已过期，请重新登录', '系统提示', {
      confirmButtonText: '重新登录',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      isRelogin.show = false
      clearAuth()
      window.location.href = '/login'
    }).catch(() => {
      isRelogin.show = false
    })
  }
}

export default service
