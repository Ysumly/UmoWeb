<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import SiteFooter from '@/components/public/SiteFooter.vue'
import SiteHeader from '@/components/public/SiteHeader.vue'
import { useSiteStore } from '@/stores/site'

const route = useRoute()
const router = useRouter()
const siteStore = useSiteStore()
const transitionName = ref('page-forward')

const removeNavigationHook = router.afterEach((to, from) => {
  const toOrder = Number(to.meta.order ?? 0)
  const fromOrder = Number(from.meta.order ?? toOrder)
  transitionName.value = toOrder >= fromOrder ? 'page-forward' : 'page-back'
})

onBeforeUnmount(removeNavigationHook)

onMounted(() => {
  siteStore.load().catch(() => {})
})
</script>

<template>
  <div class="public-shell" :class="`motion-${route.meta.motion || 'focused'}`">
    <div v-if="!route.meta.instantTransition" :key="route.fullPath" class="route-wipe" aria-hidden="true" />
    <SiteHeader />
    <div v-if="siteStore.status === 'error'" class="site-notice" role="alert">
      <span>{{ siteStore.error }}</span>
      <button type="button" @click="siteStore.load(true).catch(() => {})">重试</button>
    </div>
    <main id="main-content" class="public-main">
      <router-view v-slot="{ Component }">
        <transition :name="route.meta.instantTransition ? 'instant' : transitionName" mode="out-in">
          <component :is="Component" :key="route.fullPath" />
        </transition>
      </router-view>
    </main>
    <SiteFooter />
  </div>
</template>
