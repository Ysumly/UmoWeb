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

export function renderMarkdown(source = '') {
  const renderer = new Renderer()

  renderer.html = ({ text }) => escapeHtml(text)
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
