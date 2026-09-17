<script setup>
import { computed, onMounted, ref } from 'vue'

import { getContents } from '@/api/public'
import ContentCard from '@/components/public/ContentCard.vue'
import ContentState from '@/components/public/ContentState.vue'
import SectionHeading from '@/components/public/SectionHeading.vue'
import { contentTypes } from '@/config/contentTypes'
import { gameCatalog } from '@/config/games'
import { toolCatalog } from '@/config/tools'
import { useSiteStore } from '@/stores/site'
import { getApiErrorMessage } from '@/utils/apiError'

const isPlaying = ref(true)
const bookStage = ref(null)
const heroOffset = ref({ x: 0, y: 0 })
const siteStore = useSiteStore()
const status = ref('loading')
const errorMessage = ref('')
const latestContents = ref([])
const totalContents = ref(0)
const typeCounts = ref({})

const leadStory = computed(() => latestContents.value[0])
const recentStories = computed(() => latestContents.value.slice(1, 5))
const typeEntries = computed(() => {
  return contentTypes
    .filter((item) => item.value)
    .map((item) => ({
      ...item,
      count: typeCounts.value[item.value] ?? 0,
    }))
})
const siteSubtitle = computed(() => siteStore.siteSubtitle || '')

async function load() {
  status.value = 'loading'
  errorMessage.value = ''

  try {
    const typeRequests = contentTypes
      .filter((item) => item.value)
      .map((item) => getContents({ page: 1, size: 1, type: item.value }))
    const [listResult, ...typeResults] = await Promise.allSettled([
      getContents({ page: 1, size: 5 }),
      ...typeRequests,
    ])

    if (listResult.status === 'rejected') {
      throw listResult.reason
    }

    latestContents.value = listResult.value.data.items || []
    totalContents.value = listResult.value.data.total || 0
    typeCounts.value = Object.fromEntries(
      contentTypes
        .filter((item) => item.value)
        .map((item, index) => {
          const result = typeResults[index]
          return [item.value, result?.status === 'fulfilled' ? result.value.data.total || 0 : 0]
        }),
    )
    status.value = 'success'
  } catch (error) {
    status.value = 'error'
    errorMessage.value = getApiErrorMessage(error, '首页内容加载失败')
  }
}

onMounted(() => {
  siteStore.load().catch(() => {})
  load()
})

function handleBookPointer(event) {
  if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
    return
  }
  if (window.matchMedia?.('(pointer: coarse)').matches) {
    return
  }

  const bounds = bookStage.value?.getBoundingClientRect()
  if (!bounds) {
    return
  }

  heroOffset.value = {
    x: ((event.clientX - bounds.left) / bounds.width - 0.5) * 10,
    y: ((event.clientY - bounds.top) / bounds.height - 0.5) * 10,
  }
}

function resetBookPointer() {
  heroOffset.value = { x: 0, y: 0 }
}
</script>

