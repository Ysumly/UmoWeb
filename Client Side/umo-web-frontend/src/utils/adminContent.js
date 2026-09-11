export const ADMIN_PAGE_SIZE = 10
export const ADMIN_PAGE_SIZE_OPTIONS = [10, 20, 50]
export const ADMIN_CONTENT_TYPES = ['NOTE', 'BOOK_REVIEW', 'NOVEL']
export const ADMIN_CONTENT_STATUSES = ['DRAFT', 'PUBLISHED']
export const ADMIN_CONTENT_SORTS = ['published_at_desc', 'created_at_desc']

const SLUG_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_-]{0,199}$/
const IMAGE_MIME_TYPES = new Set(['image/jpeg', 'image/png', 'image/gif', 'image/webp'])
const MAX_IMAGE_SIZE = 50 * 1024 * 1024

function positiveInteger(value, fallback = null) {
  const normalized = Number(value)
  return Number.isInteger(normalized) && normalized > 0 ? normalized : fallback
}

function enumValue(value, values, fallback = '') {
  const normalized = String(value ?? '')
  return values.includes(normalized) ? normalized : fallback
}

export function resolveAdminContentQuery(query = {}) {
  const size = positiveInteger(query.size, ADMIN_PAGE_SIZE)

  return {
    page: positiveInteger(query.page, 1),
    size: ADMIN_PAGE_SIZE_OPTIONS.includes(size) ? size : ADMIN_PAGE_SIZE,
    type: enumValue(query.type, ADMIN_CONTENT_TYPES),
    status: enumValue(query.status, ADMIN_CONTENT_STATUSES),
    categoryId: positiveInteger(query.categoryId),
    tagId: positiveInteger(query.tagId),
    sort: enumValue(query.sort, ADMIN_CONTENT_SORTS, 'published_at_desc'),
  }
}

export function contentToForm(content = {}) {
  const metadata = content.metadata && Object.keys(content.metadata).length
    ? JSON.stringify(content.metadata, null, 2)
    : ''

  return {
    title: content.title || '',
    slug: content.slug || '',
    summary: content.summary || '',
    type: content.type || 'NOTE',
    status: content.status || 'DRAFT',
    body: content.body || '',
    metadata,
    categoryIds: (content.categories || []).map((category) => category.id),
    tagIds: (content.tags || []).map((tag) => tag.id),
  }
}

export function validateContentForm(form = {}, categories = []) {
  const errors = {}
  const title = String(form.title || '').trim()
  const slug = String(form.slug || '').trim()
  const metadata = String(form.metadata || '').trim()

  if (!title) {
    errors.title = '标题不能为空'
  }
  if (!SLUG_PATTERN.test(slug)) {
    errors.slug = 'slug 只能包含字母、数字、下划线和连字符'
  }
  if (!ADMIN_CONTENT_TYPES.includes(form.type)) {
    errors.type = '请选择有效的内容类型'
  }
  if (!ADMIN_CONTENT_STATUSES.includes(form.status)) {
    errors.status = '请选择有效的内容状态'
  }
  if (metadata) {
    try {
      const parsed = JSON.parse(metadata)
      if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
        errors.metadata = 'metadata 必须是 JSON 对象'
      }
    } catch {
      errors.metadata = 'metadata 必须是合法 JSON'
    }
  }

  if (form.type === 'NOVEL') {
    const selectedIds = Array.isArray(form.categoryIds) ? form.categoryIds : []
    const hasNovelCategory = categories.some(
      (category) => category.type === 'NOVEL' && selectedIds.includes(category.id),
    )
    if (!hasNovelCategory) {
      errors.categoryIds = '小说必须选择一个小说分类作为作品目录'
    }
  }

  return errors
}

export function orderCategoryIds(categoryIds = [], categories = [], type = '') {
  const selectedIds = categoryIds
    .map((id) => Number(id))
    .filter((id) => Number.isInteger(id) && id > 0)
  if (type !== 'NOVEL') {
    return selectedIds
  }

  const novelIds = new Set(
    categories
      .filter((category) => category.type === 'NOVEL')
      .map((category) => category.id),
  )
  return [
    ...selectedIds.filter((id) => novelIds.has(id)),
    ...selectedIds.filter((id) => !novelIds.has(id)),
  ]
}

export function buildContentPayload(form = {}, categories = []) {
  const metadata = String(form.metadata || '').trim()
  const categoryIds = orderCategoryIds(form.categoryIds, categories, form.type)
  const tagIds = (form.tagIds || [])
    .map((id) => Number(id))
    .filter((id) => Number.isInteger(id) && id > 0)

  return {
    title: String(form.title || '').trim(),
    slug: String(form.slug || '').trim(),
    summary: String(form.summary || ''),
    type: form.type,
    status: form.status,
    body: String(form.body || ''),
    metadata: metadata ? JSON.stringify(JSON.parse(metadata)) : null,
    categoryIds,
    tagIds,
  }
}

export function insertImageMarkdown({
  body = '',
  selectionStart = body.length,
  selectionEnd = selectionStart,
  url = '',
  alt = 'image',
} = {}) {
  const source = String(body)
  const start = Math.max(0, Math.min(Number(selectionStart) || 0, source.length))
  const end = Math.max(start, Math.min(Number(selectionEnd) || start, source.length))
  const safeAlt = String(alt || 'image').replaceAll('\\', '\\\\').replaceAll(']', '\\]')
  const markdown = `![${safeAlt}](${url})`

  return {
    body: `${source.slice(0, start)}${markdown}${source.slice(end)}`,
    cursor: start + markdown.length,
  }
}

export function validateImageFile(file) {
  if (!file) {
    return '请选择图片'
  }
  if (!IMAGE_MIME_TYPES.has(file.type)) {
    return '仅支持 JPG、PNG、GIF 和 WebP 图片'
  }
  if (file.size > MAX_IMAGE_SIZE) {
    return '图片不能超过 50MB'
  }
  return ''
}
