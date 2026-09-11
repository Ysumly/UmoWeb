export function getApiErrorMessage(error, fallback = '请求失败，请稍后重试') {
  const message = error?.response?.data?.message
  return typeof message === 'string' && message.trim() ? message.trim() : fallback
}

export function parseRetryAfterSeconds(error, fallback = 10) {
  const headerValue = Number(error?.response?.headers?.['retry-after'])
  if (Number.isFinite(headerValue) && headerValue > 0) {
    return Math.ceil(headerValue)
  }

  const message = error?.response?.data?.message
  const match = typeof message === 'string'
    ? message.match(/wait\s+(\d+)\s+seconds?/i)
    : null
  if (match) {
    return Math.max(1, Number(match[1]))
  }

  return Math.max(1, Number(fallback) || 10)
}
