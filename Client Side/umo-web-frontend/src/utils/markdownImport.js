import { marked } from 'marked'
import { parseDocument } from 'yaml'

export const MAX_MARKDOWN_IMPORT_SIZE = 10 * 1024 * 1024

const MARKDOWN_FILE_PATTERN = /\.(?:md|markdown)$/i
const SLUG_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_-]{0,199}$/
const CONTENT_TYPES = new Set(['NOTE', 'BOOK_REVIEW', 'NOVEL'])
const CONTENT_STATUSES = new Set(['DRAFT', 'PUBLISHED'])
const SUPPORTED_KEYS = new Set([
  'title',
  'slug',
  'summary',
  'type',
  'status',
  'categorySlugs',
  'tagSlugs',
  'categories',
  'tags',
  'metadata',
])

export function validateMarkdownImportFile(file) {
  if (!file) {
    return '请选择 Markdown 文件'
  }
  if (!MARKDOWN_FILE_PATTERN.test(String(file.name || '').trim())) {
    return '仅支持 .md 或 .markdown 文件'
  }
  if (Number(file.size) <= 0) {
    return 'Markdown 文件不能为空'
  }
  if (Number(file.size) > MAX_MARKDOWN_IMPORT_SIZE) {
    return 'Markdown 文件不能超过 10 MiB'
  }
  return ''
}

export function parseMarkdownImport({
  filename = '',
  source = '',
  categories = [],
  tags = [],
} = {}) {
  const normalized = normalizeMarkdown(source)
  if (!normalized.trim()) {
    return importError('Markdown 文件不能为空')
  }

  const split = splitFrontMatter(normalized)
  if (split.error) {
    return importError(split.error)
  }

  let data = {}
  if (split.frontMatter !== null) {
    const document = parseDocument(split.frontMatter, {
      maxAliasCount: 50,
      uniqueKeys: true,
    })
    if (document.errors.length) {
      return importError(`YAML 解析失败：${document.errors[0].message}`)
    }
    try {
      data = document.toJS({ maxAliasCount: 50 })
    } catch (error) {
      return importError(`YAML 解析失败：${error.message}`)
    }
    if (!data || Array.isArray(data) || typeof data !== 'object') {
      return importError('front matter 必须是 YAML 对象')
    }
  }

  const errors = {}
  const warnings = []
  const type = normalizeEnum(data.type, CONTENT_TYPES, 'NOTE', 'type', errors)
  const status = normalizeEnum(data.status, CONTENT_STATUSES, 'DRAFT', 'status', errors)
  const categorySlugs = mergeReferenceFields(
    data.categorySlugs,
    data.categories,
    'categorySlugs',
    errors,
  )
  const tagSlugs = mergeReferenceFields(
    data.tagSlugs,
    data.tags,
    'tagSlugs',
    errors,
  )
  const categoryIds = resolveCategoryIds(categorySlugs, categories, type, errors)
  const tagIds = resolveReferenceIds(tagSlugs, tags, 'tagIds', errors)
  const metadata = normalizeMetadata(data.metadata, errors)
  const title = stringField(data.title, errors, 'title', '标题必须是字符串')
    || firstHeading(split.body)
    || filenameStem(filename)
  const slug = stringField(data.slug, errors, 'slug', 'slug 必须是字符串')
    || filenameStem(filename)

  if (!title) {
    errors.title = '未能从 front matter、H1 或文件名确定标题'
  }
  if (!SLUG_PATTERN.test(slug)) {
    errors.slug = 'slug 只能包含字母、数字、下划线和连字符'
  }
  if (type === 'NOVEL' && !categoryIds.some((id) => (
    categories.some((category) => category.id === id && category.type === 'NOVEL')
  ))) {
    errors.categoryIds = '小说必须选择一个小说分类作为作品目录'
  }

  for (const key of Object.keys(data)) {
    if (!SUPPORTED_KEYS.has(key)) {
      warnings.push(`忽略不支持的 front matter 字段：${key}`)
    }
  }
  warnings.push(...imageReferenceWarnings(split.body))

  return {
    form: {
      title,
      slug,
      summary: stringField(data.summary, errors, 'summary', '摘要必须是字符串'),
      type,
      status,
      body: split.body,
      metadata,
      categoryIds,
      tagIds,
    },
    errors,
    warnings,
  }
}

function normalizeMarkdown(value) {
  return String(value ?? '')
    .replace(/^\uFEFF/, '')
    .replace(/\r\n?/g, '\n')
}

