function validDimension(value) {
  return typeof value === 'number' && Number.isFinite(value) && value > 0
    ? value
    : 0
}

export function extractMarkdownHeadingLines(source = '') {
  const lines = String(source).replace(/\r\n?/g, '\n').split('\n')
  const headings = []
  let fence = null

  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index]
    const fenceMatch = line.match(/^ {0,3}(`{3,}|~{3,})/)
    if (fenceMatch) {
      const marker = fenceMatch[1][0]
      if (!fence) {
        fence = marker
      } else if (fence === marker) {
        fence = null
      }
      continue
    }
    if (fence) {
      continue
    }

    const atx = line.match(/^ {0,3}(#{1,6})(?:[ \t]+|$)(.*)$/)
    if (atx) {
      headings.push({
        line: index,
        level: atx[1].length,
        text: atx[2].replace(/[ \t]+#+[ \t]*$/, '').trim(),
      })
      continue
    }

    const nextLine = lines[index + 1] || ''
    const setext = nextLine.match(/^ {0,3}(=+|-+)[ \t]*$/)
    if (setext && line.trim()) {
      headings.push({
        line: index,
        level: setext[1][0] === '=' ? 1 : 2,
        text: line.trim(),
      })
      index += 1
    }
  }

  return headings
}

export function interpolateBetweenAnchors(sourceOffsets = [], targetOffsets = [], scrollTop = 0) {
  if (
    !Array.isArray(sourceOffsets)
    || !Array.isArray(targetOffsets)
    || sourceOffsets.length < 2
    || sourceOffsets.length !== targetOffsets.length
  ) {
    return null
  }

  const sourceStart = sourceOffsets[0]
  const sourceEnd = sourceOffsets[sourceOffsets.length - 1]
  const position = Math.min(sourceEnd, Math.max(sourceStart, Number(scrollTop) || 0))
  let lowerIndex = 0
  let upperIndex = sourceOffsets.length - 1

  while (lowerIndex + 1 < upperIndex) {
    const middleIndex = Math.floor((lowerIndex + upperIndex) / 2)
    if (sourceOffsets[middleIndex] <= position) {
      lowerIndex = middleIndex
    } else {
      upperIndex = middleIndex
    }
  }

  const nextIndex = lowerIndex + 1
  const sourceDistance = sourceOffsets[nextIndex] - sourceOffsets[lowerIndex]
  const ratio = sourceDistance > 0
    ? Math.min(1, Math.max(0, (position - sourceOffsets[lowerIndex]) / sourceDistance))
    : 0
  const targetDistance = targetOffsets[nextIndex] - targetOffsets[lowerIndex]

  return targetOffsets[lowerIndex] + ratio * targetDistance
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
