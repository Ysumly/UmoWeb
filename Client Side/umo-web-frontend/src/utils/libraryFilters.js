import { contentTypes } from '../config/contentTypes.js'
import { flattenCategoryTree } from './publicContent.js'

export function countActiveLibraryFilters(filters = {}) {
  return ['type', 'categoryId', 'tagId']
    .filter((key) => Boolean(filters[key]))
    .length
}

export function buildLibraryFilterChips(filters = {}, categories = [], tags = []) {
  const chips = []
  const type = contentTypes.find((item) => item.value === filters.type)
  if (filters.type && type) {
    chips.push({ key: 'type', label: `类型：${type.zh}` })
  }

  const category = flattenCategoryTree(categories)
    .find((item) => item.id === filters.categoryId)
  if (category) {
    chips.push({ key: 'category', label: `分类：${category.name}` })
  }

  const tag = tags.find((item) => item.id === filters.tagId)
  if (tag) {
    chips.push({ key: 'tag', label: `标签：${tag.name}` })
  }

  return chips
}
