function includesId(items, id) {
  return !id || items.some((item) => item.id === Number(id))
}

export function filterContents(contents, filters = {}) {
  const {
    type = '',
    categoryId = null,
    tagId = null,
    query = '',
  } = filters
  const keyword = query.trim().toLowerCase()

  return contents.filter((content) => {
    if (type && content.type !== type) {
      return false
    }
    if (!includesId(content.categories || [], categoryId)) {
      return false
    }
    if (!includesId(content.tags || [], tagId)) {
      return false
    }
    if (!keyword) {
      return true
    }
    return `${content.title} ${content.summary}`.toLowerCase().includes(keyword)
  })
}

export function paginateContents(items, page = 1, size = 6) {
  const normalizedPage = Math.max(1, Number(page) || 1)
  const normalizedSize = Math.max(1, Math.min(100, Number(size) || 6))
  const start = (normalizedPage - 1) * normalizedSize

  return {
    items: items.slice(start, start + normalizedSize),
    page: normalizedPage,
    size: normalizedSize,
    total: items.length,
  }
}

export function formatPublishedDate(value) {
  if (!value) {
    return ''
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }

  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}年${month}月${day}日`
}
