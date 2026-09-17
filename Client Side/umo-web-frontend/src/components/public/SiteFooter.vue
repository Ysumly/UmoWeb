<script setup>
import { computed } from 'vue'
import { footerNavigation } from '@/config/publicNavigation'
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
        <router-link
          v-for="item in footerNavigation"
          :key="item.to"
          :to="item.to"
        >
          {{ item.label }}
        </router-link>
      </nav>
      <p class="site-footer__copyright">© {{ currentYear }} {{ siteTitle }}</p>
    </div>
  </footer>
</template>
