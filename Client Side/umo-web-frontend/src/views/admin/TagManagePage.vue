<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'

import {
  createTag,
  deleteTag,
  listAdminTags,
  updateTag,
} from '@/api/admin'
import ContentState from '@/components/public/ContentState.vue'
import {
  getTagDeleteError,
  validateTagForm,
} from '@/utils/adminManagement'
import { getApiErrorMessage } from '@/utils/apiError'

const status = ref('loading')
const errorMessage = ref('')
const actionMessage = ref('')
const tags = ref([])
const formOpen = ref(false)
const editingId = ref(null)
const saving = ref(false)
const deletingId = ref(null)
const errors = ref({})
const initialSnapshot = ref('')

const form = reactive({
  name: '',
  slug: '',
})

const dirty = computed(() => {
  return formOpen.value
    && initialSnapshot.value
    && JSON.stringify(form) !== initialSnapshot.value
})
const formTitle = computed(() => editingId.value ? '编辑标签' : '新建标签')

function snapshotForm() {
  initialSnapshot.value = JSON.stringify(form)
}

function resetForm() {
  editingId.value = null
  Object.assign(form, { name: '', slug: '' })
  errors.value = {}
  snapshotForm()
}

function openCreate() {
  resetForm()
  formOpen.value = true
}

function openEdit(tag) {
  editingId.value = tag.id
  Object.assign(form, { name: tag.name, slug: tag.slug })
  errors.value = {}
  snapshotForm()
  formOpen.value = true
}

function closeForm() {
  if (dirty.value && !window.confirm('当前标签修改尚未保存，确定关闭吗？')) {
    return
  }
  formOpen.value = false
  resetForm()
}

async function loadTags() {
  status.value = 'loading'
  errorMessage.value = ''
  try {
    const response = await listAdminTags()
    tags.value = response.data || []
    status.value = 'success'
  } catch (error) {
    status.value = 'error'
    errorMessage.value = getApiErrorMessage(error, '标签加载失败')
  }
}

async function handleSubmit() {
  errors.value = validateTagForm(form)
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
  }

  try {
    if (editingId.value) {
      await updateTag(editingId.value, payload)
    } else {
      await createTag(payload)
    }
    formOpen.value = false
    resetForm()
    actionMessage.value = wasEditing ? '标签已更新' : '标签已创建'
    await loadTags()
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '标签保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(tag) {
  if (!window.confirm(`确定删除标签“${tag.name}”吗？`)) {
    return
  }

  deletingId.value = tag.id
  actionMessage.value = ''
  errorMessage.value = ''
  try {
    await deleteTag(tag.id)
    if (editingId.value === tag.id) {
      formOpen.value = false
      resetForm()
    }
    await loadTags()
    actionMessage.value = '标签已删除'
  } catch (error) {
    errorMessage.value = getTagDeleteError(error)
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
  return window.confirm('当前标签修改尚未保存，确定离开吗？')
})

onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  loadTags()
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})
</script>

<template>
  <section class="admin-page">
    <header class="admin-page__header">
      <div>
        <span class="admin-page__eyebrow">TAXONOMY / 标签</span>
        <h1>标签管理</h1>
        <p>用轻量标签连接不同篇幅和主题的内容。</p>
      </div>
      <button class="button button--primary" type="button" @click="openCreate">
        新建标签
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
        <button class="button button--quiet" type="button" @click="closeForm">取消</button>
      </header>

      <form class="admin-form-grid admin-form-grid--two" @submit.prevent="handleSubmit">
        <label class="admin-field">
          <span>标签名 <b>*</b></span>
          <input v-model="form.name" type="text" maxlength="100" required />
          <small v-if="errors.name">{{ errors.name }}</small>
        </label>

        <label class="admin-field">
          <span>slug <b>*</b></span>
          <input v-model.trim="form.slug" type="text" maxlength="100" placeholder="java" required />
          <small v-if="errors.slug">{{ errors.slug }}</small>
        </label>

        <div class="admin-management-panel__actions">
          <button class="button button--primary" type="submit" :disabled="saving">
            {{ saving ? '保存中...' : editingId ? '保存修改' : '创建标签' }}
          </button>
        </div>
      </form>
    </section>

    <ContentState
      v-if="status === 'loading' && !tags.length"
      state="loading"
      title="正在读取标签"
    />

    <ContentState
      v-else-if="status === 'error'"
      state="error"
      title="标签加载失败"
      :message="errorMessage"
      action-label="重新加载"
      @retry="loadTags"
    />

    <ContentState
      v-else-if="!tags.length"
      state="empty"
      title="还没有标签"
      message="创建第一个标签，为内容补充主题线索。"
      action-label="新建标签"
      @retry="openCreate"
    />

    <div v-else class="admin-table-wrap">
      <table class="admin-table">
        <thead>
          <tr>
            <th>标签</th>
            <th>slug</th>
            <th aria-label="操作" />
          </tr>
        </thead>
        <tbody>
          <tr v-for="tag in tags" :key="tag.id">
            <td data-label="标签">
              <strong>#{{ tag.name }}</strong>
            </td>
            <td data-label="slug">
              <small>{{ tag.slug }}</small>
            </td>
            <td class="admin-table__actions">
              <button type="button" @click="openEdit(tag)">编辑</button>
              <button
                type="button"
                :disabled="deletingId === tag.id"
                @click="handleDelete(tag)"
              >
                {{ deletingId === tag.id ? '删除中' : '删除' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
