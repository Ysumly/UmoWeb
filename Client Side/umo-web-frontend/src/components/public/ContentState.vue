<script setup>
defineProps({
  state: {
    type: String,
    default: 'loading',
  },
  title: {
    type: String,
    default: '',
  },
  message: {
    type: String,
    default: '',
  },
  actionLabel: {
    type: String,
    default: '',
  },
})

defineEmits(['retry'])
</script>

<template>
  <div v-if="state === 'loading'" class="content-state" role="status">
    <span class="content-state__loader" aria-hidden="true" />
    <p>{{ title || '正在读取内容' }}</p>
  </div>

  <div v-else class="empty-state content-state--empty" :role="state === 'error' ? 'alert' : 'status'">
    <span>{{ state === 'error' ? 'REQUEST FAILED' : 'NO CONTENT' }}</span>
    <h3>{{ title }}</h3>
    <p>{{ message }}</p>
    <button
      v-if="actionLabel"
      class="button button--outline"
      type="button"
      @click="$emit('retry')"
    >
      {{ actionLabel }}
    </button>
  </div>
</template>
