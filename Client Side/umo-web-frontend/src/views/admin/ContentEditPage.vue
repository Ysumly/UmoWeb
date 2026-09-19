<script setup>
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
} from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'

import {
  createContent,
  getAiCapabilities,
  getAdminCats,
  getAdminContent,
  listAdminTags,
  updateContent,
  uploadImage,
} from '@/api/admin'
import AiTransformDrawer from '@/components/admin/AiTransformDrawer.vue'
import MarkdownArticle from '@/components/public/MarkdownArticle.vue'
import ContentState from '@/components/public/ContentState.vue'
import { useSyncedScroll } from '@/composables/useSyncedScroll'
import { adminPath } from '@/config/adminPath'
import {
  buildContentPayload,
  canScheduleContent,
  contentToForm,
  insertImageMarkdown,
  validateContentForm,
  validateImageFile,
} from '@/utils/adminContent'
import { getApiErrorMessage } from '@/utils/apiError'
import {
  parseMarkdownImport,
  validateMarkdownImportFile,
} from '@/utils/markdownImport'
import { flattenCategoryTree } from '@/utils/publicContent'

const route = useRoute()
const router = useRouter()
const CHOICE_PAGE_SIZE = 12

const isEdit = computed(() => Boolean(route.params.id))
const loading = ref(true)
const saving = ref(false)
const uploading = ref(false)
const dragActive = ref(false)
const generalError = ref('')
const uploadMessage = ref('')
const mobilePane = ref('editor')
const categories = ref([])
const tags = ref([])
const errors = ref({})
const textareaRef = ref(null)
const previewRef = ref(null)
const fileInputRef = ref(null)
const markdownFileInputRef = ref(null)
const aiOpenButtonRef = ref(null)
const aiDrawerRef = ref(null)
const initialSnapshot = ref('')
const pendingSelection = ref(null)
const categoryPage = ref(1)
const tagPage = ref(1)
const importMessage = ref('')
const importWarnings = ref([])
const importErrors = ref({})
const originalContent = ref(null)
const aiCapabilities = ref(null)
const aiDrawerOpen = ref(false)

useSyncedScroll(textareaRef, previewRef, {
  mediaQuery: '(min-width: 701px)',
})

const form = reactive({
  title: '',
  slug: '',
  summary: '',
  type: 'NOTE',
  status: 'DRAFT',
  scheduledAt: '',
  body: '',
  metadata: '',
  categoryIds: [],
  tagIds: [],
})

const allCategories = computed(() => flattenCategoryTree(categories.value))
const categoryOptions = computed(() => {
  return allCategories.value.filter((category) => category.type === form.type)
})
const categoryPageCount = computed(() => {
  return Math.max(1, Math.ceil(categoryOptions.value.length / CHOICE_PAGE_SIZE))
})
const pagedCategoryOptions = computed(() => {
  const start = (categoryPage.value - 1) * CHOICE_PAGE_SIZE
  return categoryOptions.value.slice(start, start + CHOICE_PAGE_SIZE)
})
const tagPageCount = computed(() => {
  return Math.max(1, Math.ceil(tags.value.length / CHOICE_PAGE_SIZE))
})
const pagedTags = computed(() => {
  const start = (tagPage.value - 1) * CHOICE_PAGE_SIZE
  return tags.value.slice(start, start + CHOICE_PAGE_SIZE)
})
const categoryMap = computed(() => {
  return new Map(allCategories.value.map((category) => [category.id, category]))
})
const selectedNovelCategory = computed(() => {
  return form.categoryIds
    .map((id) => categoryMap.value.get(id))
    .find((category) => category?.type === 'NOVEL')
})
const scheduleAllowed = computed(() => {
  return !isEdit.value || canScheduleContent(originalContent.value || {})
})
const statusOptions = computed(() => {
  return [
    { value: 'DRAFT', label: '草稿' },
    ...(scheduleAllowed.value ? [{ value: 'SCHEDULED', label: '待发布' }] : []),
    { value: 'PUBLISHED', label: '已发布' },
    ...(isEdit.value ? [{ value: 'ARCHIVED', label: '已归档' }] : []),
  ]
})
const importErrorMessages = computed(() => {
  return [...new Set(Object.values(importErrors.value).filter(Boolean))]
})
const dirty = computed(() => {
  return !loading.value
    && initialSnapshot.value
    && JSON.stringify(form) !== initialSnapshot.value
})
const aiAvailable = computed(() => aiCapabilities.value?.enabled === true)

