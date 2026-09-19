import { onBeforeUnmount, watch } from 'vue'

import {
  calculateScrollRatio,
  scrollTopForRatio,
} from '@/utils/editorScroll'

const PROGRAMMATIC_RATIO_TOLERANCE = 0.005

export function useSyncedScroll(primaryRef, secondaryRef, { mediaQuery } = {}) {
  let media = null
  let expectedTarget = null
  let expectedRatio = 0
  const bindings = []
  const frames = new Map()

  function mediaMatches() {
    return !media || media.matches
  }

  function clearExpectedTarget() {
    expectedTarget = null
    expectedRatio = 0
  }

  function cancelFrames() {
    for (const frame of frames.values()) {
      window.cancelAnimationFrame(frame)
    }
    frames.clear()
  }

  function unbind() {
    for (const { element, handler } of bindings) {
      element.removeEventListener('scroll', handler)
    }
    bindings.length = 0
  }

  function bind() {
    unbind()
    cancelFrames()
    clearExpectedTarget()

    const primary = primaryRef.value
    const secondary = secondaryRef.value
    if (!primary || !secondary || !mediaMatches()) {
      return
    }

    const primaryHandler = () => scheduleSync('primary')
    const secondaryHandler = () => scheduleSync('secondary')

    primary.addEventListener('scroll', primaryHandler, { passive: true })
    secondary.addEventListener('scroll', secondaryHandler, { passive: true })
    bindings.push(
      { element: primary, handler: primaryHandler },
      { element: secondary, handler: secondaryHandler },
    )
  }

  function scheduleSync(sourceKey) {
    const source = sourceKey === 'primary' ? primaryRef.value : secondaryRef.value
    const target = sourceKey === 'primary' ? secondaryRef.value : primaryRef.value
    if (!source || !target || !mediaMatches()) {
      return
    }

    if (expectedTarget === source) {
      const currentRatio = calculateScrollRatio(source)
      clearExpectedTarget()
      if (Math.abs(currentRatio - expectedRatio) <= PROGRAMMATIC_RATIO_TOLERANCE) {
        return
      }
    }

    if (frames.has(sourceKey)) {
      return
    }

    const frame = window.requestAnimationFrame(() => {
      frames.delete(sourceKey)
      if (!mediaMatches() || !source.isConnected || !target.isConnected) {
        return
      }

      const ratio = calculateScrollRatio(source)
      const targetTop = scrollTopForRatio(ratio, target)
      if (target.scrollHeight <= target.clientHeight) {
        return
      }

      expectedTarget = target
      expectedRatio = ratio
      target.scrollTop = targetTop
    })
    frames.set(sourceKey, frame)
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
    cancelFrames()
  })
}
