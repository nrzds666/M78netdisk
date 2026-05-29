import { defineStore } from 'pinia'
import { login as apiLogin, getUserInfo } from '@/api/user'

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
    /**
     * Login and persist token
     * @param {string} username
     * @param {string} password
     */
    async login(username, password) {
      const res = await apiLogin(username, password)
      this.token = res.data.token
      localStorage.setItem('token', this.token)
      // fetch user info after login
      await this.fetchUserInfo()
    },

    /**
     * Fetch current user info from server
     */
    async fetchUserInfo() {
      try {
        const res = await getUserInfo()
        this.userInfo = res.data
      } catch {
        // silently fail — token may be stale
      }
    },

    /**
     * Logout: clear token and user info
     */
    logout() {
      this.token = ''
      this.userInfo = null
      localStorage.removeItem('token')
    }
  }
})
