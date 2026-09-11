<script setup>
defineProps({
  eyebrow: {
    type: String,
    default: '',
  },
  title: {
    type: String,
    required: true,
  },
  description: {
    type: String,
    default: '',
  },
})
</script>

<template>
  <header class="section-heading">
    <span v-if="eyebrow" class="section-heading__eyebrow">{{ eyebrow }}</span>
    <div class="section-heading__row">
      <h2>{{ title }}</h2>
      <p v-if="description">{{ description }}</p>
    </div>
  </header>
</template>
