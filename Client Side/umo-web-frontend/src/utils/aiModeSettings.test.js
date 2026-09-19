import test from 'node:test'
import assert from 'node:assert/strict'

import {
  AI_VALIDATION_PROFILES,
  buildAiModeCopyPayload,
  buildAiModeCreatePayload,
  buildAiModeUpdatePayload,
  createEmptyAiModeForm,
  formatModeVersion,
  modeToForm,
  sortAiModes,
  validateAiModeForm,
} from './aiModeSettings.js'

test('rejects invalid mode key and blank prompt', () => {
  const errors = validateAiModeForm({
    modeKey: 'bad-key',
    name: '翻译',
    systemPrompt: '',
    validationProfile: 'NONE',
  }, { creating: true })

  assert.equal(errors.modeKey, 'modeKey 只能使用大写字母、数字和下划线')
  assert.equal(errors.systemPrompt, '系统提示词不能为空')
})

test('validates supported field boundaries and profile', () => {
  const errors = validateAiModeForm({
    modeKey: 'CUSTOM_MODE',
    name: '名'.repeat(101),
    description: '说'.repeat(501),
    systemPrompt: '词'.repeat(20_001),
    validationProfile: 'UNKNOWN',
    sortOrder: 10_000,
  }, { creating: true })

  assert.equal(errors.name, '名称长度不能超过 100')
  assert.equal(errors.description, '说明长度不能超过 500')
  assert.equal(errors.systemPrompt, '系统提示词长度不能超过 20000')
  assert.equal(errors.validationProfile, '校验策略无效')
  assert.equal(errors.sortOrder, '排序值必须是 -9999 到 9999 的整数')
})

test('editing ignores the immutable mode key', () => {
  const errors = validateAiModeForm({
    modeKey: 'bad-key',
    name: '翻译',
    systemPrompt: '你是翻译器。',
    validationProfile: 'TRANSLATION',
    sortOrder: 0,
  })

  assert.equal(errors.modeKey, undefined)
})

test('creates a disabled mode payload with canonical values', () => {
  assert.deepEqual(buildAiModeCreatePayload({
    modeKey: ' POEM_REWRITE ',
    name: ' 诗歌改写 ',
    description: '',
    systemPrompt: ' 你是诗歌改写器。 ',
    validationProfile: 'NONE',
    enabled: true,
    sortOrder: '10',
  }), {
    modeKey: 'POEM_REWRITE',
    name: '诗歌改写',
    description: '',
    systemPrompt: '你是诗歌改写器。',
    validationProfile: 'NONE',
    enabled: false,
    sortOrder: 10,
  })
})

test('copies only the new identity fields', () => {
  assert.deepEqual(buildAiModeCopyPayload({
    modeKey: 'POEM_COPY',
    name: '诗歌改写副本',
    systemPrompt: '不会被提交',
  }), {
    modeKey: 'POEM_COPY',
    name: '诗歌改写副本',
  })
})

test('metadata-only edit keeps expected version', () => {
  const payload = buildAiModeUpdatePayload(
    { currentVersion: 4, systemPrompt: 'same', validationProfile: 'NONE' },
    {
      name: '新名称',
      description: '',
      systemPrompt: 'same',
      validationProfile: 'NONE',
      enabled: true,
      sortOrder: 2,
    },
  )

  assert.equal(payload.expectedVersion, 4)
  assert.equal(payload.name, '新名称')
  assert.equal(payload.systemPrompt, 'same')
  assert.equal(payload.validationProfile, 'NONE')
})

test('prompt edit also keeps expected version for server-side versioning', () => {
  const payload = buildAiModeUpdatePayload(
    { currentVersion: 7, systemPrompt: 'old', validationProfile: 'NONE' },
    {
      name: '模式',
      description: '说明',
      systemPrompt: 'new',
      validationProfile: 'NONE',
      enabled: false,
      sortOrder: -2,
    },
  )

  assert.equal(payload.expectedVersion, 7)
  assert.equal(payload.systemPrompt, 'new')
})

test('form conversion is a deep-enough copy of scalar state', () => {
  const mode = {
    modeKey: 'CUSTOM_MODE',
    name: '自定义',
    description: '说明',
    systemPrompt: '提示词',
    validationProfile: 'NONE',
    enabled: true,
    sortOrder: 3,
  }
  const form = modeToForm(mode)

  form.name = '已修改'
  form.systemPrompt = '新提示词'

  assert.equal(mode.name, '自定义')
  assert.equal(mode.systemPrompt, '提示词')
  assert.equal(form.enabled, true)
})

test('sorts modes by sort order and id', () => {
  assert.deepEqual(sortAiModes([
    { id: 3, sortOrder: 2 },
    { id: 2, sortOrder: 1 },
    { id: 1, sortOrder: 1 },
  ]).map(({ id }) => id), [1, 2, 3])
})

test('provides the frozen validation profile labels', () => {
  assert.deepEqual(AI_VALIDATION_PROFILES.map(({ value }) => value), [
    'EXACT_CONTENT',
    'TRANSLATION',
    'LIGHT_EXPANSION',
    'NONE',
  ])
  assert.equal(AI_VALIDATION_PROFILES.every(({ description }) => (
    typeof description === 'string' && description.length > 0
  )), true)
})

test('formats a version timestamp without timezone shifts', () => {
  assert.equal(formatModeVersion({
    versionNo: 7,
    createdAt: '2026-09-18T12:30:00',
  }), '版本 7 · 2026-09-18 12:30')
})

test('empty form starts disabled with the basic validation profile', () => {
  assert.deepEqual(createEmptyAiModeForm(), {
    modeKey: '',
    name: '',
    description: '',
    systemPrompt: '',
    validationProfile: 'NONE',
    enabled: false,
    sortOrder: 0,
  })
})
