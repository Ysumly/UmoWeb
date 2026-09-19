import assert from 'node:assert/strict'
import test from 'node:test'

import {
  buildLibraryFilterChips,
  countActiveLibraryFilters,
} from './libraryFilters.js'

const filters = {
  type: 'NOTE',
  categoryId: 2,
  tagId: 3,
}

const categories = [
  {
    id: 1,
    name: '技术笔记',
    type: 'NOTE',
    children: [{ id: 2, name: 'Vue', type: 'NOTE', children: [] }],
  },
]

const tags = [
  { id: 3, name: '长期主义' },
]

test('counts only applied library filters', () => {
  assert.equal(countActiveLibraryFilters(filters), 3)
  assert.equal(countActiveLibraryFilters({ type: '', categoryId: null, tagId: null }), 0)
})

test('does not treat the all-types option as an active chip', () => {
  assert.deepEqual(
    buildLibraryFilterChips({ type: '', categoryId: null, tagId: null }, categories, tags),
    [],
  )
})

test('builds readable chips for every applied filter', () => {
  assert.deepEqual(
    buildLibraryFilterChips(filters, categories, tags),
    [
      { key: 'type', label: '类型：技术笔记' },
      { key: 'category', label: '分类：Vue' },
      { key: 'tag', label: '标签：长期主义' },
    ],
  )
})

test('omits missing or stale references from filter chips', () => {
  assert.deepEqual(
    buildLibraryFilterChips(
      { type: 'NOVEL', categoryId: 99, tagId: 99 },
      categories,
      tags,
    ),
    [{ key: 'type', label: '类型：连载章节' }],
  )
})
