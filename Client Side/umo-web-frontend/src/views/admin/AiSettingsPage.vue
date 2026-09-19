<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'

import {
  copyAiMode,
  createAiMode,
  getAiModeVersions,
  getAiModes,
  rollbackAiMode,
  updateAiMode,
} from '@/api/admin'
import ContentState from '@/components/public/ContentState.vue'
import {
  AI_VALIDATION_PROFILES,
  buildAiModeCopyPayload,
  buildAiModeCreatePayload,
  buildAiModeUpdatePayload,
  createEmptyAiModeForm,
  formatModeVersion,
  modeToForm,
  sortAiModes,
  validateAiModeForm,
} from '@/utils/aiModeSettings'
import { getApiErrorMessage } from '@/utils/apiError'

const status = ref('loading')
const errorMessage = ref('')
const actionMessage = ref('')
const conflictMessage = ref('')
const errors = ref({})
const modes = ref([])
const versions = ref([])
const versionsStatus = ref('idle')
const versionsError = ref('')
const previewVersion = ref(null)
const selectedId = ref(null)
const editorMode = ref('edit')
const saving = ref(false)
const rollingBack = ref(false)
const initialSnapshot = ref('')
const form = reactive(createEmptyAiModeForm())
let versionsRequestId = 0

const selectedMode = computed(() => {
  return modes.value.find((mode) => mode.id === selectedId.value) || null
})

const creating = computed(() => editorMode.value === 'create')
const copying = computed(() => editorMode.value === 'copy')
const editorTitle = computed(() => {
  if (creating.value) {
    return '新转换模式'
  }
  if (copying.value) {
    return `复制 ${selectedMode.value?.name || '模式'}`
  }
  return selectedMode.value?.name || '转换模式'
})

function validationProfileLabel(value) {
  return AI_VALIDATION_PROFILES.find((profile) => profile.value === value)?.label || value
}

const dirty = computed(() => {
  return Boolean(initialSnapshot.value)
    && JSON.stringify(form) !== initialSnapshot.value
})

function snapshotForm() {
  initialSnapshot.value = JSON.stringify(form)
}

function resetForm(mode = null) {
  Object.assign(form, mode ? modeToForm(mode) : createEmptyAiModeForm())
  errors.value = {}
  snapshotForm()
}

function confirmDiscard(message) {
  return !dirty.value || window.confirm(message)
}

function selectMode(mode) {
  if (mode.id === selectedId.value && editorMode.value === 'edit') {
    return true
  }
  if (!confirmDiscard('当前模式修改尚未保存，确定切换模式吗？')) {
    return false
  }
  editorMode.value = 'edit'
  selectedId.value = mode.id
  resetForm(mode)
  conflictMessage.value = ''
  loadVersions(mode.id)
  return true
}

function openCreate() {
  if (!confirmDiscard('当前模式修改尚未保存，确定新建模式吗？')) {
    return
  }
  editorMode.value = 'create'
  selectedId.value = null
  resetForm()
  versions.value = []
  previewVersion.value = null
  versionsStatus.value = 'idle'
  status.value = 'success'
  conflictMessage.value = ''
  actionMessage.value = ''
}

function openCopy() {
  if (!selectedMode.value || !confirmDiscard('当前模式修改尚未保存，确定复制模式吗？')) {
    return
  }
  const source = selectedMode.value
  editorMode.value = 'copy'
  previewVersion.value = null
  resetForm({
    ...source,
    modeKey: '',
    name: `${source.name} 副本`,
  })
  conflictMessage.value = ''
  actionMessage.value = ''
}

function discardChanges() {
  if (!confirmDiscard('确定放弃当前未保存的修改吗？')) {
    return
  }
  if (creating.value) {
    const nextMode = modes.value[0] || null
    editorMode.value = nextMode ? 'edit' : 'create'
    selectedId.value = nextMode?.id ?? null
    resetForm(nextMode)
  } else {
    editorMode.value = 'edit'
    resetForm(selectedMode.value)
  }
  conflictMessage.value = ''
}

