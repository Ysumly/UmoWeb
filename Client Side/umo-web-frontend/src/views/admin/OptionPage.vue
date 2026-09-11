<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'

import { getOptions, updateOption } from '@/api/admin'
import MarkdownArticle from '@/components/public/MarkdownArticle.vue'
import ContentState from '@/components/public/ContentState.vue'
import { useSiteStore } from '@/stores/site'
import {
  SITE_OPTION_FIELDS,
  diffSiteOptions,
  formatOptionSaveFailure,
  validateSiteOptions,
} from '@/utils/adminManagement'
import { getApiErrorMessage } from '@/utils/apiError'

const site = useSiteStore()
const status = ref('loading')
const errorMessage = ref('')
const actionMessage = ref('')
const saving = ref(false)
const errors = ref({})
const original = ref({})
const aboutPane = ref('editor')
const projectPane = ref('editor')

const form = reactive(Object.fromEntries(
  SITE_OPTION_FIELDS.map(({ key }) => [key, '']),
))

const changes = computed(() => diffSiteOptions(original.value, form))
const dirty = computed(() => changes.value.length > 0)

async function loadOptions() {
  status.value = 'loading'
  errorMessage.value = ''
  try {
    const response = await getOptions()
    const options = response.data || {}
    for (const { key } of SITE_OPTION_FIELDS) {
      form[key] = options[key] || ''
    }
    original.value = { ...form }
    status.value = 'success'
  } catch (error) {
    status.value = 'error'
    errorMessage.value = getApiErrorMessage(error, '站点设置加载失败')
  }
}

async function handleSubmit() {
  errors.value = validateSiteOptions(form)
  actionMessage.value = ''
  errorMessage.value = ''
  if (Object.keys(errors.value).length) {
    return
  }

  const pending = diffSiteOptions(original.value, form)
  if (!pending.length) {
    actionMessage.value = '没有需要保存的修改'
    return
  }

  saving.value = true
  const savedKeys = []

  try {
    for (let index = 0; index < pending.length; index += 1) {
      const change = pending[index]
      try {
        await updateOption(change.key, change.value)
        savedKeys.push(change.key)
        original.value[change.key] = change.value
      } catch (error) {
        const remainingKeys = pending.slice(index + 1).map((item) => item.key)
        errorMessage.value = formatOptionSaveFailure({
          savedKeys,
          failedKey: change.key,
          remainingKeys,
        })
        if (savedKeys.length) {
          await refreshSiteCache()
        }
        return
      }
    }

    original.value = { ...form }
    actionMessage.value = '站点设置已保存'
    await refreshSiteCache()
  } finally {
    saving.value = false
  }
}

async function refreshSiteCache() {
  try {
    await site.load(true)
  } catch {
    actionMessage.value = '设置已保存，公开站点头部将在下次加载时刷新'
  }
}

function handleBeforeUnload(event) {
  if (!dirty.value) {
    return
  }
  event.preventDefault()
  event.returnValue = ''
}

onBeforeRouteLeave(() => {
  if (!dirty.value) {
    return true
  }
  return window.confirm('站点设置尚未保存，确定离开吗？')
})

onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  loadOptions()
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})
</script>

