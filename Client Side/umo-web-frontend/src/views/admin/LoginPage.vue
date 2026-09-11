<script setup>
import { ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useRouter, useRoute } from 'vue-router'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function handleLogin() {
  loading.value = true
  error.value = ''
  try {
    await auth.login(username.value, password.value)
    const redirect = route.query.redirect || '/secret-admin/contents'
    router.push(redirect)
  } catch (e) {
    error.value = e.response?.data?.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-gray-100">
    <form @submit.prevent="handleLogin" class="bg-white p-8 rounded-lg shadow-md w-96">
      <h1 class="text-xl font-bold mb-6">管理员登录</h1>
      <div v-if="error" class="mb-4 p-3 bg-red-100 text-red-700 rounded">{{ error }}</div>
      <label class="block mb-4">
        <span class="text-sm text-gray-600">用户名</span>
        <input v-model="username" type="text" required class="mt-1 block w-full border rounded px-3 py-2" />
      </label>
      <label class="block mb-6">
        <span class="text-sm text-gray-600">密码</span>
        <input v-model="password" type="password" required class="mt-1 block w-full border rounded px-3 py-2" />
      </label>
      <button type="submit" :disabled="loading"
        class="w-full py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50">
        {{ loading ? '登录中...' : '登录' }}
      </button>
    </form>
  </div>
</template>
