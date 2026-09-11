<script setup>
import { computed, onMounted, ref } from 'vue'

import { getAbout } from '@/api/public'
import ContentState from '@/components/public/ContentState.vue'
import MarkdownArticle from '@/components/public/MarkdownArticle.vue'
import { useSiteStore } from '@/stores/site'
import { getApiErrorMessage } from '@/utils/apiError'

const siteStore = useSiteStore()
const status = ref('loading')
const errorMessage = ref('')
const content = ref('')
const introduction = computed(() => {
  return siteStore.siteSubtitle || '一个关于技术、阅读与长期创作的私人空间。'
})

async function load() {
  status.value = 'loading'
  errorMessage.value = ''
  try {
    const response = await getAbout()
    content.value = response.data.content || ''
    status.value = 'success'
  } catch (error) {
    status.value = 'error'
    errorMessage.value = getApiErrorMessage(error, 'About 页面加载失败')
  }
}

onMounted(() => {
  siteStore.load().catch(() => {})
  load()
})
</script>

<template>
  <div class="about-page">
    <header class="about-hero">
      <div class="about-hero__seal">U</div>
      <div>
        <span class="editorial-eyebrow">About / 关于这个空间</span>
        <h1>一个人如何留下<br>长期思考的痕迹。</h1>
      </div>
      <p>{{ introduction }}</p>
    </header>

    <div class="about-layout">
      <aside class="about-facts">
        <span>EDITION 01</span>
        <dl>
          <div><dt>地点</dt><dd>中国 · 上海</dd></div>
          <div><dt>方向</dt><dd>Java / Spring / Vue</dd></div>
          <div><dt>正在写</dt><dd>技术笔记与长篇故事</dd></div>
        </dl>
      </aside>
      <div class="about-content">
        <ContentState
          v-if="status === 'loading'"
          state="loading"
          title="正在读取关于页"
        />
        <ContentState
          v-else-if="status === 'error'"
          state="error"
          title="About 页面加载失败"
          :message="errorMessage"
          action-label="重新加载"
          @retry="load"
        />
        <MarkdownArticle v-else-if="content" :source="content" />
        <ContentState
          v-else
          state="empty"
          title="关于页尚未配置"
          message="站点设置中还没有 About 页面内容。"
        />
      </div>
    </div>

    <section class="about-closing">
      <span class="editorial-eyebrow">Continue reading</span>
      <h2>如果这里有一句话对你有用，<br>它就已经完成了自己的任务。</h2>
      <router-link class="button button--primary" to="/library">去书库看看</router-link>
    </section>
  </div>
</template>
