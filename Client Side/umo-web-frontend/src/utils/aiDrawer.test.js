import test from 'node:test'
import assert from 'node:assert/strict'

import {
  AI_MAX_INPUT_CHARS,
  AI_RESULT_STORAGE_KEY,
  AI_SOURCE_STORAGE_KEY,
  countAiCharacters,
  createAiResultState,
  createAiSourceState,
  getAiMaxInputChars,
  isAiResultEdited,
  parseAiStoredState,
  resolveAiModeKey,
  serializeAiState,
  shouldConfirmResultOverwrite,
  shouldWarnBeforeClose,
  validateAiSource,
} from './aiDrawer.js'

test('uses the frozen AI storage and input limits', () => {
  assert.equal(AI_SOURCE_STORAGE_KEY, 'umo-admin-ai-source-v1')
  assert.equal(AI_RESULT_STORAGE_KEY, 'umo-admin-ai-result-v1')
  assert.equal(AI_MAX_INPUT_CHARS, 20_000)
})

test('counts Unicode code points instead of UTF-16 units', () => {
  assert.equal(countAiCharacters('中文🙂'), 3)
  assert.equal(countAiCharacters(''), 0)
})

test('validates empty and oversized AI source content', () => {
  assert.deepEqual(validateAiSource('', 20_000), {
    valid: false,
    message: '请先输入或带入正文',
  })
  assert.deepEqual(validateAiSource('字'.repeat(20_001), 20_000), {
    valid: false,
    message: '正文不能超过 20000 字符',
  })
  assert.deepEqual(validateAiSource('正文', 20_000), {
    valid: true,
    message: '',
  })
  assert.equal(validateAiSource('🙂'.repeat(20_000), 20_000).valid, true)
})

test('uses the capability limit when it is a positive integer', () => {
  assert.equal(getAiMaxInputChars({ maxInputChars: 12_000 }), 12_000)
  assert.equal(getAiMaxInputChars({ maxInputChars: 0 }), AI_MAX_INPUT_CHARS)
  assert.equal(getAiMaxInputChars(null), AI_MAX_INPUT_CHARS)
})

test('restores a mode only when it is still enabled', () => {
  const modes = [
    { modeKey: 'STRUCTURE_CLEANUP' },
    { modeKey: 'TRANSLATION' },
  ]

  assert.equal(resolveAiModeKey('TRANSLATION', modes), 'TRANSLATION')
  assert.equal(resolveAiModeKey('REMOVED_MODE', modes), 'STRUCTURE_CLEANUP')
  assert.equal(resolveAiModeKey('', []), '')
})

test('creates serializable versioned source and result states', () => {
  const source = createAiSourceState('源正文')
  const result = createAiResultState({
    modeKey: 'STRUCTURE_CLEANUP',
    modeVersion: 3,
    content: '转换结果',
    usage: { inputTokens: 2, outputTokens: 3, totalTokens: 5 },
  })

  assert.equal(source.schemaVersion, 1)
  assert.equal(source.content, '源正文')
  assert.equal(source.updatedAt > 0, true)
  assert.deepEqual(serializeAiState(source), JSON.stringify(source))
  assert.equal(result.schemaVersion, 1)
  assert.equal(result.originalContent, '转换结果')
  assert.equal(result.content, '转换结果')
  assert.equal(result.modeVersion, 3)
  assert.equal(result.usage.totalTokens, 5)
  assert.equal(result.updatedAt > 0, true)
})

test('parses valid source and result records', () => {
  const source = {
    schemaVersion: 1,
    content: '源正文',
    updatedAt: 123,
  }
  const result = {
    schemaVersion: 1,
    modeKey: 'STRUCTURE_CLEANUP',
    modeVersion: 1,
    originalContent: '结果',
    content: '结果',
    usage: null,
    updatedAt: 456,
  }

  assert.deepEqual(parseAiStoredState(JSON.stringify(source), 'source'), source)
  assert.deepEqual(parseAiStoredState(JSON.stringify(result), 'result'), result)
})

test('rejects malformed and unsupported stored AI records', () => {
  assert.equal(parseAiStoredState('{bad json', 'source'), null)
  assert.equal(parseAiStoredState('', 'result'), null)
  assert.equal(parseAiStoredState(JSON.stringify({
    schemaVersion: 2,
    content: '源正文',
    updatedAt: 123,
  }), 'source'), null)
  assert.equal(parseAiStoredState(JSON.stringify({
    schemaVersion: 1,
    content: 42,
    updatedAt: 123,
  }), 'source'), null)
  assert.equal(parseAiStoredState(JSON.stringify({
    schemaVersion: 1,
    modeKey: 'STRUCTURE_CLEANUP',
    modeVersion: '1',
    originalContent: '结果',
    content: '结果',
    usage: null,
    updatedAt: 456,
  }), 'result'), null)
})

test('detects manually edited AI results and confirms overwrite', () => {
  assert.equal(isAiResultEdited({ originalContent: 'A', content: 'B' }), true)
  assert.equal(isAiResultEdited({ originalContent: 'A', content: 'A' }), false)
  assert.equal(isAiResultEdited(null), false)
  assert.equal(shouldConfirmResultOverwrite({
    originalContent: 'A',
    content: 'B',
  }), true)
  assert.equal(shouldConfirmResultOverwrite({
    originalContent: 'A',
    content: 'A',
  }), false)
  assert.equal(shouldConfirmResultOverwrite(null), false)
})

test('warns before close for dirty source or an in-flight request', () => {
  assert.equal(shouldWarnBeforeClose({
    sourceState: { dirty: true },
    inFlight: false,
  }), true)
  assert.equal(shouldWarnBeforeClose({
    sourceState: { dirty: false },
    inFlight: true,
  }), true)
  assert.equal(shouldWarnBeforeClose({
    sourceState: { dirty: false },
    inFlight: false,
  }), false)
})
