import { readonly, ref } from 'vue'

import {
  THEME_STORAGE_KEY,
  applyTheme,
  normalizeTheme,
  resolveInitialTheme,
} from './theme.js'

const currentTheme = ref('light')
let initialized = false

function persistTheme(theme) {
  currentTheme.value = applyTheme(document.documentElement, localStorage, theme)
  document.documentElement.style.colorScheme = currentTheme.value
}

export function useTheme() {
  if (!initialized && typeof document !== 'undefined') {
    const storedTheme = localStorage.getItem(THEME_STORAGE_KEY)
    const prefersDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false
    currentTheme.value = resolveInitialTheme(storedTheme, prefersDark)
    document.documentElement.style.colorScheme = currentTheme.value
    initialized = true
  }

  function setTheme(theme) {
    persistTheme(normalizeTheme(theme))
  }

  function toggleTheme() {
    persistTheme(currentTheme.value === 'dark' ? 'light' : 'dark')
  }

  return {
    currentTheme: readonly(currentTheme),
    setTheme,
    toggleTheme,
  }
}
