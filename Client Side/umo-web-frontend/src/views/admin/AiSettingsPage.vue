<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'

import { getAiModes } from '@/api/admin'
import ContentState from '@/components/public/ContentState.vue'
import {
  AI_VALIDATION_PROFILES,
  createEmptyAiModeForm,
  modeToForm,
  sortAiModes,
} from '@/utils/aiModeSettings'
import { getApiErrorMessage } from '@/utils/apiError'

const status = ref('loading')
const errorMessage = ref('')
const modes = ref([])
const selectedId = ref(null)
const creating = ref(false)
const initialSnapshot = ref('')
const form = reactive(createEmptyAiModeForm())

const selectedMode = computed(() => {
  return modes.value.find((mode) => mode.id === selectedId.value) || null
})

const dirty = computed(() => {
  return Boolean(initialSnapshot.value)
    && JSON.stringify(form) !== initialSnapshot.value
})

function snapshotForm() {
  initialSnapshot.value = JSON.stringify(form)
}

function resetForm(mode = null) {
  Object.assign(form, mode ? modeToForm(mode) : createEmptyAiModeForm())
  snapshotForm()
}

function confirmDiscard(message) {
  return !dirty.value || window.confirm(message)
}

function selectMode(mode) {
  if (mode.id === selectedId.value && !creating.value) {
    return
  }
  if (!confirmDiscard('当前模式修改尚未保存，确定切换模式吗？')) {
    return
  }
  creating.value = false
  selectedId.value = mode.id
  resetForm(mode)
}

function openCreate() {
  if (!confirmDiscard('当前模式修改尚未保存，确定新建模式吗？')) {
    return
  }
  creating.value = true
  selectedId.value = null
  resetForm()
}

async function loadModes() {
  status.value = 'loading'
  errorMessage.value = ''
  try {
    const response = await getAiModes()
    modes.value = sortAiModes(response.data || [])
    if (!modes.value.length) {
      selectedId.value = null
      creating.value = false
      resetForm()
      status.value = 'empty'
      return
    }

    const nextMode = modes.value.find((mode) => mode.id === selectedId.value)
      || modes.value[0]
    creating.value = false
    selectedId.value = nextMode.id
    resetForm(nextMode)
    status.value = 'success'
  } catch (error) {
    status.value = 'error'
    errorMessage.value = getApiErrorMessage(error, 'AI 模式加载失败')
  }
}

function handleBeforeUnload(event) {
  if (!dirty.value) {
    return
  }
  event.preventDefault()
  event.returnValue = ''
}

onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  loadModes()
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

onBeforeRouteLeave(() => {
  return confirmDiscard('当前模式修改尚未保存，确定离开页面吗？')
})
</script>

<template>
  <section class="admin-page">
    <header class="admin-page__header">
      <div>
        <span class="admin-page__eyebrow">AI / 转换模式</span>
        <h1>AI 设置</h1>
        <p>维护文章转换模式、系统提示词和版本。</p>
      </div>
      <button class="button button--primary" type="button" @click="openCreate">
        新建模式
      </button>
    </header>

    <div v-if="errorMessage && status !== 'error'" class="admin-notice" role="alert">
      {{ errorMessage }}
    </div>

    <ContentState
      v-if="status === 'loading'"
      state="loading"
      title="正在读取 AI 模式"
    />

    <ContentState
      v-else-if="status === 'error'"
      state="error"
      title="AI 模式加载失败"
      :message="errorMessage"
      action-label="重新加载"
      @retry="loadModes"
    />

    <ContentState
      v-else-if="status === 'empty'"
      state="empty"
      title="还没有转换模式"
      message="创建第一个模式后即可在文章编辑器中调用。"
      action-label="新建模式"
      @retry="openCreate"
    />

    <div v-else class="admin-ai-layout">
      <section class="admin-management-panel admin-ai-mode-list">
        <header>
          <div>
            <span class="admin-page__eyebrow">CATALOG / 模式目录</span>
            <h2>转换模式</h2>
          </div>
          <span>{{ modes.length }} 项</span>
        </header>

        <div class="admin-table-wrap">
          <table class="admin-table admin-ai-mode-table">
            <thead>
              <tr>
                <th>名称</th>
                <th>策略</th>
                <th>状态</th>
                <th>排序</th>
                <th>版本</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="mode in modes"
                :key="mode.id"
                :class="{ 'is-selected': !creating && mode.id === selectedId }"
              >
                <td>
                  <button type="button" @click="selectMode(mode)">
                    <strong>{{ mode.name }}</strong>
                    <small>{{ mode.modeKey }}</small>
                  </button>
                </td>
                <td>{{ mode.validationProfile }}</td>
                <td>
                  <span
                    class="admin-status"
                    :class="mode.enabled ? 'admin-status--published' : 'admin-status--draft'"
                  >
                    {{ mode.enabled ? '启用' : '停用' }}
                  </span>
                </td>
                <td>{{ mode.sortOrder }}</td>
                <td>v{{ mode.currentVersion }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="admin-management-panel admin-ai-editor">
        <header>
          <div>
            <span class="admin-page__eyebrow">
              {{ creating ? 'NEW / 新建' : 'EDIT / 编辑' }}
            </span>
            <h2>{{ creating ? '新转换模式' : selectedMode?.name || '转换模式' }}</h2>
          </div>
          <span v-if="selectedMode">当前版本 v{{ selectedMode.currentVersion }}</span>
        </header>

        <form class="admin-form-grid" @submit.prevent>
          <label class="admin-field">
            <span>模式标识</span>
            <input
              v-model.trim="form.modeKey"
              type="text"
              maxlength="64"
              :disabled="!creating"
              placeholder="CUSTOM_MODE"
            />
          </label>

          <div class="admin-form-grid admin-form-grid--two">
            <label class="admin-field">
              <span>模式名称</span>
              <input v-model="form.name" type="text" maxlength="100" />
            </label>
            <label class="admin-field">
              <span>排序值</span>
              <input v-model.number="form.sortOrder" type="number" min="-9999" max="9999" />
            </label>
          </div>

          <label class="admin-field">
            <span>模式说明</span>
            <textarea v-model="form.description" maxlength="500" rows="3" />
          </label>

          <div class="admin-form-grid admin-form-grid--two">
            <label class="admin-field">
              <span>校验策略</span>
              <select v-model="form.validationProfile">
                <option
                  v-for="profile in AI_VALIDATION_PROFILES"
                  :key="profile.value"
                  :value="profile.value"
                >
                  {{ profile.label }}
                </option>
              </select>
            </label>
            <label class="admin-field admin-ai-enabled">
              <span>启用状态</span>
              <input v-model="form.enabled" type="checkbox" />
              <em>{{ form.enabled ? '启用' : '停用' }}</em>
            </label>
          </div>

          <label class="admin-field">
            <span>系统提示词</span>
            <textarea
              v-model="form.systemPrompt"
              class="admin-ai-prompt"
              maxlength="20000"
              rows="14"
              spellcheck="false"
            />
          </label>
        </form>
      </section>
    </div>
  </section>
</template>
