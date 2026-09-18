<script setup>
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue'

import { transformAiContent } from '@/api/admin'
import MarkdownArticle from '@/components/public/MarkdownArticle.vue'
import {
  AI_RESULT_STORAGE_KEY,
  AI_SOURCE_STORAGE_KEY,
  countAiCharacters,
  createAiResultState,
  createAiSourceState,
  getAiMaxInputChars,
  isAiResultEdited,
  parseAiStoredState,
  resolveAiModeKey,
  serializeAiState,
  shouldConfirmResultOverwrite,
  shouldWarnBeforeClose,
  validateAiSource,
} from '@/utils/aiDrawer'
import { getApiErrorMessage } from '@/utils/apiError'

const props = defineProps({
  open: Boolean,
  currentSource: {
    type: String,
    default: '',
  },
  capabilities: {
    type: Object,
    default: null,
  },
})

const emit = defineEmits(['close', 'minimize'])

const drawerRef = ref(null)
const drawerBodyRef = ref(null)
const sourceRef = ref(null)
const resultRef = ref(null)
const source = ref('')
const sourceDirty = ref(false)
const selectedModeKey = ref('')
const resultState = ref(null)
const statusMessage = ref('')
const errorMessage = ref('')
const inFlight = ref(false)
const minimized = ref(false)

let abortController = null
let previousFocus = null
let preservedScroll = {
  body: 0,
  source: 0,
  result: 0,
}

const modes = computed(() => (
  Array.isArray(props.capabilities?.modes) ? props.capabilities.modes : []
))
const maxInputChars = computed(() => getAiMaxInputChars(props.capabilities))
const sourceCharacterCount = computed(() => countAiCharacters(source.value))
const selectedMode = computed(() => (
  modes.value.find((mode) => mode.modeKey === selectedModeKey.value) || null
))
const resultContent = computed(() => resultState.value?.content || '')
const resultEdited = computed(() => isAiResultEdited(resultState.value))

watch(modes, (nextModes) => {
  if (nextModes.some((mode) => mode.modeKey === selectedModeKey.value)) {
    return
  }
  selectedModeKey.value = nextModes[0]?.modeKey || ''
}, { immediate: true })

watch(() => props.open, async (open) => {
  if (open) {
    previousFocus = document.activeElement
    minimized.value = false
    await nextTick()
    sourceRef.value?.focus()
    return
  }

  if (previousFocus instanceof HTMLElement) {
    await nextTick()
    previousFocus.focus()
  }
})

function persistSource() {
  try {
    sessionStorage.setItem(
      AI_SOURCE_STORAGE_KEY,
      serializeAiState(createAiSourceState(source.value)),
    )
    return true
  } catch {
    statusMessage.value = '本地保存不可用，当前会话仍可继续编辑'
    return false
  }
}

function persistResult() {
  if (!resultState.value) {
    return
  }
  try {
    localStorage.setItem(AI_RESULT_STORAGE_KEY, serializeAiState(resultState.value))
  } catch {
    statusMessage.value = '本地保存不可用，当前会话仍可继续编辑'
  }
}

function readStoredState(storage, key, type) {
  try {
    const rawValue = storage.getItem(key)
    if (!rawValue) {
      return null
    }
    const parsed = parseAiStoredState(rawValue, type)
    if (!parsed) {
      storage.removeItem(key)
    }
    return parsed
  } catch {
    statusMessage.value = '本地保存不可用，当前会话仍可继续编辑'
    return null
  }
}

function restoreLocalState() {
  const storedSource = readStoredState(
    sessionStorage,
    AI_SOURCE_STORAGE_KEY,
    'source',
  )
  const storedResult = readStoredState(
    localStorage,
    AI_RESULT_STORAGE_KEY,
    'result',
  )

  if (storedSource) {
    source.value = storedSource.content
    sourceDirty.value = false
  }
  if (storedResult) {
    resultState.value = storedResult
    selectedModeKey.value = resolveAiModeKey(storedResult.modeKey, modes.value)
  }
  if (storedSource || storedResult) {
    const labels = [
      storedSource ? '草稿' : '',
      storedResult ? '结果' : '',
    ].filter(Boolean)
    statusMessage.value = `已恢复本地${labels.join('和')}`
  }
}

function handleSourceInput() {
  sourceDirty.value = !persistSource()
}

function bringCurrentSource() {
  const nextSource = props.currentSource || ''
  if (
    source.value
    && source.value !== nextSource
    && !window.confirm('带入当前正文会覆盖已有 AI 源草稿，确定继续吗？')
  ) {
    return
  }

  source.value = nextSource
  errorMessage.value = ''
  statusMessage.value = '已带入当前正文'
  sourceDirty.value = !persistSource()
}

