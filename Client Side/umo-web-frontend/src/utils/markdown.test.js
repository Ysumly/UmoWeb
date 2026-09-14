import test from 'node:test'
import assert from 'node:assert/strict'

import { extractMarkdownOutline, renderMarkdown } from './markdown.js'

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

test('escapes image alt text before writing an HTML attribute', () => {
  const html = renderMarkdown('![x" onerror="alert(1)"](/images/missing.png)')

  assert.doesNotMatch(html, /onerror="alert\(1\)"/)
  assert.match(html, /alt="x&quot; onerror=&quot;alert\(1\)&quot;"/)
})

test('builds an outline from later H1, H2, and H3 headings', () => {
  const source = [
    '# 文档标题',
    '',
    '## 目录',
    '',
    '- [第一节](#第一节)',
    '',
    '## 第一节',
    '',
    '### 子节',
    '',
    '#### 不进入目录',
    '',
    '# 后续章节',
    '',
    '## 后续小节',
  ].join('\n')

  assert.deepEqual(extractMarkdownOutline(source), [
    {
      id: '第一节',
      text: '第一节',
      depth: 2,
      children: [
        {
          id: '子节',
          text: '子节',
          depth: 3,
          children: [],
        },
      ],
    },
    {
      id: '后续章节',
      text: '后续章节',
      depth: 1,
      children: [
        {
          id: '后续小节',
          text: '后续小节',
          depth: 2,
          children: [],
        },
      ],
    },
  ])
})

test('generates compatible heading ids and aliases for legacy links', () => {
  const source = [
    '## 主要技术：数组结构体 (SoA)',
    '## 混合方案：Array of Structures of Arrays (AoSoA)',
    '## 与 **DOD** 的关系',
  ].join('\n\n')
  const html = renderMarkdown(source)
  const outline = extractMarkdownOutline(source)

  assert.deepEqual(
    outline.map((heading) => heading.id),
    [
      '主要技术数组结构体-soa',
      '混合方案array-of-structures-of-arrays-aosoa',
      '与-dod-的关系',
    ],
  )
  assert.match(html, /id="主要技术数组结构体-soa"/)
  assert.match(html, /id="主要技术-数组结构体-soa"/)
  assert.match(html, /id="混合方案array-of-structures-of-arrays-aosoa"/)
  assert.match(html, /id="混合方案-array-of-structures-of-arrays-aosoa"/)
  assert.match(html, /id="与-dod-的关系"/)
})

test('numbers duplicate heading ids without breaking outline order', () => {
  const source = '## 重复标题\n\n### 重复标题\n\n## 重复标题'
  const html = renderMarkdown(source)
  const outline = extractMarkdownOutline(source)
  const ids = []
  const collectIds = (nodes) => {
    for (const node of nodes) {
      ids.push(node.id)
      collectIds(node.children)
    }
  }
  collectIds(outline)

  assert.deepEqual(
    ids,
    ['重复标题', '重复标题-2', '重复标题-3'],
  )
  assert.match(html, /<h2 id="重复标题">/)
  assert.match(html, /<h3 id="重复标题-2">/)
  assert.match(html, /<h2 id="重复标题-3">/)
})

test('preserves an explicit safe heading span id without rendering raw HTML', () => {
  const source =
    '##### <span id="通过C#脚本创建一个场景">通过C#脚本创建场景</span>'
  const html = renderMarkdown(source)

  assert.match(html, /<h5 id="通过C#脚本创建一个场景">/)
  assert.match(html, /通过C#脚本创建场景<\/h5>/)
  assert.doesNotMatch(html, /&lt;span/)
})

test('keeps unsafe heading HTML escaped', () => {
  const source = '<span id="x" onclick="alert(1)">危险标题</span>'
  const html = renderMarkdown(source)

  assert.doesNotMatch(html, /onclick="alert\(1\)"/)
  assert.match(html, /&lt;span/)
})

test('ignores heading-like lines inside fenced code blocks', () => {
  const source = '# 文档\n\n```md\n## 代码里的标题\n```\n\n## 真正的标题'

  assert.deepEqual(extractMarkdownOutline(source), [
    {
      id: '真正的标题',
      text: '真正的标题',
      depth: 2,
      children: [],
    },
  ])
})
