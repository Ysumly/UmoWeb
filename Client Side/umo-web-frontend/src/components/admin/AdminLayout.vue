<script setup>
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'
import { adminPath } from '@/config/adminPath'

const auth = useAuthStore()
const router = useRouter()

function handleLogout() {
  auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="flex min-h-screen">
    <aside class="w-56 bg-gray-900 text-white p-4 flex flex-col gap-4">
      <h2 class="text-lg font-bold">管理后台</h2>
      <nav class="flex flex-col gap-2">
        <router-link :to="adminPath('contents')"   class="text-gray-300 hover:text-white">文章管理</router-link>
        <router-link :to="adminPath('categories')" class="text-gray-300 hover:text-white">分类管理</router-link>
        <router-link :to="adminPath('tags')"       class="text-gray-300 hover:text-white">标签管理</router-link>
        <router-link :to="adminPath('options')"    class="text-gray-300 hover:text-white">站点设置</router-link>
      </nav>
      <button @click="handleLogout" class="mt-auto text-left text-gray-400 hover:text-red-400">退出登录</button>
    </aside>
    <main class="flex-1 p-6 bg-gray-50">
      <router-view />
    </main>
  </div>
</template>
