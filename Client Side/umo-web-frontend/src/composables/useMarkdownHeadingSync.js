import { onBeforeUnmount, watch } from 'vue'

import {
  calculateScrollRatio,
  extractMarkdownHeadingLines,
  interpolateBetweenAnchors,
  scrollTopForRatio,
} from '@/utils/editorScroll'

export function useMarkdownHeadingSync(primaryRef, secondaryRef, { mediaQuery } = {}) {
  let media = null
  let primaryHandler = null
  let secondaryHandler = null
  let mirror = null
  let frame = null
  let resizeObserver = null
  let expectedTarget = null
  let expectedScrollTop = 0
  let expectedClearFrame = null
  const cache = {
    source: null,
    editorWidth: 0,
    headings: [],
    editorOffsets: [],
    previewElements: [],
    previewOffsets: [],
    editorMax: 0,
    previewMax: 0,
  }

  function mediaMatches() {
    return !media || media.matches
  }

  function invalidateCache() {
    cache.source = null
    cache.editorWidth = 0
  }

  function createMirror(textarea) {
    if (mirror?.isConnected) {
      return mirror
    }
    mirror = document.createElement('div')
    mirror.setAttribute('aria-hidden', 'true')
    document.body.appendChild(mirror)
    updateMirrorStyles(textarea)
    return mirror
  }

  function updateMirrorStyles(textarea) {
    if (!mirror) {
      return
    }
    const styles = window.getComputedStyle(textarea)
    Object.assign(mirror.style, {
      position: 'fixed',
      top: '0',
      left: '-10000px',
      width: `${textarea.clientWidth}px`,
      margin: '0',
      padding: styles.padding,
      border: styles.border,
      boxSizing: styles.boxSizing,
      fontFamily: styles.fontFamily,
      fontSize: styles.fontSize,
      fontWeight: styles.fontWeight,
      fontStyle: styles.fontStyle,
      lineHeight: styles.lineHeight,
      letterSpacing: styles.letterSpacing,
      tabSize: styles.tabSize,
      whiteSpace: 'pre-wrap',
      overflowWrap: 'break-word',
      wordBreak: styles.wordBreak,
      visibility: 'hidden',
      pointerEvents: 'none',
    })
  }

  function editorHeadingOffsets(textarea, headings) {
    const currentMirror = createMirror(textarea)
    updateMirrorStyles(textarea)
    const lines = textarea.value.replace(/\r\n?/g, '\n').split('\n')
    return headings.map((heading) => {
      currentMirror.textContent = lines.slice(0, heading.line).join('\n')
      return currentMirror.scrollHeight
    })
  }

  function getSyncData() {
    const editor = primaryRef.value
    const preview = secondaryRef.value
    if (!editor || !preview) {
      return null
    }

    if (
      cache.source !== editor.value
      || cache.editorWidth !== editor.clientWidth
    ) {
      cache.source = editor.value
      cache.editorWidth = editor.clientWidth
      cache.headings = extractMarkdownHeadingLines(editor.value)
      cache.previewElements = [...preview.querySelectorAll('h1, h2, h3, h4, h5, h6')]
      cache.editorMax = Math.max(0, editor.scrollHeight - editor.clientHeight)
      cache.previewMax = Math.max(0, preview.scrollHeight - preview.clientHeight)
      cache.editorOffsets = [
        0,
        ...editorHeadingOffsets(editor, cache.headings)
          .map((offset) => Math.min(cache.editorMax, Math.max(0, offset))),
        cache.editorMax,
      ]
      cache.previewOffsets = [
        0,
        ...cache.previewElements
          .map((heading) => Math.min(cache.previewMax, Math.max(0, heading.offsetTop))),
        cache.previewMax,
      ]
    }

    return {
      editor,
      preview,
      ...cache,
    }
  }

  function isProgrammaticTarget(element) {
    if (expectedTarget !== element) {
      return false
    }
    const matches = Math.abs(element.scrollTop - expectedScrollTop) <= 3
    clearExpectedTarget()
    return matches
  }

  function clearExpectedTarget() {
    expectedTarget = null
    if (expectedClearFrame) {
      window.cancelAnimationFrame(expectedClearFrame)
      expectedClearFrame = null
    }
  }

  function setProgrammaticScroll(target, scrollTop) {
    if (expectedClearFrame) {
      window.cancelAnimationFrame(expectedClearFrame)
    }
    expectedTarget = target
    expectedScrollTop = scrollTop
    target.scrollTop = scrollTop
    expectedClearFrame = window.requestAnimationFrame(() => {
      expectedClearFrame = window.requestAnimationFrame(() => {
        expectedTarget = null
        expectedClearFrame = null
      })
    })
  }

  function syncFromEditor() {
    const data = getSyncData()
    if (!data || !mediaMatches()) {
      return
    }
    const {
      editor,
      preview,
      headings,
      editorOffsets,
      previewElements,
      previewOffsets,
    } = data
    if (!headings.length || previewElements.length !== headings.length) {
      setProgrammaticScroll(
        preview,
        scrollTopForRatio(calculateScrollRatio(editor), preview),
      )
      return
    }

    const targetTop = interpolateBetweenAnchors(
      editorOffsets,
      previewOffsets,
      editor.scrollTop,
    )
    setProgrammaticScroll(
      preview,
      targetTop ?? scrollTopForRatio(calculateScrollRatio(editor), preview),
    )
  }

  function syncFromPreview() {
    const data = getSyncData()
    if (!data || !mediaMatches()) {
      return
    }
    const {
      editor,
      preview,
      headings,
      editorOffsets,
      previewOffsets,
    } = data
    if (!headings.length || !previewOffsets.length) {
      setProgrammaticScroll(
        editor,
        scrollTopForRatio(calculateScrollRatio(preview), editor),
      )
      return
    }

    const targetTop = interpolateBetweenAnchors(
      previewOffsets,
      editorOffsets,
      preview.scrollTop,
    )
    setProgrammaticScroll(
      editor,
      targetTop ?? scrollTopForRatio(calculateScrollRatio(preview), editor),
    )
  }

  function scheduleSync(direction) {
    if (!mediaMatches() || frame) {
      return
    }
    frame = window.requestAnimationFrame(() => {
      frame = null
      if (direction === 'editor') {
        syncFromEditor()
      } else {
        syncFromPreview()
      }
    })
  }

  function unbind() {
    if (primaryHandler) {
      primaryRef.value?.removeEventListener('scroll', primaryHandler)
      primaryHandler = null
    }
    if (secondaryHandler) {
      secondaryRef.value?.removeEventListener('scroll', secondaryHandler)
      secondaryHandler = null
    }
    resizeObserver?.disconnect()
    resizeObserver = null
    if (frame) {
      window.cancelAnimationFrame(frame)
      frame = null
    }
    clearExpectedTarget()
  }

  function bind() {
    unbind()
    if (!mediaMatches() || !primaryRef.value || !secondaryRef.value) {
      return
    }

    primaryHandler = () => {
      if (!isProgrammaticTarget(primaryRef.value)) {
        scheduleSync('editor')
      }
    }
    secondaryHandler = () => {
      if (!isProgrammaticTarget(secondaryRef.value)) {
        scheduleSync('preview')
      }
    }
    primaryRef.value.addEventListener('scroll', primaryHandler, { passive: true })
    secondaryRef.value.addEventListener('scroll', secondaryHandler, { passive: true })

    if (window.ResizeObserver) {
      resizeObserver = new ResizeObserver(invalidateCache)
      resizeObserver.observe(primaryRef.value)
      resizeObserver.observe(secondaryRef.value)
    }
  }

  function handleMediaChange() {
    bind()
  }

  if (typeof window !== 'undefined' && mediaQuery && window.matchMedia) {
    media = window.matchMedia(mediaQuery)
    media.addEventListener?.('change', handleMediaChange)
  }

  watch([primaryRef, secondaryRef], bind, { flush: 'post', immediate: true })

  onBeforeUnmount(() => {
    media?.removeEventListener?.('change', handleMediaChange)
    unbind()
    mirror?.remove()
    mirror = null
  })
}
