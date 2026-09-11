import test from 'node:test'
import assert from 'node:assert/strict'

import { filterContents, formatPublishedDate, paginateContents } from './catalog.js'

const contents = [
  {
    id: 1,
    title: 'Spring Boot 快速上手',
    summary: '从零搭建一个 Spring Boot 项目',
    type: 'NOTE',
    categories: [{ id: 3 }],
    tags: [{ id: 2 }],
  },
  {
    id: 2,
    title: '读《代码整洁之道》',
    summary: '关于协作成本与代码质量',
    type: 'BOOK_REVIEW',
    categories: [{ id: 10 }],
    tags: [{ id: 9 }],
  },
]

test('filters contents by type, exact category, exact tag, and text', () => {
  assert.deepEqual(
    filterContents(contents, { type: 'NOTE', categoryId: 3, tagId: 2, query: 'Spring' })
      .map((item) => item.id),
    [1],
  )
  assert.deepEqual(
    filterContents(contents, { query: '代码质量' }).map((item) => item.id),
    [2],
  )
})

test('paginates contents without changing their order', () => {
  const page = paginateContents([1, 2, 3, 4, 5], 2, 2)

  assert.deepEqual(page.items, [3, 4])
  assert.equal(page.page, 2)
  assert.equal(page.total, 5)
})

test('formats API date strings for Chinese editorial metadata', () => {
  assert.equal(formatPublishedDate('2026-06-20T10:00:00'), '2026年06月20日')
})
