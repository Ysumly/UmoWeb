import { onBeforeUnmount, watch } from 'vue'

import {
  calculateScrollRatio,
  extractMarkdownHeadingLines,
  findActiveHeadingIndex,
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
  const cache = {
    source: null,
    editorWidth: 0,
    headings: [],
    editorOffsets: [],
    previewElements: [],
    previewOffsets: [],
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
    const offsets = []
    let previousLine = 0
    let mirrorText = ''

    for (const heading of headings) {
      const segment = lines.slice(previousLine, heading.line).join('\n')
      if (previousLine > 0 && segment) {
        mirrorText += '\n'
      }
      mirrorText += segment
      currentMirror.textContent = mirrorText
      offsets.push(currentMirror.scrollHeight)
      previousLine = heading.line
    }
    return offsets
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
      cache.editorOffsets = editorHeadingOffsets(editor, cache.headings)
      cache.previewElements = [...preview.querySelectorAll('h1, h2, h3, h4, h5, h6')]
      cache.previewOffsets = cache.previewElements.map((heading) => heading.offsetTop)
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
    expectedTarget = null
    return Math.abs(element.scrollTop - expectedScrollTop) <= 3
  }

  function setProgrammaticScroll(target, scrollTop) {
    expectedTarget = target
    expectedScrollTop = scrollTop
    target.scrollTop = scrollTop
  }

  function syncFromEditor() {
    const data = getSyncData()
    if (!data || !mediaMatches()) {
      return
    }
    const { editor, preview, headings, editorOffsets, previewElements } = data
    if (!headings.length || !previewElements.length) {
      setProgrammaticScroll(
        preview,
        scrollTopForRatio(calculateScrollRatio(editor), preview),
      )
      return
    }

    const headingIndex = findActiveHeadingIndex(editorOffsets, editor.scrollTop)
    const target = previewElements[Math.min(headingIndex, previewElements.length - 1)]
    setProgrammaticScroll(preview, Math.max(0, target.offsetTop))
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

    const headingIndex = Math.min(
      findActiveHeadingIndex(previewOffsets, preview.scrollTop),
      headings.length - 1,
    )
    setProgrammaticScroll(editor, Math.max(0, editorOffsets[headingIndex]))
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
    expectedTarget = null
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
