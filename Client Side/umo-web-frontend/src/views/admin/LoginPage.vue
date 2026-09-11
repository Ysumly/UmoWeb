<script setup>
import { ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useRouter, useRoute } from 'vue-router'
import { adminPath } from '@/config/adminPath'
import { getApiErrorMessage } from '@/utils/apiError'

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
    const redirect = route.query.redirect || adminPath('contents')
    router.push(redirect)
  } catch (e) {
    error.value = getApiErrorMessage(e, '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="admin-login">
    <form class="admin-login__panel" @submit.prevent="handleLogin">
      <span class="admin-page__eyebrow">Umo / PRIVATE DESK</span>
      <h1>管理员登录</h1>
      <p>进入内容工作台，管理草稿、发布与站点资料。</p>

      <div v-if="error" class="admin-notice" role="alert">{{ error }}</div>

      <label class="admin-field">
        <span>用户名</span>
        <input v-model="username" type="text" autocomplete="username" required />
      </label>
      <label class="admin-field">
        <span>密码</span>
        <input v-model="password" type="password" autocomplete="current-password" required />
      </label>

      <button class="button button--primary" type="submit" :disabled="loading">
        {{ loading ? '登录中...' : '登录' }}
      </button>
    </form>
  </main>
</template>
