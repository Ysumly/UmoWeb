const configuredPath = import.meta.env?.VITE_ADMIN_PATH

export function normalizeAdminPath(value) {
  const trimmed = (value || '').trim()
  if (!trimmed) {
    return '/secret-admin'
  }
  const withLeadingSlash = trimmed.startsWith('/') ? trimmed : `/${trimmed}`
  return withLeadingSlash.replace(/\/+$/, '') || '/'
}

export const ADMIN_PATH = normalizeAdminPath(configuredPath)

export function adminPath(path = '') {
  const suffix = path ? (path.startsWith('/') ? path : `/${path}`) : ''
  return `${ADMIN_PATH === '/' ? '' : ADMIN_PATH}${suffix}` || '/'
}
