import { defineStore } from 'pinia'
import { login as apiLogin, register as apiRegister, getUserInfo } from '@/api/user'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('m78_token') || '',
    userInfo: null
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
    username: (state) => state.userInfo?.username || ''
  },

  actions: {
    async login(username, password, captchaKey, captchaCode) {
      const res = await apiLogin(username, password, captchaKey, captchaCode)
      this.token = res.data.accessToken
      localStorage.setItem('m78_token', this.token)
      localStorage.setItem('m78_refresh_token', res.data.refreshToken)
      await this.fetchUserInfo()
    },

    async register(username, password, email, captchaKey, captchaCode) {
      const res = await apiRegister(username, password, email, captchaKey, captchaCode)
      this.token = res.data.accessToken
      localStorage.setItem('m78_token', this.token)
      localStorage.setItem('m78_refresh_token', res.data.refreshToken)
      await this.fetchUserInfo()
    },

    async fetchUserInfo() {
      try {
        const res = await getUserInfo()
        this.userInfo = res.data
        return true
      } catch {
        this.userInfo = null
        return false
      }
    },

    logout() {
      this.token = ''
      this.userInfo = null
      localStorage.removeItem('m78_token')
      localStorage.removeItem('m78_refresh_token')
    }
  }
})
