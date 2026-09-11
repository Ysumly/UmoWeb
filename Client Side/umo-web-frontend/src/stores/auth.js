import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as apiLogin } from '@/api/admin'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || null)

  async function login(username, password) {
    const res = await apiLogin({ username, password })
    token.value = res.data.token
    localStorage.setItem('token', res.data.token)
  }

  function logout() {
    token.value = null
    localStorage.removeItem('token')
  }

  return { token, login, logout }
})
