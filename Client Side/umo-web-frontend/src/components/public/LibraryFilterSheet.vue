<script setup>
import {
  computed,
  nextTick,
  onBeforeUnmount,
  reactive,
  ref,
  watch,
} from 'vue'

import { contentTypes } from '@/config/contentTypes'

const props = defineProps({
  open: {
    type: Boolean,
    default: false,
  },
  filters: {
    type: Object,
    default: () => ({}),
  },
  categoryOptions: {
    type: Array,
    default: () => [],
  },
  tags: {
    type: Array,
    default: () => [],
  },
})

const emit = defineEmits(['close', 'apply'])

const sheetRef = ref(null)
const closeButtonRef = ref(null)
const draft = reactive({
  type: '',
  categoryId: null,
  tagId: null,
})
let previousFocus = null
let previousBodyOverflow = ''

const availableCategories = computed(() => {
  return props.categoryOptions.filter((category) => {
    return !draft.type || category.type === draft.type
  })
})

const selectedCount = computed(() => {
  return ['type', 'categoryId', 'tagId']
    .filter((key) => Boolean(draft[key]))
    .length
})

function resetDraft() {
  draft.type = props.filters.type || ''
  draft.categoryId = props.filters.categoryId || null
  draft.tagId = props.filters.tagId || null
}

function selectType(type) {
  draft.type = type
  const selectedCategory = props.categoryOptions.find((category) => {
    return category.id === draft.categoryId
  })
  if (selectedCategory && type && selectedCategory.type !== type) {
    draft.categoryId = null
  }
}

function selectCategory(categoryId) {
  draft.categoryId = draft.categoryId === categoryId ? null : categoryId
}

function selectTag(tagId) {
  draft.tagId = draft.tagId === tagId ? null : tagId
}

function closeSheet() {
  emit('close')
}

function applyFilters() {
  emit('apply', {
    type: draft.type,
    categoryId: draft.categoryId,
    includeDescendants: Boolean(draft.categoryId),
    tagId: draft.tagId,
  })
}

function handleKeydown(event) {
  if (event.key === 'Escape') {
    event.preventDefault()
    closeSheet()
    return
  }
  if (event.key !== 'Tab' || !sheetRef.value) {
    return
  }

  const focusable = Array.from(sheetRef.value.querySelectorAll(
    'button:not([disabled]), [href], [tabindex]:not([tabindex="-1"])',
  )).filter((element) => element.getClientRects().length > 0)
  if (!focusable.length) {
    event.preventDefault()
    return
  }

  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

watch(
  () => props.open,
  async (open) => {
    if (open) {
      previousFocus = document.activeElement
      previousBodyOverflow = document.body.style.overflow
      document.body.style.overflow = 'hidden'
      resetDraft()
      await nextTick()
      closeButtonRef.value?.focus()
      return
    }

    document.body.style.overflow = previousBodyOverflow
    await nextTick()
    if (previousFocus instanceof HTMLElement && previousFocus.isConnected) {
      previousFocus.focus()
    }
    previousFocus = null
  },
)

onBeforeUnmount(() => {
  document.body.style.overflow = previousBodyOverflow
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="library-filter-sheet-layer"
      @pointerdown.self="closeSheet"
    >
      <section
        id="library-filter-sheet"
        ref="sheetRef"
        class="library-filter-sheet"
        role="dialog"
        aria-modal="true"
        aria-label="筛选书库"
        @keydown="handleKeydown"
      >
        <header class="library-filter-sheet__header">
          <div>
            <span>Library / Filters</span>
            <h2>筛选书库</h2>
          </div>
          <button
            ref="closeButtonRef"
            type="button"
            aria-label="关闭筛选"
            @click="closeSheet"
          >
            关闭
          </button>
        </header>

        <div class="library-filter-sheet__body">
          <section class="library-filter-sheet__group" role="group" aria-label="内容类型">
            <span class="library-filter-sheet__label">内容类型</span>
            <button
              v-for="item in contentTypes"
              :key="item.value"
              class="library-filter-sheet__option"
              type="button"
              :aria-pressed="draft.type === item.value"
              @click="selectType(item.value)"
            >
              <span>{{ item.zh }}</span>
              <small>{{ item.label }}</small>
            </button>
          </section>

          <section class="library-filter-sheet__group" role="group" aria-label="分类">
            <span class="library-filter-sheet__label">分类</span>
            <button
              v-for="category in availableCategories"
              :key="category.id"
              class="library-filter-sheet__option"
              type="button"
              :aria-pressed="draft.categoryId === category.id"
              @click="selectCategory(category.id)"
            >
              <span :style="{ paddingLeft: `${category.depth * 12}px` }">
                {{ category.name }}
              </span>
            </button>
          </section>

          <section class="library-filter-sheet__group" role="group" aria-label="标签">
            <span class="library-filter-sheet__label">标签</span>
            <div class="library-filter-sheet__tags">
              <button
                v-for="tag in tags"
                :key="tag.id"
                class="library-filter-sheet__tag"
                type="button"
                :aria-pressed="draft.tagId === tag.id"
                @click="selectTag(tag.id)"
              >
                {{ tag.name }}
              </button>
            </div>
          </section>
        </div>

        <footer class="library-filter-sheet__footer">
          <button type="button" @click="resetDraft">重置选择</button>
          <button class="button button--primary" type="button" @click="applyFilters">
            查看结果
          </button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>
