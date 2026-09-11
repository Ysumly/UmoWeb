<script setup>
import { computed } from 'vue'

import { formatPublishedDate } from '@/demo/catalog'

const props = defineProps({
  content: {
    type: Object,
    required: true,
  },
  variant: {
    type: String,
    default: 'default',
  },
  index: {
    type: Number,
    default: 0,
  },
})

const typeLabels = {
  NOTE: '技术笔记',
  BOOK_REVIEW: '读后有感',
  NOVEL: '小说章节',
}

const publishedLabel = computed(() => formatPublishedDate(props.content.publishedAt))
const readingLabel = computed(() => {
  const minutes = props.content.metadata?.readingTime
  return minutes ? `${minutes} 分钟` : ''
})
</script>

<template>
  <article
    class="content-card"
    :class="`content-card--${variant}`"
    :style="{ '--card-index': index }"
  >
    <div class="content-card__topline">
      <span>{{ typeLabels[content.type] }}</span>
      <span>{{ publishedLabel }}</span>
    </div>

    <router-link class="content-card__title" :to="`/post/${content.slug}`">
      {{ content.title }}
    </router-link>

    <p class="content-card__summary">{{ content.summary }}</p>

    <div class="content-card__footer">
      <div class="content-card__tags">
        <span v-for="tag in content.tags.slice(0, 3)" :key="tag.id">{{ tag.name }}</span>
      </div>
      <span v-if="readingLabel" class="content-card__reading">{{ readingLabel }}</span>
    </div>
  </article>
</template>
