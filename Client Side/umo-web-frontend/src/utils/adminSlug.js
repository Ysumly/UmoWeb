import { pinyin } from 'pinyin-pro'

export const MAX_CONTENT_SLUG_LENGTH = 200

function normalizeSlugPart(value) {
  return String(value ?? '')
    .normalize('NFKD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
}

export function buildSlugFromTitle(title) {
  const romanized = pinyin(String(title ?? ''), {
    toneType: 'none',
    type: 'array',
    nonZh: 'consecutive',
  }).join(' ')
  const normalized = normalizeSlugPart(romanized)
  const truncated = normalized
    .slice(0, MAX_CONTENT_SLUG_LENGTH)
    .replace(/-+$/g, '')

  return truncated || 'article'
}

export function syncSlugFromTitle({
  title = '',
  slug = '',
  autoSync = true,
  lastGeneratedSlug = '',
} = {}) {
  if (!autoSync) {
    return { slug, autoSync, lastGeneratedSlug }
  }

  const nextSlug = buildSlugFromTitle(title)
  return {
    slug: nextSlug,
    autoSync: true,
    lastGeneratedSlug: nextSlug,
  }
}

export function syncSlugFromUserInput({
  value = '',
  generatedSlug = '',
} = {}) {
  const slug = String(value ?? '').trim()
  if (!slug) {
    return {
      slug: '',
      autoSync: true,
      lastGeneratedSlug: '',
    }
  }

  return {
    slug,
    autoSync: slug === generatedSlug,
    lastGeneratedSlug: generatedSlug,
  }
}
