import test from 'node:test'
import assert from 'node:assert/strict'

import { resolveAuthNavigation } from './guard.js'

const fullPath = '/target'

test('public route without meta is accessible without a token', () => {
  assert.equal(resolveAuthNavigation({ fullPath, meta: undefined }, null), null)
})

test('not-found route without meta is accessible without a token', () => {
  assert.equal(resolveAuthNavigation({ fullPath, meta: {} }, null), null)
})

test('login route marked public is accessible without a token', () => {
  assert.equal(
    resolveAuthNavigation({ fullPath, meta: { requiresAuth: false } }, null),
    null,
  )
})

test('protected route redirects to login without a token', () => {
  assert.deepEqual(
    resolveAuthNavigation({ fullPath, meta: { requiresAuth: true } }, null),
    { name: 'login', query: { redirect: fullPath } },
  )
})

test('protected route is accessible with a token', () => {
  assert.equal(
    resolveAuthNavigation({ fullPath, meta: { requiresAuth: true } }, 'token'),
    null,
  )
})
