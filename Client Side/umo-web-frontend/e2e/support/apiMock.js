import { expect as baseExpect, test as base } from '@playwright/test'

const E2E_TOKEN = 'e2e-token'

function json(route, body, status = 200) {
  return route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  })
}

function empty(route, status = 204) {
  return route.fulfill({ status })
}

function error(route, status, message) {
  return json(route, { code: status, message }, status)
}

function clone(value) {
  return structuredClone(value)
}

function buildCategoryTree(categories) {
  const nodes = new Map(categories.map((category) => [
    category.id,
    { ...category, children: [] },
  ]))
  const roots = []

  for (const category of nodes.values()) {
    if (category.parentId && nodes.has(category.parentId)) {
      nodes.get(category.parentId).children.push(category)
    } else {
      roots.push(category)
    }
  }

  function sort(nodesToSort) {
    nodesToSort.sort((left, right) => {
      return left.sortOrder - right.sortOrder || left.id - right.id
    })
    nodesToSort.forEach((node) => sort(node.children))
  }

  sort(roots)
  return roots
}

function createContentState() {
  const contents = Array.from({ length: 12 }, (_, index) => {
    const id = index + 1
    const type = ['NOTE', 'BOOK_REVIEW', 'NOVEL'][index % 3]
    return {
      id,
      title: id === 1 ? '第一篇公开文章' : `公开文章 ${String(id).padStart(2, '0')}`,
      slug: id === 1 ? 'first-public' : `public-${id}`,
      summary: id === 1
        ? '用于验证公开首页、书库与详情主路径。'
        : `第 ${id} 篇用于浏览器回归的公开摘要。`,
      type,
      status: 'PUBLISHED',
      body: id === 1
        ? '# 第一篇公开文章\n\n这是一篇 **E2E** 正文。\n\n```js\nconsole.log("Umo")\n```'
        : `## 公开文章 ${id}\n\n浏览器回归正文。`,
      metadata: { readingTime: 5 + (id % 4), difficulty: 'intermediate' },
      publishedAt: `2026-09-${String(12 - id).padStart(2, '0')}T10:00:00`,
      categoryIds: type === 'NOVEL' ? [4] : type === 'BOOK_REVIEW' ? [3] : [1, 2],
      tagIds: [((id - 1) % 3) + 1],
      createdAt: `2026-09-${String(12 - id).padStart(2, '0')}T09:00:00`,
    }
  })

  contents.push(
    {
      id: 13,
      title: '待发布草稿',
      slug: 'draft-content',
      summary: '管理端可见但公开端不可见。',
      type: 'NOTE',
      status: 'DRAFT',
      body: '# 草稿',
      metadata: {},
      publishedAt: null,
      categoryIds: [1],
      tagIds: [1],
      createdAt: '2026-09-11T08:00:00',
    },
    {
      id: 14,
      title: '第二篇草稿',
      slug: 'second-draft',
      summary: '用于分页和筛选。',
      type: 'BOOK_REVIEW',
      status: 'DRAFT',
      body: '# 第二篇草稿',
      metadata: {},
      publishedAt: null,
      categoryIds: [3],
      tagIds: [2],
      createdAt: '2026-09-10T08:00:00',
    },
    {
      id: 15,
      title: '仅属于子分类的公开文章',
      slug: 'child-category-only',
      summary: '用于验证父分类筛选会包含后代分类。',
      type: 'NOTE',
      status: 'PUBLISHED',
      body: '# 仅属于子分类',
      metadata: {},
      publishedAt: '2026-08-31T08:00:00',
      categoryIds: [2],
      tagIds: [],
      createdAt: '2026-08-31T07:00:00',
    },
  )

  return contents
}

