import hljs from 'highlight.js/lib/core'
import bash from 'highlight.js/lib/languages/bash'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import sql from 'highlight.js/lib/languages/sql'
import { marked, Renderer } from 'marked'

hljs.registerLanguage('bash', bash)
hljs.registerLanguage('java', java)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('js', javascript)
hljs.registerLanguage('sql', sql)

marked.use({
  extensions: [
    {
      name: 'adjacentStrong',
      level: 'inline',
      start(source) {
        const index = source.indexOf('**')
        return index === -1 ? undefined : index
      },
      tokenizer(source) {
        const match = /^\*\*(?!\s)([^\n]*?[^\s*])\*\*(?=[^\s*])/.exec(source)
        if (!match) {
          return undefined
        }

        return {
          type: 'strong',
          raw: match[0],
          text: match[1],
          tokens: this.lexer.inlineTokens(match[1]),
        }
      },
    },
  ],
})

function escapeHtml(value) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;')
}

function sanitizeUrl(value) {
  const url = typeof value === 'string' ? value.trim() : ''
  if (!url) {
    return null
  }

  const compact = url.replace(/[\u0000-\u0020\u007F]+/g, '')
  const scheme = compact.match(/^([A-Za-z][A-Za-z0-9+.-]*):/)?.[1]?.toLowerCase()
  if (scheme && !['http', 'https', 'mailto'].includes(scheme)) {
    return null
  }

  try {
    return encodeURI(url)
  } catch {
    return null
  }
}

function isRemoteUrl(value) {
  const compact = String(value || '').replace(/[\u0000-\u0020\u007F]+/g, '')
  return /^(https?:)?\/\//i.test(compact)
}

function plainInlineText(tokens = []) {
  return tokens
    .map((token) => {
      if (!token) {
        return ''
      }
      if (token.type === 'image') {
        return token.text || token.title || ''
      }
      if (Array.isArray(token.tokens)) {
        return plainInlineText(token.tokens)
      }
      if (typeof token.text === 'string') {
        return token.text
      }
      return token.raw || ''
    })
    .join('')
}

function normalizeHeadingText(value) {
  return String(value || '')
    .replace(/<[^>]*>/g, '')
    .normalize('NFKC')
    .toLowerCase()
    .trim()
}

function canonicalHeadingSlug(value) {
  const normalized = normalizeHeadingText(value)
  const compact = normalized.replace(/[\p{P}\p{S}]/gu, '')
  return (
    compact
      .replace(/\s+/g, '-')
      .replace(/-+/g, '-')
      .replace(/^-+|-+$/g, '') || 'section'
  )
}

function compatibleHeadingSlug(value) {
  const normalized = normalizeHeadingText(value)
  return (
    normalized
      .replace(/[\p{P}\p{S}]/gu, '-')
      .replace(/\s+/g, '-')
      .replace(/-+/g, '-')
      .replace(/^-+|-+$/g, '') || 'section'
  )
}

function extractSafeHeadingSpan(value) {
  const match = String(value || '')
    .trim()
    .match(/^<span\s+id="([^"]+)"\s*>([\s\S]*)<\/span>$/i)
  if (!match) {
    return null
  }

  const id = match[1].trim()
  const text = match[2].trim()
  if (
    !id ||
    !text ||
    id.length > 200 ||
    !/^[^\s"'<>\\\u0000-\u001f]+$/u.test(id)
  ) {
    return null
  }
  return { id, text }
}

function createHeadingIdResolver() {
  const usedIds = new Set()
  const counters = new Map()

  function uniqueId(base) {
    let suffix = counters.get(base) || 1
    let candidate = base
    if (usedIds.has(candidate)) {
      do {
        suffix += 1
        candidate = `${base}-${suffix}`
      } while (usedIds.has(candidate))
    }
    counters.set(base, suffix)
    usedIds.add(candidate)
    return candidate
  }

  return (text, explicitId = '') => {
    const canonical = canonicalHeadingSlug(text)
    const compatible = compatibleHeadingSlug(text)
    const primary = uniqueId(explicitId || canonical)
    const aliases = []
    const aliasCandidates = explicitId
      ? [canonical, compatible]
      : compatible === canonical
        ? []
        : [compatible]

    for (const candidate of aliasCandidates) {
      if (candidate === primary || aliases.includes(candidate)) {
        continue
      }
      const alias = uniqueId(candidate)
      if (alias !== primary) {
        aliases.push(alias)
      }
    }

    return { primary, aliases }
  }
}

