<script setup>
import { useTheme } from '@/theme/useTheme'

const { currentTheme, setTheme } = useTheme()
</script>

<template>
  <div class="theme-switch" role="group" aria-label="主题切换">
    <button
      type="button"
      :aria-pressed="currentTheme === 'light'"
      title="纸本亮色"
      @click="setTheme('light')"
    >
      纸
    </button>
    <button
      type="button"
      :aria-pressed="currentTheme === 'dark'"
      title="墨色暗色"
      @click="setTheme('dark')"
    >
      墨
    </button>
  </div>
</template>
