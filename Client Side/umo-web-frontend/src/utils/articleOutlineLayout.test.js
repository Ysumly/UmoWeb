import assert from 'node:assert/strict'
import test from 'node:test'

import { shouldUseOutlineDrawer } from './articleOutlineLayout.js'

test('uses drawer on narrow screens', () => {
  assert.equal(
    shouldUseOutlineDrawer({
      contentHeight: 100,
      viewportHeight: 800,
      narrow: true,
    }),
    true,
  )
})

test('uses drawer only when content exceeds half the viewport', () => {
  assert.equal(
    shouldUseOutlineDrawer({
      contentHeight: 401,
      viewportHeight: 800,
      narrow: false,
    }),
    true,
  )
  assert.equal(
    shouldUseOutlineDrawer({
      contentHeight: 400,
      viewportHeight: 800,
      narrow: false,
    }),
    false,
  )
})

test('treats missing dimensions as zero', () => {
  assert.equal(shouldUseOutlineDrawer(), false)
  assert.equal(
    shouldUseOutlineDrawer({
      contentHeight: undefined,
      viewportHeight: null,
      narrow: false,
    }),
    false,
  )
})

test('ignores non-numeric and negative dimensions', () => {
  assert.equal(
    shouldUseOutlineDrawer({
      contentHeight: '600',
      viewportHeight: '800',
      narrow: false,
    }),
    false,
  )
  assert.equal(
    shouldUseOutlineDrawer({
      contentHeight: -1,
      viewportHeight: -1,
      narrow: false,
    }),
    false,
  )
})