function upsertMode(mode) {
  const nextModes = modes.value.filter((item) => item.id !== mode.id)
  nextModes.push(mode)
  modes.value = sortAiModes(nextModes)
  status.value = 'success'
}

function applySavedMode(mode, message) {
  upsertMode(mode)
  editorMode.value = 'edit'
  selectedId.value = mode.id
  resetForm(mode)
  actionMessage.value = message
  conflictMessage.value = ''
  errorMessage.value = ''
  loadVersions(mode.id)
}

function handleSaveError(error, { preserveForm = true } = {}) {
  if (error?.response?.status === 409) {
    conflictMessage.value = '提示词已在其他窗口更新，请重新加载后再保存。'
    return
  }
  errorMessage.value = getApiErrorMessage(error, 'AI 模式保存失败')
  if (!preserveForm) {
    resetForm(selectedMode.value)
  }
}

async function saveMode() {
  errors.value = validateAiModeForm(form, { creating: creating.value })
  actionMessage.value = ''
  conflictMessage.value = ''
  errorMessage.value = ''
  if (Object.keys(errors.value).length) {
    return
  }

  saving.value = true
  try {
    let response
    let message
    if (creating.value) {
      response = await createAiMode(buildAiModeCreatePayload(form))
      message = '模式已创建，默认停用'
    } else if (copying.value) {
      response = await copyAiMode(selectedMode.value.id, buildAiModeCopyPayload(form))
      message = '模式已复制，默认停用'
    } else {
      response = await updateAiMode(
        selectedMode.value.id,
        buildAiModeUpdatePayload(selectedMode.value, form),
      )
      message = '模式已保存'
    }
    applySavedMode(response.data, message)
  } catch (error) {
    handleSaveError(error)
  } finally {
    saving.value = false
  }
}

async function toggleMode(mode) {
  if (saving.value) {
    return
  }
  if (!selectMode(mode)) {
    return
  }

  const targetEnabled = !mode.enabled
  const nextForm = {
    ...form,
    enabled: targetEnabled,
  }
  saving.value = true
  actionMessage.value = ''
  conflictMessage.value = ''
  errorMessage.value = ''
  try {
    const response = await updateAiMode(
      mode.id,
      buildAiModeUpdatePayload(mode, nextForm),
    )
    applySavedMode(response.data, targetEnabled ? '模式已启用' : '模式已停用')
  } catch (error) {
    handleSaveError(error)
  } finally {
    saving.value = false
  }
}

async function reloadSelectedMode() {
  await loadModes()
  conflictMessage.value = ''
}

async function loadVersions(modeId = selectedMode.value?.id) {
  const currentRequest = ++versionsRequestId
  previewVersion.value = null
  if (!modeId || editorMode.value !== 'edit') {
    versions.value = []
    versionsStatus.value = 'idle'
    versionsError.value = ''
    return
  }

  versionsStatus.value = 'loading'
  versionsError.value = ''
  try {
    const response = await getAiModeVersions(modeId)
    if (currentRequest !== versionsRequestId) {
      return
    }
    versions.value = response.data || []
    versionsStatus.value = 'success'
  } catch (error) {
    if (currentRequest !== versionsRequestId) {
      return
    }
    versions.value = []
    versionsStatus.value = 'error'
    versionsError.value = getApiErrorMessage(error, '历史版本加载失败')
  }
}

