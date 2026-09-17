function validDimension(value) {
  return typeof value === 'number' && Number.isFinite(value) && value > 0
    ? value
    : 0
}

function scrollableDistance({ scrollHeight, clientHeight } = {}) {
  return Math.max(0, validDimension(scrollHeight) - validDimension(clientHeight))
}

export function calculateScrollRatio({
  scrollTop,
  scrollHeight,
  clientHeight,
} = {}) {
  const validScrollTop =
    typeof scrollTop === 'number' && Number.isFinite(scrollTop)
      ? scrollTop
      : 0
  const maxScroll = scrollableDistance({ scrollHeight, clientHeight })

  if (!maxScroll) {
    return 0
  }

  return Math.min(1, Math.max(0, validScrollTop / maxScroll))
}

export function scrollTopForRatio(ratio, { scrollHeight, clientHeight } = {}) {
  const validRatio =
    typeof ratio === 'number' && Number.isFinite(ratio)
      ? ratio
      : 0
  const maxScroll = scrollableDistance({ scrollHeight, clientHeight })

  return Math.min(maxScroll, Math.max(0, validRatio) * maxScroll)
}
