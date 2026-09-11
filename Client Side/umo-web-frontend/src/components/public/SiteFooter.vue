<script setup>
import { computed } from 'vue'
import { useSiteStore } from '@/stores/site'

const siteStore = useSiteStore()
const siteTitle = computed(() => siteStore.siteTitle || 'Umo')
const currentYear = new Date().getFullYear()
</script>

<template>
  <footer class="site-footer">
    <div class="site-footer__inner">
      <div>
        <span class="site-footer__eyebrow">{{ siteTitle }} · 长期写作计划</span>
        <p>慢一点写，让每个结论都经过时间。</p>
      </div>
      <nav aria-label="页脚导航">
        <router-link to="/">首页</router-link>
        <router-link to="/library">书库</router-link>
        <router-link to="/search">搜索</router-link>
        <router-link to="/project">项目</router-link>
        <router-link to="/about">关于</router-link>
      </nav>
      <p class="site-footer__copyright">© {{ currentYear }} {{ siteTitle }}</p>
    </div>
  </footer>
</template>
