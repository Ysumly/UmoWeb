<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import SiteFooter from '@/components/public/SiteFooter.vue'
import SiteHeader from '@/components/public/SiteHeader.vue'

const route = useRoute()

const transitionName = computed(() => {
  return route.meta.motion === 'cinematic' ? 'page-cinematic' : 'page-focused'
})
</script>

<template>
  <div class="public-shell" :class="`motion-${route.meta.motion || 'focused'}`">
    <div :key="route.fullPath" class="route-wipe" aria-hidden="true" />
    <SiteHeader />
    <main id="main-content" class="public-main">
      <router-view v-slot="{ Component }">
        <transition :name="transitionName" mode="out-in">
          <component :is="Component" :key="route.fullPath" />
        </transition>
      </router-view>
    </main>
    <SiteFooter />
  </div>
</template>
