export const EDITOR_DRAFT_KEY = 'umo-editor-draft-v1'
export const DEFAULT_EDITOR_FILENAME = 'untitled.md'

const EDITOR_DRAFT_VERSION = 1
const INVALID_FILENAME_CHARS = /[<>:"/\\|?*\u0000-\u001F\u007F]/g

function sanitizeFileName(value) {
  const raw = String(value ?? '')
  const basename = raw.split(/[\\/]/).pop() || ''

  return basename
    .replace(INVALID_FILENAME_CHARS, '-')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/^\.+/, '')
    .replace(/[.\s]+$/, '')
}

export function normalizeEditorFileName(value) {
  const safeName = sanitizeFileName(value)
  const baseName = safeName
    .replace(/\.(?:md|markdown)$/i, '')
    .replace(/[.\s]+$/, '')

  return `${baseName || 'untitled'}.md`
}

export function isMarkdownFile(file) {
  return Boolean(file && typeof file.name === 'string' && /\.(?:md|markdown)$/i.test(file.name.trim()))
}

export function createEditorDraft({
  content = '',
  fileName = DEFAULT_EDITOR_FILENAME,
  updatedAt = Date.now(),
} = {}) {
  const timestamp = Number(updatedAt)

  return {
    version: EDITOR_DRAFT_VERSION,
    content: String(content),
    fileName: normalizeEditorFileName(fileName),
    updatedAt: Number.isFinite(timestamp) && timestamp > 0 ? timestamp : Date.now(),
  }
}

export function parseEditorDraft(serialized) {
  if (typeof serialized !== 'string' || !serialized) {
    return null
  }

  try {
    const draft = JSON.parse(serialized)
    if (
      draft?.version !== EDITOR_DRAFT_VERSION
      || typeof draft.content !== 'string'
      || typeof draft.fileName !== 'string'
      || !Number.isFinite(Number(draft.updatedAt))
      || Number(draft.updatedAt) <= 0
    ) {
      return null
    }

    return createEditorDraft(draft)
  } catch {
    return null
  }
}

export function createMarkdownBlob(content = '') {
  return new Blob([String(content)], { type: 'text/markdown;charset=utf-8' })
}
