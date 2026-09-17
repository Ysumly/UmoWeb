<script setup>
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue'
import { onBeforeRouteLeave } from 'vue-router'

import MarkdownArticle from '@/components/public/MarkdownArticle.vue'
import { useSyncedScroll } from '@/composables/useSyncedScroll'
import {
  createEditorDraft,
  createMarkdownBlob,
  DEFAULT_EDITOR_FILENAME,
  EDITOR_DRAFT_KEY,
  isMarkdownFile,
  normalizeEditorFileName,
  parseEditorDraft,
} from '@/utils/editor'

const content = ref('')
const fileName = ref(DEFAULT_EDITOR_FILENAME)
const mobilePane = ref('editor')
const draftState = ref('idle')
const savedAt = ref(null)
const storageError = ref('')
const notice = ref({ tone: '', message: '' })
const importing = ref(false)
const fileInputRef = ref(null)
const textareaRef = ref(null)
const previewRef = ref(null)

let saveTimer = null
let mounted = false

useSyncedScroll(textareaRef, previewRef, {
  mediaQuery: '(min-width: 981px)',
})

const lineCount = computed(() => {
  return content.value ? content.value.split(/\r?\n/).length : 0
})
const characterCount = computed(() => content.value.length)
const canDownload = computed(() => content.value.length > 0)
const canClear = computed(() => {
  return content.value.length > 0
    || normalizeEditorFileName(fileName.value) !== DEFAULT_EDITOR_FILENAME
})
const draftStatus = computed(() => {
  if (draftState.value === 'pending') {
    return '正在保存...'
  }
  if (draftState.value === 'saved') {
    return `已保存 ${formatSavedTime(savedAt.value)}`
  }
  if (draftState.value === 'restored') {
    return `已恢复 ${formatSavedTime(savedAt.value)}`
  }
  if (draftState.value === 'error') {
    return storageError.value || '草稿无法保存到当前浏览器'
  }
  return ''
})

function formatSavedTime(timestamp) {
  if (!timestamp) {
    return ''
  }
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(timestamp))
}

function setNotice(message, tone = 'success') {
  notice.value = { tone, message }
}

function clearNotice() {
  notice.value = { tone: '', message: '' }
}

function readStoredDraft() {
  try {
    const serialized = localStorage.getItem(EDITOR_DRAFT_KEY)
    if (!serialized) {
      return
    }

    const draft = parseEditorDraft(serialized)
    if (!draft) {
      localStorage.removeItem(EDITOR_DRAFT_KEY)
      return
    }

    content.value = draft.content
    fileName.value = draft.fileName
    savedAt.value = draft.updatedAt
    draftState.value = 'restored'
  } catch {
    storageError.value = '无法读取本机草稿'
    draftState.value = 'error'
  }
}

function persistDraft() {
  if (saveTimer) {
    window.clearTimeout(saveTimer)
    saveTimer = null
  }

  const normalizedFileName = normalizeEditorFileName(fileName.value)
  const shouldPersist = content.value.length > 0 || normalizedFileName !== DEFAULT_EDITOR_FILENAME

  try {
    if (!shouldPersist) {
      localStorage.removeItem(EDITOR_DRAFT_KEY)
      savedAt.value = null
      storageError.value = ''
      draftState.value = 'idle'
      return true
    }

    const draft = createEditorDraft({
      content: content.value,
      fileName: normalizedFileName,
    })
    localStorage.setItem(EDITOR_DRAFT_KEY, JSON.stringify(draft))
    savedAt.value = draft.updatedAt
    storageError.value = ''
    draftState.value = 'saved'
    return true
  } catch {
    storageError.value = '草稿无法保存到当前浏览器'
    draftState.value = 'error'
    return false
  }
}

function scheduleDraftSave() {
  if (!mounted) {
    return
  }

  if (saveTimer) {
    window.clearTimeout(saveTimer)
  }
  draftState.value = 'pending'
  saveTimer = window.setTimeout(persistDraft, 300)
}

function normalizeFileNameField() {
  fileName.value = normalizeEditorFileName(fileName.value)
}

function openFilePicker() {
  fileInputRef.value?.click()
}

async function importMarkdown(event) {
  const [file] = event.target.files || []
  event.target.value = ''

  if (!file) {
    return
  }
  if (!isMarkdownFile(file)) {
    setNotice('仅支持 .md 或 .markdown 文件', 'error')
    return
  }
  if (content.value.length && !window.confirm('导入会替换当前编辑内容，确定继续吗？')) {
    return
  }

  importing.value = true
  try {
    const text = await file.text()
    content.value = text
    fileName.value = normalizeEditorFileName(file.name)
    mobilePane.value = 'editor'
    setNotice(`已导入 ${fileName.value}`)
    await nextTick()
    textareaRef.value?.focus()
  } catch {
    setNotice('文件读取失败，请重试', 'error')
  } finally {
    importing.value = false
  }
}

function downloadMarkdown() {
  if (!canDownload.value) {
    return
  }

  const downloadName = normalizeEditorFileName(fileName.value)
  fileName.value = downloadName
  const url = URL.createObjectURL(createMarkdownBlob(content.value))
  const link = document.createElement('a')

  link.href = url
  link.download = downloadName
  link.rel = 'noopener'
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 0)
}

