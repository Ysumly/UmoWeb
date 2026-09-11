import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getSiteInfo } from '@/api/public'
import { getApiErrorMessage } from '@/utils/apiError'

export const useSiteStore = defineStore('site', () => {
  const siteTitle = ref('')
  const siteSubtitle = ref('')
  const status = ref('idle')
  const error = ref('')
  let loadPromise = null

  async function load(force = false) {
    if (!force && status.value === 'success') {
      return
    }
    if (!force && loadPromise) {
      return loadPromise
    }

    status.value = 'loading'
    error.value = ''
    loadPromise = getSiteInfo()
      .then(({ data }) => {
        siteTitle.value = data.siteTitle || ''
        siteSubtitle.value = data.siteSubtitle || ''
        status.value = 'success'
      })
      .catch((requestError) => {
        status.value = 'error'
        error.value = getApiErrorMessage(requestError, '站点信息加载失败')
        throw requestError
      })
      .finally(() => {
        loadPromise = null
      })

    return loadPromise
  }

  return {
    siteTitle,
    siteSubtitle,
    status,
    error,
    load,
  }
})
