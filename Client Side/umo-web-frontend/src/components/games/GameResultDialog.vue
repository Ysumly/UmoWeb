<script setup>
import { nextTick, ref, watch } from 'vue'

const props = defineProps({
  open: {
    type: Boolean,
    default: false,
  },
  title: {
    type: String,
    required: true,
  },
  icon: {
    type: String,
    default: '结',
  },
  stats: {
    type: Array,
    default: () => [],
  },
  detail: {
    type: String,
    default: '',
  },
  actionLabel: {
    type: String,
    default: '再来一局',
  },
})

const emit = defineEmits(['close'])
const actionRef = ref(null)

watch(
  () => props.open,
  async (open) => {
    if (open) {
      await nextTick()
      actionRef.value?.focus()
    }
  },
)

function handleKeydown(event) {
  if (event.key === 'Escape' && props.open) {
    emit('close')
  }
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="game-overlay"
      role="dialog"
      aria-modal="true"
      :aria-label="title"
      @keydown="handleKeydown"
      @click.self="emit('close')"
    >
      <div class="game-result">
        <span class="game-result__mark" aria-hidden="true">{{ icon }}</span>
        <p class="game-result__eyebrow">训练结算</p>
        <h2>{{ title }}</h2>
        <dl v-if="stats.length" class="game-result__stats">
          <div v-for="stat in stats" :key="stat.label">
            <dt>{{ stat.label }}</dt>
            <dd :class="stat.tone ? `is-${stat.tone}` : ''">{{ stat.value }}</dd>
          </div>
        </dl>
        <p v-if="detail" class="game-result__detail">{{ detail }}</p>
        <button ref="actionRef" class="button button--primary" type="button" @click="emit('close')">
          {{ actionLabel }}
        </button>
      </div>
    </div>
  </Teleport>
</template>
