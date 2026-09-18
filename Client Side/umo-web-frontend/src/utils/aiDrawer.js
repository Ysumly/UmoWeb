export const AI_SOURCE_STORAGE_KEY = 'umo-admin-ai-source-v1'
export const AI_RESULT_STORAGE_KEY = 'umo-admin-ai-result-v1'
export const AI_MAX_INPUT_CHARS = 20_000

const AI_STORAGE_SCHEMA_VERSION = 1

export function countAiCharacters(value = '') {
  return Array.from(String(value ?? '')).length
}

export function getAiMaxInputChars(capabilities) {
  const value = Number(capabilities?.maxInputChars)
  return Number.isInteger(value) && value > 0 ? value : AI_MAX_INPUT_CHARS
}

export function validateAiSource(value, maxInputChars = AI_MAX_INPUT_CHARS) {
  const content = String(value ?? '')
  if (!content.trim()) {
    return {
      valid: false,
      message: '请先输入或带入正文',
    }
  }

  if (countAiCharacters(content) > maxInputChars) {
    return {
      valid: false,
      message: `正文不能超过 ${maxInputChars} 字符`,
    }
  }

  return {
    valid: true,
    message: '',
  }
}

export function createAiSourceState(source = '') {
  return {
    schemaVersion: AI_STORAGE_SCHEMA_VERSION,
    content: String(source ?? ''),
    updatedAt: Date.now(),
  }
}

export function createAiResultState(result = {}) {
  const content = String(result.content ?? '')
  return {
    schemaVersion: AI_STORAGE_SCHEMA_VERSION,
    modeKey: String(result.modeKey ?? '').trim(),
    modeVersion: Number.isInteger(result.modeVersion) ? result.modeVersion : 0,
    originalContent: content,
    content,
    usage: normalizeUsage(result.usage),
    updatedAt: Date.now(),
  }
}

export function parseAiStoredState(rawValue, type) {
  if (typeof rawValue !== 'string' || !rawValue.trim()) {
    return null
  }

  let parsed
  try {
    parsed = JSON.parse(rawValue)
  } catch {
    return null
  }

  if (!isRecord(parsed) || parsed.schemaVersion !== AI_STORAGE_SCHEMA_VERSION) {
    return null
  }

  if (!Number.isFinite(parsed.updatedAt)) {
    return null
  }

  if (type === 'source') {
    return typeof parsed.content === 'string'
      ? {
          schemaVersion: AI_STORAGE_SCHEMA_VERSION,
          content: parsed.content,
          updatedAt: parsed.updatedAt,
        }
      : null
  }

  if (type !== 'result') {
    return null
  }

  if (
    typeof parsed.modeKey !== 'string'
    || !parsed.modeKey.trim()
    || !Number.isInteger(parsed.modeVersion)
    || typeof parsed.originalContent !== 'string'
    || typeof parsed.content !== 'string'
    || !isValidUsage(parsed.usage)
  ) {
    return null
  }

  return {
    schemaVersion: AI_STORAGE_SCHEMA_VERSION,
    modeKey: parsed.modeKey,
    modeVersion: parsed.modeVersion,
    originalContent: parsed.originalContent,
    content: parsed.content,
    usage: normalizeUsage(parsed.usage),
    updatedAt: parsed.updatedAt,
  }
}

export function serializeAiState(state) {
  return JSON.stringify(state)
}

export function isAiResultEdited(resultState) {
  return Boolean(
    resultState
    && typeof resultState.originalContent === 'string'
    && typeof resultState.content === 'string'
    && resultState.originalContent !== resultState.content,
  )
}

export function shouldConfirmResultOverwrite(resultState) {
  return isAiResultEdited(resultState)
}

export function shouldWarnBeforeClose({ sourceState, inFlight } = {}) {
  return Boolean(sourceState?.dirty || inFlight)
}

function normalizeUsage(usage) {
  if (!isRecord(usage)) {
    return null
  }

  return {
    inputTokens: Number.isFinite(usage.inputTokens) ? usage.inputTokens : 0,
    outputTokens: Number.isFinite(usage.outputTokens) ? usage.outputTokens : 0,
    totalTokens: Number.isFinite(usage.totalTokens) ? usage.totalTokens : 0,
  }
}

function isValidUsage(usage) {
  if (usage === null || usage === undefined) {
    return true
  }
  if (!isRecord(usage)) {
    return false
  }
  return (
    Number.isFinite(usage.inputTokens)
    && Number.isFinite(usage.outputTokens)
    && Number.isFinite(usage.totalTokens)
  )
}

function isRecord(value) {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}
