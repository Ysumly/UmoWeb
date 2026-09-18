import test from 'node:test'
import assert from 'node:assert/strict'

import { adminPath, normalizeAdminPath } from './adminPath.js'

test('normalizes a custom admin path', () => {
  assert.equal(normalizeAdminPath(' /manage/ '), '/manage')
})

test('falls back to the default admin path', () => {
  assert.equal(normalizeAdminPath(''), '/secret-admin')
})

test('builds the AI settings admin path', () => {
  assert.equal(adminPath('ai-settings'), '/secret-admin/ai-settings')
})
