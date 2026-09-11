<script setup>
import { computed, ref } from 'vue'

import ContentCard from '@/components/public/ContentCard.vue'
import SectionHeading from '@/components/public/SectionHeading.vue'
import { contentTypes, publicContents, siteInfo } from '@/demo/content'

const isPlaying = ref(true)
const bookStage = ref(null)
const heroOffset = ref({ x: 0, y: 0 })

const orderedContents = computed(() => {
  return [...publicContents].sort((a, b) => {
    return new Date(b.publishedAt) - new Date(a.publishedAt)
  })
})

const leadStory = computed(() => orderedContents.value[0])
const recentStories = computed(() => orderedContents.value.slice(1, 5))
const typeEntries = computed(() => {
  return contentTypes
    .filter((item) => item.value)
    .map((item) => ({
      ...item,
      count: publicContents.filter((content) => content.type === item.value).length,
    }))
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
        <p class="home-intro">{{ siteInfo.introduction }}</p>
        <div class="home-actions">
          <router-link class="button button--primary" to="/library">进入书库</router-link>
          <router-link class="button button--text" to="/about">了解这个空间</router-link>
        </div>
        <dl class="home-stats">
          <div><dt>{{ publicContents.length }}</dt><dd>篇公开内容</dd></div>
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

    <section class="home-section" v-reveal>
      <SectionHeading
        eyebrow="Featured"
        title="本期刊首"
        description="一篇文章，作为这一阶段思考的入口。"
      />
      <ContentCard :content="leadStory" variant="lead" />
    </section>

    <section class="home-section" v-reveal>
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

    <section class="home-section" v-reveal>
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

    <section class="about-preview" v-reveal>
      <div>
        <span class="editorial-eyebrow">About this place</span>
        <h2>这里不追逐即时答案。</h2>
      </div>
      <div>
        <p>每一篇文章都允许被重写，每一个判断都保留被推翻的可能。这个空间更像一本持续装订中的书。</p>
        <router-link class="button button--text" to="/about">翻开关于页</router-link>
      </div>
    </section>
  </div>
</template>