function createState() {
  return {
    contents: createContentState(),
    categories: [
      { id: 1, name: '技术笔记', slug: 'notes', type: 'NOTE', parentId: null, sortOrder: 1 },
      { id: 2, name: 'Vue', slug: 'vue', type: 'NOTE', parentId: 1, sortOrder: 1 },
      { id: 3, name: '读后有感', slug: 'reviews', type: 'BOOK_REVIEW', parentId: null, sortOrder: 2 },
      { id: 4, name: '长篇连载', slug: 'serials', type: 'NOVEL', parentId: null, sortOrder: 3 },
    ],
    tags: [
      { id: 1, name: 'Vue', slug: 'vue' },
      { id: 2, name: '阅读', slug: 'reading' },
      { id: 3, name: '长期主义', slug: 'long-term' },
    ],
    options: {
      site_title: 'Umo 测试站',
      site_subtitle: '代码、阅读与创作',
      about_page: '## 关于测试站\n\n用于浏览器回归。',
      project_page: '## 测试项目\n\n这里是项目页面。',
    },
    images: [
      {
        id: 1,
        url: '/images/2026/09/used-image.png',
        originalName: 'used-image.png',
        size: 1024,
        contentType: 'image/png',
        createdAt: '2026-09-12T09:00:00',
        referenced: true,
      },
      {
        id: 2,
        url: '/images/2026/09/orphan-image.png',
        originalName: 'orphan-image.png',
        size: 2048,
        contentType: 'image/png',
        createdAt: '2026-09-13T09:00:00',
        referenced: false,
      },
    ],
    password: 'admin123',
    validToken: true,
    searchRateLimitOnce: false,
    requests: [],
  }
}

function enrichContent(content, state) {
  const categoriesById = new Map(state.categories.map((category) => [category.id, category]))
  const tagsById = new Map(state.tags.map((tag) => [tag.id, tag]))
  return {
    ...clone(content),
    categories: content.categoryIds
      .map((id) => categoriesById.get(id))
      .filter(Boolean)
      .map(({ id, name, slug, type }) => ({ id, name, slug, type })),
    tags: content.tagIds
      .map((id) => tagsById.get(id))
      .filter(Boolean)
      .map(({ id, name, slug }) => ({ id, name, slug })),
  }
}

