<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import ThemeToggle from '@/components/common/ThemeToggle.vue'
import { publicNavigation } from '@/config/publicNavigation'
import { useSiteStore } from '@/stores/site'

const route = useRoute()
const siteStore = useSiteStore()
const menuOpen = ref(false)
const menuButtonRef = ref(null)

const currentSection = computed(() => route.meta.section || route.name)
const siteTitle = computed(() => siteStore.siteTitle || 'Umo')
const siteSubtitle = computed(() => siteStore.siteSubtitle)

watch(
  () => route.fullPath,
  () => {
    menuOpen.value = false
  },
)

function closeMenu({ restoreFocus = false } = {}) {
  if (!menuOpen.value) {
    return
  }
  menuOpen.value = false
  if (restoreFocus) {
    nextTick(() => menuButtonRef.value?.focus())
  }
}

function toggleMenu() {
  if (menuOpen.value) {
    closeMenu()
  } else {
    menuOpen.value = true
  }
}

function handleWindowKeydown(event) {
  if (event.key === 'Escape' && menuOpen.value) {
    event.preventDefault()
    closeMenu({ restoreFocus: true })
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleWindowKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleWindowKeydown)
})
</script>

<template>
  <header class="site-header">
    <div class="site-header__inner">
      <router-link class="site-brand" to="/" :aria-label="`返回 ${siteTitle} 首页`">
        <img class="site-brand__seal" src="/umo-logo.png" alt="" width="38" height="38" />
        <span>
          <strong>{{ siteTitle }}</strong>
          <small v-if="siteSubtitle">{{ siteSubtitle }}</small>
        </span>
      </router-link>

      <nav class="site-nav" aria-label="公开端主导航">
        <router-link
          v-for="item in publicNavigation"
          :key="item.to"
          :to="item.to"
          :class="{ 'is-active': currentSection === item.name }"
        >
          {{ item.label }}
        </router-link>
      </nav>

      <div class="site-header__actions">
        <ThemeToggle />
        <button
          ref="menuButtonRef"
          class="menu-button"
          type="button"
          :aria-expanded="menuOpen"
          aria-controls="public-mobile-navigation"
          :aria-label="menuOpen ? '关闭导航目录' : '打开导航目录'"
          @click="toggleMenu"
        >
          <span />
          <span />
        </button>
      </div>
    </div>

    <transition name="menu-reveal">
      <nav
        v-if="menuOpen"
        id="public-mobile-navigation"
        class="mobile-nav"
        aria-label="移动端主导航"
      >
        <router-link
          v-for="(item, index) in publicNavigation"
          :key="item.to"
          :to="item.to"
          :class="{ 'is-active': currentSection === item.name }"
          :style="{ '--nav-index': index }"
          @click="closeMenu()"
        >
          <span>{{ String(index + 1).padStart(2, '0') }}</span>
          {{ item.label }}
        </router-link>
      </nav>
    </transition>
  </header>
</template>
