import test from 'node:test'
import assert from 'node:assert/strict'

import { renderMarkdown } from './markdown.js'

test('escapes raw HTML while preserving Markdown output', () => {
  const html = renderMarkdown('## 标题\n\n<img src=x onerror="alert(1)">\n\n**正文**')

  assert.doesNotMatch(html, /<img/i)
  assert.match(html, /&lt;img/i)
  assert.match(html, /<h2/)
  assert.match(html, /<strong>正文<\/strong>/)
})
