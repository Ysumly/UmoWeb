<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getCategories, getContents, getTags } from '@/api/public'
import ContentCard from '@/components/public/ContentCard.vue'
import ContentState from '@/components/public/ContentState.vue'
import LibraryFilterSheet from '@/components/public/LibraryFilterSheet.vue'
import SectionHeading from '@/components/public/SectionHeading.vue'
import { contentTypes } from '@/config/contentTypes'
import { getApiErrorMessage } from '@/utils/apiError'
import {
  buildLibraryFilterChips,
  countActiveLibraryFilters,
} from '@/utils/libraryFilters'
import {
  flattenCategoryTree,
  PUBLIC_PAGE_SIZE,
  resolveLibraryQuery,
} from '@/utils/publicContent'

const route = useRoute()
const router = useRouter()
const status = ref('loading')
const errorMessage = ref('')
const categories = ref([])
const tags = ref([])
const contents = ref([])
const pageInfo = ref({ page: 1, size: PUBLIC_PAGE_SIZE, total: 0 })
const filterSheetOpen = ref(false)
const filterTriggerRef = ref(null)
const pendingScrollRestore = ref(null)
let requestId = 0

const filters = computed(() => resolveLibraryQuery(route.query))
const allCategoryOptions = computed(() => flattenCategoryTree(categories.value))
const categoryOptions = computed(() => {
  return allCategoryOptions.value.filter((category) => {
    return !filters.value.type || category.type === filters.value.type
  })
})
const activeFilterCount = computed(() => countActiveLibraryFilters(filters.value))
const activeFilterChips = computed(() => {
  return buildLibraryFilterChips(filters.value, categories.value, tags.value)
})
const totalPages = computed(() => {
  return Math.max(1, Math.ceil(pageInfo.value.total / PUBLIC_PAGE_SIZE))
})

function replaceQuery(nextFilters) {
  router.replace({
    query: {
      ...(nextFilters.type ? { type: nextFilters.type } : {}),
      ...(nextFilters.categoryId ? { category: nextFilters.categoryId } : {}),
      ...(nextFilters.categoryId
        ? { includeDescendants: nextFilters.includeDescendants }
        : {}),
      ...(nextFilters.tagId ? { tag: nextFilters.tagId } : {}),
      ...(nextFilters.page > 1 ? { page: nextFilters.page } : {}),
    },
  })
}

function updateQuery(changes) {
  replaceQuery({
    ...filters.value,
    ...changes,
    page: changes.page ?? 1,
  })
}

function clearFilters() {
  if (!activeFilterCount.value) {
    return
  }
  pendingScrollRestore.value = window.scrollY
  filterSheetOpen.value = false
  router.replace({ query: {} })
}

function removeFilter(key) {
  const changes = {
    ...(key === 'type' ? { type: '' } : {}),
    ...(key === 'category' ? {
      categoryId: null,
      includeDescendants: false,
    } : {}),
    ...(key === 'tag' ? { tagId: null } : {}),
  }
  updateQuery(changes)
}

function applyMobileFilters(nextFilters) {
  filterSheetOpen.value = false
  updateQuery(nextFilters)
  nextTick(() => filterTriggerRef.value?.focus())
}

function closeMobileFilters() {
  filterSheetOpen.value = false
  nextTick(() => filterTriggerRef.value?.focus())
}

async function restoreScrollPosition() {
  if (pendingScrollRestore.value === null) {
    return
  }
  const target = pendingScrollRestore.value
  pendingScrollRestore.value = null
  await nextTick()
  const maxScroll = Math.max(0, document.documentElement.scrollHeight - window.innerHeight)
  window.scrollTo(0, Math.min(target, maxScroll))
}

function hasInvalidQuery() {
  const raw = route.query
  const normalized = filters.value
  return Boolean(
    (raw.type && String(raw.type) !== normalized.type)
    || (raw.category && !normalized.categoryId)
    || (normalized.categoryId
      && String(raw.includeDescendants ?? '') !== String(normalized.includeDescendants))
    || (!normalized.categoryId && raw.includeDescendants)
    || (raw.tag && !normalized.tagId)
    || (raw.page && String(normalized.page) !== String(raw.page)),
  )
}

async function load() {
  const currentFilters = filters.value
  const currentRequest = ++requestId
  status.value = 'loading'
  errorMessage.value = ''

  try {
    const [categoryResponse, tagResponse, contentResponse] = await Promise.all([
      getCategories(),
      getTags(),
      getContents({
        page: currentFilters.page,
        size: PUBLIC_PAGE_SIZE,
        ...(currentFilters.type ? { type: currentFilters.type } : {}),
        ...(currentFilters.categoryId ? { categoryId: currentFilters.categoryId } : {}),
        ...(currentFilters.categoryId
          ? { includeDescendants: currentFilters.includeDescendants }
          : {}),
        ...(currentFilters.tagId ? { tagId: currentFilters.tagId } : {}),
      }),
    ])

    if (currentRequest !== requestId) {
      return
    }

    categories.value = categoryResponse.data || []
    tags.value = tagResponse.data || []
    contents.value = contentResponse.data.items || []
    pageInfo.value = {
      page: contentResponse.data.page || currentFilters.page,
      size: contentResponse.data.size || PUBLIC_PAGE_SIZE,
      total: contentResponse.data.total || 0,
    }

    const lastPage = Math.max(1, Math.ceil(pageInfo.value.total / PUBLIC_PAGE_SIZE))
    if (currentFilters.page > lastPage && pageInfo.value.total > 0) {
      replaceQuery({ ...currentFilters, page: lastPage })
      return
    }

    status.value = 'success'
  } catch (error) {
    if (currentRequest !== requestId) {
      return
    }
    status.value = 'error'
    errorMessage.value = getApiErrorMessage(error, '书库内容加载失败')
  } finally {
    if (currentRequest === requestId) {
      await restoreScrollPosition()
    }
  }
}