function clearEditor() {
  if (content.value.length && !window.confirm('确定清空当前编辑内容和本机草稿吗？')) {
    return
  }

  if (saveTimer) {
    window.clearTimeout(saveTimer)
    saveTimer = null
  }

  content.value = ''
  fileName.value = DEFAULT_EDITOR_FILENAME
  savedAt.value = null
  mobilePane.value = 'editor'
  storageError.value = ''
  draftState.value = 'idle'
  clearNotice()

  try {
    localStorage.removeItem(EDITOR_DRAFT_KEY)
  } catch {
    storageError.value = '无法清除本机草稿'
    draftState.value = 'error'
  }

  nextTick(() => textareaRef.value?.focus())
}

function handleBeforeUnload(event) {
  if (draftState.value !== 'error' || !content.value.length) {
    return
  }
  event.preventDefault()
  event.returnValue = ''
}

function flushDraft() {
  if (mounted) {
    persistDraft()
  }
}

watch([content, fileName], scheduleDraftSave)

onBeforeRouteLeave(() => {
  if (
    draftState.value === 'error'
    && content.value.length
    && !window.confirm('草稿无法保存，确定离开并放弃当前内容吗？')
  ) {
    return false
  }
  flushDraft()
  return true
})

onMounted(async () => {
  readStoredDraft()
  await nextTick()
  mounted = true
  window.addEventListener('beforeunload', handleBeforeUnload)
  window.addEventListener('pagehide', flushDraft)
})

onBeforeUnmount(() => {
  if (saveTimer) {
    window.clearTimeout(saveTimer)
  }
  window.removeEventListener('beforeunload', handleBeforeUnload)
  window.removeEventListener('pagehide', flushDraft)
})
</script>

<template>
  <section class="editor-page">
    <header class="editor-page__header">
      <div>
        <span class="editorial-eyebrow">Editor / 本地写作台</span>
        <h1>Markdown 编辑器</h1>
      </div>
      <p
        v-if="draftStatus"
        class="editor-page__status"
        :class="{ 'is-error': draftState === 'error' }"
        aria-live="polite"
      >
        <span aria-hidden="true" />
        {{ draftStatus }}
      </p>
    </header>

    <div class="editor-toolbar">
      <label class="editor-toolbar__name">
        <span>文件名</span>
        <input
          v-model="fileName"
          type="text"
          maxlength="160"
          spellcheck="false"
          @blur="normalizeFileNameField"
        />
      </label>

      <div class="editor-toolbar__actions">
        <input
          ref="fileInputRef"
          class="sr-only"
          type="file"
          accept=".md,.markdown,text/markdown,text/plain"
          @change="importMarkdown"
        />
        <button
          class="button button--outline"
          type="button"
          :disabled="importing"
          @click="openFilePicker"
        >
          {{ importing ? '读取中...' : '导入' }}
        </button>
        <button
          class="button button--primary"
          type="button"
          :disabled="!canDownload"
          @click="downloadMarkdown"
        >
          下载 .md
        </button>
        <button
          class="button button--quiet"
          type="button"
          :disabled="!canClear"
          @click="clearEditor"
        >
          清空
        </button>
      </div>
    </div>

    <div
      v-if="notice.message"
      class="editor-notice"
      :class="{ 'is-error': notice.tone === 'error' }"
      role="status"
    >
      <span>{{ notice.message }}</span>
      <button type="button" aria-label="关闭提示" @click="clearNotice">关闭</button>
    </div>

    <div class="editor-tabs" role="tablist" aria-label="编辑区切换">
      <button
        type="button"
        role="tab"
        :aria-selected="mobilePane === 'editor'"
        :tabindex="mobilePane === 'editor' ? 0 : -1"
        @click="mobilePane = 'editor'"
      >
        编辑
      </button>
      <button
        type="button"
        role="tab"
        :aria-selected="mobilePane === 'preview'"
        :tabindex="mobilePane === 'preview' ? 0 : -1"
        @click="mobilePane = 'preview'"
      >
        预览
      </button>
    </div>

    <div class="editor-workspace">
      <section
        class="editor-pane editor-pane--input"
        :class="{ 'is-mobile-active': mobilePane === 'editor' }"
        role="tabpanel"
      >
        <header class="editor-pane__header">
          <span>Markdown</span>
          <small>{{ lineCount }} 行 · {{ characterCount }} 字符</small>
        </header>
        <textarea
          ref="textareaRef"
          v-model="content"
          spellcheck="false"
          aria-label="Markdown 正文"
          placeholder="# 从这里开始"
        />
      </section>

      <section
        ref="previewRef"
        class="editor-pane editor-pane--preview"
        :class="{ 'is-mobile-active': mobilePane === 'preview' }"
        role="tabpanel"
      >
        <header class="editor-pane__header">
          <span>预览</span>
        </header>
        <MarkdownArticle v-if="content" :source="content" />
        <p v-else class="editor-empty">预览会显示在这里。</p>
      </section>
    </div>
  </section>
</template>
