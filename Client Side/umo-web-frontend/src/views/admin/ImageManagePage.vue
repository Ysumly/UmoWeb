<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { deleteImage, getAdminImages, getImageIntegrity } from '@/api/admin'
import ContentState from '@/components/public/ContentState.vue'
import {
  buildImageListParams,
  getImageDeleteError,
} from '@/utils/adminManagement'
import { getApiErrorMessage } from '@/utils/apiError'
import { formatPublishedDate } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const status = ref('loading')
const errorMessage = ref('')
const actionMessage = ref('')
const images = ref([])
const usage = ref(normalizeUsage(route.query.usage))
const page = ref(normalizePage(route.query.page))
const pageInfo = ref({ page: 1, size: 24, total: 0 })
const deletingId = ref(null)
const integrityReport = ref(null)
const integrityError = ref('')
const checkingIntegrity = ref(false)
let integrityRequestId = 0

const totalPages = computed(() => {
  return Math.max(1, Math.ceil(pageInfo.value.total / pageInfo.value.size))
})

const filters = [
  { value: '', label: '全部' },
  { value: 'REFERENCED', label: '使用中' },
  { value: 'ORPHANED', label: '未引用' },
]

async function loadImages(targetPage = page.value) {
  status.value = 'loading'
  errorMessage.value = ''
  try {
    const params = buildImageListParams({
      page: targetPage,
      size: 24,
      usage: usage.value,
    })
    const response = await getAdminImages(params)
    images.value = response.data?.items || []
    pageInfo.value = {
      page: response.data?.page || params.page,
      size: response.data?.size || params.size,
      total: response.data?.total || 0,
    }
    page.value = pageInfo.value.page
    status.value = 'success'
  } catch (error) {
    status.value = 'error'
    errorMessage.value = getApiErrorMessage(error, '图片列表加载失败')
  }
}

async function selectUsage(nextUsage) {
  usage.value = nextUsage
  page.value = 1
  await syncRoute()
  await loadImages(1)
}

async function changePage(nextPage) {
  if (nextPage < 1 || nextPage > totalPages.value) {
    return
  }
  page.value = nextPage
  await syncRoute()
  await loadImages(nextPage)
}

async function handleDelete(image) {
  if (!window.confirm(`确定删除图片“${image.originalName}”吗？`)) {
    return
  }

  deletingId.value = image.id
  actionMessage.value = ''
  errorMessage.value = ''
  try {
    await deleteImage(image.id)
    integrityRequestId += 1
    integrityReport.value = null
    integrityError.value = ''
    checkingIntegrity.value = false
    actionMessage.value = '图片已删除'
    const lastPage = images.value.length === 1 && page.value > 1
      ? page.value - 1
      : page.value
    await loadImages(lastPage)
  } catch (error) {
    errorMessage.value = getImageDeleteError(error)
  } finally {
    deletingId.value = null
  }
}

async function checkIntegrity() {
  if (checkingIntegrity.value) {
    return
  }
  const requestId = ++integrityRequestId
  checkingIntegrity.value = true
  integrityReport.value = null
  integrityError.value = ''
  actionMessage.value = ''
  try {
    const response = await getImageIntegrity()
    if (requestId !== integrityRequestId) {
      return
    }
    integrityReport.value = response.data || null
  } catch (error) {
    if (requestId !== integrityRequestId) {
      return
    }
    integrityError.value = getApiErrorMessage(error, '图片一致性检查失败')
  } finally {
    if (requestId === integrityRequestId) {
      checkingIntegrity.value = false
    }
  }
}

function referenceSourceLabel(reference) {
  const prefix = reference.sourceType === 'FIXED_PAGE' ? '固定页' : '文章'
  return `${prefix}：${reference.sourceLabel}`
}

async function syncRoute() {
  const query = { ...route.query }
  if (usage.value) {
    query.usage = usage.value
  } else {
    delete query.usage
  }
  if (page.value > 1) {
    query.page = String(page.value)
  } else {
    delete query.page
  }
  await router.replace({ query })
}

function normalizeUsage(value) {
  return ['REFERENCED', 'ORPHANED'].includes(value) ? value : ''
}

function normalizePage(value) {
  const parsed = Number.parseInt(value, 10)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : 1
}

