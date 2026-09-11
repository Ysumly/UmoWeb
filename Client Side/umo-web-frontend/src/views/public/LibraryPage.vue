<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import ContentCard from '@/components/public/ContentCard.vue'
import SectionHeading from '@/components/public/SectionHeading.vue'
import { filterContents, paginateContents } from '@/demo/catalog'
import {
  allCategoryOptions,
  contentTypes,
  publicContents,
  tags,
} from '@/demo/content'

const route = useRoute()
const router = useRouter()

const activeType = ref(contentTypes.some((item) => item.value === route.query.type) ? String(route.query.type) : '')
const activeCategory = ref(route.query.category ? Number(route.query.category) : null)
const activeTag = ref(route.query.tag ? Number(route.query.tag) : null)
const page = ref(1)
const pageSize = 6

const filteredContents = computed(() => {
  return filterContents(publicContents, {
    type: activeType.value,
    categoryId: activeCategory.value,
    tagId: activeTag.value,
  }).sort((a, b) => new Date(b.publishedAt) - new Date(a.publishedAt))
})

const pagedContents = computed(() => {
  return paginateContents(filteredContents.value, page.value, pageSize)
})

const totalPages = computed(() => {
  return Math.max(1, Math.ceil(filteredContents.value.length / pageSize))
})

function updateFilters() {
  page.value = 1
  router.replace({
    query: {
      ...(activeType.value ? { type: activeType.value } : {}),
      ...(activeCategory.value ? { category: activeCategory.value } : {}),
      ...(activeTag.value ? { tag: activeTag.value } : {}),
    },
  })
}

function clearFilters() {
  activeType.value = ''
  activeCategory.value = null
  activeTag.value = null
  page.value = 1
  router.replace({ query: {} })
}
</script>

<template>
  <div class="library-page">
    <header class="page-intro">
      <span class="editorial-eyebrow">Library / 公开书库</span>
      <h1>按主题，慢慢翻阅。</h1>
      <p>当前收录 {{ publicContents.length }} 篇公开内容。筛选只匹配当前分类或标签，不自动包含子分类。</p>
    </header>

    <div class="library-layout">
      <aside class="library-filters" aria-label="内容筛选">
        <div class="filter-group">
          <span class="filter-label">内容类型</span>
          <button
            v-for="item in contentTypes"
            :key="item.value"
            type="button"
            :class="{ 'is-active': activeType === item.value }"
            @click="activeType = item.value; updateFilters()"
          >
            <span>{{ item.zh }}</span>
            <small>{{ item.label }}</small>
          </button>
        </div>

        <div class="filter-group">
          <span class="filter-label">分类</span>
          <button
            v-for="category in allCategoryOptions"
            :key="category.id"
            type="button"
            :class="{ 'is-active': activeCategory === category.id }"
            @click="activeCategory = activeCategory === category.id ? null : category.id; updateFilters()"
          >
            <span>{{ category.name }}</span>
          </button>
        </div>

        <div class="filter-group filter-group--tags">
          <span class="filter-label">标签</span>
          <button
            v-for="tag in tags"
            :key="tag.id"
            type="button"
            :class="{ 'is-active': activeTag === tag.id }"
            @click="activeTag = activeTag === tag.id ? null : tag.id; updateFilters()"
          >
            {{ tag.name }}
          </button>
        </div>

        <button class="filter-clear" type="button" @click="clearFilters">清除全部筛选</button>
      </aside>

      <section class="library-results">
        <SectionHeading
          :eyebrow="`${pagedContents.total} Results`"
          title="筛选结果"
          :description="`第 ${pagedContents.page} / ${totalPages} 页`"
        />

        <div v-if="pagedContents.items.length" class="content-grid content-grid--library">
          <ContentCard
            v-for="(content, index) in pagedContents.items"
            :key="content.id"
            v-reveal
            :content="content"
            :index="index"
            variant="library"
          />
        </div>

        <div v-else class="empty-state">
          <span>NO MATCHES</span>
          <h3>这一页暂时没有内容</h3>
          <p>换一个分类或标签，或者清除筛选重新浏览。</p>
          <button class="button button--outline" type="button" @click="clearFilters">清除筛选</button>
        </div>

        <nav v-if="totalPages > 1" class="pagination" aria-label="分页">
          <button type="button" :disabled="page <= 1" @click="page -= 1">上一页</button>
          <span>{{ String(page).padStart(2, '0') }} / {{ String(totalPages).padStart(2, '0') }}</span>
          <button type="button" :disabled="page >= totalPages" @click="page += 1">下一页</button>
        </nav>
      </section>
    </div>
  </div>
</template>
