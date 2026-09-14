<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'

import ArticleOutlineList from '@/components/public/ArticleOutlineList.vue'
import {
  flattenOutline,
  getVisibleOutlineRows,
} from '@/utils/articleOutline'

const props = defineProps({
  headings: {
    type: Array,
    default: () => [],
  },
  activeId: {
    type: String,
    default: '',
  },
})

const emit = defineEmits(['select'])

const mobileOpen = ref(false)
const manuallyExpandedIds = ref(new Set())
const triggerRef = ref(null)
const panelRef = ref(null)
const closeButtonRef = ref(null)

const allRows = computed(() => flattenOutline(props.headings))
const hasOutline = computed(() => allRows.value.length >= 2)
const visibleRows = computed(() =>
  getVisibleOutlineRows(props.headings, {
    activeId: props.activeId,
    manuallyExpandedIds: manuallyExpandedIds.value,
  }),
)

watch(
  () => props.headings,
  () => {
    manuallyExpandedIds.value = new Set()
  },
)

function toggleBranch(id) {
  const next = new Set(manuallyExpandedIds.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  manuallyExpandedIds.value = next
}

function closeMobile({ restoreFocus = true } = {}) {
  if (!mobileOpen.value) {
    return
  }
  mobileOpen.value = false
  if (restoreFocus) {
    nextTick(() => triggerRef.value?.focus())
  }
}

function openMobile() {
  mobileOpen.value = true
  nextTick(() => closeButtonRef.value?.focus())
}

function toggleMobile() {
  if (mobileOpen.value) {
    closeMobile()
  } else {
    openMobile()
  }
}

function selectRow(row) {
  emit('select', row)
  closeMobile()
}

function handleDocumentPointerDown(event) {
  if (!mobileOpen.value) {
    return
  }
  if (
    panelRef.value?.contains(event.target) ||
    triggerRef.value?.contains(event.target)
  ) {
    return
  }
  closeMobile({ restoreFocus: false })
}

function handleDocumentKeydown(event) {
  if (event.key === 'Escape') {
    closeMobile()
  }
}

watch(mobileOpen, (open) => {
  if (open) {
    document.addEventListener('pointerdown', handleDocumentPointerDown)
    document.addEventListener('keydown', handleDocumentKeydown)
  } else {
    document.removeEventListener('pointerdown', handleDocumentPointerDown)
    document.removeEventListener('keydown', handleDocumentKeydown)
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleDocumentPointerDown)
  document.removeEventListener('keydown', handleDocumentKeydown)
})
</script>

<template>
  <nav
    v-if="hasOutline"
    class="post-toc post-toc--desktop"
    aria-label="文章目录"
  >
    <span class="post-toc__label">文章目录</span>
    <ArticleOutlineList
      :rows="visibleRows"
      @select="selectRow"
      @toggle="toggleBranch"
    />
  </nav>

  <button
    v-if="hasOutline"
    ref="triggerRef"
    class="post-toc-trigger"
    type="button"
    :aria-expanded="mobileOpen"
    aria-controls="mobile-article-outline"
    :aria-label="mobileOpen ? '关闭文章目录' : '打开文章目录'"
    @click="toggleMobile"
  >
    目录
  </button>

  <Transition name="toc-panel">
    <section
      v-if="hasOutline && mobileOpen"
      id="mobile-article-outline"
      ref="panelRef"
      class="post-toc-panel"
      role="dialog"
      aria-modal="false"
      aria-label="文章目录"
    >
      <header class="post-toc-panel__header">
        <strong>文章目录</strong>
        <button
          ref="closeButtonRef"
          type="button"
          aria-label="关闭文章目录"
          @click="closeMobile()"
        >
          <span aria-hidden="true" />
        </button>
      </header>
      <ArticleOutlineList
        :rows="visibleRows"
        @select="selectRow"
        @toggle="toggleBranch"
      />
    </section>
  </Transition>
</template>