function mapRequestError(error) {
  if (error?.code === 'ERR_CANCELED' || error?.name === 'CanceledError') {
    return ''
  }

  const status = error?.response?.status
  let message
  if (status === 429) {
    message = 'AI 服务请求过于频繁'
  } else if (status === 502) {
    message = 'AI 服务返回无效响应'
  } else if (status === 503) {
    message = 'AI 服务暂时不可用，文章编辑仍可继续'
  } else if (status === 504) {
    message = 'AI 服务响应超时，可手动重试'
  } else {
    message = getApiErrorMessage(error, 'AI 转换失败')
  }

  const responseMessage = getApiErrorMessage(error, '')
  const requestId = responseMessage.match(/请求 ID:\s*([^)）]+)/)?.[1]?.trim()
  if (requestId && !message.includes(requestId)) {
    return `${message}（请求 ID: ${requestId}）`
  }
  return message
}

async function executeTransform() {
  if (inFlight.value) {
    return
  }

  const validation = validateAiSource(source.value, maxInputChars.value)
  errorMessage.value = ''
  if (!validation.valid) {
    errorMessage.value = validation.message
    return
  }
  if (!selectedMode.value) {
    errorMessage.value = '请选择一个可用的转换模式'
    return
  }
  if (
    shouldConfirmResultOverwrite(resultState.value)
    && !window.confirm('AI 结果已手动修改，重新转换会覆盖当前结果，确定继续吗？')
  ) {
    return
  }

  inFlight.value = true
  statusMessage.value = '正在转换...'
  const controller = new AbortController()
  abortController = controller

  try {
    const response = await transformAiContent(
      {
        modeKey: selectedModeKey.value,
        content: source.value,
      },
      {
        signal: controller.signal,
        timeout: 185_000,
      },
    )
    resultState.value = createAiResultState(response.data)
    persistResult()
    statusMessage.value = `转换完成 · 模式版本 ${response.data?.modeVersion ?? '-'}`
  } catch (error) {
    if (error?.code === 'ERR_CANCELED' || error?.name === 'CanceledError') {
      statusMessage.value = '已取消，已有结果未改变'
    } else {
      errorMessage.value = mapRequestError(error)
      statusMessage.value = '转换失败，已有结果未改变'
    }
  } finally {
    if (abortController === controller) {
      abortController = null
    }
    inFlight.value = false
  }
}

function cancelTransform() {
  if (!abortController) {
    return
  }
  statusMessage.value = '正在取消...'
  abortController.abort()
}

function handleResultInput() {
  persistResult()
}

async function copyResult() {
  try {
    if (navigator.clipboard?.writeText) {
      try {
        await navigator.clipboard.writeText(resultContent.value)
        statusMessage.value = '结果已复制'
        return
      } catch {
        // Fall through to the legacy command for browsers that deny Clipboard API access.
      }
    }

    const textarea = document.createElement('textarea')
    textarea.value = resultContent.value
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    const copied = document.execCommand('copy')
    textarea.remove()
    if (!copied) {
      throw new Error('copy failed')
    }
    statusMessage.value = '结果已复制'
  } catch {
    errorMessage.value = '复制失败，请手动选择结果文本'
  }
}

function minimize() {
  preservedScroll = {
    body: drawerBodyRef.value?.scrollTop || 0,
    source: sourceRef.value?.scrollTop || 0,
    result: resultRef.value?.scrollTop || 0,
  }
  minimized.value = true
  emit('minimize')
}

async function restore() {
  minimized.value = false
  await nextTick()
  await new Promise((resolve) => requestAnimationFrame(resolve))
  if (sourceRef.value) {
    sourceRef.value.focus()
    sourceRef.value.scrollTop = preservedScroll.source
  }
  if (drawerBodyRef.value) {
    drawerBodyRef.value.scrollTop = preservedScroll.body
  }
  if (resultRef.value) {
    resultRef.value.scrollTop = preservedScroll.result
  }
}

