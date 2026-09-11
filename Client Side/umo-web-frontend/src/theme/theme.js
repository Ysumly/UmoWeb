export const THEME_STORAGE_KEY = 'umo-theme'

export function normalizeTheme(value) {
  return value === 'dark' ? 'dark' : 'light'
}

export function resolveInitialTheme(storedTheme, prefersDark) {
  if (storedTheme === 'light' || storedTheme === 'dark') {
    return storedTheme
  }
  return prefersDark ? 'dark' : 'light'
}

export function applyTheme(root, storage, theme) {
  const normalized = normalizeTheme(theme)
  root.setAttribute('data-theme', normalized)
  storage.setItem(THEME_STORAGE_KEY, normalized)
  return normalized
}
