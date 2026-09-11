import test from 'node:test'
import assert from 'node:assert/strict'

import {
  ADMIN_PAGE_SIZE,
  buildContentPayload,
  contentToForm,
  insertImageMarkdown,
  orderCategoryIds,
  resolveAdminContentQuery,
  validateContentForm,
  validateImageFile,
} from './adminContent.js'

test('normalizes admin content query values', () => {
  assert.deepEqual(
    resolveAdminContentQuery({
      page: '3',
      size: '20',
      type: 'NOTE',
      status: 'DRAFT',
      categoryId: '7',
      tagId: '9',
      sort: 'created_at_desc',
    }),
    {
      page: 3,
      size: 20,
      type: 'NOTE',
      status: 'DRAFT',
      categoryId: 7,
      tagId: 9,
      sort: 'created_at_desc',
    },
  )

  assert.deepEqual(resolveAdminContentQuery({ size: '999', type: 'INVALID' }), {
    page: 1,
    size: ADMIN_PAGE_SIZE,
    type: '',
    status: '',
    categoryId: null,
    tagId: null,
    sort: 'published_at_desc',
  })
})

test('converts content detail into editable form fields', () => {
  const form = contentToForm({
    title: '文章',
    slug: 'article',
    summary: '摘要',
    type: 'NOTE',
    status: 'DRAFT',
    body: '# 正文',
    metadata: { readingTime: 10 },
    categories: [{ id: 2, name: 'Java', slug: 'java' }],
    tags: [{ id: 3, name: 'Spring', slug: 'spring' }],
  })

  assert.deepEqual(form, {
    title: '文章',
    slug: 'article',
    summary: '摘要',
    type: 'NOTE',
    status: 'DRAFT',
    body: '# 正文',
    metadata: '{\n  "readingTime": 10\n}',
    categoryIds: [2],
    tagIds: [3],
  })
})

test('validates required fields and metadata JSON object', () => {
  const errors = validateContentForm({
    title: '',
    slug: 'bad slug',
    type: 'NOTE',
    status: 'DRAFT',
    metadata: '[]',
  })

  assert.equal(errors.title, '标题不能为空')
  assert.equal(errors.slug, 'slug 只能包含字母、数字、下划线和连字符')
  assert.equal(errors.metadata, 'metadata 必须是 JSON 对象')
})

test('puts the selected novel category first in categoryIds', () => {
  const categories = [
    { id: 10, name: '随笔', type: 'NOTE' },
    { id: 20, name: '长篇小说', type: 'NOVEL' },
    { id: 21, name: '番外', type: 'NOVEL' },
  ]

  assert.deepEqual(
    orderCategoryIds([10, 21, 20], categories, 'NOVEL'),
    [21, 20, 10],
  )
  assert.deepEqual(orderCategoryIds([10, 21, 20], categories, 'NOTE'), [10, 21, 20])
})

test('requires a novel category for novel content', () => {
  const errors = validateContentForm(
    {
      title: '标题',
      slug: 'novel-chapter',
      type: 'NOVEL',
      status: 'DRAFT',
      metadata: '',
      categoryIds: [10],
    },
    [{ id: 10, name: '随笔', type: 'NOTE' }],
  )

  assert.equal(errors.categoryIds, '小说必须选择一个小说分类作为作品目录')
})

test('builds a content payload with canonical metadata', () => {
  const categories = [
    { id: 10, name: '散文', type: 'NOTE' },
    { id: 20, name: '小说', type: 'NOVEL' },
  ]

  assert.deepEqual(
    buildContentPayload(
      {
        title: '标题',
        slug: 'slug',
        summary: '',
        type: 'NOVEL',
        status: 'PUBLISHED',
        body: '# 正文',
        metadata: '{"chapter": 1}',
        categoryIds: [10, 20],
        tagIds: [4],
      },
      categories,
    ),
    {
      title: '标题',
      slug: 'slug',
      summary: '',
      type: 'NOVEL',
      status: 'PUBLISHED',
      body: '# 正文',
      metadata: '{"chapter":1}',
      categoryIds: [20, 10],
      tagIds: [4],
    },
  )
})

test('inserts an image at the selected text range', () => {
  assert.deepEqual(
    insertImageMarkdown({
      body: 'before after',
      selectionStart: 7,
      selectionEnd: 7,
      url: '/images/example.png',
      alt: '示例]图',
    }),
    {
      body: 'before ![示例\\]图](/images/example.png)after',
      cursor: 36,
    },
  )
})

test('accepts supported images up to 50MB', () => {
  assert.equal(validateImageFile({ type: 'image/png', size: 1024 }), '')
  assert.equal(
    validateImageFile({ type: 'text/plain', size: 1024 }),
    '仅支持 JPG、PNG、GIF 和 WebP 图片',
  )
  assert.equal(
    validateImageFile({ type: 'image/png', size: 50 * 1024 * 1024 + 1 }),
    '图片不能超过 50MB',
  )
})
