<script setup>
import { computed } from 'vue'

import { renderMarkdown } from '@/utils/markdown'

const props = defineProps({
  source: {
    type: String,
    default: '',
  },
  allowRemoteImages: {
    type: Boolean,
    default: true,
  },
})

const html = computed(() => renderMarkdown(props.source, {
  allowRemoteImages: props.allowRemoteImages,
}))
</script>

<template>
  <div class="markdown-body" v-html="html" />
</template>
