<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useRoute, useRouter } from 'vue-router'
import ThemeToggle from '@/components/common/ThemeToggle.vue'
import { adminPath } from '@/config/adminPath'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const menuOpen = ref(false)
const menuButtonRef = ref(null)
const sidebarRef = ref(null)
const sidebarTransitionMs = 180
let bodyOverflow = ''
let restoreFocusOnClose = true

const navigation = computed(() => [
  { to: adminPath('contents'), label: '文章管理' },
  { to: adminPath('categories'), label: '分类管理' },
  { to: adminPath('tags'), label: '标签管理' },
  { to: adminPath('images'), label: '图片管理' },
  { to: adminPath('ai-settings'), label: 'AI 设置' },
  { to: adminPath('options'), label: '站点设置' },
  { to: adminPath('password'), label: '修改密码' },
])

function getSidebarFocusableElements() {
  if (!sidebarRef.value) {
    return []
  }
  return [...sidebarRef.value.querySelectorAll(
    'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled])',
  )].filter((element) => {
    const styles = window.getComputedStyle(element)
    return styles.display !== 'none' && styles.visibility !== 'hidden'
  })
}

function focusFirstSidebarItem() {
  window.setTimeout(() => {
    if (!menuOpen.value) {
      return
    }
    sidebarRef.value
      ?.querySelector('a[href], button:not([disabled]), input:not([disabled])')
      ?.focus()
  }, sidebarTransitionMs)
}

function openMenu() {
  restoreFocusOnClose = true
  menuOpen.value = true
  nextTick(focusFirstSidebarItem)
}

function closeMenu({ restoreFocus = true } = {}) {
  if (!menuOpen.value) {
    return
  }
  restoreFocusOnClose = restoreFocus
  menuOpen.value = false
}

function toggleMenu() {
  if (menuOpen.value) {
    closeMenu()
  } else {
    openMenu()
  }
}

function handleSidebarKeydown(event) {
  if (event.key === 'Escape') {
    event.preventDefault()
    closeMenu()
    return
  }
  if (event.key !== 'Tab') {
    return
  }

  const focusable = getSidebarFocusableElements()
  if (!focusable.length) {
    return
  }
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

function handleWindowKeydown(event) {
  if (event.key === 'Escape' && menuOpen.value) {
    event.preventDefault()
    closeMenu()
  }
}

watch(() => route.fullPath, () => {
  closeMenu({ restoreFocus: false })
})

watch(menuOpen, (isOpen, wasOpen) => {
  if (isOpen) {
    bodyOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
  } else {
    document.body.style.overflow = bodyOverflow
    bodyOverflow = ''
    if (wasOpen && restoreFocusOnClose) {
      nextTick(() => menuButtonRef.value?.focus())
    }
    restoreFocusOnClose = true
  }
})

function handleLogout() {
  auth.logout()
  return router.push({ name: 'login' })
}

onMounted(() => {
  window.addEventListener('keydown', handleWindowKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleWindowKeydown)
  if (menuOpen.value) {
    document.body.style.overflow = bodyOverflow
  }
})
</script>

<template>
  <div class="admin-shell">
    <header class="admin-mobile-header" :inert="menuOpen">
      <button
        ref="menuButtonRef"
        class="admin-menu-button"
        type="button"
        :aria-expanded="menuOpen"
        aria-controls="admin-navigation"
        :aria-label="menuOpen ? '关闭管理菜单' : '打开管理菜单'"
        @click="toggleMenu"
      >
        {{ menuOpen ? '关闭' : '菜单' }}
      </button>
      <strong>Umo 管理</strong>
      <ThemeToggle />
    </header>

    <button
      v-if="menuOpen"
      class="admin-sidebar-backdrop"
      type="button"
      aria-label="关闭管理菜单"
      @click="closeMenu()"
    />

    <aside
      id="admin-navigation"
      ref="sidebarRef"
      class="admin-sidebar"
      :class="{ 'is-open': menuOpen }"
      @keydown="handleSidebarKeydown"
    >
      <div class="admin-brand">
        <img class="admin-brand__seal" src="/umo-logo.png" alt="" width="42" height="42" />
        <div>
          <strong>Umo 管理</strong>
          <small>CONTENT STUDIO</small>
        </div>
      </div>

      <nav class="admin-navigation" aria-label="管理导航">
        <router-link
          v-for="item in navigation"
          :key="item.to"
          :to="item.to"
          @click="closeMenu({ restoreFocus: false })"
        >
          {{ item.label }}
        </router-link>
      </nav>

      <div class="admin-sidebar__footer">
        <ThemeToggle />
        <button type="button" @click="handleLogout">退出登录</button>
      </div>
    </aside>

    <main class="admin-main" :inert="menuOpen">
      <router-view />
    </main>
  </div>
</template>
