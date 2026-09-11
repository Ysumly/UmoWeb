import { getApiErrorMessage } from './apiError.js'
import { flattenCategoryTree } from './publicContent.js'

export const CATEGORY_TYPES = ['NOTE', 'NOVEL', 'BOOK_REVIEW']

export const SITE_OPTION_FIELDS = [
  { key: 'site_title', label: '站点标题' },
  { key: 'site_subtitle', label: '站点副标题' },
  { key: 'about_page', label: 'About 页面' },
  { key: 'project_page', label: 'Project 页面' },
]

const SLUG_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_-]{0,99}$/

function textLength(value) {
  return typeof value === 'string' ? value.length : 0
}

function isBlank(value) {
  return typeof value !== 'string' || !value.trim()
}

function collectCategoryIds(category, ids) {
  ids.add(category.id)
  for (const child of category.children || []) {
    collectCategoryIds(child, ids)
  }
}

function findCategory(categories, id) {
  for (const category of categories) {
    if (category.id === id) {
      return category
    }
    const child = findCategory(category.children || [], id)
    if (child) {
      return child
    }
  }
  return null
}

export function buildCategoryParentOptions(categories = [], { type = '', editingId = null } = {}) {
  const excludedIds = new Set()
  if (editingId !== null && editingId !== undefined && editingId !== '') {
    const editingCategory = findCategory(categories, Number(editingId))
    if (editingCategory) {
      collectCategoryIds(editingCategory, excludedIds)
    }
  }

  return flattenCategoryTree(categories).filter((category) => {
    return category.type === type && !excludedIds.has(category.id)
  })
}

export function categoryDetailToForm(category = {}) {
  return {
    name: category.name || '',
    slug: category.slug || '',
    type: category.type || 'NOTE',
    parentId: category.parentId ?? '',
    sortOrder: category.sortOrder ?? 0,
  }
}

export function validateCategoryForm(form = {}) {
  const errors = {}
  const name = typeof form.name === 'string' ? form.name.trim() : ''
  const slug = typeof form.slug === 'string' ? form.slug.trim() : ''
  const sortOrder = form.sortOrder === '' || form.sortOrder === null || form.sortOrder === undefined
    ? 0
    : Number(form.sortOrder)

  if (!name) {
    errors.name = '分类名不能为空'
  } else if (textLength(name) > 100) {
    errors.name = '分类名长度不能超过 100'
  }

  if (!slug) {
    errors.slug = 'slug 不能为空'
  } else if (!SLUG_PATTERN.test(slug)) {
    errors.slug = 'slug 只能包含字母、数字、下划线和连字符'
  }

  if (!CATEGORY_TYPES.includes(form.type)) {
    errors.type = '分类类型无效'
  }

  if (!Number.isInteger(sortOrder)) {
    errors.sortOrder = '排序值必须是整数'
  }

  return errors
}

export function validateTagForm(form = {}) {
  const errors = {}
  const name = typeof form.name === 'string' ? form.name.trim() : ''
  const slug = typeof form.slug === 'string' ? form.slug.trim() : ''

  if (!name) {
    errors.name = '标签名不能为空'
  } else if (textLength(name) > 100) {
    errors.name = '标签名长度不能超过 100'
  }

  if (!slug) {
    errors.slug = 'slug 不能为空'
  } else if (!SLUG_PATTERN.test(slug)) {
    errors.slug = 'slug 只能包含字母、数字、下划线和连字符'
  }

  return errors
}

export function diffSiteOptions(original = {}, current = {}) {
  return SITE_OPTION_FIELDS.flatMap(({ key }) => {
    if (original[key] === current[key]) {
      return []
    }
    return [{ key, value: current[key] }]
  })
}

export function validateSiteOptions(options = {}) {
  return Object.fromEntries(
    SITE_OPTION_FIELDS.flatMap(({ key, label }) => {
      return isBlank(options[key]) ? [[key, `${label}不能为空`]] : []
    }),
  )
}

export function validatePasswordForm(form = {}) {
  const errors = {}
  const oldPassword = typeof form.oldPassword === 'string' ? form.oldPassword : ''
  const newPassword = typeof form.newPassword === 'string' ? form.newPassword : ''
  const confirmPassword = typeof form.confirmPassword === 'string' ? form.confirmPassword : ''

  if (!oldPassword.trim()) {
    errors.oldPassword = '旧密码不能为空'
  } else if (oldPassword.length > 200) {
    errors.oldPassword = '旧密码长度不能超过 200'
  }

  if (!newPassword.trim()) {
    errors.newPassword = '新密码不能为空'
  } else if (newPassword.length < 6) {
    errors.newPassword = '新密码至少 6 位'
  } else if (newPassword.length > 200) {
    errors.newPassword = '新密码长度不能超过 200'
  }

  if (confirmPassword !== newPassword) {
    errors.confirmPassword = '两次输入的新密码不一致'
  }

  return errors
}

function optionLabel(key) {
  return SITE_OPTION_FIELDS.find((field) => field.key === key)?.label || key
}

function optionLabels(keys) {
  return keys.map(optionLabel).join('、')
}

export function formatOptionSaveFailure({ savedKeys = [], failedKey, remainingKeys = [] }) {
  const saved = savedKeys.length ? `已保存：${optionLabels(savedKeys)}；` : ''
  const remaining = remainingKeys.length ? `；未保存：${optionLabels(remainingKeys)}` : ''
  return `${saved}保存失败：${optionLabel(failedKey)}${remaining}`
}

export function getCategoryDeleteError(error) {
  const message = getApiErrorMessage(error, '')
  if (error?.response?.status === 409 && /child category/i.test(message)) {
    return '无法删除：该分类仍有子分类'
  }
  if (error?.response?.status === 409 && /associated with/i.test(message)) {
    return '无法删除：该分类仍有关联文章'
  }
  return getApiErrorMessage(error, '分类删除失败')
}

export function getTagDeleteError(error) {
  const message = getApiErrorMessage(error, '')
  if (error?.response?.status === 409 && /associated with/i.test(message)) {
    return '无法删除：该标签仍有关联文章'
  }
  return getApiErrorMessage(error, '标签删除失败')
}
