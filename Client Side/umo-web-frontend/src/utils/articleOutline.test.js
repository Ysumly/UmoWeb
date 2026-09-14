import test from 'node:test'
import assert from 'node:assert/strict'

import {
  findOutlinePath,
  flattenOutline,
  getVisibleOutlineRows,
} from './articleOutline.js'

const outline = [
  {
    id: '第一节',
    text: '第一节',
    depth: 2,
    children: [
      {
        id: '子节一',
        text: '子节一',
        depth: 3,
        children: [],
      },
      {
        id: '子节二',
        text: '子节二',
        depth: 3,
        children: [],
      },
    ],
  },
  {
    id: '第二节',
    text: '第二节',
    depth: 2,
    children: [],
  },
]

test('flattens nested outline nodes with their structural level', () => {
  assert.deepEqual(
    flattenOutline(outline).map(({ id, level }) => ({ id, level })),
    [
      { id: '第一节', level: 0 },
      { id: '子节一', level: 1 },
      { id: '子节二', level: 1 },
      { id: '第二节', level: 0 },
    ],
  )
})

test('finds every ancestor on the active outline path', () => {
  assert.deepEqual(findOutlinePath(outline, '子节二'), [
    '第一节',
    '子节二',
  ])
  assert.deepEqual(findOutlinePath(outline, 'missing'), [])
})

test('shows roots by default and expands only the active path', () => {
  const rows = getVisibleOutlineRows(outline, {
    activeId: '子节二',
    manuallyExpandedIds: new Set(),
  })

  assert.deepEqual(rows.map((row) => row.id), [
    '第一节',
    '子节一',
    '子节二',
    '第二节',
  ])
  assert.equal(rows[0].expanded, true)
  assert.equal(rows[0].activeAncestor, true)
  assert.equal(rows[2].active, true)
})

test('keeps manually expanded branches visible after focus moves away', () => {
  const rows = getVisibleOutlineRows(outline, {
    activeId: '第二节',
    manuallyExpandedIds: new Set(['第一节']),
  })

  assert.deepEqual(rows.map((row) => row.id), [
    '第一节',
    '子节一',
    '子节二',
    '第二节',
  ])
  assert.equal(rows[0].expanded, true)
  assert.equal(rows[3].active, true)
})
