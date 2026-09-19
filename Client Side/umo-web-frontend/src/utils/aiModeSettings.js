export const AI_VALIDATION_PROFILES = [
  {
    value: 'EXACT_CONTENT',
    label: '严格内容一致',
    description: '检查非标题正文与源文本完全一致，适合 Markdown 结构整理。',
  },
  {
    value: 'TRANSLATION',
    label: '翻译保真',
    description: '检查数字、专有名词、链接和代码等保真，适合双向翻译。',
  },
  {
    value: 'LIGHT_EXPANSION',
    label: '轻度扩写',
    description: '允许轻度扩写并限制输出长度，适合叙事增强。',
  },
  {
    value: 'NONE',
    label: '仅基础校验',
    description: '只执行空值和长度等基础校验，不保证内容保真。',
  },
]

const MODE_KEY_PATTERN = /^[A-Z][A-Z0-9_]{2,63}$/
const PROFILE_VALUES = new Set(AI_VALIDATION_PROFILES.map(({ value }) => value))

function textLength(value) {
  return typeof value === 'string' ? value.length : 0
}

function normalizedText(value) {
  return typeof value === 'string' ? value.trim() : ''
}

function normalizedSortOrder(value) {
  if (value === '' || value === null || value === undefined) {
    return 0
  }
  return Number(value)
}

function validSortOrder(value) {
  return Number.isInteger(value) && value >= -9999 && value <= 9999
}

export function createEmptyAiModeForm() {
  return {
    modeKey: '',
    name: '',
    description: '',
    systemPrompt: '',
    validationProfile: 'NONE',
    enabled: false,
    sortOrder: 0,
  }
}

export function modeToForm(mode = {}) {
  return {
    modeKey: mode.modeKey || '',
    name: mode.name || '',
    description: mode.description || '',
    systemPrompt: mode.systemPrompt || '',
    validationProfile: mode.validationProfile || 'NONE',
    enabled: mode.enabled === true,
    sortOrder: mode.sortOrder ?? 0,
  }
}

export function validateAiModeForm(form = {}, { creating = false } = {}) {
  const errors = {}
  const modeKey = normalizedText(form.modeKey)
  const name = normalizedText(form.name)
  const systemPrompt = normalizedText(form.systemPrompt)
  const sortOrder = normalizedSortOrder(form.sortOrder)

  if (creating) {
    if (!modeKey) {
      errors.modeKey = 'modeKey 不能为空'
    } else if (!MODE_KEY_PATTERN.test(modeKey)) {
      errors.modeKey = 'modeKey 只能使用大写字母、数字和下划线'
    }
  }

  if (!name) {
    errors.name = '名称不能为空'
  } else if (textLength(name) > 100) {
    errors.name = '名称长度不能超过 100'
  }

  if (textLength(form.description) > 500) {
    errors.description = '说明长度不能超过 500'
  }

  if (!systemPrompt) {
    errors.systemPrompt = '系统提示词不能为空'
  } else if (textLength(form.systemPrompt) > 20_000) {
    errors.systemPrompt = '系统提示词长度不能超过 20000'
  }

  if (!PROFILE_VALUES.has(form.validationProfile)) {
    errors.validationProfile = '校验策略无效'
  }

  if (!validSortOrder(sortOrder)) {
    errors.sortOrder = '排序值必须是 -9999 到 9999 的整数'
  }

  return errors
}

export function buildAiModeCreatePayload(form = {}) {
  return {
    modeKey: normalizedText(form.modeKey),
    name: normalizedText(form.name),
    description: normalizedText(form.description),
    systemPrompt: normalizedText(form.systemPrompt),
    validationProfile: form.validationProfile,
    enabled: false,
    sortOrder: normalizedSortOrder(form.sortOrder),
  }
}

export function buildAiModeCopyPayload(form = {}) {
  return {
    modeKey: normalizedText(form.modeKey),
    name: normalizedText(form.name),
  }
}

export function buildAiModeUpdatePayload(mode = {}, form = {}) {
  return {
    name: normalizedText(form.name),
    description: normalizedText(form.description),
    systemPrompt: normalizedText(form.systemPrompt),
    validationProfile: form.validationProfile,
    enabled: form.enabled === true,
    sortOrder: normalizedSortOrder(form.sortOrder),
    expectedVersion: mode.currentVersion,
  }
}

export function sortAiModes(modes = []) {
  return [...modes].sort((left, right) => {
    return (left.sortOrder ?? 0) - (right.sortOrder ?? 0) || left.id - right.id
  })
}

export function formatModeVersion({ versionNo, createdAt } = {}) {
  const timestamp = typeof createdAt === 'string'
    ? createdAt.replace('T', ' ').slice(0, 16)
    : '未知时间'
  return `版本 ${versionNo} · ${timestamp}`
}
