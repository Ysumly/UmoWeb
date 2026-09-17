import test from 'node:test'
import assert from 'node:assert/strict'

import {
  ADMIN_CONTENT_STATUSES,
  ADMIN_PAGE_SIZE,
  buildBulkContentPayload,
  buildContentPayload,
  canScheduleContent,
  contentToForm,
  formatBulkOperationError,
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

test('exposes the complete admin content lifecycle', () => {
  assert.deepEqual(ADMIN_CONTENT_STATUSES, ['DRAFT', 'SCHEDULED', 'PUBLISHED', 'ARCHIVED'])
})

test('converts content detail into editable form fields', () => {
  const form = contentToForm({
    title: '文章',
    slug: 'article',
    summary: '摘要',
    type: 'NOTE',
    status: 'DRAFT',
    scheduledAt: null,
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
    scheduledAt: '',
    body: '# 正文',
    metadata: '{\n  "readingTime": 10\n}',
    categoryIds: [2],
    tagIds: [3],
  })
})

test('converts a scheduled timestamp for datetime-local input', () => {
  const form = contentToForm({
    title: '计划文章',
    slug: 'scheduled',
    type: 'NOTE',
    status: 'SCHEDULED',
    scheduledAt: '2026-09-16T10:30:00',
  })

  assert.equal(form.scheduledAt, '2026-09-16T10:30')
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

test('requires a future timestamp only for scheduled content', () => {
  const now = new Date('2026-09-15T11:59:00Z')
  const base = {
    title: '标题',
    slug: 'scheduled-content',
    type: 'NOTE',
    metadata: '',
  }

  assert.equal(
    validateContentForm(
      { ...base, status: 'SCHEDULED', scheduledAt: '' },
      [],
      { now },
    ).scheduledAt,
    '请选择计划发布时间',
  )
  assert.equal(
    validateContentForm(
      { ...base, status: 'SCHEDULED', scheduledAt: '2026-09-15T19:59' },
      [],
      { now },
    ).scheduledAt,
    '计划发布时间必须晚于当前时间',
  )
  assert.deepEqual(
    validateContentForm(
      { ...base, status: 'SCHEDULED', scheduledAt: '2026-09-15T20:00' },
      [],
      { now },
    ),
    {},
  )
  assert.deepEqual(
    validateContentForm(
      { ...base, status: 'PUBLISHED', scheduledAt: '' },
      [],
      { now },
    ),
    {},
  )
})

test('only unpublished draft content can be scheduled', () => {
  assert.equal(canScheduleContent({ status: 'DRAFT', publishedAt: null }), true)
  assert.equal(
    canScheduleContent({
      status: 'SCHEDULED',
      publishedAt: null,
      scheduledAt: '2026-09-16T10:00:00',
    }),
    true,
  )
  assert.equal(
    canScheduleContent({
      status: 'DRAFT',
      publishedAt: '2026-09-14T10:00:00',
    }),
    false,
  )
  assert.equal(
    canScheduleContent({
      status: 'PUBLISHED',
      publishedAt: '2026-09-14T10:00:00',
    }),
    false,
  )
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
        scheduledAt: '',
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
      scheduledAt: null,
      body: '# 正文',
      metadata: '{"chapter":1}',
      categoryIds: [20, 10],
      tagIds: [4],
    },
  )
})

test('builds a scheduled content payload and omits stale schedule time', () => {
  const scheduled = buildContentPayload({
    title: '标题',
    slug: 'scheduled',
    type: 'NOTE',
    status: 'SCHEDULED',
    scheduledAt: '2026-09-16T10:30',
    categoryIds: [],
    tagIds: [],
  })
  const draft = buildContentPayload({
    title: '标题',
    slug: 'draft',
    type: 'NOTE',
    status: 'DRAFT',
    scheduledAt: '2026-09-16T10:30',
    categoryIds: [],
    tagIds: [],
  })

  assert.equal(scheduled.scheduledAt, '2026-09-16T10:30')
  assert.equal(draft.scheduledAt, null)
})

test('builds action-specific bulk payloads', () => {
  assert.deepEqual(
    buildBulkContentPayload({
      action: 'ADD_CATEGORIES',
      contentIds: [1, 2],
      categoryIds: [3],
      tagIds: [4],
    }),
    {
      action: 'ADD_CATEGORIES',
      contentIds: [1, 2],
      categoryIds: [3],
    },
  )
  assert.deepEqual(
    buildBulkContentPayload({
      action: 'ARCHIVE',
      contentIds: [1, 2],
      categoryIds: [3],
      tagIds: [4],
    }),
    {
      action: 'ARCHIVE',
      contentIds: [1, 2],
    },
  )
})

test('formats structured bulk operation failures', () => {
  const message = formatBulkOperationError({
    response: {
      status: 409,
      data: {
        message: '只有已归档内容可以恢复为草稿',
        failures: [
          { contentId: 7, reason: 'NOT_ARCHIVED' },
          { contentId: 8, reason: 'NOVEL_CATEGORY_REMOVE_REQUIRES_EDIT' },
        ],
      },
    },
  })

  assert.equal(
    message,
    '只有已归档内容可以恢复为草稿；内容 7：不是已归档内容；内容 8：小说分类需通过编辑页调整',
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