watch(
  () => [
    route.query.type,
    route.query.category,
    route.query.includeDescendants,
    route.query.tag,
    route.query.page,
  ],
  () => {
    if (hasInvalidQuery()) {
      replaceQuery(filters.value)
      return
    }
    load()
  },
  { immediate: true },
)
</script>

<template>
  <div class="library-page">
    <header class="page-intro">
      <span class="editorial-eyebrow">Library / 公开书库</span>
      <h1>按主题，慢慢翻阅。</h1>
      <p>
        当前收录 {{ pageInfo.total }} 篇公开内容。分类筛选包含当前分类及全部子分类，标签筛选精确匹配。
      </p>
    </header>

    <div class="library-layout">
      <aside class="library-filters" aria-label="内容筛选">
        <div class="filter-group">
          <span class="filter-label">内容类型</span>
          <button
            v-for="item in contentTypes"
            :key="item.value"
            type="button"
            :class="{ 'is-active': filters.type === item.value }"
            @click="updateQuery({ type: item.value })"
          >
            <span>{{ item.zh }}</span>
            <small>{{ item.label }}</small>
          </button>
        </div>

        <div class="filter-group">
          <span class="filter-label">分类</span>
          <button
            v-for="category in categoryOptions"
            :key="category.id"
            type="button"
            :class="{ 'is-active': filters.categoryId === category.id }"
            @click="updateQuery({
              categoryId: filters.categoryId === category.id ? null : category.id,
              includeDescendants: filters.categoryId !== category.id,
            })"
          >
            <span :style="{ paddingLeft: `${category.depth * 12}px` }">
              {{ category.name }}
            </span>
          </button>
        </div>

        <div class="filter-group filter-group--tags">
          <span class="filter-label">标签</span>
          <button
            v-for="tag in tags"
            :key="tag.id"
            type="button"
            :class="{ 'is-active': filters.tagId === tag.id }"
            @click="updateQuery({ tagId: filters.tagId === tag.id ? null : tag.id })"
          >
            {{ tag.name }}
          </button>
        </div>

        <button class="filter-clear" type="button" @click="clearFilters">清除全部筛选</button>
      </aside>

      <section class="library-results">
        <div class="library-mobile-filter-bar">
          <button
            ref="filterTriggerRef"
            class="library-filter-trigger"
            type="button"
            aria-controls="library-filter-sheet"
            :aria-expanded="filterSheetOpen"
            @click="filterSheetOpen = true"
          >
            筛选（已选 {{ activeFilterCount }} 项）
          </button>
          <div
            v-if="activeFilterChips.length"
            class="library-active-filters"
            aria-label="当前筛选"
          >
            <button
              v-for="chip in activeFilterChips"
              :key="chip.key"
              class="library-filter-chip"
              type="button"
              :aria-label="`移除筛选：${chip.label}`"
              @click="removeFilter(chip.key)"
            >
              {{ chip.label }}
              <span aria-hidden="true">×</span>
            </button>
            <button
              class="library-filter-clear"
              type="button"
              @click="clearFilters"
            >
              清除全部筛选
            </button>
          </div>
        </div>

        <SectionHeading
          :eyebrow="`${pageInfo.total} Results`"
          title="筛选结果"
          :description="`第 ${pageInfo.page} / ${totalPages} 页`"
        />

        <ContentState
          v-if="status === 'loading' && !contents.length"
          state="loading"
          title="正在整理书库"
        />

        <ContentState
          v-else-if="status === 'error'"
          state="error"
          title="书库加载失败"
          :message="errorMessage"
          action-label="重新加载"
          @retry="load"
        />

        <div v-else-if="contents.length" class="content-grid content-grid--library">
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
          title="这一页暂时没有内容"
          message="换一个分类或标签，或者清除筛选重新浏览。"
          action-label="清除筛选"
          @retry="clearFilters"
        />

        <nav v-if="totalPages > 1 && status === 'success'" class="pagination" aria-label="分页">
          <button
            type="button"
            :disabled="filters.page <= 1"
            @click="updateQuery({ page: filters.page - 1 })"
          >
            上一页
          </button>
          <span>
            {{ String(filters.page).padStart(2, '0') }} /
            {{ String(totalPages).padStart(2, '0') }}
          </span>
          <button
            type="button"
            :disabled="filters.page >= totalPages"
            @click="updateQuery({ page: filters.page + 1 })"
          >
            下一页
          </button>
        </nav>
      </section>
    </div>

    <LibraryFilterSheet
      :open="filterSheetOpen"
      :filters="filters"
      :category-options="allCategoryOptions"
      :tags="tags"
      @close="closeMobileFilters"
      @apply="applyMobileFilters"
    />
  </div>
</template>
