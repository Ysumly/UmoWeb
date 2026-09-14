<script setup>
defineProps({
  eyebrow: {
    type: String,
    default: 'Brain Training',
  },
  title: {
    type: String,
    required: true,
  },
  subtitle: {
    type: String,
    default: '',
  },
  note: {
    type: String,
    default: '',
  },
})
</script>

<template>
  <div class="game-page">
    <header class="game-heading">
      <div class="game-heading__top">
        <span class="editorial-eyebrow">{{ eyebrow }}</span>
        <router-link class="game-back" to="/games">返回游戏中心</router-link>
      </div>
      <h1>{{ title }}</h1>
      <p v-if="subtitle">{{ subtitle }}</p>
    </header>

    <section class="game-board" :aria-label="`${title}游戏区域`">
      <slot />
    </section>

    <p v-if="note" class="game-note">{{ note }}</p>
  </div>
</template>
