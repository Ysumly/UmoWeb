import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getSiteInfo } from '@/api/public'

export const useSiteStore = defineStore('site', () => {
  const title    = ref('')
  const subtitle = ref('')

  async function fetch() {
    const res = await getSiteInfo()
    title.value    = res.data.siteTitle || res.data.data?.siteTitle || ''
    subtitle.value = res.data.siteSubtitle || res.data.data?.siteSubtitle || ''
  }

  return { title, subtitle, fetch }
})
