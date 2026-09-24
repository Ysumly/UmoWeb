import assert from 'node:assert/strict'
import test from 'node:test'

import {
  ADMIN_AUTOSAVE_INTERVAL_MS,
  canAutosave,
  formatAutosaveStatus,
} from './adminAutosave.js'

test('autosave runs only for dirty existing valid articles outside busy states', () => {
  const ready = {
    isEdit: true,
    dirty: true,
    saving: false,
    uploading: false,
    hasValidationErrors: false,
  }

  assert.equal(ADMIN_AUTOSAVE_INTERVAL_MS, 30_000)
  assert.equal(canAutosave(ready), true)
  assert.equal(canAutosave({ ...ready, isEdit: false }), false)
  assert.equal(canAutosave({ ...ready, dirty: false }), false)
  assert.equal(canAutosave({ ...ready, saving: true }), false)
  assert.equal(canAutosave({ ...ready, uploading: true }), false)
  assert.equal(canAutosave({ ...ready, hasValidationErrors: true }), false)
})

test('formats autosave progress and validation feedback', () => {
  assert.equal(formatAutosaveStatus('saving'), '自动保存中...')
  assert.equal(
    formatAutosaveStatus('saved', new Date(2026, 8, 21, 14, 5)),
    '已自动保存 14:05',
  )
  assert.equal(formatAutosaveStatus('error'), '保存失败，将重试')
  assert.equal(formatAutosaveStatus('invalid'), '待修正，自动保存暂停')
  assert.equal(formatAutosaveStatus('idle'), '')
})