function snapshotForm() {
  initialSnapshot.value = JSON.stringify(form)
}

function friendlySaveError(error) {
  const status = error?.response?.status
  if (status === 409) {
    return getApiErrorMessage(error, 'slug 已存在，请更换后重试')
  }
  if (status === 413) {
    return '图片或请求内容超过 50MB'
  }
  return getApiErrorMessage(error, '文章保存失败')
}

function clearImportError(...fields) {
  for (const field of fields) {
    delete importErrors.value[field]
    delete errors.value[field]
  }
  if (!importErrorMessages.value.length) {
    const message = generalError.value
    if (message.startsWith('Markdown 已读取') || message.startsWith('请先修正 Markdown 导入错误')) {
      generalError.value = ''
    }
  }
}

function clearCategoryImportErrors() {
  clearImportError('categoryIds', 'categorySlugs')
}

function openMarkdownPicker() {
  if (dirty.value && !window.confirm('导入会覆盖当前表单内容，确定继续吗？')) {
    return
  }
  importMessage.value = ''
  importWarnings.value = []
  markdownFileInputRef.value?.click()
}

async function handleMarkdownFileInput(event) {
  const [file] = event.target.files || []
  event.target.value = ''
  if (!file) {
    return
  }

  const validationMessage = validateMarkdownImportFile(file)
  if (validationMessage) {
    generalError.value = validationMessage
    return
  }

  try {
    const result = parseMarkdownImport({
      filename: file.name,
      source: await file.text(),
      categories: allCategories.value,
      tags: tags.value,
    })
    if (!result.form) {
      generalError.value = result.errors.import || 'Markdown 导入失败'
      return
    }

    Object.assign(form, { scheduledAt: '' }, result.form)
    importErrors.value = { ...result.errors }
    errors.value = { ...result.errors }
    importWarnings.value = result.warnings
    importMessage.value = `已读取 ${file.name}`
    generalError.value = Object.keys(result.errors).length
      ? 'Markdown 已读取，请检查表单中的错误项'
      : ''
    categoryPage.value = 1
    tagPage.value = 1
    mobilePane.value = 'editor'
  } catch (error) {
    generalError.value = error?.message || 'Markdown 读取失败'
  }
}

function handleTypeChange() {
  const validIds = new Set(categoryOptions.value.map((category) => category.id))
  form.categoryIds = form.categoryIds.filter((id) => validIds.has(id))
  categoryPage.value = 1
  clearCategoryImportErrors()
  clearImportError('type')
}

function changeCategoryPage(offset) {
  categoryPage.value = Math.max(1, Math.min(categoryPage.value + offset, categoryPageCount.value))
}

function changeTagPage(offset) {
  tagPage.value = Math.max(1, Math.min(tagPage.value + offset, tagPageCount.value))
}

async function load() {
  loading.value = true
  generalError.value = ''

  try {
    const requests = [getAdminCats(), listAdminTags()]
    if (isEdit.value) {
      requests.push(getAdminContent(route.params.id))
    }
    const [categoryResponse, tagResponse, contentResponse] = await Promise.all(requests)

    categories.value = categoryResponse.data || []
    tags.value = tagResponse.data || []
    if (contentResponse) {
      originalContent.value = contentResponse.data
      Object.assign(form, contentToForm(contentResponse.data))
    } else {
      originalContent.value = null
    }
    snapshotForm()
  } catch (error) {
    generalError.value = getApiErrorMessage(error, '文章加载失败')
  } finally {
    loading.value = false
  }
}

