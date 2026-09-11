<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { changePassword } from '@/api/admin'
import { useAuthStore } from '@/stores/auth'
import { validatePasswordForm } from '@/utils/adminManagement'
import { getApiErrorMessage } from '@/utils/apiError'

const auth = useAuthStore()
const router = useRouter()
const saving = ref(false)
const errorMessage = ref('')
const errors = ref({})
const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

async function handleSubmit() {
  errors.value = validatePasswordForm(form)
  errorMessage.value = ''
  if (Object.keys(errors.value).length) {
    return
  }

  saving.value = true
  try {
    await changePassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
    })
    auth.logout()
    await router.push({
      name: 'login',
      query: { changed: '1' },
    })
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '密码修改失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="admin-page">
    <header class="admin-page__header">
      <div>
        <span class="admin-page__eyebrow">SECURITY / 账号安全</span>
        <h1>修改密码</h1>
        <p>修改成功后当前登录会立即失效，需要使用新密码重新登录。</p>
      </div>
    </header>

    <div v-if="errorMessage" class="admin-notice" role="alert">
      {{ errorMessage }}
      <button type="button" aria-label="关闭提示" @click="errorMessage = ''">关闭</button>
    </div>

    <section class="admin-management-panel admin-password-panel">
      <header>
        <div>
          <span class="admin-page__eyebrow">PASSWORD / 密码</span>
          <h2>更新管理员密码</h2>
        </div>
      </header>

      <form class="admin-form-grid" @submit.prevent="handleSubmit">
        <label class="admin-field">
          <span>旧密码 <b>*</b></span>
          <input
            v-model="form.oldPassword"
            type="password"
            maxlength="200"
            autocomplete="current-password"
            required
          />
          <small v-if="errors.oldPassword">{{ errors.oldPassword }}</small>
        </label>

        <label class="admin-field">
          <span>新密码 <b>*</b></span>
          <input
            v-model="form.newPassword"
            type="password"
            maxlength="200"
            autocomplete="new-password"
            required
          />
          <small v-if="errors.newPassword">{{ errors.newPassword }}</small>
          <small v-else>至少 6 位，最多 200 位。</small>
        </label>

        <label class="admin-field">
          <span>确认新密码 <b>*</b></span>
          <input
            v-model="form.confirmPassword"
            type="password"
            maxlength="200"
            autocomplete="new-password"
            required
          />
          <small v-if="errors.confirmPassword">{{ errors.confirmPassword }}</small>
        </label>

        <div class="admin-management-panel__actions">
          <button class="button button--primary" type="submit" :disabled="saving">
            {{ saving ? '修改中...' : '修改密码' }}
          </button>
        </div>
      </form>
    </section>
  </section>
</template>
