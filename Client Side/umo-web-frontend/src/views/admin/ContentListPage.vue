<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  bulkUpdateContents,
  deleteContent,
  getAdminCats,
  getAdminContents,
  listAdminTags,
} from '@/api/admin'
import ContentState from '@/components/public/ContentState.vue'
import { adminPath } from '@/config/adminPath'
import { contentTypes } from '@/config/contentTypes'
import {
  ADMIN_CONTENT_SORTS,
  ADMIN_CONTENT_STATUSES,
  ADMIN_PAGE_SIZE,
  ADMIN_PAGE_SIZE_OPTIONS,
  buildBulkContentPayload,
  formatBulkOperationError,
  resolveAdminContentQuery,
} from '@/utils/adminContent'
import { getApiErrorMessage } from '@/utils/apiError'
import { flattenCategoryTree } from '@/utils/publicContent'
import { formatPublishedDate } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const initialQuery = resolveAdminContentQuery(route.query)
const filters = reactive(initialQuery)
const status = ref('loading')
const errorMessage = ref('')
const actionMessage = ref(route.query.saved ? '文章已保存' : '')
const categories = ref([])
const tags = ref([])
const contents = ref([])
const pageInfo = ref({ page: 1, size: initialQuery.size, total: 0 })
const deletingId = ref(null)
const selectedIds = ref([])
const bulkAction = ref('ADD_CATEGORIES')
const bulkTargetId = ref('')
const bulkLoading = ref(false)
let requestId = 0

const categoryOptions = computed(() => {
  const flattened = flattenCategoryTree(categories.value)
  if (!filters.type) {
    return flattened
  }
  return flattened.filter((category) => category.type === filters.type)
})

const totalPages = computed(() => {
  return Math.max(1, Math.ceil(pageInfo.value.total / filters.size))
})

const typeOptions = computed(() => contentTypes.filter((item) => item.value))
const selectedContentCount = computed(() => selectedIds.value.length)
const allCurrentPageSelected = computed(() => {
  return contents.value.length > 0
    && contents.value.every((content) => selectedIds.value.includes(content.id))
})
const bulkNeedsCategory = computed(() => {
  return ['ADD_CATEGORIES', 'REMOVE_CATEGORIES'].includes(bulkAction.value)
})
const bulkNeedsTag = computed(() => {
  return ['ADD_TAGS', 'REMOVE_TAGS'].includes(bulkAction.value)
})
const bulkTargetOptions = computed(() => {
  if (bulkNeedsCategory.value) {
    return categoryOptions.value
  }
  if (bulkNeedsTag.value) {
    return tags.value
  }
  return []
})
const bulkActionLabel = computed(() => {
  return {
    ADD_CATEGORIES: '添加分类',
    REMOVE_CATEGORIES: '移除分类',
    ADD_TAGS: '添加标签',
    REMOVE_TAGS: '移除标签',
    ARCHIVE: '归档',
    RESTORE_DRAFT: '恢复为草稿',
  }[bulkAction.value] || '执行'
})

function clearSelection() {
  selectedIds.value = []
}

function toggleAllCurrentPage(event) {
  if (event.target.checked) {
    selectedIds.value = contents.value.map((content) => content.id)
  } else {
    clearSelection()
  }
}

function statusLabel(status) {
  return {
    DRAFT: '草稿',
    SCHEDULED: '待发布',
    PUBLISHED: '已发布',
    ARCHIVED: '已归档',
  }[status] || '草稿'
}

function syncQuery() {
  router.replace({
    query: {
      ...(filters.page > 1 ? { page: filters.page } : {}),
      ...(filters.size !== ADMIN_PAGE_SIZE ? { size: filters.size } : {}),
      ...(filters.type ? { type: filters.type } : {}),
      ...(filters.status ? { status: filters.status } : {}),
      ...(filters.categoryId ? { categoryId: filters.categoryId } : {}),
      ...(filters.tagId ? { tagId: filters.tagId } : {}),
      ...(filters.sort !== 'published_at_desc' ? { sort: filters.sort } : {}),
    },
  })
}

function changeFilter(key, value) {
  filters[key] = value
  filters.page = 1
  if (key === 'type' && filters.categoryId) {
    const current = categoryOptions.value.find((item) => item.id === filters.categoryId)
    if (!current) {
      filters.categoryId = null
    }
  }
  syncQuery()
  loadList()
}

function changePage(page) {
  filters.page = Math.max(1, Math.min(page, totalPages.value))
  syncQuery()
  loadList()
}

async function loadOptions() {
  try {
    const [categoryResponse, tagResponse] = await Promise.all([
      getAdminCats(),
      listAdminTags(),
    ])
    categories.value = categoryResponse.data || []
    tags.value = tagResponse.data || []
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '分类或标签加载失败')
  }
}