async function loadAiCapabilities() {
  try {
    const response = await getAiCapabilities()
    aiCapabilities.value = response.data
  } catch {
    aiCapabilities.value = null
  }
}

function openAiDrawer() {
  aiDrawerOpen.value = true
}

function closeAiDrawer() {
  aiDrawerOpen.value = false
  nextTick(() => aiOpenButtonRef.value?.focus())
}

async function handleSubmit() {
  errors.value = {
    ...validateContentForm(form, allCategories.value, {
      canSchedule: scheduleAllowed.value,
    }),
    ...importErrors.value,
  }
  generalError.value = ''
  if (Object.keys(errors.value).length) {
    generalError.value = importErrorMessages.value.length
      ? '请先修正 Markdown 导入错误'
      : '请检查表单中的错误项'
    return
  }

  saving.value = true
  try {
    const payload = buildContentPayload(form, allCategories.value)
    if (isEdit.value) {
      await updateContent(route.params.id, payload)
    } else {
      await createContent(payload)
    }
    snapshotForm()
    await router.push({
      name: 'admin-contents',
      query: { saved: '1' },
    })
  } catch (error) {
    generalError.value = friendlySaveError(error)
  } finally {
    saving.value = false
  }
}

function openImagePicker() {
  pendingSelection.value = {
    start: textareaRef.value?.selectionStart ?? form.body.length,
    end: textareaRef.value?.selectionEnd ?? form.body.length,
  }
  fileInputRef.value?.click()
}

async function uploadAndInsert(file, selection = pendingSelection.value) {
  const validationMessage = validateImageFile(file)
  if (validationMessage) {
    uploadMessage.value = validationMessage
    pendingSelection.value = null
    return
  }

  const start = selection?.start ?? form.body.length
  const end = selection?.end ?? start
  uploading.value = true
  uploadMessage.value = '正在上传图片...'

  try {
    const response = await uploadImage(file)
    const alt = String(file.name || 'image').replace(/\.[^.]+$/, '')
    const result = insertImageMarkdown({
      body: form.body,
      selectionStart: start,
      selectionEnd: end,
      url: response.data.url,
      alt,
    })
    form.body = result.body
    uploadMessage.value = '图片已插入正文'
    await nextTick()
    textareaRef.value?.focus()
    textareaRef.value?.setSelectionRange(result.cursor, result.cursor)
  } catch (error) {
    uploadMessage.value = error?.response?.status === 413
      ? '图片不能超过 50MB'
      : getApiErrorMessage(error, '图片上传失败')
  } finally {
    uploading.value = false
    pendingSelection.value = null
  }
}

function handleFileInput(event) {
  const [file] = event.target.files || []
  if (file) {
    uploadAndInsert(file, pendingSelection.value)
  }
  event.target.value = ''
}

function handlePaste(event) {
  const imageItem = Array.from(event.clipboardData?.items || [])
    .find((item) => item.type.startsWith('image/'))
  if (!imageItem) {
    return
  }

  event.preventDefault()
  uploadAndInsert(imageItem.getAsFile(), {
    start: textareaRef.value?.selectionStart ?? form.body.length,
    end: textareaRef.value?.selectionEnd ?? form.body.length,
  })
}

function handleDrop(event) {
  dragActive.value = false
  const [file] = event.dataTransfer?.files || []
  if (file) {
    uploadAndInsert(file, {
      start: textareaRef.value?.selectionStart ?? form.body.length,
      end: textareaRef.value?.selectionEnd ?? form.body.length,
    })
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
  if (saving.value) {
    return true
  }

  const articleDirty = dirty.value
  const aiDrawerBlocked = aiDrawerRef.value?.shouldBlockNavigation() === true
  if (!articleDirty && !aiDrawerBlocked) {
    return true
  }

  const message = articleDirty && aiDrawerBlocked
    ? '当前文章修改和 AI 转换状态尚未处理，确定离开吗？'
    : articleDirty
      ? '当前修改尚未保存，确定离开吗？'
      : 'AI 转换正在进行或草稿尚未保存，确定离开吗？'
  if (!window.confirm(message)) {
    return false
  }
  if (aiDrawerBlocked) {
    aiDrawerRef.value?.cancelActiveRequest()
  }
  return true
})

onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  load()
  loadAiCapabilities()
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})
</script>

<template>
  <section class="admin-page admin-editor-page">
    <ContentState
      v-if="loading"
      state="loading"
      title="正在读取文章"
    />

    <ContentState
      v-else-if="generalError && !form.title && isEdit"
      state="error"
      title="文章加载失败"
      :message="generalError"
      action-label="返回文章列表"
      @retry="router.push(adminPath('contents'))"
    />

    <template v-else>
      <header class="admin-page__header admin-editor-header">
        <div>
          <span class="admin-page__eyebrow">
            {{ isEdit ? 'EDIT / 编辑文章' : 'NEW / 新建文章' }}
          </span>
          <h1>{{ isEdit ? '编辑文章' : '新建文章' }}</h1>
          <p>Markdown 文件会随文章保存，发布状态可以随时切换。</p>
        </div>
        <div class="admin-editor-header__actions">
          <button class="button button--quiet" type="button" @click="router.push(adminPath('contents'))">
            返回列表
          </button>
          <button
            v-if="!isEdit"
            class="button button--outline"
            type="button"
            :disabled="saving || uploading"
            @click="openMarkdownPicker"
          >
            导入 Markdown
          </button>
          <input
            ref="markdownFileInputRef"
            class="sr-only"
            type="file"
            tabindex="-1"
            accept=".md,.markdown,text/markdown,text/plain"
            aria-label="选择 Markdown 文件"
            @change="handleMarkdownFileInput"
          />
          <button
            class="button button--primary"
            type="button"
            :disabled="saving || uploading"
            @click="handleSubmit"
          >
            {{ saving ? '保存中...' : isEdit ? '保存修改' : '创建文章' }}
          </button>
        </div>
      </header>

      <div v-if="generalError" class="admin-notice" role="alert">
        {{ generalError }}
        <button type="button" @click="generalError = ''">关闭</button>
      </div>

      <div
        v-if="importMessage"
        class="admin-notice admin-import-result"
        role="status"
        aria-label="Markdown 导入结果"
      >
        <div>
          <strong>{{ importMessage }}</strong>
          <ul v-if="importErrorMessages.length || importWarnings.length">
            <li
              v-for="message in importErrorMessages"
              :key="`error-${message}`"
              class="admin-import-result__error"
            >
              {{ message }}
            </li>
            <li v-for="warning in importWarnings" :key="warning">{{ warning }}</li>
          </ul>
        </div>
      </div>

      <div class="admin-editor-layout">
        <form class="admin-editor-form" @submit.prevent="handleSubmit">
          <div class="admin-form-grid admin-form-grid--two">
            <label class="admin-field">
              <span>标题 <b>*</b></span>
              <input
                v-model="form.title"
                type="text"
                maxlength="500"
                required
                @input="clearImportError('title')"
              />
              <small v-if="errors.title">{{ errors.title }}</small>
            </label>

            <label class="admin-field">
              <span>slug <b>*</b></span>
              <input
                v-model.trim="form.slug"
                type="text"
                maxlength="200"
                placeholder="article-slug"
                required
                @input="clearImportError('slug')"
              />
              <small v-if="errors.slug">{{ errors.slug }}</small>
            </label>
          </div>

          <label class="admin-field">
            <span>摘要</span>
            <textarea
              v-model="form.summary"
              rows="3"
              maxlength="2000"
              @input="clearImportError('summary')"
            />
            <small>{{ form.summary.length }} / 2000</small>
          </label>

          <div class="admin-form-grid admin-form-grid--two">
            <label class="admin-field">
              <span>类型 <b>*</b></span>
              <select v-model="form.type" @change="handleTypeChange">
                <option value="NOTE">笔记</option>
                <option value="BOOK_REVIEW">书评</option>
                <option value="NOVEL">小说</option>
              </select>
              <small v-if="errors.type">{{ errors.type }}</small>
            </label>

            <label class="admin-field">
              <span>状态 <b>*</b></span>
              <select v-model="form.status" @change="clearImportError('status')">
                <option
                  v-for="option in statusOptions"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ option.label }}
                </option>
              </select>
              <small v-if="errors.status">{{ errors.status }}</small>
            </label>
          </div>

          <label v-if="form.status === 'SCHEDULED'" class="admin-field">
            <span>计划发布时间 <b>*</b></span>
            <input
              v-model="form.scheduledAt"
              type="datetime-local"
              @input="clearImportError('scheduledAt', 'status')"
            />
            <small v-if="errors.scheduledAt">{{ errors.scheduledAt }}</small>
            <small v-else>到点后由服务器自动发布，停机期间到期内容会在恢复后补发。</small>
          </label>

          <fieldset class="admin-field">
            <legend>分类</legend>
            <p v-if="!categoryOptions.length" class="admin-field__empty">
              当前类型还没有可选分类，请先到分类管理中创建。
            </p>
            <div v-else class="admin-choice-list">
              <label
                v-for="category in pagedCategoryOptions"
                :key="category.id"
                :title="category.name"
                :style="{ paddingLeft: `${category.depth * 16}px` }"
              >
                <input
                  v-model="form.categoryIds"
                  type="checkbox"
                  :value="category.id"
                  @change="clearCategoryImportErrors"
                />
                <span>{{ category.name }}</span>
              </label>
            </div>
            <nav
              v-if="categoryPageCount > 1"
              class="admin-choice-pagination"
              aria-label="分类分页"
            >
              <button type="button" :disabled="categoryPage <= 1" @click="changeCategoryPage(-1)">
                上一页
              </button>
              <span>{{ categoryPage }} / {{ categoryPageCount }}</span>
              <button
                type="button"
                :disabled="categoryPage >= categoryPageCount"
                @click="changeCategoryPage(1)"
              >
                下一页
              </button>
            </nav>
            <small v-if="errors.categoryIds">{{ errors.categoryIds }}</small>
            <small v-else-if="form.type === 'NOVEL'">
              小说必须选择至少一个小说分类；第一条小说分类会作为作品目录。
              <template v-if="selectedNovelCategory">
                当前目录：{{ selectedNovelCategory.slug }}
              </template>
            </small>
          </fieldset>

          <fieldset class="admin-field">
            <legend>标签</legend>
            <p v-if="!tags.length" class="admin-field__empty">暂无标签。</p>
            <div v-else class="admin-choice-list admin-choice-list--tags">
              <label v-for="tag in pagedTags" :key="tag.id" :title="tag.name">
                <input
                  v-model="form.tagIds"
                  type="checkbox"
                  :value="tag.id"
                  @change="clearImportError('tagIds', 'tagSlugs')"
                />
                <span>{{ tag.name }}</span>
              </label>
            </div>
            <nav v-if="tagPageCount > 1" class="admin-choice-pagination" aria-label="标签分页">
              <button type="button" :disabled="tagPage <= 1" @click="changeTagPage(-1)">
                上一页
              </button>
              <span>{{ tagPage }} / {{ tagPageCount }}</span>
              <button type="button" :disabled="tagPage >= tagPageCount" @click="changeTagPage(1)">
                下一页
              </button>
            </nav>
          </fieldset>

          <label class="admin-field">
            <span>metadata</span>
            <textarea
              v-model="form.metadata"
              rows="5"
              placeholder="{&#10;  &quot;readingTime&quot;: 10,&#10;  &quot;difficulty&quot;: &quot;beginner&quot;,&#10;  &quot;author&quot;: &quot;Umo&quot;,&#10;  &quot;source&quot;: &quot;https://example.com&quot;&#10;}"
              spellcheck="false"
              @input="clearImportError('metadata')"
            />
            <small v-if="errors.metadata">{{ errors.metadata }}</small>
            <small v-else>填写非空内容时，必须是合法 JSON 对象。</small>
            <details class="admin-field__details">
              <summary>更多</summary>
              <div class="admin-field__details-body">
                <p>metadata 用于保存文章的结构化信息，可按内容类型自行扩展。</p>
                <dl>
                  <div>
                    <dt><code>readingTime</code></dt>
                    <dd>预计阅读分钟数</dd>
                  </div>
                  <div>
                    <dt><code>difficulty</code></dt>
                    <dd>难度等级，例如 beginner、intermediate、advanced</dd>
                  </div>
                  <div>
                    <dt><code>author</code></dt>
                    <dd>作者或内容来源</dd>
                  </div>
                  <div>
                    <dt><code>source</code></dt>
                    <dd>原文链接</dd>
                  </div>
                </dl>
                <p>示例：</p>
                <pre>{
  "readingTime": 10,
  "difficulty": "beginner",
  "author": "Umo",
  "source": "https://example.com"
}</pre>
                <p>JSON 不支持注释；字段说明只用于提示，不要复制到输入框。</p>
              </div>
            </details>
          </label>

          <div class="admin-editor-toolbar">
            <div>
              <span class="filter-label">正文 Markdown</span>
              <small v-if="uploadMessage">{{ uploadMessage }}</small>
            </div>
            <div class="admin-editor-toolbar__actions">
              <button
                class="button button--outline"
                type="button"
                :disabled="uploading"
                @click="openImagePicker"
              >
                {{ uploading ? '上传中...' : '上传图片' }}
              </button>
              <button
                v-if="aiAvailable"
                ref="aiOpenButtonRef"
                class="button button--outline"
                type="button"
                :disabled="uploading"
                @click="openAiDrawer"
              >
                AI 转换
              </button>
            </div>
            <input
              ref="fileInputRef"
              class="sr-only"
              type="file"
              tabindex="-1"
              aria-label="选择图片文件"
              accept="image/jpeg,image/png,image/gif,image/webp"
              @change="handleFileInput"
            />
          </div>

          <div class="admin-mobile-tabs" role="group" aria-label="编辑区切换">
            <button
              type="button"
              :aria-pressed="mobilePane === 'editor'"
              @click="mobilePane = 'editor'"
            >
              编辑
            </button>
            <button
              type="button"
              :aria-pressed="mobilePane === 'preview'"
              @click="mobilePane = 'preview'"
            >
              预览
            </button>
          </div>

          <div class="admin-editor-workspace">
            <div
              class="admin-editor-pane admin-editor-pane--input"
              :class="{ 'is-mobile-active': mobilePane === 'editor' }"
              @dragover.prevent="dragActive = true"
              @dragleave.prevent="dragActive = false"
              @drop.prevent="handleDrop"
            >
              <div v-if="dragActive" class="admin-drop-overlay">松开以上传图片</div>
              <textarea
                ref="textareaRef"
                v-model="form.body"
                spellcheck="false"
                aria-label="Markdown 正文"
                @paste="handlePaste"
              />
            </div>
            <div
              ref="previewRef"
              class="admin-editor-pane admin-editor-pane--preview"
              :class="{ 'is-mobile-active': mobilePane === 'preview' }"
            >
              <MarkdownArticle :source="form.body" />
            </div>
          </div>
        </form>
      </div>

      <AiTransformDrawer
        v-if="aiAvailable"
        ref="aiDrawerRef"
        :open="aiDrawerOpen"
        :current-source="form.body || ''"
        :capabilities="aiCapabilities"
        @close="closeAiDrawer"
      />
    </template>
  </section>
</template>
