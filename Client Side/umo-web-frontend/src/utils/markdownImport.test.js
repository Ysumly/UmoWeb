import test from 'node:test'
import assert from 'node:assert/strict'

import {
  MAX_MARKDOWN_IMPORT_SIZE,
  parseMarkdownImport,
  validateMarkdownImportFile,
} from './markdownImport.js'

test('accepts Markdown files up to 10 MiB', () => {
  assert.equal(
    validateMarkdownImportFile({ name: 'article.md', size: MAX_MARKDOWN_IMPORT_SIZE }),
    '',
  )
  assert.equal(
    validateMarkdownImportFile({ name: 'article.markdown', size: 1024 }),
    '',
  )
  assert.equal(
    validateMarkdownImportFile({ name: 'article.txt', size: 1024 }),
    '仅支持 .md 或 .markdown 文件',
  )
  assert.equal(
    validateMarkdownImportFile({
      name: 'article.md',
      size: MAX_MARKDOWN_IMPORT_SIZE + 1,
    }),
    'Markdown 文件不能超过 10 MiB',
  )
  assert.equal(
    validateMarkdownImportFile({ name: 'empty.md', size: 0 }),
    'Markdown 文件不能为空',
  )
})

test('maps complete YAML front matter to the existing editor form', () => {
  const result = parseMarkdownImport({
    filename: 'fallback.md',
    source: [
      '---',
      'title: Spring Boot 快速上手',
      'slug: spring-boot-quickstart',
      'summary: 快速上手指南',
      'type: NOTE',
      'status: PUBLISHED',
      'categorySlugs: [java, spring]',
      'tagSlugs: [backend]',
      'metadata:',
      '  readingTime: 10',
      '---',
      '# Spring Boot',
      '',
      '正文',
    ].join('\n'),
    categories: [
      { id: 2, slug: 'java', type: 'NOTE' },
      { id: 3, slug: 'spring', type: 'NOTE' },
    ],
    tags: [{ id: 4, slug: 'backend' }],
  })

  assert.deepEqual(result.form, {
    title: 'Spring Boot 快速上手',
    slug: 'spring-boot-quickstart',
    summary: '快速上手指南',
    type: 'NOTE',
    status: 'PUBLISHED',
    body: '# Spring Boot\n\n正文',
    metadata: '{\n  "readingTime": 10\n}',
    categoryIds: [2, 3],
    tagIds: [4],
  })
  assert.deepEqual(result.errors, {})
  assert.deepEqual(result.warnings, [])
})

test('falls back to H1 and filename without front matter', () => {
  const result = parseMarkdownImport({
    filename: 'fallback-note.md',
    source: '\uFEFF# 回退标题\r\n\r\n正文\r\n',
  })

  assert.equal(result.form.title, '回退标题')
  assert.equal(result.form.slug, 'fallback-note')
  assert.equal(result.form.type, 'NOTE')
  assert.equal(result.form.status, 'DRAFT')
  assert.equal(result.form.body, '# 回退标题\n\n正文\n')
  assert.equal(result.form.metadata, '{}')
})

test('rejects malformed or unclosed YAML front matter', () => {
  const malformed = parseMarkdownImport({
    filename: 'article.md',
    source: '---\ntitle: [broken\n---\n# Body',
  })
  const unclosed = parseMarkdownImport({
    filename: 'article.md',
    source: '---\ntitle: Article\n# Body',
  })

  assert.match(malformed.errors.import, /YAML/)
  assert.match(unclosed.errors.import, /front matter/)
  assert.equal(malformed.form, undefined)
  assert.equal(unclosed.form, undefined)
})

test('reports field errors and warnings without discarding the parsed form', () => {
  const result = parseMarkdownImport({
    filename: 'warning.md',
    source: [
      '---',
      'title: 带警告的文章',
      'slug: warning',
      'type: invalid',
      'categorySlugs: [missing]',
      'categories: [different]',
      'unknownField: value',
      'metadata: [not, an, object]',
      '---',
      '![本地图](./image.png)',
      '![站内图](/images/2026/09/site.png)',
      '![远程图](https://example.com/remote.png)',
    ].join('\n'),
    categories: [{ id: 1, slug: 'java', type: 'NOTE' }],
    tags: [],
  })

  assert.equal(result.form.title, '带警告的文章')
  assert.equal(result.form.type, 'NOTE')
  assert.match(result.errors.type, /NOTE/)
  assert.match(result.errors.categorySlugs, /冲突/)
  assert.match(result.errors.categoryIds, /missing/)
  assert.equal(result.form.metadata, '{}')
  assert.match(result.errors.metadata, /YAML 对象/)
  assert.deepEqual(result.warnings, [
    '忽略不支持的 front matter 字段：unknownField',
    '图片引用不会自动上传：./image.png',
  ])
})

test('requires an existing novel category for novel imports', () => {
  const result = parseMarkdownImport({
    filename: 'chapter.md',
    source: [
      '---',
      'title: 第一章',
      'slug: chapter-1',
      'type: novel',
      'categorySlugs: [notes]',
      '---',
      '正文',
    ].join('\n'),
    categories: [{ id: 1, slug: 'notes', type: 'NOTE' }],
  })

  assert.equal(result.form.type, 'NOVEL')
  assert.equal(result.errors.categoryIds, '小说必须选择一个小说分类作为作品目录')
})

test('rejects category slugs belonging to another content type', () => {
  const result = parseMarkdownImport({
    filename: 'note.md',
    source: [
      '---',
      'title: 跨类型分类',
      'slug: cross-type-category',
      'type: NOTE',
      'categorySlugs: [novel-series]',
      '---',
      '正文',
    ].join('\n'),
    categories: [{ id: 9, slug: 'novel-series', type: 'NOVEL' }],
  })

  assert.match(result.errors.categoryIds, /novel-series/)
  assert.deepEqual(result.form.categoryIds, [])
})

test('uses the first Markdown H1 while ignoring fenced code blocks', () => {
  const result = parseMarkdownImport({
    filename: 'fallback.md',
    source: [
      '```text',
      '# 代码块标题',
      '```',
      '',
      '   # 缩进的真实标题',
      '',
      '正文',
    ].join('\n'),
  })

  assert.equal(result.form.title, '缩进的真实标题')
})

test('warns about relative images inside Markdown tables', () => {
  const result = parseMarkdownImport({
    filename: 'table.md',
    source: [
      '# 表格图片',
      '',
      '| 图片 |',
      '| --- |',
      '| ![本地图](./table.png) |',
    ].join('\n'),
  })

  assert.deepEqual(result.warnings, [
    '图片引用不会自动上传：./table.png',
  ])
})
