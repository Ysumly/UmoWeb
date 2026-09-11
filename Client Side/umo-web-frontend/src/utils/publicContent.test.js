import test from 'node:test'
import assert from 'node:assert/strict'

import {
  flattenCategoryTree,
  normalizePositiveInteger,
  resolveLibraryQuery,
} from './publicContent.js'

test('normalizes positive integer query values', () => {
  assert.equal(normalizePositiveInteger('12'), 12)
  assert.equal(normalizePositiveInteger('0'), null)
  assert.equal(normalizePositiveInteger('abc'), null)
})

test('flattens category trees while preserving parent order', () => {
  const result = flattenCategoryTree([
    {
      id: 1,
      name: '编程',
      children: [{ id: 2, name: 'Java', children: [] }],
    },
    { id: 3, name: '小说', children: [] },
  ])

  assert.deepEqual(
    result.map(({ id, depth }) => ({ id, depth })),
    [
      { id: 1, depth: 0 },
      { id: 2, depth: 1 },
      { id: 3, depth: 0 },
    ],
  )
})

test('drops invalid library filters and normalizes the page', () => {
  assert.deepEqual(
    resolveLibraryQuery({
      type: 'INVALID',
      category: '-1',
      tag: '7',
      page: '3',
    }),
    { type: '', categoryId: null, tagId: 7, page: 3 },
  )
})