<template>
  <section class="admin-page">
    <header class="admin-page__header admin-editor-header">
      <div>
        <span class="admin-page__eyebrow">SITE / 站点设置</span>
        <h1>站点设置</h1>
        <p>调整站点名称、副标题和固定页面内容。</p>
      </div>
      <button
        class="button button--primary"
        type="button"
        :disabled="saving || status === 'loading'"
        @click="handleSubmit"
      >
        {{ saving ? '保存中...' : '保存全部修改' }}
      </button>
    </header>

    <div v-if="actionMessage" class="admin-notice admin-notice--success" role="status">
      {{ actionMessage }}
      <button type="button" aria-label="关闭提示" @click="actionMessage = ''">关闭</button>
    </div>
    <div v-if="errorMessage && status !== 'error'" class="admin-notice" role="alert">
      {{ errorMessage }}
      <button type="button" aria-label="关闭提示" @click="errorMessage = ''">关闭</button>
    </div>

    <ContentState
      v-if="status === 'loading'"
      state="loading"
      title="正在读取站点设置"
    />

    <ContentState
      v-else-if="status === 'error'"
      state="error"
      title="站点设置加载失败"
      :message="errorMessage"
      action-label="重新加载"
      @retry="loadOptions"
    />

    <form v-else class="admin-option-form" @submit.prevent="handleSubmit">
      <section class="admin-management-panel">
        <header>
          <div>
            <span class="admin-page__eyebrow">IDENTITY / 站点信息</span>
            <h2>名称与介绍</h2>
          </div>
        </header>

        <div class="admin-form-grid admin-form-grid--two">
          <label class="admin-field">
            <span>站点标题 <b>*</b></span>
            <input v-model="form.site_title" type="text" required />
            <small v-if="errors.site_title">{{ errors.site_title }}</small>
          </label>

          <label class="admin-field">
            <span>站点副标题 <b>*</b></span>
            <input v-model="form.site_subtitle" type="text" required />
            <small v-if="errors.site_subtitle">{{ errors.site_subtitle }}</small>
          </label>
        </div>
      </section>

      <section class="admin-management-panel">
        <header>
          <div>
            <span class="admin-page__eyebrow">ABOUT / 关于</span>
            <h2>About 页面</h2>
          </div>
        </header>

        <div class="admin-mobile-tabs" role="group" aria-label="About 页面编辑区切换">
          <button
            type="button"
            :aria-pressed="aboutPane === 'editor'"
            @click="aboutPane = 'editor'"
          >
            编辑
          </button>
          <button
            type="button"
            :aria-pressed="aboutPane === 'preview'"
            @click="aboutPane = 'preview'"
          >
            预览
          </button>
        </div>

        <div class="admin-editor-workspace admin-option-workspace">
          <div
            class="admin-editor-pane admin-editor-pane--input"
            :class="{ 'is-mobile-active': aboutPane === 'editor' }"
          >
            <textarea v-model="form.about_page" spellcheck="false" aria-label="About 页面 Markdown" />
          </div>
          <div
            class="admin-editor-pane admin-editor-pane--preview"
            :class="{ 'is-mobile-active': aboutPane === 'preview' }"
          >
            <MarkdownArticle :source="form.about_page" />
          </div>
        </div>
        <small v-if="errors.about_page" class="admin-option-error">{{ errors.about_page }}</small>
      </section>

      <section class="admin-management-panel">
        <header>
          <div>
            <span class="admin-page__eyebrow">PROJECT / 项目</span>
            <h2>Project 页面</h2>
          </div>
        </header>

        <div class="admin-mobile-tabs" role="group" aria-label="Project 页面编辑区切换">
          <button
            type="button"
            :aria-pressed="projectPane === 'editor'"
            @click="projectPane = 'editor'"
          >
            编辑
          </button>
          <button
            type="button"
            :aria-pressed="projectPane === 'preview'"
            @click="projectPane = 'preview'"
          >
            预览
          </button>
        </div>

        <div class="admin-editor-workspace admin-option-workspace">
          <div
            class="admin-editor-pane admin-editor-pane--input"
            :class="{ 'is-mobile-active': projectPane === 'editor' }"
          >
            <textarea
              v-model="form.project_page"
              spellcheck="false"
              aria-label="Project 页面 Markdown"
            />
          </div>
          <div
            class="admin-editor-pane admin-editor-pane--preview"
            :class="{ 'is-mobile-active': projectPane === 'preview' }"
          >
            <MarkdownArticle :source="form.project_page" />
          </div>
        </div>
        <small v-if="errors.project_page" class="admin-option-error">{{ errors.project_page }}</small>
      </section>

      <div class="admin-option-form__footer">
        <span>{{ dirty ? `有 ${changes.length} 项修改待保存` : '当前没有未保存修改' }}</span>
        <button class="button button--primary" type="submit" :disabled="saving">
          {{ saving ? '保存中...' : '保存全部修改' }}
        </button>
      </div>
    </form>
  </section>
</template>
