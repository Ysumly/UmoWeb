<script setup>
defineProps({
  rows: {
    type: Array,
    default: () => [],
  },
})

defineEmits(['select', 'toggle'])
</script>

<template>
  <ul class="post-toc__list">
    <li
      v-for="row in rows"
      :key="row.id"
      class="post-toc__item"
      :class="{
        'is-active': row.active,
        'is-ancestor': row.activeAncestor,
      }"
      :style="{ '--toc-level': row.level }"
    >
      <div class="post-toc__row">
        <button
          v-if="row.hasChildren"
          class="post-toc__toggle"
          type="button"
          :aria-expanded="row.expanded"
          :aria-label="`${row.expanded ? '收起' : '展开'} ${row.text}`"
          @click="$emit('toggle', row.id)"
        >
          <span aria-hidden="true" />
        </button>
        <span v-else class="post-toc__spacer" aria-hidden="true" />

        <a
          class="post-toc__link"
          :href="`#${encodeURIComponent(row.id)}`"
          :aria-current="row.active ? 'location' : undefined"
          @click.prevent="$emit('select', row)"
        >
          {{ row.text }}
        </a>
      </div>
    </li>
  </ul>
</template>
