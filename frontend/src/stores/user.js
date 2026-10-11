import { defineStore } from 'pinia'
import { loginApi } from '@/api/auth'
import { getCurrentUserApi, updateNicknameApi } from '@/api/user'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userInfo: null,
  }),

  getters: {
    isLoggedIn: (state) => Boolean(state.token),
  },

  actions: {
    async login(loginForm) {
      const data = await loginApi(loginForm)

      this.token = data.token
      this.userInfo = data.user
      localStorage.setItem('token', data.token)
    },

    async fetchCurrentUser() {
      const user = await getCurrentUserApi()

      this.userInfo = user
      return user
    },

    async updateNickname(nickname) {
      const user = await updateNicknameApi({
        nickname,
      })

      this.userInfo = user
      return user
    },

    logout() {
      this.token = ''
      this.userInfo = null
      localStorage.removeItem('token')
      sessionStorage.removeItem('moodecho.homeDraft')
    },
  },
})