function formatFileSize(bytes) {
  const value = Number(bytes)
  if (!Number.isFinite(value) || value < 0) {
    return '未知大小'
  }
  if (value < 1024) {
    return `${value} B`
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`
  }
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

onMounted(loadImages)
</script>

<template>
  <section class="admin-page">
    <header class="admin-page__header">
      <div>
        <span class="admin-page__eyebrow">MEDIA / 图片</span>
        <h1>图片管理</h1>
        <p>查看图片引用状态，删除不再使用的媒体文件。</p>
      </div>
      <div class="admin-image-actions">
        <button
          class="button button--primary"
          type="button"
          :disabled="checkingIntegrity"
          :aria-busy="checkingIntegrity"
          @click="checkIntegrity"
        >
          {{ checkingIntegrity ? '检查中...' : '检查一致性' }}
        </button>
        <div class="admin-image-filter" role="group" aria-label="图片引用筛选">
          <button
            v-for="filter in filters"
            :key="filter.value"
            type="button"
            :aria-pressed="usage === filter.value"
            @click="selectUsage(filter.value)"
          >
            {{ filter.label }}
          </button>
        </div>
      </div>
    </header>

    <div v-if="actionMessage" class="admin-notice admin-notice--success" role="status">
      {{ actionMessage }}
      <button type="button" aria-label="关闭提示" @click="actionMessage = ''">关闭</button>
    </div>
    <div v-if="errorMessage && status !== 'error'" class="admin-notice" role="alert">
      {{ errorMessage }}
      <button type="button" aria-label="关闭提示" @click="errorMessage = ''">关闭</button>
    </div>
    <div v-if="integrityError" class="admin-notice" role="alert">
      {{ integrityError }}
      <button type="button" aria-label="关闭一致性检查错误" @click="integrityError = ''">
        关闭
      </button>
    </div>

    <section
      v-if="integrityReport"
      class="admin-integrity-report"
      aria-labelledby="image-integrity-title"
    >
      <header>
        <div>
          <span class="admin-page__eyebrow">MEDIA CHECK / 一致性</span>
          <h2 id="image-integrity-title">图片一致性报告</h2>
        </div>
        <small>扫描时间：{{ formatPublishedDate(integrityReport.scannedAt) }}</small>
      </header>

      <p
        v-if="integrityReport.counts.total === 0"
        class="admin-integrity-report__clean"
        role="status"
      >
        检查完成：未发现一致性问题
      </p>

      <template v-else>
        <p class="admin-integrity-report__summary" role="status">
          检查完成：发现 {{ integrityReport.counts.total }} 项问题
        </p>

        <div class="admin-integrity-counts">
          <article>
            <strong>{{ integrityReport.counts.brokenReferences }}</strong>
            <span>引用断裂</span>
          </article>
          <article>
            <strong>{{ integrityReport.counts.missingFiles }}</strong>
            <span>记录缺文件</span>
          </article>
          <article>
            <strong>{{ integrityReport.counts.untrackedFiles }}</strong>
            <span>磁盘孤立文件</span>
          </article>
        </div>

        <div class="admin-integrity-issues">
          <section v-if="integrityReport.brokenReferences.length">
            <h3>引用断裂</h3>
            <ul>
              <li
                v-for="reference in integrityReport.brokenReferences"
                :key="`${reference.url}-${reference.sourceType}-${reference.sourceId || reference.sourceLabel}`"
              >
                <code>{{ reference.url }}</code>
                <span>{{ referenceSourceLabel(reference) }}</span>
              </li>
            </ul>
          </section>

          <section v-if="integrityReport.missingFiles.length">
            <h3>记录缺文件</h3>
            <ul>
              <li v-for="file in integrityReport.missingFiles" :key="file.id">
                <strong>{{ file.originalName }}</strong>
                <code>{{ file.url }}</code>
              </li>
            </ul>
          </section>

          <section v-if="integrityReport.untrackedFiles.length">
            <h3>磁盘孤立文件</h3>
            <ul>
              <li v-for="file in integrityReport.untrackedFiles" :key="file.url">
                <code>{{ file.url }}</code>
              </li>
            </ul>
          </section>
        </div>
      </template>
    </section>

    <ContentState
      v-if="status === 'loading' && !images.length"
      state="loading"
      title="正在读取图片"
    />

    <ContentState
      v-else-if="status === 'error'"
      state="error"
      title="图片列表加载失败"
      :message="errorMessage"
      action-label="重新加载"
      @retry="loadImages()"
    />

    <ContentState
      v-else-if="!images.length"
      state="empty"
      title="没有符合条件的图片"
      message="可以切换引用筛选，或先到文章编辑器中上传图片。"
    />

    <template v-else>
      <div class="admin-image-grid">
        <article v-for="image in images" :key="image.id" class="admin-image-card">
          <div class="admin-image-card__preview">
            <img :src="image.url" :alt="image.originalName" loading="lazy" />
          </div>
          <div class="admin-image-card__body">
            <div class="admin-image-card__heading">
              <strong>{{ image.originalName }}</strong>
              <span
                class="admin-status"
                :class="image.referenced ? 'admin-status--published' : 'admin-status--draft'"
              >
                {{ image.referenced ? '使用中' : '未引用' }}
              </span>
            </div>
            <small>{{ formatFileSize(image.size) }} · {{ formatPublishedDate(image.createdAt) }}</small>
            <button
              class="admin-image-card__delete"
              type="button"
              :disabled="deletingId === image.id"
              :aria-label="`删除 ${image.originalName}`"
              @click="handleDelete(image)"
            >
              {{ deletingId === image.id ? '删除中...' : '删除' }}
            </button>
          </div>
        </article>
      </div>

      <nav v-if="totalPages > 1" class="admin-pagination" aria-label="图片分页">
        <button type="button" :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
        <span>第 {{ page }} / {{ totalPages }} 页，共 {{ pageInfo.total }} 张</span>
        <button type="button" :disabled="page >= totalPages" @click="changePage(page + 1)">
          下一页
        </button>
      </nav>
    </template>
  </section>
</template>
