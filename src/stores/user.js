import { defineStore } from 'pinia'
import { login as apiLogin, register as apiRegister, getUserInfo } from '@/api/user'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userInfo: null
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
    username: (state) => state.userInfo?.username || ''
  },

  actions: {
    async login(username, password, captchaKey, captchaCode) {
      const res = await apiLogin(username, password, captchaKey, captchaCode)
      this.token = res.data.data.accessToken
      localStorage.setItem('token', this.token)
      await this.fetchUserInfo()
    },

    async register(username, password, email, captchaKey, captchaCode) {
      const res = await apiRegister(username, password, email, captchaKey, captchaCode)
      this.token = res.data.data.accessToken
      localStorage.setItem('token', this.token)
      await this.fetchUserInfo()
    },

    async fetchUserInfo() {
      try {
        const res = await getUserInfo()
        this.userInfo = res.data.data
      } catch {
        // silently fail — token may be stale
      }
    },

    logout() {
      this.token = ''
      this.userInfo = null
      localStorage.removeItem('token')
    }
  }
})