function collectHeadingMetadata(source) {
  const resolver = createHeadingIdResolver()
  const headings = []
  let skippedDocumentTitle = false

  function visit(tokens = []) {
    for (const token of tokens) {
      if (token.type === 'heading') {
        const safeSpan = extractSafeHeadingSpan(token.text)
        const text = safeSpan?.text || plainInlineText(token.tokens)
        const ids = resolver(text, safeSpan?.id)
        const skipAsDocumentTitle = token.depth === 1 && !skippedDocumentTitle
        if (token.depth === 1) {
          skippedDocumentTitle = true
        }
        headings.push({
          id: ids.primary,
          aliases: ids.aliases,
          depth: token.depth,
          text,
          safeSpan,
          skipAsDocumentTitle,
        })
      }

      if (Array.isArray(token.tokens)) {
        visit(token.tokens)
      }
      if (Array.isArray(token.items)) {
        for (const item of token.items) {
          visit(item.tokens || [])
        }
      }
    }
  }

  visit(marked.lexer(source))
  return headings
}

export function extractMarkdownOutline(source = '') {
  const headings = collectHeadingMetadata(source)
  const roots = []
  const stack = []

  for (const heading of headings) {
    if (
      heading.skipAsDocumentTitle ||
      heading.depth > 3 ||
      heading.text.trim() === '目录'
    ) {
      continue
    }

    const node = {
      id: heading.id,
      text: heading.text,
      depth: heading.depth,
      children: [],
    }
    while (stack.length && stack[stack.length - 1].depth >= node.depth) {
      stack.pop()
    }
    if (stack.length) {
      stack[stack.length - 1].children.push(node)
    } else {
      roots.push(node)
    }
    stack.push(node)
  }

  return roots
}

export function renderMarkdown(source = '', options = {}) {
  const renderer = new Renderer()
  const headings = collectHeadingMetadata(source)
  let headingIndex = 0

  renderer.html = ({ text }) => escapeHtml(text)
  renderer.heading = ({ tokens, depth }) => {
    const heading = headings[headingIndex]
    headingIndex += 1
    if (!heading) {
      return `<h${depth}>${renderer.parser.parseInline(tokens)}</h${depth}>`
    }

    const aliasHtml = heading.aliases
      .map(
        (id) =>
          `<span id="${escapeHtml(id)}" class="markdown-anchor-alias" aria-hidden="true"></span>`,
      )
      .join('')
    const content = heading.safeSpan
      ? escapeHtml(heading.safeSpan.text)
      : renderer.parser.parseInline(tokens)

    return `<h${depth} id="${escapeHtml(heading.id)}">${aliasHtml}${content}</h${depth}>`
  }
  renderer.link = ({ href, title, tokens }) => {
    const safeHref = sanitizeUrl(href)
    const text = renderer.parser.parseInline(tokens)
    if (!safeHref) {
      return text
    }
    const titleAttribute = title ? ` title="${escapeHtml(title)}"` : ''
    return `<a href="${escapeHtml(safeHref)}"${titleAttribute}>${text}</a>`
  }
  renderer.image = ({ href, title, text }) => {
    const safeHref = sanitizeUrl(href)
    const alt = escapeHtml(text || '')
    if (!safeHref || (options.allowRemoteImages === false && isRemoteUrl(safeHref))) {
      return alt
    }
    const titleAttribute = title ? ` title="${escapeHtml(title)}"` : ''
    return `<img src="${escapeHtml(safeHref)}" alt="${alt}"${titleAttribute}>`
  }
  renderer.code = ({ text, lang }) => {
    const language = lang && hljs.getLanguage(lang) ? lang : null
    const highlighted = language
      ? hljs.highlight(text, { language }).value
      : escapeHtml(text)
    const languageClass = language ? ` language-${language}` : ''

    return `<pre><code class="hljs${languageClass}">${highlighted}</code></pre>`
  }

  return marked.parse(source, {
    breaks: false,
    gfm: true,
    renderer,
  })
}
