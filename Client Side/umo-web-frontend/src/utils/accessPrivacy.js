export const DEFAULT_ACCESS_PRIVACY = Object.freeze({
  rawRetentionDays: 30,
  aggregateRetentionDays: 180,
})

function requireInteger(value, minimum, maximum, label) {
  if (!Number.isInteger(value) || value < minimum || value > maximum) {
    throw new Error(`${label}保留期配置无效`)
  }
  return value
}

export function parseAccessPrivacy(payload) {
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
    throw new Error('访问统计配置无效')
  }
  return {
    rawRetentionDays: requireInteger(
      payload.rawRetentionDays,
      7,
      30,
      '原始日志',
    ),
    aggregateRetentionDays: requireInteger(
      payload.aggregateRetentionDays,
      1,
      3650,
      '聚合数据',
    ),
  }
}

export async function loadAccessPrivacy(fetcher = globalThis.fetch) {
  if (typeof fetcher !== 'function') {
    throw new Error('当前环境无法读取访问统计配置')
  }
  const response = await fetcher('/privacy-config.json', { cache: 'no-store' })
  if (!response.ok) {
    throw new Error(`访问统计配置读取失败（${response.status}）`)
  }
  return parseAccessPrivacy(await response.json())
}
