export function shouldUseOutlineDrawer({
  contentHeight,
  viewportHeight,
  narrow = false,
} = {}) {
  if (narrow) {
    return true
  }

  const validContentHeight =
    typeof contentHeight === 'number'
    && Number.isFinite(contentHeight)
    && contentHeight > 0
      ? contentHeight
      : 0
  const validViewportHeight =
    typeof viewportHeight === 'number'
    && Number.isFinite(viewportHeight)
    && viewportHeight > 0
      ? viewportHeight
      : 0

  return validContentHeight > validViewportHeight / 2
}