async function rollbackVersion(version) {
  if (
    rollingBack.value
    || conflictMessage.value
    || !selectedMode.value
    || version.versionNo === selectedMode.value.currentVersion
  ) {
    return
  }
  if (!window.confirm(`回滚到版本 ${version.versionNo} 将生成一个新版本，确定继续吗？`)) {
    return
  }

  rollingBack.value = true
  actionMessage.value = ''
  errorMessage.value = ''
  try {
    const response = await rollbackAiMode(
      selectedMode.value.id,
      version.versionNo,
      selectedMode.value.currentVersion,
    )
    applySavedMode(response.data, '已生成新版本')
  } catch (error) {
    handleSaveError(error)
  } finally {
    rollingBack.value = false
  }
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
    editorMode.value = 'edit'
    selectedId.value = nextMode.id
    resetForm(nextMode)
    status.value = 'success'
    loadVersions(nextMode.id)
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
      <button class="button button--primary" type="button" :disabled="saving" @click="openCreate">
        新建模式
      </button>
    </header>

    <div v-if="actionMessage" class="admin-notice admin-notice--success" role="status">
      {{ actionMessage }}
    </div>

    <div v-if="errorMessage && status !== 'error'" class="admin-notice" role="alert">
      {{ errorMessage }}
    </div>

    <div v-if="conflictMessage" class="admin-notice admin-ai-conflict" role="alert">
      <span>{{ conflictMessage }}</span>
      <button type="button" :disabled="saving" @click="reloadSelectedMode">重新加载</button>
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

        <ul class="admin-ai-mode-listbox" aria-label="转换模式">
          <li
            v-for="mode in modes"
            :key="mode.id"
            class="admin-ai-mode-item"
            :class="{ 'is-selected': !creating && mode.id === selectedId }"
          >
            <button
              class="admin-ai-mode-item__select"
              type="button"
              :aria-pressed="!creating && mode.id === selectedId"
              @click="selectMode(mode)"
            >
              <span class="admin-ai-mode-item__heading">
                <strong>{{ mode.name }}</strong>
                <span
                  class="admin-status"
                  :class="mode.enabled ? 'admin-status--published' : 'admin-status--draft'"
                >
                  {{ mode.enabled ? '启用' : '停用' }}
                </span>
              </span>
              <small class="admin-ai-mode-item__key">{{ mode.modeKey }}</small>
              <span class="admin-ai-mode-item__meta">
                <span>{{ validationProfileLabel(mode.validationProfile) }}</span>
                <span>排序 {{ mode.sortOrder }}</span>
                <span>版本 v{{ mode.currentVersion }}</span>
              </span>
            </button>
            <button
              class="admin-ai-mode-item__toggle"
              type="button"
              :disabled="saving"
              @click="toggleMode(mode)"
            >
              {{ mode.enabled ? '停用' : '启用' }}
            </button>
          </li>
        </ul>
      </section>

      <section class="admin-management-panel admin-ai-editor">
        <header>
          <div>
            <span class="admin-page__eyebrow">
              {{ creating ? 'NEW / 新建' : copying ? 'COPY / 复制' : 'EDIT / 编辑' }}
            </span>
            <h2>{{ editorTitle }}</h2>
          </div>
          <span v-if="selectedMode && !creating && !copying">
            当前版本 v{{ selectedMode.currentVersion }}
          </span>
        </header>

        <form class="admin-form-grid" @submit.prevent="saveMode">
          <label class="admin-field">
            <span>模式标识</span>
            <input
              v-model.trim="form.modeKey"
              type="text"
              maxlength="64"
              :disabled="!creating && !copying"
              placeholder="CUSTOM_MODE"
            />
            <small>{{ errors.modeKey }}</small>
          </label>

          <div class="admin-form-grid admin-form-grid--two">
            <label class="admin-field">
              <span>模式名称</span>
              <input v-model="form.name" type="text" maxlength="100" :disabled="saving" />
              <small>{{ errors.name }}</small>
            </label>
            <label class="admin-field">
              <span>排序值</span>
              <input
                v-model.number="form.sortOrder"
                type="number"
                min="-9999"
                max="9999"
                :disabled="saving"
              />
              <small>{{ errors.sortOrder }}</small>
            </label>
          </div>

          <label class="admin-field">
            <span>模式说明</span>
            <textarea
              v-model="form.description"
              maxlength="500"
              rows="3"
              :disabled="copying || saving"
            />
            <small class="admin-field__hint">
              仅用于管理端识别和说明，不会发送给模型。
            </small>
            <small>{{ errors.description }}</small>
          </label>

          <div class="admin-form-grid admin-form-grid--two">
            <label class="admin-field">
              <span>校验策略</span>
              <select v-model="form.validationProfile" :disabled="copying || saving">
                <option
                  v-for="profile in AI_VALIDATION_PROFILES"
                  :key="profile.value"
                  :value="profile.value"
                >
                  {{ profile.label }}
                </option>
              </select>
              <details class="admin-field__details admin-ai-profile-help">
                <summary>查看策略说明</summary>
                <dl>
                  <div v-for="profile in AI_VALIDATION_PROFILES" :key="profile.value">
                    <dt>{{ profile.label }}</dt>
                    <dd>{{ profile.description }}</dd>
                  </div>
                </dl>
              </details>
              <small>{{ errors.validationProfile }}</small>
            </label>
            <label class="admin-field admin-ai-enabled">
              <span>启用状态</span>
              <input v-model="form.enabled" type="checkbox" :disabled="copying || saving" />
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
              :disabled="copying || saving"
            />
            <small class="admin-field__hint">
              会作为 system prompt 发送给大模型；请不要填写敏感信息。
            </small>
            <small>{{ errors.systemPrompt }}</small>
          </label>

          <div class="admin-management-panel__actions admin-ai-form-actions">
            <button
              v-if="selectedMode && !creating && !copying"
              class="button button--quiet"
              type="button"
              :disabled="saving"
              @click="openCopy"
            >
              复制模式
            </button>
            <button
              class="button button--quiet"
              type="button"
              :disabled="saving || !dirty"
              @click="discardChanges"
            >
              放弃修改
            </button>
            <button class="button button--primary" type="submit" :disabled="saving">
              {{
                saving
                  ? '正在保存'
                  : creating
                    ? '创建模式'
                    : copying
                      ? '复制模式'
                      : '保存修改'
              }}
            </button>
          </div>
        </form>

        <details
          v-if="selectedMode && !creating && !copying"
          class="admin-management-panel admin-ai-versions"
          open
        >
          <summary>
            <span>历史版本</span>
            <small>最近 {{ versions.length }} 版</small>
          </summary>

          <div v-if="versionsStatus === 'loading'" class="admin-ai-versions__state">
            正在读取历史版本
          </div>
          <div v-else-if="versionsStatus === 'error'" class="admin-ai-versions__state">
            <span>{{ versionsError }}</span>
            <button type="button" @click="loadVersions()">重新加载</button>
          </div>
          <ol v-else class="admin-ai-version-list">
            <li v-for="version in versions" :key="version.versionNo">
              <div>
                <strong>{{ formatModeVersion(version) }}</strong>
                <span v-if="version.versionNo === selectedMode.currentVersion">当前</span>
                <small>{{ version.validationProfile }}</small>
              </div>
              <div class="admin-ai-version-list__actions">
                <button type="button" @click="previewVersion = version">查看提示词</button>
                <button
                  v-if="version.versionNo !== selectedMode.currentVersion"
                  type="button"
                  :disabled="rollingBack || Boolean(conflictMessage)"
                  @click="rollbackVersion(version)"
                >
                  回滚到此版本
                </button>
              </div>
            </li>
          </ol>

          <div v-if="previewVersion" class="admin-ai-version-preview">
            <header>
              <strong>{{ formatModeVersion(previewVersion) }}</strong>
              <button type="button" @click="previewVersion = null">关闭预览</button>
            </header>
            <pre>{{ previewVersion.systemPrompt }}</pre>
          </div>
        </details>
      </section>
    </div>
  </section>
</template>
