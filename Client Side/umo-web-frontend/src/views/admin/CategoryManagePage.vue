<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'

import {
  createCategory,
  deleteCategory,
  getAdminCats,
  getAdminCat,
  updateCategory,
} from '@/api/admin'
import ContentState from '@/components/public/ContentState.vue'
import { contentTypes } from '@/config/contentTypes'
import {
  buildCategoryParentOptions,
  categoryDetailToForm,
  getCategoryDeleteError,
  validateCategoryForm,
} from '@/utils/adminManagement'
import { getApiErrorMessage } from '@/utils/apiError'
import { flattenCategoryTree } from '@/utils/publicContent'

const typeOptions = contentTypes.filter((item) => item.value)
const typeFilter = ref('')
const status = ref('loading')
const errorMessage = ref('')
const actionMessage = ref('')
const categories = ref([])
const formOpen = ref(false)
const editingId = ref(null)
const saving = ref(false)
const deletingId = ref(null)
const loadingDetailId = ref(null)
const errors = ref({})
const initialSnapshot = ref('')
let loadRequestId = 0
let detailRequestId = 0

const form = reactive({
  name: '',
  slug: '',
  type: 'NOTE',
  parentId: '',
  sortOrder: 0,
})

const categoryRows = computed(() => flattenCategoryTree(categories.value))
const parentOptions = computed(() => buildCategoryParentOptions(categories.value, {
  type: form.type,
  editingId: editingId.value,
}))
const dirty = computed(() => {
  return formOpen.value
    && initialSnapshot.value
    && JSON.stringify(form) !== initialSnapshot.value
})
const formTitle = computed(() => editingId.value ? '编辑分类' : '新建分类')

function snapshotForm() {
  initialSnapshot.value = JSON.stringify(form)
}

function resetForm(type = typeFilter.value || 'NOTE') {
  editingId.value = null
  Object.assign(form, {
    name: '',
    slug: '',
    type,
    parentId: '',
    sortOrder: 0,
  })
  errors.value = {}
  snapshotForm()
}

function openCreate() {
  detailRequestId += 1
  loadingDetailId.value = null
  resetForm()
  formOpen.value = true
}

async function openEdit(category) {
  if (saving.value || deletingId.value) {
    return
  }

  const currentRequest = ++detailRequestId
  loadingDetailId.value = category.id
  errorMessage.value = ''
  try {
    const response = await getAdminCat(category.id)
    if (currentRequest !== detailRequestId) {
      return
    }
    editingId.value = category.id
    Object.assign(form, categoryDetailToForm(response.data))
    errors.value = {}
    snapshotForm()
    formOpen.value = true
  } catch (error) {
    if (currentRequest === detailRequestId) {
      errorMessage.value = getApiErrorMessage(error, '分类详情加载失败')
    }
  } finally {
    if (currentRequest === detailRequestId) {
      loadingDetailId.value = null
    }
  }
}

function closeForm() {
  if (dirty.value && !window.confirm('当前分类修改尚未保存，确定关闭吗？')) {
    return
  }
  formOpen.value = false
  resetForm()
}

function handleTypeChange() {
  form.parentId = ''
}

async function loadCategories() {
  const currentRequest = ++loadRequestId
  status.value = 'loading'
  errorMessage.value = ''
  try {
    const response = await getAdminCats(typeFilter.value || undefined)
    if (currentRequest !== loadRequestId) {
      return
    }
    categories.value = response.data || []
    status.value = 'success'
  } catch (error) {
    if (currentRequest !== loadRequestId) {
      return
    }
    status.value = 'error'
    errorMessage.value = getApiErrorMessage(error, '分类加载失败')
  }
}

function handleFilterChange(event) {
  const nextType = event.target.value
  if (nextType === typeFilter.value) {
    return
  }
  if (dirty.value && !window.confirm('当前分类修改尚未保存，确定切换筛选吗？')) {
    event.target.value = typeFilter.value
    return
  }
  typeFilter.value = nextType
  detailRequestId += 1
  loadingDetailId.value = null
  formOpen.value = false
  resetForm()
  loadCategories()
}

async function handleSubmit() {
  errors.value = validateCategoryForm(form)
  actionMessage.value = ''
  errorMessage.value = ''
  if (Object.keys(errors.value).length) {
    return
  }

  saving.value = true
  const wasEditing = Boolean(editingId.value)
  const payload = {
    name: form.name.trim(),
    slug: form.slug.trim(),
    type: form.type,
    parentId: form.parentId === '' ? null : Number(form.parentId),
    sortOrder: Number(form.sortOrder || 0),
  }

  try {
    if (editingId.value) {
      await updateCategory(editingId.value, payload)
    } else {
      await createCategory(payload)
    }
    formOpen.value = false
    resetForm()
    actionMessage.value = wasEditing ? '分类已更新' : '分类已创建'
    await loadCategories()
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '分类保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(category) {
  if (!window.confirm(`确定删除分类“${category.name}”吗？`)) {
    return
  }

  deletingId.value = category.id
  actionMessage.value = ''
  errorMessage.value = ''
  try {
    await deleteCategory(category.id)
    if (editingId.value === category.id) {
      formOpen.value = false
      resetForm()
    }
    await loadCategories()
    actionMessage.value = '分类已删除'
  } catch (error) {
    errorMessage.value = getCategoryDeleteError(error)
  } finally {
    deletingId.value = null
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
  if (!dirty.value) {
    return true
  }
  return window.confirm('当前分类修改尚未保存，确定离开吗？')
})

onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  loadCategories()
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})
</script>

