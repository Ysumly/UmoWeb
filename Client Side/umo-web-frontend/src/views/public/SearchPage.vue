<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { searchContents } from '@/api/public'
import ContentCard from '@/components/public/ContentCard.vue'
import ContentState from '@/components/public/ContentState.vue'
import { getApiErrorMessage, parseRetryAfterSeconds } from '@/utils/apiError'
import { normalizePositiveInteger, PUBLIC_PAGE_SIZE } from '@/utils/publicContent'

const route = useRoute()
const router = useRouter()
const queryInput = ref(String(route.query.q || ''))
const status = ref('idle')
const errorMessage = ref('')
const errorCode = ref(null)
const contents = ref([])
const pageInfo = ref({ page: 1, size: PUBLIC_PAGE_SIZE, total: 0 })
const retrySeconds = ref(0)
let requestId = 0
let countdownTimer = null

const query = computed(() => String(route.query.q || '').trim())
const page = computed(() => normalizePositiveInteger(route.query.page, 1))
const totalPages = computed(() => {
  return Math.max(1, Math.ceil(pageInfo.value.total / PUBLIC_PAGE_SIZE))
})

function clearCountdown() {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
  retrySeconds.value = 0
}

function startCountdown(seconds) {
  clearCountdown()
  retrySeconds.value = seconds
  countdownTimer = setInterval(() => {
    retrySeconds.value = Math.max(0, retrySeconds.value - 1)
    if (retrySeconds.value === 0) {
      clearCountdown()
    }
  }, 1000)
}

function submitSearch() {
  if (status.value === 'loading' || retrySeconds.value > 0) {
    return
  }

  const nextQuery = queryInput.value.trim()
  if (!nextQuery) {
    router.replace({ query: {} })
    contents.value = []
    pageInfo.value = { page: 1, size: PUBLIC_PAGE_SIZE, total: 0 }
    status.value = 'idle'
    return
  }

  if (nextQuery === query.value && page.value === 1) {
    load()
    return
  }

  router.replace({ query: { q: nextQuery } })
}

function goToPage(nextPage) {
  router.replace({
    query: {
      q: query.value,
      ...(nextPage > 1 ? { page: nextPage } : {}),
    },
  })
}

async function load() {
  if (status.value === 'loading' || retrySeconds.value > 0) {
    return
  }

  const currentQuery = query.value
  const currentPage = page.value
  if (!currentQuery) {
    clearCountdown()
    contents.value = []
    pageInfo.value = { page: 1, size: PUBLIC_PAGE_SIZE, total: 0 }
    status.value = 'idle'
    return
  }

  const currentRequest = ++requestId
  status.value = 'loading'
  errorMessage.value = ''
  errorCode.value = null

  try {
    const response = await searchContents({
      q: currentQuery,
      page: currentPage,
      size: PUBLIC_PAGE_SIZE,
    })
    if (currentRequest !== requestId) {
      return
    }

    contents.value = response.data.items || []
    pageInfo.value = {
      page: response.data.page || currentPage,
      size: response.data.size || PUBLIC_PAGE_SIZE,
      total: response.data.total || 0,
    }

    const lastPage = Math.max(1, Math.ceil(pageInfo.value.total / PUBLIC_PAGE_SIZE))
    if (currentPage > lastPage && pageInfo.value.total > 0) {
      goToPage(lastPage)
      return
    }

    clearCountdown()
    status.value = 'success'
  } catch (error) {
    if (currentRequest !== requestId) {
      return
    }

    status.value = 'error'
    errorCode.value = error.response?.status || null
    errorMessage.value = getApiErrorMessage(error, '搜索失败，请稍后重试')
    if (errorCode.value === 429) {
      startCountdown(parseRetryAfterSeconds(error))
    }
  }
}

watch(
  () => [query.value, page.value],
  () => {
    queryInput.value = query.value
    const rawPage = route.query.page
    if (rawPage != null && String(page.value) !== String(rawPage)) {
      router.replace({
        query: {
          q: query.value,
          ...(page.value > 1 ? { page: page.value } : {}),
        },
      })
      return
    }
    load()
  },
  { immediate: true },
)

onBeforeUnmount(clearCountdown)
</script>

<template>
  <div class="search-page">
    <header class="page-intro search-intro">
      <span class="editorial-eyebrow">Search / 标题、摘要与正文</span>
      <h1>从留下的文字里，<br>寻找一条线索。</h1>
      <form class="search-form" role="search" @submit.prevent="submitSearch">
        <label class="sr-only" for="search-query">搜索关键词</label>
        <input
          id="search-query"
          v-model="queryInput"
          class="search-input"
          type="search"
          maxlength="200"
          autocomplete="off"
          placeholder="输入标题、摘要或正文关键词"
        >
        <button
          class="button button--primary"
          type="submit"
          :disabled="status === 'loading' || retrySeconds > 0"
        >
          {{ retrySeconds > 0 ? `${retrySeconds} 秒后重试` : '搜索' }}
        </button>
      </form>
      <p v-if="retrySeconds > 0" class="search-rate-limit" role="status">
        请求过于频繁，请在 {{ retrySeconds }} 秒后重试。
      </p>
    </header>

    <ContentState
      v-if="status === 'idle'"
      state="empty"
      title="输入关键词开始搜索"
      message="搜索范围包含文章标题、摘要和 Markdown 正文。"
    />

    <ContentState
      v-else-if="status === 'loading'"
      state="loading"
      title="正在搜索"
    />

    <ContentState
      v-else-if="status === 'error'"
      state="error"
      :title="errorCode === 429 ? '请求过于频繁' : '搜索失败'"
      :message="errorMessage"
      :action-label="retrySeconds > 0 ? '' : '重新搜索'"
      @retry="load"
    />

    <template v-else>
      <div v-if="contents.length" class="content-grid content-grid--library">
        <ContentCard
          v-for="(content, index) in contents"
          :key="content.id"
          v-reveal
          :content="content"
          :index="index"
          variant="library"
        />
      </div>

      <ContentState
        v-else
        state="empty"
        title="没有找到匹配内容"
        message="尝试缩短关键词，或者换一个标题、摘要和正文中可能出现的词。"
      />

      <nav v-if="totalPages > 1 && status === 'success'" class="pagination" aria-label="搜索分页">
        <button
          type="button"
          :disabled="page <= 1 || retrySeconds > 0"
          @click="goToPage(page - 1)"
        >
          上一页
        </button>
        <span>
          {{ String(page).padStart(2, '0') }} /
          {{ String(totalPages).padStart(2, '0') }}
        </span>
        <button
          type="button"
          :disabled="page >= totalPages || retrySeconds > 0"
          @click="goToPage(page + 1)"
        >
          下一页
        </button>
      </nav>
    </template>
  </div>
</template>