function splitFrontMatter(source) {
  if (!source.startsWith('---\n')) {
    return { frontMatter: null, body: source, error: '' }
  }

  const match = source.match(/^---\n([\s\S]*?)\n---(?:\n|$)/)
  if (!match) {
    return { frontMatter: null, body: '', error: 'front matter 未闭合' }
  }

  return {
    frontMatter: match[1],
    body: source.slice(match[0].length),
    error: '',
  }
}

function importError(message) {
  return { errors: { import: message }, warnings: [] }
}

function stringField(value, errors, field, message) {
  if (value === undefined || value === null) {
    return ''
  }
  if (typeof value !== 'string') {
    errors[field] = message
    return ''
  }
  return value.trim()
}

function normalizeEnum(value, allowed, fallback, field, errors) {
  if (value === undefined || value === null || value === '') {
    return fallback
  }
  const normalized = String(value).trim().toUpperCase()
  if (!allowed.has(normalized)) {
    errors[field] = `必须是 ${[...allowed].join('、')} 之一`
    return fallback
  }
  return normalized
}

function mergeReferenceFields(primary, alias, field, errors) {
  const primaryValues = referenceValues(primary, field, errors)
  const aliasValues = referenceValues(alias, field, errors)

  if (primary !== undefined && alias !== undefined
      && JSON.stringify(primaryValues) !== JSON.stringify(aliasValues)) {
    errors[field] = `${field} 与别名字段值冲突`
  }
  return primary === undefined ? aliasValues : primaryValues
}

function referenceValues(value, field, errors) {
  if (value === undefined || value === null || value === '') {
    return []
  }
  const values = Array.isArray(value) ? value : [value]
  if (values.some((item) => typeof item !== 'string' || !item.trim())) {
    errors[field] = `${field} 必须是字符串或非空字符串数组`
    return []
  }
  return [...new Set(values.map((item) => item.trim()))]
}

function resolveReferenceIds(slugs, items, field, errors) {
  const ids = []
  const missing = []

  for (const slug of slugs) {
    const item = items.find((candidate) => String(candidate.slug || '') === slug)
    if (item) {
      ids.push(Number(item.id))
    } else {
      missing.push(slug)
    }
  }
  if (missing.length) {
    errors[field] = `找不到对应的 slug：${missing.join('、')}`
  }
  return ids
}

function resolveCategoryIds(slugs, categories, type, errors) {
  return resolveReferenceIds(
    slugs,
    categories.filter((category) => category.type === type),
    'categoryIds',
    errors,
  )
}

function normalizeMetadata(value, errors) {
  if (value === undefined || value === null) {
    return '{}'
  }
  if (Array.isArray(value) || typeof value !== 'object') {
    errors.metadata = 'metadata 必须是 YAML 对象'
    return '{}'
  }
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    errors.metadata = 'metadata 必须可序列化为 JSON'
    return '{}'
  }
}

function filenameStem(filename) {
  const basename = String(filename || '').split(/[\\/]/).pop() || ''
  return basename.replace(MARKDOWN_FILE_PATTERN, '').trim()
}

function firstHeading(body) {
  const heading = marked.lexer(String(body))
    .find((token) => token.type === 'heading' && token.depth === 1)
  return heading ? inlineText(heading.tokens) || String(heading.text || '').trim() : ''
}

function imageReferenceWarnings(body) {
  const warnings = []
  for (const url of collectImageUrls(marked.lexer(String(body)))) {
    if (/^https?:\/\//i.test(url) || url.startsWith('/images/')) {
      continue
    }
    warnings.push(`图片引用不会自动上传：${url}`)
  }
  return [...new Set(warnings)]
}

function collectImageUrls(value) {
  const urls = []
  if (Array.isArray(value)) {
    for (const item of value) {
      urls.push(...collectImageUrls(item))
    }
    return urls
  }
  if (!value || typeof value !== 'object') {
    return urls
  }
  if (value.type === 'image' && value.href) {
    urls.push(value.href)
  }
  for (const key of ['tokens', 'items', 'header', 'rows']) {
    if (value[key]) {
      urls.push(...collectImageUrls(value[key]))
    }
  }
  return urls
}

function inlineText(tokens = []) {
  return tokens
    .map((token) => {
      if (token.type === 'br' || token.type === 'space') {
        return ' '
      }
      if (Array.isArray(token.tokens)) {
        return inlineText(token.tokens)
      }
      return String(token.text || '')
    })
    .join('')
    .trim()
}
