<script setup>
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue'
import { useRoute } from 'vue-router'

import { getContent } from '@/api/public'
import ArticleOutline from '@/components/public/ArticleOutline.vue'
import ContentCard from '@/components/public/ContentCard.vue'
import ContentState from '@/components/public/ContentState.vue'
import MarkdownArticle from '@/components/public/MarkdownArticle.vue'
import { flattenOutline } from '@/utils/articleOutline'
import { getApiErrorMessage } from '@/utils/apiError'
import { formatPublishedDate } from '@/utils/format'
import { extractMarkdownOutline } from '@/utils/markdown'

const route = useRoute()
const status = ref('loading')
const errorMessage = ref('')
const article = ref(null)
const articleRef = ref(null)
const activeHeadingId = ref('')
const readingProgress = ref(0)
const readingProgressVisible = ref(false)
let requestId = 0
let animationFrame = 0
let resizeObserver = null

const typeLabels = {
  NOTE: '技术笔记',
  BOOK_REVIEW: '读后有感',
  NOVEL: '小说章节',
}

const categories = computed(() => article.value?.categories || [])
const tags = computed(() => article.value?.tags || [])
const metadata = computed(() => article.value?.metadata || {})
const previousArticle = computed(() => article.value?.previous || null)
const nextArticle = computed(() => article.value?.next || null)
const relatedArticles = computed(() => article.value?.related || [])
const infoItems = computed(() => {
  const items = []
  if (metadata.value.difficulty) {
    items.push({ label: '难度', value: metadata.value.difficulty })
  }
  if (metadata.value.chapter) {
    items.push({ label: '章节', value: `第 ${metadata.value.chapter} 章` })
  }
  if (metadata.value.bookTitle) {
    items.push({ label: '书目', value: metadata.value.bookTitle })
  }
  if (categories.value.length) {
    items.push({
      label: '分类',
      value: categories.value.map((item) => item.name).join(' / '),
    })
  }
  return items
})
const compactMetaText = computed(() => {
  const parts = []
  if (metadata.value.difficulty) {
    parts.push(`难度 ${metadata.value.difficulty}`)
  }
  if (categories.value.length) {
    parts.push(`分类 ${categories.value.map((item) => item.name).join(' / ')}`)
  }
  return parts.join(' · ') || '暂无补充信息'
})
const outline = computed(() =>
  article.value?.body ? extractMarkdownOutline(article.value.body) : [],
)
const flatOutline = computed(() => flattenOutline(outline.value))

function readingOffset() {
  const header = document.querySelector('.site-header')
  return (header?.getBoundingClientRect().height || 78) + 40
}

function updateReadingState() {
  animationFrame = 0
  const element = articleRef.value
  if (!element) {
    activeHeadingId.value = ''
    readingProgress.value = 0
    readingProgressVisible.value = false
    return
  }

  const offset = readingOffset()
  const bounds = element.getBoundingClientRect()
  const readableHeight = Math.max(1, window.innerHeight - offset)
  const scrollableDistance = Math.max(1, bounds.height - readableHeight)
  readingProgress.value = Math.min(
    1,
    Math.max(0, (offset - bounds.top) / scrollableDistance),
  )
  readingProgressVisible.value = bounds.height > readableHeight + 96

  let currentHeading = ''
  for (const heading of flatOutline.value) {
    const headingElement = document.getElementById(heading.id)
    if (!headingElement) {
      continue
    }
    if (headingElement.getBoundingClientRect().top <= offset + 1) {
      currentHeading = heading.id
    } else {
      break
    }
  }
  activeHeadingId.value = currentHeading
}

function scheduleReadingUpdate() {
  if (animationFrame) {
    return
  }
  animationFrame = window.requestAnimationFrame(updateReadingState)
}

function scrollToHeading(heading) {
  const element = document.getElementById(heading.id)
  if (!element) {
    return
  }

  const nextHash = `#${encodeURIComponent(heading.id)}`
  if (window.location.hash !== nextHash) {
    window.history.replaceState(null, '', nextHash)
  }
  activeHeadingId.value = heading.id
  element.scrollIntoView({
    behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches
      ? 'auto'
      : 'smooth',
    block: 'start',
  })
  scheduleReadingUpdate()
}

async function load() {
  const currentRequest = ++requestId
  status.value = 'loading'
  errorMessage.value = ''

  try {
    const response = await getContent(route.params.slug)
    if (currentRequest !== requestId) {
      return
    }
    article.value = response.data
    status.value = 'success'
    activeHeadingId.value = ''
    await nextTick()
    if (route.hash) {
      const targetId = decodeURIComponent(route.hash.slice(1))
      document.getElementById(targetId)?.scrollIntoView({ block: 'start' })
    }
    updateReadingState()
  } catch (error) {
    if (currentRequest !== requestId) {
      return
    }
    article.value = null
    status.value = error.response?.status === 404 ? 'not-found' : 'error'
    errorMessage.value = getApiErrorMessage(error, '文章加载失败')
  }
}