<template>
  <div class="home-page" :class="{ 'is-playing': isPlaying }">
    <section class="home-hero">
      <div class="home-hero__copy">
        <span class="editorial-eyebrow">私人札记 · 书评 · 长篇连载</span>
        <h1 class="home-title">
          <span class="home-title__line"><span>把代码写进</span></span>
          <span class="home-title__line"><span>时间的纸页</span></span>
        </h1>
        <p class="home-intro">
          {{ siteSubtitle || '一个关于技术、阅读与长期创作的私人空间。' }}
        </p>
        <div class="home-actions">
          <router-link class="button button--primary" to="/library">进入书库</router-link>
          <router-link class="button button--text" to="/about">了解这个空间</router-link>
        </div>
        <dl class="home-stats">
          <div><dt>{{ totalContents }}</dt><dd>篇公开内容</dd></div>
          <div><dt>{{ typeEntries.length }}</dt><dd>长期主题</dd></div>
          <div><dt>2026</dt><dd>持续更新</dd></div>
        </dl>
      </div>

      <div
        ref="bookStage"
        class="book-stage"
        :style="{ '--book-x': `${heroOffset.x}px`, '--book-y': `${heroOffset.y}px` }"
        @pointermove="handleBookPointer"
        @pointerleave="resetBookPointer"
      >
        <div class="book-shadow" />
        <div class="book-object">
          <div class="book-object__page book-object__page--back" />
          <div class="book-object__page book-object__page--middle" />
          <div class="book-object__cover">
            <span class="book-object__number">UMO / VOL. 01</span>
            <span class="book-object__mark">文</span>
            <h2>长期写作与缓慢思考</h2>
            <div><span>2026</span><span>ISSUE 01</span></div>
          </div>
        </div>
        <div class="book-seal" aria-hidden="true">U</div>
      </div>
    </section>

    <section
      v-if="status === 'loading' || status === 'error'"
      class="home-section"
      v-reveal
    >
      <ContentState
        :state="status"
        :title="status === 'loading' ? '正在整理首页内容' : '首页内容加载失败'"
        :message="status === 'loading' ? '' : errorMessage"
        :action-label="status === 'error' ? '重新加载' : ''"
        @retry="load"
      />
    </section>

    <section v-else-if="leadStory" class="home-section" v-reveal>
      <SectionHeading
        eyebrow="Featured"
        title="本期刊首"
        description="一篇文章，作为这一阶段思考的入口。"
      />
      <ContentCard :content="leadStory" variant="lead" />
    </section>

    <section v-else class="home-section" v-reveal>
      <ContentState
        state="empty"
        title="书库还没有公开内容"
        message="发布第一篇文章后，首页会自动展示最新的内容。"
      />
    </section>

    <section v-if="status === 'success' && recentStories.length" class="home-section" v-reveal>
      <SectionHeading
        eyebrow="Recent"
        title="近期篇幅"
        description="技术、阅读与故事，在同一个时间轴上交替出现。"
      />
      <div class="content-grid content-grid--recent">
        <ContentCard
          v-for="(content, index) in recentStories"
          :key="content.id"
          :content="content"
          :index="index"
        />
      </div>
      <div class="section-more">
        <router-link class="button button--outline" to="/library">浏览全部内容</router-link>
      </div>
    </section>

    <section v-if="status === 'success' && leadStory" class="home-section" v-reveal>
      <SectionHeading
        eyebrow="Collections"
        title="长期主题"
        description="不是频道分类，而是不同节奏与体裁的长期写作。"
      />
      <div class="type-grid">
        <router-link
          v-for="(item, index) in typeEntries"
          :key="item.value"
          class="type-card"
          :to="{ path: '/library', query: { type: item.value } }"
          :style="{ '--type-index': index }"
        >
          <span>{{ String(index + 1).padStart(2, '0') }}</span>
          <h3>{{ item.zh }}</h3>
          <p>{{ item.description }}</p>
          <strong>{{ item.count }} 篇</strong>
        </router-link>
      </div>
    </section>

    <section v-if="status === 'success' && leadStory" class="about-preview" v-reveal>
      <div>
        <span class="editorial-eyebrow">About this place</span>
        <h2>这里不追逐即时答案。</h2>
      </div>
      <div>
        <p>每一篇文章都允许被重写，每一个判断都保留被推翻的可能。这个空间更像一本持续装订中的书。</p>
        <router-link class="button button--text" to="/about">翻开关于页</router-link>
      </div>
    </section>

    <section class="home-games" v-reveal aria-labelledby="home-games-title">
      <div class="home-games__copy">
        <span class="editorial-eyebrow">Brain Training</span>
        <h2 id="home-games-title">让注意力短跑一会儿。</h2>
        <p>四款本地训练游戏，不登录、不上传成绩，练习结束即可离开。</p>
        <router-link class="button button--outline" to="/games">进入游戏中心</router-link>
      </div>
      <div class="home-games__list">
        <router-link v-for="game in gameCatalog" :key="game.id" :to="game.path">
          <span aria-hidden="true">{{ game.mark }}</span>
          <strong>{{ game.title }}</strong>
          <small>{{ game.english }}</small>
        </router-link>
      </div>
    </section>

    <section class="home-tools" v-reveal aria-labelledby="home-tools-title">
      <div class="home-tools__copy">
        <span class="editorial-eyebrow">Local Tools</span>
        <h2 id="home-tools-title">把草稿留在浏览器里。</h2>
        <p>无需登录、不上传正文，在本地完成 Markdown 写作、预览和下载。</p>
        <router-link class="button button--outline" to="/tools">进入工具中心</router-link>
      </div>
      <div class="home-tools__list">
        <router-link v-for="tool in toolCatalog" :key="tool.id" :to="tool.path">
          <span aria-hidden="true">{{ tool.mark }}</span>
          <strong>{{ tool.title }}</strong>
          <small>{{ tool.english }}</small>
        </router-link>
      </div>
    </section>
  </div>
</template>
