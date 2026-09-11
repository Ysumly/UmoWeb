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

export function renderMarkdown(source = '') {
  const renderer = new Renderer()

  renderer.html = ({ text }) => escapeHtml(text)
  renderer.link = ({ href, title, tokens }) => {
    const safeHref = sanitizeUrl(href)
    const text = renderer.parser.parseInline(tokens)
    if (!safeHref) {
      return text
    }
    const titleAttribute = title ? ` title="${escapeHtml(title)}"` : ''
    return `<a href="${escapeHtml(safeHref)}"${titleAttribute}>${text}</a>`
  }
  renderer.image = ({ href, title, text, tokens }) => {
    const safeHref = sanitizeUrl(href)
    const alt = tokens
      ? renderer.parser.parseInline(tokens, renderer.parser.textRenderer)
      : escapeHtml(text || '')
    if (!safeHref) {
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
