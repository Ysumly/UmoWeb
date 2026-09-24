<script setup>
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  useId,
} from 'vue'

const props = defineProps({
  modelValue: {
    type: Array,
    default: () => [],
  },
  categories: {
    type: Array,
    default: () => [],
  },
  disabled: Boolean,
})

const emit = defineEmits(['update:modelValue', 'change'])

const rootRef = ref(null)
const triggerRef = ref(null)
const searchRef = ref(null)
const open = ref(false)
const query = ref('')
const panelId = `admin-category-select-${useId().replaceAll(':', '')}`

const selectedIds = computed(() => new Set(props.modelValue.map(Number)))
const selectedCount = computed(() => props.modelValue.length)
const filteredCategories = computed(() => {
  const keyword = query.value.trim().toLocaleLowerCase()
  if (!keyword) {
    return props.categories
  }
  return props.categories.filter((category) => (
    String(category.name || '').toLocaleLowerCase().includes(keyword)
  ))
})

function toggleOpen() {
  if (props.disabled) {
    return
  }
  open.value = !open.value
  if (open.value) {
    query.value = ''
    nextTick(() => searchRef.value?.focus())
  }
}

function close({ restoreFocus = false } = {}) {
  if (!open.value) {
    return
  }
  open.value = false
  query.value = ''
  if (restoreFocus) {
    nextTick(() => triggerRef.value?.focus())
  }
}

function toggleCategory(categoryId) {
  const normalizedId = Number(categoryId)
  const next = selectedIds.value.has(normalizedId)
    ? props.modelValue.filter((id) => Number(id) !== normalizedId)
    : [...props.modelValue, normalizedId]
  emit('update:modelValue', next)
  emit('change', next)
}

function handleDocumentPointerDown(event) {
  if (!rootRef.value?.contains(event.target)) {
    close()
  }
}

onMounted(() => {
  document.addEventListener('pointerdown', handleDocumentPointerDown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleDocumentPointerDown)
})
</script>

<template>
  <div
    ref="rootRef"
    class="admin-multi-select"
    @keydown.esc.prevent="close({ restoreFocus: true })"
  >
    <button
      ref="triggerRef"
      class="admin-multi-select__trigger"
      type="button"
      :disabled="disabled"
      :aria-label="selectedCount ? `分类选择，已选 ${selectedCount} 项` : '分类选择'"
      aria-haspopup="listbox"
      :aria-expanded="open"
      :aria-controls="panelId"
      @click="toggleOpen"
    >
      <span>{{ selectedCount ? `已选 ${selectedCount} 项` : '选择分类' }}</span>
      <i aria-hidden="true">{{ open ? '⌃' : '⌄' }}</i>
    </button>

    <div
      v-if="open"
      :id="panelId"
      class="admin-multi-select__panel"
      role="listbox"
      aria-label="分类选项"
      aria-multiselectable="true"
    >
      <label class="admin-multi-select__search">
        <span class="sr-only">搜索分类</span>
        <input
          ref="searchRef"
          v-model="query"
          type="search"
          aria-label="搜索分类"
          placeholder="搜索分类"
        />
      </label>

      <div class="admin-multi-select__options">
        <label
          v-for="category in filteredCategories"
          :key="category.id"
          class="admin-multi-select__option"
          role="option"
          :aria-selected="selectedIds.has(Number(category.id))"
          :style="{ paddingLeft: `${12 + category.depth * 16}px` }"
        >
          <input
            type="checkbox"
            :checked="selectedIds.has(Number(category.id))"
            @change="toggleCategory(category.id)"
          />
          <span>{{ category.name }}</span>
        </label>
        <p v-if="!filteredCategories.length" class="admin-multi-select__empty">
          没有匹配的分类
        </p>
      </div>
    </div>
  </div>
</template>