function handleDialogKeydown(event) {
  if (event.key === 'Escape') {
    event.preventDefault()
    requestClose()
    return
  }
  if (event.key !== 'Tab' || !drawerRef.value) {
    return
  }

  const focusable = Array.from(drawerRef.value.querySelectorAll(
    'button:not([disabled]), textarea:not([disabled]), select:not([disabled]), '
      + 'a[href], [tabindex]:not([tabindex="-1"])',
  )).filter((element) => element.getClientRects().length > 0)
  if (!focusable.length) {
    event.preventDefault()
    return
  }

  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

function requestClose() {
  if (
    shouldWarnBeforeClose({
      sourceState: { dirty: sourceDirty.value },
      inFlight: inFlight.value,
    })
    && !window.confirm(inFlight.value
      ? '取消正在进行的 AI 转换并关闭吗？'
      : 'AI 源草稿尚未保存，关闭后可能无法恢复，确定关闭吗？')
  ) {
    return
  }

  if (inFlight.value) {
    abortController?.abort()
  }
  emit('close')
}

function handleBeforeUnload(event) {
  if (!shouldWarnBeforeClose({
    sourceState: { dirty: sourceDirty.value },
    inFlight: inFlight.value,
  })) {
    return
  }
  event.preventDefault()
  event.returnValue = ''
}

onMounted(() => {
  restoreLocalState()
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onBeforeUnmount(() => {
  abortController?.abort()
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

defineExpose({
  shouldBlockNavigation() {
    return shouldWarnBeforeClose({
      sourceState: { dirty: sourceDirty.value },
      inFlight: inFlight.value,
    })
  },
  cancelActiveRequest() {
    if (inFlight.value) {
      abortController?.abort()
    }
  },
})
</script>

<template>
  <button
    v-if="open && minimized"
    class="admin-ai-floating"
    type="button"
    aria-label="恢复 AI 转换"
    @click="restore"
    @keydown.esc="requestClose"
  >
    <strong>{{ selectedMode?.name || 'AI 转换' }}</strong>
    <span>{{ inFlight ? '转换中...' : '恢复 AI 转换' }}</span>
  </button>

  <div
    v-if="open && !minimized"
    class="admin-ai-drawer-layer"
  >
    <section
      ref="drawerRef"
      class="admin-ai-drawer"
      role="dialog"
      aria-modal="true"
      aria-label="AI 转换"
      @keydown="handleDialogKeydown"
    >
      <header class="admin-ai-drawer__header">
        <div>
          <span class="admin-page__eyebrow">AI / LOCAL DRAFT</span>
          <h2>AI 转换</h2>
          <p>转换结果只保留在当前浏览器，不会自动写入文章。</p>
        </div>
        <div class="admin-ai-drawer__window-actions">
          <button type="button" aria-label="缩成小窗" @click="minimize">缩成小窗</button>
          <button type="button" aria-label="关闭 AI 转换" @click="requestClose">关闭</button>
        </div>
      </header>

      <div ref="drawerBodyRef" class="admin-ai-drawer__body">
        <section class="admin-ai-drawer__section">
          <header>
            <div>
              <strong>源草稿</strong>
              <small>{{ sourceCharacterCount }} / {{ maxInputChars }}</small>
            </div>
            <button type="button" @click="bringCurrentSource">带入当前正文</button>
          </header>
          <textarea
            ref="sourceRef"
            v-model="source"
            aria-label="AI 源草稿"
            spellcheck="false"
            @input="handleSourceInput"
          />
        </section>

        <section class="admin-ai-drawer__section admin-ai-drawer__controls">
          <label>
            <span>转换模式</span>
            <select v-model="selectedModeKey" aria-label="转换模式" :disabled="!modes.length">
              <option v-if="!modes.length" value="">暂无可用模式</option>
              <option
                v-for="mode in modes"
                :key="mode.modeKey"
                :value="mode.modeKey"
              >
                {{ mode.name }}
              </option>
            </select>
          </label>
          <p v-if="selectedMode?.description">{{ selectedMode.description }}</p>
          <p class="admin-ai-drawer__privacy">
            正文会发送到已配置的第三方 AI 服务；取消仅停止当前浏览器等待，
            供应商可能仍在处理并计费。
          </p>
          <div class="admin-ai-drawer__run-actions">
            <button
              class="button button--primary"
              type="button"
              :disabled="inFlight || !modes.length"
              @click="executeTransform"
            >
              {{ inFlight ? '转换中...' : resultState ? '重新转换' : '开始转换' }}
            </button>
            <button
              v-if="inFlight"
              class="button button--quiet"
              type="button"
              @click="cancelTransform"
            >
              取消请求
            </button>
          </div>
        </section>

        <p v-if="errorMessage" class="admin-notice" role="alert">{{ errorMessage }}</p>
        <p
          v-if="statusMessage"
          class="admin-ai-drawer__status"
          role="status"
        >
          {{ statusMessage }}
        </p>

        <section class="admin-ai-drawer__section admin-ai-drawer__result">
          <header>
            <div>
              <strong>转换结果</strong>
              <small v-if="resultState">
                {{ resultState.modeKey }} · v{{ resultState.modeVersion }}
              </small>
            </div>
            <div class="admin-ai-drawer__result-actions">
              <span v-if="resultEdited">结果已手动修改</span>
              <button
                type="button"
                :disabled="!resultState"
                @click="copyResult"
              >
                复制结果
              </button>
            </div>
          </header>

          <div v-if="resultState" class="admin-ai-drawer__result-grid">
            <label>
              <span>Markdown 源码</span>
              <textarea
                ref="resultRef"
                v-model="resultState.content"
                aria-label="AI 转换结果"
                spellcheck="false"
                @input="handleResultInput"
              />
            </label>
            <div class="admin-ai-drawer__preview">
              <span>只读预览</span>
              <MarkdownArticle
                :source="resultContent"
                :allow-remote-images="false"
              />
            </div>
          </div>
          <p v-else class="admin-ai-drawer__empty">执行转换后，结果会显示在这里。</p>
        </section>
      </div>
    </section>
  </div>
</template>
