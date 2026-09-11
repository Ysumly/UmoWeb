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

test('rejects executable Markdown link and image protocols', () => {
  const html = renderMarkdown(
    '[危险链接](javascript:alert(1))\n\n![危险图片](data:text/html;base64,PHNjcmlwdD4=)\n\n[安全链接](https://example.com)',
  )

  assert.doesNotMatch(html, /href="javascript:/i)
  assert.doesNotMatch(html, /<img/i)
  assert.match(html, /危险链接/)
  assert.match(html, /<a href="https:\/\/example\.com">安全链接<\/a>/)
})
