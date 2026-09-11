<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import ThemeToggle from '@/components/common/ThemeToggle.vue'
import { useSiteStore } from '@/stores/site'

const route = useRoute()
const siteStore = useSiteStore()
const menuOpen = ref(false)

const navigation = [
  { to: '/', label: '首页', name: 'home' },
  { to: '/library', label: '书库', name: 'library' },
  { to: '/search', label: '搜索', name: 'search' },
  { to: '/project', label: '项目', name: 'project' },
  { to: '/about', label: '关于', name: 'about' },
]

const currentSection = computed(() => route.name)
const siteTitle = computed(() => siteStore.siteTitle || 'Umo')
const siteSubtitle = computed(() => siteStore.siteSubtitle)

watch(
  () => route.fullPath,
  () => {
    menuOpen.value = false
  },
)
</script>

<template>
  <header class="site-header">
    <div class="site-header__inner">
      <router-link class="site-brand" to="/" :aria-label="`返回 ${siteTitle} 首页`">
        <span class="site-brand__seal">U</span>
        <span>
          <strong>{{ siteTitle }}</strong>
          <small v-if="siteSubtitle">{{ siteSubtitle }}</small>
        </span>
      </router-link>

      <nav class="site-nav" aria-label="公开端主导航">
        <router-link
          v-for="item in navigation"
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
          class="menu-button"
          type="button"
          :aria-expanded="menuOpen"
          aria-label="打开导航目录"
          @click="menuOpen = !menuOpen"
        >
          <span />
          <span />
        </button>
      </div>
    </div>

    <transition name="menu-reveal">
      <nav v-if="menuOpen" class="mobile-nav" aria-label="移动端主导航">
        <router-link
          v-for="(item, index) in navigation"
          :key="item.to"
          :to="item.to"
          :style="{ '--nav-index': index }"
        >
          <span>{{ String(index + 1).padStart(2, '0') }}</span>
          {{ item.label }}
        </router-link>
      </nav>
    </transition>
  </header>
</template>