async function loadList() {
  const currentRequest = ++requestId
  clearSelection()
  status.value = 'loading'
  errorMessage.value = ''

  try {
    const response = await getAdminContents({
      page: filters.page,
      size: filters.size,
      ...(filters.type ? { type: filters.type } : {}),
      ...(filters.status ? { status: filters.status } : {}),
      ...(filters.categoryId ? { categoryId: filters.categoryId } : {}),
      ...(filters.tagId ? { tagId: filters.tagId } : {}),
      sort: filters.sort,
    })

    if (currentRequest !== requestId) {
      return
    }

    contents.value = response.data.items || []
    pageInfo.value = {
      page: response.data.page || filters.page,
      size: response.data.size || filters.size,
      total: response.data.total || 0,
    }
    status.value = 'success'
  } catch (error) {
    if (currentRequest !== requestId) {
      return
    }
    status.value = 'error'
    errorMessage.value = getApiErrorMessage(error, '文章列表加载失败')
  }
}

async function handleBulkAction() {
  if (!selectedIds.value.length) {
    errorMessage.value = '请先选择本页文章'
    return
  }
  if ((bulkNeedsCategory.value || bulkNeedsTag.value) && !bulkTargetId.value) {
    errorMessage.value = bulkNeedsCategory.value ? '请选择分类' : '请选择标签'
    return
  }
  if (bulkAction.value === 'ARCHIVE'
      && !window.confirm(`确定归档选中的 ${selectedIds.value.length} 篇文章吗？`)) {
    return
  }

  const payload = buildBulkContentPayload({
    action: bulkAction.value,
    contentIds: selectedIds.value,
    categoryIds: bulkNeedsCategory.value ? [bulkTargetId.value] : [],
    tagIds: bulkNeedsTag.value ? [bulkTargetId.value] : [],
  })
  bulkLoading.value = true
  actionMessage.value = ''
  errorMessage.value = ''
  try {
    const response = await bulkUpdateContents(payload)
    actionMessage.value =
      `批量操作完成：更新 ${response.data.updatedCount} 篇，未变化 ${response.data.unchangedCount} 篇`
    await loadList()
  } catch (error) {
    errorMessage.value = formatBulkOperationError(error)
  } finally {
    bulkLoading.value = false
  }
}

async function handleDelete(content) {
  if (!window.confirm(`确定删除《${content.title}》吗？Markdown 文件也会被清理。`)) {
    return
  }

  deletingId.value = content.id
  actionMessage.value = ''
  try {
    await deleteContent(content.id)
    if (contents.value.length === 1 && filters.page > 1) {
      filters.page -= 1
      syncQuery()
    }
    await loadList()
    actionMessage.value = '文章已删除'
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '文章删除失败')
    status.value = 'error'
  } finally {
    deletingId.value = null
  }
}

onMounted(() => {
  loadOptions()
  loadList()
})
</script>

