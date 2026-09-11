<script setup>
import { computed, onMounted, ref } from 'vue'

import { getProject } from '@/api/public'
import ContentState from '@/components/public/ContentState.vue'
import MarkdownArticle from '@/components/public/MarkdownArticle.vue'
import { useSiteStore } from '@/stores/site'
import { getApiErrorMessage } from '@/utils/apiError'

const siteStore = useSiteStore()
const status = ref('loading')
const errorMessage = ref('')
const content = ref('')
const introduction = computed(() => {
  return siteStore.siteSubtitle || '正在构建、维护和长期观察的项目。'
})

async function load() {
  status.value = 'loading'
  errorMessage.value = ''
  try {
    const response = await getProject()
    content.value = response.data.content || ''
    status.value = 'success'
  } catch (error) {
    status.value = 'error'
    errorMessage.value = getApiErrorMessage(error, 'Project 页面加载失败')
  }
}

onMounted(() => {
  siteStore.load().catch(() => {})
  load()
})
</script>

<template>
  <div class="about-page project-page">
    <header class="about-hero">
      <div class="about-hero__seal">P</div>
      <div>
        <span class="editorial-eyebrow">Project / 长期项目</span>
        <h1>把想法做成<br>可以持续维护的东西。</h1>
      </div>
      <p>{{ introduction }}</p>
    </header>

    <div class="about-layout">
      <aside class="about-facts">
        <span>BUILD LOG</span>
        <dl>
          <div><dt>方式</dt><dd>从最小可用系统开始</dd></div>
          <div><dt>方向</dt><dd>工程、写作与叙事实验</dd></div>
          <div><dt>状态</dt><dd>持续迭代</dd></div>
        </dl>
      </aside>
      <div class="about-content">
        <ContentState
          v-if="status === 'loading'"
          state="loading"
          title="正在读取项目页"
        />
        <ContentState
          v-else-if="status === 'error'"
          state="error"
          title="Project 页面加载失败"
          :message="errorMessage"
          action-label="重新加载"
          @retry="load"
        />
        <MarkdownArticle v-else-if="content" :source="content" />
        <ContentState
          v-else
          state="empty"
          title="项目页尚未配置"
          message="站点设置中还没有 Project 页面内容。"
        />
      </div>
    </div>

    <section class="about-closing">
      <span class="editorial-eyebrow">Keep building</span>
      <h2>好的项目不是一次完成，<br>而是在使用中逐渐清晰。</h2>
      <router-link class="button button--primary" to="/library">去书库看看</router-link>
    </section>
  </div>
</template>
