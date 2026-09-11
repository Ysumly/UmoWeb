import test from 'node:test'
import assert from 'node:assert/strict'

import { normalizeAdminPath } from './adminPath.js'

test('normalizes a custom admin path', () => {
  assert.equal(normalizeAdminPath(' /manage/ '), '/manage')
})

test('falls back to the default admin path', () => {
  assert.equal(normalizeAdminPath(''), '/secret-admin')
})
