import assert from 'node:assert/strict'
import test from 'node:test'

import {
  DEFAULT_ACCESS_PRIVACY,
  parseAccessPrivacy,
} from './accessPrivacy.js'

test('access privacy config accepts the public retention contract', () => {
  assert.deepEqual(
    parseAccessPrivacy({
      rawRetentionDays: 7,
      aggregateRetentionDays: 365,
    }),
    {
      rawRetentionDays: 7,
      aggregateRetentionDays: 365,
    },
  )
})

test('access privacy config rejects malformed or unsafe retention values', () => {
  assert.throws(
    () => parseAccessPrivacy({ rawRetentionDays: 6, aggregateRetentionDays: 180 }),
    /原始日志/,
  )
  assert.throws(
    () => parseAccessPrivacy({ rawRetentionDays: 30, aggregateRetentionDays: 0 }),
    /聚合数据/,
  )
  assert.throws(
    () => parseAccessPrivacy({ rawRetentionDays: 30, aggregateRetentionDays: '180' }),
    /聚合数据/,
  )
})

test('access privacy fallback is the documented 30 and 180 day policy', () => {
  assert.deepEqual(DEFAULT_ACCESS_PRIVACY, {
    rawRetentionDays: 30,
    aggregateRetentionDays: 180,
  })
})
