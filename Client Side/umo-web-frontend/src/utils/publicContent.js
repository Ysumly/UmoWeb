export const PUBLIC_CONTENT_TYPES = ['NOTE', 'BOOK_REVIEW', 'NOVEL']
export const PUBLIC_PAGE_SIZE = 6

export function normalizePositiveInteger(value, fallback = null) {
  const normalized = Number(value)
  if (!Number.isInteger(normalized) || normalized < 1) {
    return fallback
  }
  return normalized
}

export function flattenCategoryTree(categories = [], depth = 0) {
  return categories.flatMap((category) => [
    { ...category, depth },
    ...flattenCategoryTree(category.children || [], depth + 1),
  ])
}

export function resolveLibraryQuery(query = {}) {
  const type = PUBLIC_CONTENT_TYPES.includes(String(query.type))
    ? String(query.type)
    : ''

  return {
    type,
    categoryId: normalizePositiveInteger(query.category),
    tagId: normalizePositiveInteger(query.tag),
    page: normalizePositiveInteger(query.page, 1),
  }
}
