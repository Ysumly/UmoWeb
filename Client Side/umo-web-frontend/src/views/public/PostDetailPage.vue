<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import MarkdownArticle from '@/components/public/MarkdownArticle.vue'
import { formatPublishedDate } from '@/demo/catalog'
import { publicContents } from '@/demo/content'

const route = useRoute()

const orderedContents = computed(() => {
  return [...publicContents].sort((a, b) => new Date(b.publishedAt) - new Date(a.publishedAt))
})

const article = computed(() => {
  return publicContents.find((item) => item.slug === route.params.slug)
})

const articleIndex = computed(() => {
  return orderedContents.value.findIndex((item) => item.slug === route.params.slug)
})

const previousArticle = computed(() => {
  return articleIndex.value >= 0 ? orderedContents.value[articleIndex.value + 1] : null
})

const nextArticle = computed(() => {
  return articleIndex.value > 0 ? orderedContents.value[articleIndex.value - 1] : null
})

const typeLabels = {
  NOTE: '技术笔记',
  BOOK_REVIEW: '读后有感',
  NOVEL: '小说章节',
}
</script>

<template>
  <article v-if="article" class="post-page">
    <header class="post-header">
      <div class="post-header__meta">
        <span>{{ typeLabels[article.type] }}</span>
        <span>{{ formatPublishedDate(article.publishedAt) }}</span>
        <span v-if="article.metadata.readingTime">{{ article.metadata.readingTime }} 分钟阅读</span>
      </div>
      <h1>{{ article.title }}</h1>
      <p>{{ article.summary }}</p>
      <div class="post-header__taxonomy">
        <router-link
          v-for="category in article.categories"
          :key="category.id"
          :to="{ path: '/library', query: { category: category.id } }"
        >
          {{ category.name }}
        </router-link>
        <span v-for="tag in article.tags" :key="tag.id"># {{ tag.name }}</span>
      </div>
    </header>

    <div class="post-layout">
      <aside class="post-aside">
        <span class="post-aside__label">篇章信息</span>
        <dl>
          <div v-if="article.metadata.difficulty">
            <dt>难度</dt>
            <dd>{{ article.metadata.difficulty }}</dd>
          </div>
          <div v-if="article.metadata.chapter">
            <dt>章节</dt>
            <dd>第 {{ article.metadata.chapter }} 章</dd>
          </div>
          <div v-if="article.metadata.bookTitle">
            <dt>书目</dt>
            <dd>{{ article.metadata.bookTitle }}</dd>
          </div>
          <div>
            <dt>分类</dt>
            <dd>{{ article.categories.map((item) => item.name).join(' / ') }}</dd>
          </div>
        </dl>
      </aside>

      <div class="post-body-wrap">
        <MarkdownArticle :source="article.body" />

        <nav class="post-navigation" aria-label="前后文章">
          <router-link v-if="previousArticle" :to="`/post/${previousArticle.slug}`">
            <span>上一篇</span>
            <strong>{{ previousArticle.title }}</strong>
          </router-link>
          <span v-else />
          <router-link v-if="nextArticle" :to="`/post/${nextArticle.slug}`">
            <span>下一篇</span>
            <strong>{{ nextArticle.title }}</strong>
          </router-link>
        </nav>
      </div>
    </div>
  </article>

  <section v-else class="empty-state empty-state--page">
    <span>ARTICLE NOT FOUND</span>
    <h1>这一页还没有被写下</h1>
    <p>文章可能已被移动，或者当前 DEMO 中尚未收录。</p>
    <router-link class="button button--outline" to="/library">返回书库</router-link>
  </section>
</template>
