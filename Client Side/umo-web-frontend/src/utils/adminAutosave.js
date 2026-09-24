export const ADMIN_AUTOSAVE_INTERVAL_MS = 30_000

export function canAutosave({
  isEdit = false,
  dirty = false,
  saving = false,
  uploading = false,
  hasValidationErrors = false,
} = {}) {
  return Boolean(
    isEdit
    && dirty
    && !saving
    && !uploading
    && !hasValidationErrors,
  )
}

export function formatAutosaveStatus(status, savedAt = null) {
  if (status === 'saving') {
    return '自动保存中...'
  }
  if (status === 'saved') {
    const date = savedAt instanceof Date ? savedAt : new Date(savedAt)
    if (!Number.isNaN(date.getTime())) {
      return `已自动保存 ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
    }
  }
  if (status === 'error') {
    return '保存失败，将重试'
  }
  if (status === 'invalid') {
    return '待修正，自动保存暂停'
  }
  return ''
}
