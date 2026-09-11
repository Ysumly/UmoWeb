import test from 'node:test'
import assert from 'node:assert/strict'

import {
  THEME_STORAGE_KEY,
  applyTheme,
  normalizeTheme,
  resolveInitialTheme,
} from './theme.js'

test('normalizes unknown theme values to light', () => {
  assert.equal(normalizeTheme('sepia'), 'light')
  assert.equal(normalizeTheme('dark'), 'dark')
})

test('prefers a stored theme over the system preference', () => {
  assert.equal(resolveInitialTheme('dark', false), 'dark')
  assert.equal(resolveInitialTheme('light', true), 'light')
})

test('falls back to the system preference when no valid theme is stored', () => {
  assert.equal(resolveInitialTheme(null, true), 'dark')
  assert.equal(resolveInitialTheme('invalid', false), 'light')
})

test('applies and persists the selected theme', () => {
  const attributes = new Map()
  const storage = new Map()
  const root = {
    setAttribute(name, value) {
      attributes.set(name, value)
    },
  }
  const storageAdapter = {
    setItem(key, value) {
      storage.set(key, value)
    },
  }

  applyTheme(root, storageAdapter, 'dark')

  assert.equal(attributes.get('data-theme'), 'dark')
  assert.equal(storage.get(THEME_STORAGE_KEY), 'dark')
})