<template>
  <section class="admin-page">
    <header class="admin-page__header">
      <div>
        <span class="admin-page__eyebrow">TAXONOMY / 分类</span>
        <h1>分类管理</h1>
        <p>维护笔记、书评和小说目录的层级结构。</p>
      </div>
      <button class="button button--primary" type="button" :disabled="saving" @click="openCreate">
        新建分类
      </button>
    </header>

    <div v-if="actionMessage" class="admin-notice admin-notice--success" role="status">
      {{ actionMessage }}
      <button type="button" aria-label="关闭提示" @click="actionMessage = ''">关闭</button>
    </div>
    <div v-if="errorMessage && status !== 'error'" class="admin-notice" role="alert">
      {{ errorMessage }}
      <button type="button" aria-label="关闭提示" @click="errorMessage = ''">关闭</button>
    </div>

    <section v-if="formOpen" class="admin-management-panel">
      <header>
        <div>
          <span class="admin-page__eyebrow">{{ editingId ? 'EDIT / 编辑' : 'NEW / 新建' }}</span>
          <h2>{{ formTitle }}</h2>
        </div>
        <button class="button button--quiet" type="button" :disabled="saving" @click="closeForm">
          取消
        </button>
      </header>

      <form class="admin-form-grid admin-form-grid--two" @submit.prevent="handleSubmit">
        <label class="admin-field">
          <span>分类名 <b>*</b></span>
          <input v-model="form.name" type="text" maxlength="100" :disabled="saving" required />
          <small v-if="errors.name">{{ errors.name }}</small>
        </label>

        <label class="admin-field">
          <span>slug <b>*</b></span>
          <input
            v-model.trim="form.slug"
            type="text"
            maxlength="100"
            placeholder="java"
            :disabled="saving"
            required
          />
          <small v-if="errors.slug">{{ errors.slug }}</small>
        </label>

        <label class="admin-field">
          <span>类型 <b>*</b></span>
          <select v-model="form.type" :disabled="saving" @change="handleTypeChange">
            <option v-for="item in typeOptions" :key="item.value" :value="item.value">
              {{ item.zh }}
            </option>
          </select>
          <small v-if="errors.type">{{ errors.type }}</small>
        </label>

        <label class="admin-field">
          <span>父分类</span>
          <select v-model="form.parentId" :disabled="saving">
            <option value="">无父分类</option>
            <option
              v-for="parent in parentOptions"
              :key="parent.id"
              :value="parent.id"
            >
              {{ `${'　'.repeat(parent.depth)}${parent.name}` }}
            </option>
          </select>
        </label>

        <label class="admin-field">
          <span>排序值</span>
          <input v-model="form.sortOrder" type="number" step="1" :disabled="saving" />
          <small v-if="errors.sortOrder">{{ errors.sortOrder }}</small>
          <small v-else>数值越小越靠前，默认 0。</small>
        </label>

        <div class="admin-management-panel__actions">
          <button class="button button--primary" type="submit" :disabled="saving">
            {{ saving ? '保存中...' : editingId ? '保存修改' : '创建分类' }}
          </button>
        </div>
      </form>
    </section>

    <form class="admin-filters admin-filters--compact" @submit.prevent>
      <label>
        <span>类型</span>
        <select :value="typeFilter" :disabled="saving" @change="handleFilterChange">
          <option value="">全部类型</option>
          <option v-for="item in typeOptions" :key="item.value" :value="item.value">
            {{ item.zh }}
          </option>
        </select>
      </label>
    </form>

    <ContentState
      v-if="status === 'loading' && !categories.length"
      state="loading"
      title="正在读取分类"
    />

    <ContentState
      v-else-if="status === 'error'"
      state="error"
      title="分类加载失败"
      :message="errorMessage"
      action-label="重新加载"
      @retry="loadCategories"
    />

    <ContentState
      v-else-if="!categoryRows.length"
      state="empty"
      title="还没有分类"
      message="创建第一个分类，为内容建立层级。"
      action-label="新建分类"
      @retry="openCreate"
    />

    <div v-else class="admin-table-wrap">
      <table class="admin-table">
        <thead>
          <tr>
            <th>分类</th>
            <th>类型</th>
            <th aria-label="操作" />
          </tr>
        </thead>
        <tbody>
          <tr v-for="category in categoryRows" :key="category.id">
            <td data-label="分类">
              <span class="admin-table__category-name">
                <i aria-hidden="true">{{ category.depth ? '└' : '·' }}</i>
                <strong>{{ category.name }}</strong>
              </span>
              <small>/{{ category.slug }}</small>
            </td>
            <td data-label="类型">{{ category.type }}</td>
            <td class="admin-table__actions">
              <button
                type="button"
                :disabled="saving || loadingDetailId === category.id || deletingId === category.id"
                :aria-busy="loadingDetailId === category.id"
                @click="openEdit(category)"
              >
                编辑
              </button>
              <button
                type="button"
                :disabled="saving || loadingDetailId === category.id || deletingId === category.id"
                :aria-busy="deletingId === category.id"
                @click="handleDelete(category)"
              >
                删除
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
