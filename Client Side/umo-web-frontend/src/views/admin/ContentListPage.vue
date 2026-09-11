<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
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
            {{ item === 'DRAFT' ? '草稿' : '已发布' }}
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
              <th>文章</th>
              <th>状态</th>
              <th>分类 / 标签</th>
              <th>发布时间</th>
              <th aria-label="操作" />
            </tr>
          </thead>
          <tbody>
            <tr v-for="content in contents" :key="content.id">
              <td data-label="文章">
                <strong>{{ content.title }}</strong>
                <small>/{{ content.slug }} · {{ content.type }}</small>
              </td>
              <td data-label="状态">
                <span
                  class="admin-status"
                  :class="`admin-status--${String(content.status || 'DRAFT').toLowerCase()}`"
                >
                  {{ content.status === 'PUBLISHED' ? '已发布' : '草稿' }}
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
                {{ content.publishedAt ? formatPublishedDate(content.publishedAt) : '未发布' }}
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
