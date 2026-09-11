import test from 'node:test'
import assert from 'node:assert/strict'

import {
  getApiErrorMessage,
  parseRetryAfterSeconds,
  shouldClearSessionOnUnauthorized,
} from './apiError.js'

test('extracts the backend error message', () => {
  const error = {
    response: {
      data: { code: 404, message: 'Content not found: missing' },
    },
  }

  assert.equal(getApiErrorMessage(error), 'Content not found: missing')
})

test('uses the fallback when the backend has no usable message', () => {
  assert.equal(getApiErrorMessage({ response: { data: {} } }, '加载失败'), '加载失败')
})

test('parses retry seconds from the rate-limit message', () => {
  const error = {
    response: {
      data: { code: 429, message: 'Too many requests. Please wait 8 seconds.' },
    },
  }

  assert.equal(parseRetryAfterSeconds(error), 8)
})

test('prefers the Retry-After response header', () => {
  const error = {
    response: {
      headers: { 'retry-after': '12' },
      data: { code: 429, message: 'Too many requests. Please wait 8 seconds.' },
    },
  }

  assert.equal(parseRetryAfterSeconds(error), 12)
})

test('keeps the session for credential errors handled by the current page', () => {
  assert.equal(
    shouldClearSessionOnUnauthorized({
      config: { url: '/admin/change-password' },
      response: { status: 401, data: { message: '旧密码错误' } },
    }),
    false,
  )
  assert.equal(
    shouldClearSessionOnUnauthorized({
      config: { url: '/admin/change-password' },
      response: { status: 401, data: { message: 'JWT expired' } },
    }),
    true,
  )
  assert.equal(
    shouldClearSessionOnUnauthorized({
      config: { url: '/admin/contents' },
      response: { status: 401 },
    }),
    true,
  )
})
