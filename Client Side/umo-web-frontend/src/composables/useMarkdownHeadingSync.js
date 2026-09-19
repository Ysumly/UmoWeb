import { onBeforeUnmount, watch } from 'vue'

import {
  calculateScrollRatio,
  extractMarkdownHeadingLines,
  findActiveHeadingIndex,
  scrollTopForRatio,
} from '@/utils/editorScroll'

export function useMarkdownHeadingSync(primaryRef, secondaryRef, { enabled } = {}) {
  const enabledRef = enabled && typeof enabled === 'object' && 'value' in enabled
    ? enabled
    : null
  let primaryHandler = null
  let secondaryHandler = null
  let mirror = null
  let frame = null
  let expectedTarget = null
  let expectedScrollTop = 0

  function isEnabled() {
    return !enabledRef || enabledRef.value
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
    const editor = primaryRef.value
    const preview = secondaryRef.value
    if (!editor || !preview || !isEnabled()) {
      return
    }

    const headings = extractMarkdownHeadingLines(editor.value)
    const previewHeadings = [...preview.querySelectorAll('h1, h2, h3, h4, h5, h6')]
    if (!headings.length || !previewHeadings.length) {
      setProgrammaticScroll(
        preview,
        scrollTopForRatio(calculateScrollRatio(editor), preview),
      )
      return
    }

    const headingIndex = findActiveHeadingIndex(
      editorHeadingOffsets(editor, headings),
      editor.scrollTop,
    )
    const target = previewHeadings[Math.min(headingIndex, previewHeadings.length - 1)]
    setProgrammaticScroll(preview, Math.max(0, target.offsetTop))
  }

  function syncFromPreview() {
    const editor = primaryRef.value
    const preview = secondaryRef.value
    if (!editor || !preview || !isEnabled()) {
      return
    }

    const headings = extractMarkdownHeadingLines(editor.value)
    const previewHeadings = [...preview.querySelectorAll('h1, h2, h3, h4, h5, h6')]
    if (!headings.length || !previewHeadings.length) {
      setProgrammaticScroll(
        editor,
        scrollTopForRatio(calculateScrollRatio(preview), editor),
      )
      return
    }

    const previewOffsets = previewHeadings.map((heading) => heading.offsetTop)
    const headingIndex = Math.min(
      findActiveHeadingIndex(previewOffsets, preview.scrollTop),
      headings.length - 1,
    )
    const editorOffsets = editorHeadingOffsets(editor, headings)
    setProgrammaticScroll(editor, Math.max(0, editorOffsets[headingIndex]))
  }

  function scheduleSync(direction) {
    if (!isEnabled() || frame) {
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
    if (frame) {
      window.cancelAnimationFrame(frame)
      frame = null
    }
    expectedTarget = null
  }

  function bind() {
    unbind()
    if (!isEnabled() || !primaryRef.value || !secondaryRef.value) {
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
  }

  watch([primaryRef, secondaryRef, ...(enabledRef ? [enabledRef] : [])], bind, {
    flush: 'post',
    immediate: true,
  })

  onBeforeUnmount(() => {
    unbind()
    mirror?.remove()
    mirror = null
  })
}
