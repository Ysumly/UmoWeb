<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { getContent } from '@/api/public'
import ContentState from '@/components/public/ContentState.vue'
import MarkdownArticle from '@/components/public/MarkdownArticle.vue'
import { getApiErrorMessage } from '@/utils/apiError'
import { formatPublishedDate } from '@/utils/format'

const route = useRoute()
const status = ref('loading')
const errorMessage = ref('')
const article = ref(null)
let requestId = 0

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

  <article v-else-if="article" class="post-page">
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
        <span class="post-aside__label">篇章信息</span>
        <dl>
          <div v-if="metadata.difficulty">
            <dt>难度</dt>
            <dd>{{ metadata.difficulty }}</dd>
          </div>
          <div v-if="metadata.chapter">
            <dt>章节</dt>
            <dd>第 {{ metadata.chapter }} 章</dd>
          </div>
          <div v-if="metadata.bookTitle">
            <dt>书目</dt>
            <dd>{{ metadata.bookTitle }}</dd>
          </div>
          <div v-if="categories.length">
            <dt>分类</dt>
            <dd>{{ categories.map((item) => item.name).join(' / ') }}</dd>
          </div>
        </dl>
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
      </div>
    </div>
  </article>
</template>