<template>
  <section class="admin-page">
    <header class="admin-page__header">
      <div>
        <span class="admin-page__eyebrow">CONTENTS / 文章</span>
        <h1>文章管理</h1>
        <p>共 {{ pageInfo.total }} 篇内容，包含草稿与已发布文章。</p>
      </div>
      <router-link
        :to="adminPath('contents/new')"
        class="button button--primary"
      >
        新建文章
      </router-link>
    </header>

    <div v-if="actionMessage" class="admin-notice admin-notice--success" role="status">
      {{ actionMessage }}
      <button type="button" aria-label="关闭提示" @click="actionMessage = ''">关闭</button>
    </div>
    <div v-if="errorMessage && status !== 'error'" class="admin-notice" role="alert">
      {{ errorMessage }}
      <button type="button" aria-label="关闭提示" @click="errorMessage = ''">关闭</button>
    </div>

    <form class="admin-filters" @submit.prevent>
      <label>
        <span>类型</span>
        <select :value="filters.type" @change="changeFilter('type', $event.target.value)">
          <option value="">全部类型</option>
          <option v-for="item in typeOptions" :key="item.value" :value="item.value">
            {{ item.zh }}
          </option>
        </select>
      </label>

      <label>
        <span>状态</span>
        <select :value="filters.status" @change="changeFilter('status', $event.target.value)">
          <option value="">全部状态</option>
          <option v-for="item in ADMIN_CONTENT_STATUSES" :key="item" :value="item">
            {{ statusLabel(item) }}
          </option>
        </select>
      </label>

      <label>
        <span>分类</span>
        <select
          :value="filters.categoryId || ''"
          @change="changeFilter('categoryId', $event.target.value ? Number($event.target.value) : null)"
        >
          <option value="">全部分类</option>
          <option
            v-for="category in categoryOptions"
            :key="category.id"
            :value="category.id"
          >
            {{ `${'　'.repeat(category.depth)}${category.name}` }}
          </option>
        </select>
      </label>

      <label>
        <span>标签</span>
        <select
          :value="filters.tagId || ''"
          @change="changeFilter('tagId', $event.target.value ? Number($event.target.value) : null)"
        >
          <option value="">全部标签</option>
          <option v-for="tag in tags" :key="tag.id" :value="tag.id">
            {{ tag.name }}
          </option>
        </select>
      </label>

      <label>
        <span>排序</span>
        <select :value="filters.sort" @change="changeFilter('sort', $event.target.value)">
          <option
            v-for="item in ADMIN_CONTENT_SORTS"
            :key="item"
            :value="item"
          >
            {{ item === 'created_at_desc' ? '按创建时间' : '按发布时间' }}
          </option>
        </select>
      </label>

      <label>
        <span>每页</span>
        <select
          :value="filters.size"
          @change="changeFilter('size', Number($event.target.value))"
        >
          <option v-for="size in ADMIN_PAGE_SIZE_OPTIONS" :key="size" :value="size">
            {{ size }} 条
          </option>
        </select>
      </label>
    </form>

    <div class="admin-bulk-bar" aria-label="批量操作">
      <strong>{{ selectedContentCount }} 篇已选</strong>
      <label>
        <span>操作</span>
        <select v-model="bulkAction" @change="bulkTargetId = ''">
          <option value="ADD_CATEGORIES">添加分类</option>
          <option value="REMOVE_CATEGORIES">移除分类</option>
          <option value="ADD_TAGS">添加标签</option>
          <option value="REMOVE_TAGS">移除标签</option>
          <option value="ARCHIVE">归档</option>
          <option value="RESTORE_DRAFT">恢复为草稿</option>
        </select>
      </label>
      <label v-if="bulkNeedsCategory || bulkNeedsTag">
        <span>{{ bulkNeedsCategory ? '分类' : '标签' }}</span>
        <select v-model="bulkTargetId">
          <option value="">请选择</option>
          <option
            v-for="target in bulkTargetOptions"
            :key="target.id"
            :value="target.id"
          >
            {{ target.name }}
          </option>
        </select>
      </label>
      <button
        class="button button--outline"
        type="button"
        :disabled="bulkLoading || !selectedContentCount"
        @click="handleBulkAction"
      >
        {{ bulkLoading ? '处理中...' : bulkActionLabel }}
      </button>
    </div>

    <ContentState
      v-if="status === 'loading' && !contents.length"
      state="loading"
      title="正在读取文章"
    />

    <ContentState
      v-else-if="status === 'error'"
      state="error"
      title="文章列表加载失败"
      :message="errorMessage"
      action-label="重新加载"
      @retry="loadList"
    />

    <ContentState
      v-else-if="!contents.length"
      state="empty"
      title="没有符合条件的文章"
      message="调整筛选条件，或者新建第一篇内容。"
      action-label="新建文章"
      @retry="router.push(adminPath('contents/new'))"
    />

    <template v-else>
      <div class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th class="admin-table__select">
                <input
                  type="checkbox"
                  aria-label="全选当前页文章"
                  :checked="allCurrentPageSelected"
                  @change="toggleAllCurrentPage"
                />
              </th>
              <th>文章</th>
              <th>状态</th>
              <th>分类 / 标签</th>
              <th>发布时间</th>
              <th aria-label="操作" />
            </tr>
          </thead>
          <tbody>
            <tr v-for="content in contents" :key="content.id">
              <td data-label="选择" class="admin-table__select">
                <input
                  v-model="selectedIds"
                  type="checkbox"
                  :value="content.id"
                  :aria-label="`选择 ${content.title}`"
                />
              </td>
              <td data-label="文章">
                <strong>{{ content.title }}</strong>
                <small>/{{ content.slug }} · {{ content.type }}</small>
              </td>
              <td data-label="状态">
                <span
                  class="admin-status"
                  :class="`admin-status--${String(content.status || 'DRAFT').toLowerCase()}`"
                >
                  {{ statusLabel(content.status) }}
                </span>
              </td>
              <td data-label="分类 / 标签">
                <div class="admin-taxonomy">
                  <span v-for="category in content.categories || []" :key="`c-${category.id}`">
                    {{ category.name }}
                  </span>
                  <span v-for="tag in content.tags || []" :key="`t-${tag.id}`">
                    #{{ tag.name }}
                  </span>
                  <em v-if="!content.categories?.length && !content.tags?.length">未分类</em>
                </div>
              </td>
              <td data-label="发布时间">
                <template v-if="content.status === 'SCHEDULED' && content.scheduledAt">
                  {{ formatPublishedDate(content.scheduledAt) }} 计划
                </template>
                <template v-else>
                  {{ content.publishedAt ? formatPublishedDate(content.publishedAt) : '未发布' }}
                </template>
              </td>
              <td class="admin-table__actions">
                <router-link :to="adminPath(`contents/${content.id}/edit`)">编辑</router-link>
                <button
                  type="button"
                  :disabled="deletingId === content.id"
                  :aria-busy="deletingId === content.id"
                  @click="handleDelete(content)"
                >
                  删除
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <nav v-if="totalPages > 1" class="admin-pagination" aria-label="文章分页">
        <button
          type="button"
          :disabled="filters.page <= 1"
          @click="changePage(filters.page - 1)"
        >
          上一页
        </button>
        <span>{{ filters.page }} / {{ totalPages }}</span>
        <button
          type="button"
          :disabled="filters.page >= totalPages"
          @click="changePage(filters.page + 1)"
        >
          下一页
        </button>
      </nav>
    </template>
  </section>
</template>
