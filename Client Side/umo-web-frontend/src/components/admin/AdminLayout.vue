<script setup>
import { computed, ref, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useRoute, useRouter } from 'vue-router'
import ThemeToggle from '@/components/common/ThemeToggle.vue'
import { adminPath } from '@/config/adminPath'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const menuOpen = ref(false)

const navigation = computed(() => [
  { to: adminPath('contents'), label: '文章管理' },
  { to: adminPath('categories'), label: '分类管理' },
  { to: adminPath('tags'), label: '标签管理' },
  { to: adminPath('options'), label: '站点设置' },
  { to: adminPath('password'), label: '修改密码' },
])

watch(() => route.fullPath, () => {
  menuOpen.value = false
})

function handleLogout() {
  auth.logout()
  return router.push({ name: 'login' })
}
</script>

<template>
  <div class="admin-shell">
    <header class="admin-mobile-header">
      <button
        class="admin-menu-button"
        type="button"
        :aria-expanded="menuOpen"
        aria-controls="admin-navigation"
        @click="menuOpen = !menuOpen"
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
      @click="menuOpen = false"
    />

    <aside id="admin-navigation" class="admin-sidebar" :class="{ 'is-open': menuOpen }">
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
        >
          {{ item.label }}
        </router-link>
      </nav>

      <div class="admin-sidebar__footer">
        <ThemeToggle />
        <button type="button" @click="handleLogout">退出登录</button>
      </div>
    </aside>

    <main class="admin-main">
      <router-view />
    </main>
  </div>
</template>