function enrichSearchContent(content, state, query) {
  const item = enrichContent(content, state)
  if (query && String(content.body || '').toLowerCase().includes(query)) {
    item.excerpt = String(content.body)
      .replace(/[#*`]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim()
  }
  return item
}

function sortContents(contents, sort) {
  return [...contents].sort((left, right) => {
    if (sort === 'created_at_desc') {
      return String(right.createdAt).localeCompare(String(left.createdAt)) || right.id - left.id
    }
    const leftDate = left.publishedAt || ''
    const rightDate = right.publishedAt || ''
    return rightDate.localeCompare(leftDate) || right.id - left.id
  })
}

function relatedScore(candidate, current) {
  const tagIds = new Set(current.tagIds || [])
  const categoryIds = new Set(current.categoryIds || [])
  const sharedTags = (candidate.tagIds || []).filter((id) => tagIds.has(id)).length
  const sharedCategories = (candidate.categoryIds || [])
    .filter((id) => categoryIds.has(id))
    .length
  return sharedTags * 3 + sharedCategories * 2 + (candidate.type === current.type ? 1 : 0)
}

function buildRelatedContents(content, state, excludedIds = []) {
  const excluded = new Set(excludedIds.filter(Boolean))
  return state.contents
    .filter((candidate) => (
      candidate.status === 'PUBLISHED'
      && !excluded.has(candidate.id)
    ))
    .map((candidate) => ({
      candidate,
      score: relatedScore(candidate, content),
    }))
    .filter(({ score }) => score > 0)
    .sort((left, right) => (
      right.score - left.score
      || String(right.candidate.publishedAt).localeCompare(String(left.candidate.publishedAt))
      || right.candidate.id - left.candidate.id
    ))
    .slice(0, 4)
    .map(({ candidate }) => enrichContent(candidate, state))
}

function paginate(contents, searchParams) {
  const page = Number(searchParams.get('page') || 1)
  const size = Number(searchParams.get('size') || 10)
  const start = (page - 1) * size
  return {
    items: contents.slice(start, start + size),
    page,
    size,
    total: contents.length,
  }
}

function collectCategoryIds(categoryId, categories) {
  const resolved = new Set([categoryId])
  let changed = true
  while (changed) {
    changed = false
    for (const category of categories) {
      if (category.parentId && resolved.has(category.parentId) && !resolved.has(category.id)) {
        resolved.add(category.id)
        changed = true
      }
    }
  }
  return resolved
}

function filterContents(contents, searchParams, publishedOnly, categories) {
  const type = searchParams.get('type')
  const status = searchParams.get('status')
  const categoryId = Number(searchParams.get('categoryId') || 0)
  const includeDescendants = searchParams.get('includeDescendants') === 'true'
  const tagId = Number(searchParams.get('tagId') || 0)
  const query = String(searchParams.get('q') || '').trim().toLowerCase()
  const categoryIds = categoryId
    ? collectCategoryIds(categoryId, categories)
    : null

  return contents.filter((content) => {
    if (publishedOnly && content.status !== 'PUBLISHED') {
      return false
    }
    if (status && content.status !== status) {
      return false
    }
    if (type && content.type !== type) {
      return false
    }
    if (categoryIds) {
      const matchesCategory = includeDescendants
        ? content.categoryIds.some((id) => categoryIds.has(id))
        : content.categoryIds.includes(categoryId)
      if (!matchesCategory) {
        return false
      }
    }
    if (tagId && !content.tagIds.includes(tagId)) {
      return false
    }
    if (
      query
      && !content.title.toLowerCase().includes(query)
      && !content.summary.toLowerCase().includes(query)
      && !String(content.body || '').toLowerCase().includes(query)
    ) {
      return false
    }
    return true
  })
}

function nextId(items) {
  return Math.max(0, ...items.map((item) => item.id)) + 1
}

function normalizeContentPayload(payload, existing = {}) {
  return {
    id: existing.id,
    title: payload.title,
    slug: payload.slug,
    summary: payload.summary || '',
    type: payload.type,
    status: payload.status,
    body: payload.body || '',
    metadata: payload.metadata ? JSON.parse(payload.metadata) : {},
    publishedAt: payload.status === 'PUBLISHED'
      ? existing.publishedAt || '2026-09-12T10:00:00'
      : null,
    categoryIds: payload.categoryIds || [],
    tagIds: payload.tagIds || [],
    createdAt: existing.createdAt || '2026-09-12T09:00:00',
  }
}

async function hasValidToken(route, state) {
  return state.validToken
    && route.request().headers().authorization === `Bearer ${E2E_TOKEN}`
}

async function handlePublicApi(route, state, pathname, searchParams) {
  if (pathname === '/api/public/site-info') {
    return json(route, {
      siteTitle: state.options.site_title,
      siteSubtitle: state.options.site_subtitle,
      aboutHtml: state.options.about_page,
      projectHtml: state.options.project_page,
    })
  }
  if (pathname === '/api/public/pages/about') {
    return json(route, { content: state.options.about_page })
  }
  if (pathname === '/api/public/pages/project') {
    return json(route, { content: state.options.project_page })
  }
  if (pathname === '/api/public/categories') {
    const type = searchParams.get('type')
    const categories = type
      ? state.categories.filter((category) => category.type === type)
      : state.categories
    return json(route, buildCategoryTree(categories))
  }
  if (pathname === '/api/public/tags') {
    return json(route, clone(state.tags))
  }
  if (pathname === '/api/public/contents/search') {
    if (state.searchRateLimitOnce) {
      state.searchRateLimitOnce = false
      return error(route, 429, 'Too many requests. Please wait 10 seconds.')
    }
    const matches = filterContents(state.contents, searchParams, true, state.categories)
    const page = paginate(sortContents(matches, 'published_at_desc'), searchParams)
    return json(route, {
      ...page,
      items: page.items.map((content) => (
        enrichSearchContent(content, state, String(searchParams.get('q') || '').trim().toLowerCase())
      )),
    })
  }
  if (pathname === '/api/public/contents') {
    const matches = filterContents(state.contents, searchParams, true, state.categories)
    const page = paginate(sortContents(matches, searchParams.get('sort')), searchParams)
    return json(route, {
      ...page,
      items: page.items.map((content) => enrichContent(content, state)),
    })
  }
  if (pathname.startsWith('/api/public/contents/')) {
    const slug = decodeURIComponent(pathname.slice('/api/public/contents/'.length))
    const content = state.contents.find((item) => (
      item.slug === slug && item.status === 'PUBLISHED'
    ))
    if (!content) {
      return error(route, 404, `Content not found: ${slug}`)
    }
    const ordered = sortContents(
      state.contents.filter((item) => item.status === 'PUBLISHED'),
      'published_at_desc',
    )
    const index = ordered.findIndex((item) => item.id === content.id)
    const neighbor = (item) => item
      ? { id: item.id, title: item.title, slug: item.slug, publishedAt: item.publishedAt }
      : null
    const previous = ordered[index + 1]
    const next = ordered[index - 1]
    return json(route, {
      ...enrichContent(content, state),
      previous: neighbor(previous),
      next: neighbor(next),
      related: buildRelatedContents(content, state, [
        content.id,
        previous?.id,
        next?.id,
      ]),
    })
  }
  return error(route, 404, `Mock endpoint not found: ${pathname}`)
}

async function handleAdminApi(route, state, pathname, searchParams) {
  const method = route.request().method()

  if (pathname === '/api/admin/login' && method === 'POST') {
    const payload = route.request().postDataJSON()
    if (payload.username !== 'admin' || payload.password !== state.password) {
      return error(route, 401, 'Invalid username or password')
    }
    state.validToken = true
    return json(route, { token: E2E_TOKEN, expiresAt: '2026-09-12T10:00:00' })
  }

  if (!(await hasValidToken(route, state))) {
    return error(route, 401, 'Missing or invalid Authorization header')
  }

  if (pathname === '/api/admin/change-password' && method === 'PUT') {
    const payload = route.request().postDataJSON()
    if (payload.oldPassword !== state.password) {
      return error(route, 401, 'Old password is incorrect')
    }
    state.password = payload.newPassword
    state.validToken = false
    return empty(route)
  }

  if (pathname === '/api/admin/options') {
    if (method === 'GET') {
      return json(route, clone(state.options))
    }
  }
  if (pathname.startsWith('/api/admin/options/') && method === 'PUT') {
    const key = decodeURIComponent(pathname.slice('/api/admin/options/'.length))
    state.options[key] = route.request().postDataJSON().value
    return empty(route)
  }

  if (pathname === '/api/admin/images/upload' && method === 'POST') {
    return json(route, {
      id: 1,
      url: '/images/2026/09/test-image.png',
      originalName: 'test-image.png',
      size: 68,
    })
  }
  if (pathname === '/api/admin/images' && method === 'GET') {
    const usage = searchParams.get('usage')
    const images = usage
      ? state.images.filter((image) => image.referenced === (usage === 'REFERENCED'))
      : state.images
    return json(route, paginate(images, searchParams))
  }
  if (pathname.match(/^\/api\/admin\/images\/\d+$/) && method === 'DELETE') {
    const id = Number(pathname.split('/').at(-1))
    const index = state.images.findIndex((image) => image.id === id)
    if (index < 0) {
      return error(route, 404, 'Image not found')
    }
    if (state.images[index].referenced) {
      return error(route, 409, '图片仍被内容引用，无法删除')
    }
    state.images.splice(index, 1)
    return empty(route)
  }

  if (pathname === '/api/admin/categories') {
    if (method === 'GET') {
      const type = searchParams.get('type')
      const categories = type
        ? state.categories.filter((category) => category.type === type)
        : state.categories
      return json(route, buildCategoryTree(categories))
    }
    if (method === 'POST') {
      const payload = route.request().postDataJSON()
      const category = { id: nextId(state.categories), ...payload }
      state.categories.push(category)
      return json(route, clone(category))
    }
  }
  if (pathname.match(/^\/api\/admin\/categories\/\d+$/)) {
    const id = Number(pathname.split('/').at(-1))
    const index = state.categories.findIndex((category) => category.id === id)
    if (index < 0) {
      return error(route, 404, 'Category not found')
    }
    if (method === 'GET') {
      return json(route, clone(state.categories[index]))
    }
    if (method === 'PUT') {
      state.categories[index] = { ...state.categories[index], ...route.request().postDataJSON() }
      return json(route, clone(state.categories[index]))
    }
    if (method === 'DELETE') {
      state.categories.splice(index, 1)
      return empty(route)
    }
  }

  if (pathname === '/api/admin/tags') {
    if (method === 'GET') {
      return json(route, clone(state.tags))
    }
    if (method === 'POST') {
      const tag = { id: nextId(state.tags), ...route.request().postDataJSON() }
      state.tags.push(tag)
      return json(route, clone(tag))
    }
  }
  if (pathname.match(/^\/api\/admin\/tags\/\d+$/)) {
    const id = Number(pathname.split('/').at(-1))
    const index = state.tags.findIndex((tag) => tag.id === id)
    if (index < 0) {
      return error(route, 404, 'Tag not found')
    }
    if (method === 'PUT') {
      state.tags[index] = { ...state.tags[index], ...route.request().postDataJSON() }
      return json(route, clone(state.tags[index]))
    }
    if (method === 'DELETE') {
      state.tags.splice(index, 1)
      return empty(route)
    }
  }

  if (pathname === '/api/admin/contents') {
    if (method === 'GET') {
      const matches = filterContents(
        state.contents,
        searchParams,
        false,
        state.categories,
      )
      const page = paginate(sortContents(matches, searchParams.get('sort')), searchParams)
      return json(route, {
        ...page,
        items: page.items.map((content) => enrichContent(content, state)),
      })
    }
    if (method === 'POST') {
      const payload = route.request().postDataJSON()
      const content = normalizeContentPayload(payload, { id: nextId(state.contents) })
      if (state.contents.some((item) => item.slug === content.slug)) {
        return error(route, 409, `文章 slug 已存在: ${content.slug}`)
      }
      state.contents.push(content)
      return json(route, enrichContent(content, state))
    }
  }
  if (pathname.match(/^\/api\/admin\/contents\/\d+$/)) {
    const id = Number(pathname.split('/').at(-1))
    const index = state.contents.findIndex((content) => content.id === id)
    if (index < 0) {
      return error(route, 404, 'Content not found')
    }
    if (method === 'GET') {
      return json(route, enrichContent(state.contents[index], state))
    }
    if (method === 'PUT') {
      const payload = route.request().postDataJSON()
      state.contents[index] = normalizeContentPayload(payload, state.contents[index])
      return json(route, enrichContent(state.contents[index], state))
    }
    if (method === 'DELETE') {
      state.contents.splice(index, 1)
      return empty(route)
    }
  }

  return error(route, 404, `Mock endpoint not found: ${pathname}`)
}

async function handleApi(route, state) {
  const request = route.request()
  const url = new URL(request.url())
  state.requests.push({
    method: request.method(),
    pathname: url.pathname,
    search: url.search,
    body: request.postData() || '',
  })

  if (url.pathname.startsWith('/api/public/')) {
    return handlePublicApi(route, state, url.pathname, url.searchParams)
  }
  if (url.pathname.startsWith('/api/admin/')) {
    return handleAdminApi(route, state, url.pathname, url.searchParams)
  }
  return route.continue()
}

export const test = base.extend({
  apiMock: async ({ page }, use) => {
    const state = createState()
    const apiMock = {
      state,
      rateLimitNextSearch() {
        state.searchRateLimitOnce = true
      },
      async authenticate() {
        await page.addInitScript((token) => {
          localStorage.setItem('token', token)
        }, E2E_TOKEN)
      },
    }

    await page.route('**/api/**', (route) => handleApi(route, state))
    await use(apiMock)
  },
})

export const expect = baseExpect