watch(() => route.params.slug, load, { immediate: true })

watch(articleRef, (element) => {
  resizeObserver?.disconnect()
  if (element) {
    resizeObserver = new ResizeObserver(scheduleReadingUpdate)
    resizeObserver.observe(element)
  }
})

onMounted(() => {
  window.addEventListener('scroll', scheduleReadingUpdate, { passive: true })
  window.addEventListener('resize', scheduleReadingUpdate)
})

onBeforeUnmount(() => {
  if (animationFrame) {
    window.cancelAnimationFrame(animationFrame)
  }
  resizeObserver?.disconnect()
  window.removeEventListener('scroll', scheduleReadingUpdate)
  window.removeEventListener('resize', scheduleReadingUpdate)
})
</script>

<template>
  <ContentState
    v-if="status === 'loading'"
    state="loading"
    title="正在展开文章"
  />

  <section v-else-if="status === 'not-found'" class="empty-state empty-state--page">
    <span>ARTICLE NOT FOUND</span>
    <h1>这一页还没有被写下</h1>
    <p>文章可能已被移动、下线，或者从未发布。</p>
    <router-link class="button button--outline" to="/library">返回书库</router-link>
  </section>

  <ContentState
    v-else-if="status === 'error'"
    state="error"
    title="文章加载失败"
    :message="errorMessage"
    action-label="重新加载"
    @retry="load"
  />

  <article v-else-if="article" ref="articleRef" class="post-page">
    <div
      v-if="readingProgressVisible"
      class="reading-progress"
      aria-hidden="true"
    >
      <span
        class="reading-progress__bar"
        :style="{ transform: `scaleX(${readingProgress})` }"
      />
    </div>

    <header class="post-header">
      <div class="post-header__meta">
        <span>{{ typeLabels[article.type] || article.type }}</span>
        <span>{{ formatPublishedDate(article.publishedAt) }}</span>
        <span v-if="metadata.readingTime">{{ metadata.readingTime }} 分钟阅读</span>
      </div>
      <h1>{{ article.title }}</h1>
      <p>{{ article.summary }}</p>
      <div class="post-header__taxonomy">
        <router-link
          v-for="category in categories"
          :key="category.id"
          :to="{ path: '/library', query: { category: category.id } }"
        >
          {{ category.name }}
        </router-link>
        <span v-for="tag in tags" :key="tag.id"># {{ tag.name }}</span>
      </div>
    </header>

    <div class="post-layout">
      <aside class="post-aside">
        <div class="post-aside__content">
          <span class="post-aside__label">篇章信息</span>
          <dl v-if="infoItems.length">
            <div v-for="item in infoItems" :key="item.label">
              <dt>{{ item.label }}</dt>
              <dd>{{ item.value }}</dd>
            </div>
          </dl>
          <details v-if="infoItems.length" class="post-mobile-meta">
            <summary>
              <span class="post-mobile-meta__label">篇章信息</span>
              <span class="post-mobile-meta__summary" :title="compactMetaText">
                {{ compactMetaText }}
              </span>
              <span class="post-mobile-meta__toggle" aria-hidden="true" />
            </summary>
            <dl>
              <div v-for="item in infoItems" :key="item.label">
                <dt>{{ item.label }}</dt>
                <dd>{{ item.value }}</dd>
              </div>
            </dl>
          </details>
          <ArticleOutline
            :headings="outline"
            :active-id="activeHeadingId"
            @select="scrollToHeading"
          />
        </div>
      </aside>

      <div class="post-body-wrap">
        <MarkdownArticle v-if="article.body" :source="article.body" />
        <ContentState
          v-else
          state="empty"
          title="正文暂不可用"
          message="文章元信息已加载，但 Markdown 正文当前为空。"
        />

        <nav
          v-if="previousArticle || nextArticle"
          class="post-navigation"
          aria-label="前后文章"
        >
          <router-link v-if="previousArticle" :to="`/post/${previousArticle.slug}`">
            <span>上一篇 / 更早</span>
            <strong>{{ previousArticle.title }}</strong>
          </router-link>
          <span v-else />
          <router-link v-if="nextArticle" :to="`/post/${nextArticle.slug}`">
            <span>下一篇 / 更新</span>
            <strong>{{ nextArticle.title }}</strong>
          </router-link>
        </nav>

        <section
          v-if="relatedArticles.length"
          class="related-content"
          role="region"
          aria-labelledby="related-content-title"
        >
          <header class="related-content__header">
            <span>Related</span>
            <h2 id="related-content-title">相关阅读</h2>
          </header>
          <div class="content-grid content-grid--related">
            <ContentCard
              v-for="(content, index) in relatedArticles"
              :key="content.id"
              v-reveal
              :content="content"
              :index="index"
            />
          </div>
        </section>
      </div>
    </div>
  </article>
</template>
