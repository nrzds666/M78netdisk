import axios from 'axios'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { getToken, clearAuth } from '@/utils/auth'
import cache from '@/plugins/cache'

// 是否正在显示重新登录弹窗
let isRelogin = { show: false }

const service = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json;charset=utf-8' },
  paramsSerializer: {
    serialize: (params) => {
      const parts = []
      for (const [key, val] of Object.entries(params)) {
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

    // 未登录 / token 过期
    if (res.code === 401) {
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
      return Promise.reject(new Error(res.msg || '请重新登录'))
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
        clearAuth()
        if (!isRelogin.show) {
          isRelogin.show = true
          ElMessageBox.confirm('登录状态已过期，请重新登录', '系统提示', {
            confirmButtonText: '重新登录',
            cancelButtonText: '取消',
            type: 'warning'
          }).then(() => {
            isRelogin.show = false
            window.location.href = '/login'
          }).catch(() => {
            isRelogin.show = false
          })
        }
      }
    }
    ElMessage({ message, type: 'error', duration: 5000 })
    return Promise.reject(error)
  }
)

export default service
