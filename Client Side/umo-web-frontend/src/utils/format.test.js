import test from 'node:test'
import assert from 'node:assert/strict'

import { formatPublishedDate } from './format.js'

test('formats API date strings for Chinese editorial metadata', () => {
  assert.equal(formatPublishedDate('2026-06-20T10:00:00'), '2026年06月20日')
})

test('returns an empty label for missing or invalid dates', () => {
  assert.equal(formatPublishedDate(''), '')
  assert.equal(formatPublishedDate('not-a-date'), '')
})
