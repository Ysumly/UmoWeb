<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import SiteFooter from '@/components/public/SiteFooter.vue'
import SiteHeader from '@/components/public/SiteHeader.vue'

const route = useRoute()
const router = useRouter()
const transitionName = ref('page-forward')

const removeNavigationHook = router.afterEach((to, from) => {
  const toOrder = Number(to.meta.order ?? 0)
  const fromOrder = Number(from.meta.order ?? toOrder)
  transitionName.value = toOrder >= fromOrder ? 'page-forward' : 'page-back'
})

onBeforeUnmount(removeNavigationHook)
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
