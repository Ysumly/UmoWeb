import { expect, test } from './support/apiMock.js'

test('未登录时重定向登录页，登录后可退出', async ({ page, apiMock }) => {
  void apiMock
  await page.goto('/secret-admin/contents')

  await expect(page).toHaveURL(/\/secret-admin\/login\?redirect=/)
  await page.getByLabel('用户名').fill('admin')
  await page.getByLabel('密码').fill('admin123')
  await page.getByRole('button', { name: '登录' }).click()

  await expect(page).toHaveURL(/\/secret-admin\/contents$/)
  await expect(page.getByRole('heading', { name: '文章管理' })).toBeVisible()
  await expect(page.getByRole('row', { name: /第一篇公开文章/ })).toBeVisible()
  expect(await page.evaluate(() => localStorage.getItem('token'))).toBe('e2e-token')

  await page.getByRole('button', { name: '退出登录' }).click()
  await expect(page).toHaveURL(/\/secret-admin\/login$/)
  expect(await page.evaluate(() => localStorage.getItem('token'))).toBeNull()
})

test('文章列表支持筛选并完成新建、编辑、发布和删除', async ({ page, apiMock }) => {
  await apiMock.authenticate()
  await page.goto('/secret-admin/contents')

  await page.locator('.admin-filters').getByLabel('状态').selectOption('DRAFT')
  await expect(page).toHaveURL(/status=DRAFT/)
  await expect(page.locator('tbody tr')).toHaveCount(2)
  await expect(page.getByRole('row', { name: /待发布草稿/ })).toBeVisible()

  await page.getByRole('link', { name: '新建文章' }).click()
  await expect(page.getByRole('heading', { name: '新建文章' })).toBeVisible()
  await page.getByLabel(/^标题/).fill('E2E 新文章')
  await page.getByLabel(/^slug/).fill('e2e-new-content')
  await page.getByLabel('摘要').fill('由 Playwright 创建。')
  await page.getByLabel('Markdown 正文').fill('# E2E 内容\n\n正文已创建。')
  await page.getByRole('button', { name: '创建文章' }).click()

  await expect(page).toHaveURL(/\/secret-admin\/contents\?saved=1/)
  await expect(page.getByText('文章已保存')).toBeVisible()
  await page.locator('.admin-filters').getByLabel('状态').selectOption('DRAFT')
  await expect(page.getByRole('row', { name: /E2E 新文章/ })).toBeVisible()

  await page.getByRole('row', { name: /E2E 新文章/ })
    .getByRole('link', { name: '编辑' })
    .click()
  await expect(page.getByRole('heading', { name: '编辑文章' })).toBeVisible()
  await page.getByLabel(/^标题/).fill('E2E 已发布文章')
  await page.getByLabel(/^状态/).selectOption('PUBLISHED')
  await page.getByRole('button', { name: '保存修改' }).click()

  await expect(page.getByRole('row', { name: /E2E 已发布文章/ })).toContainText('已发布')
  const updatedRow = page.getByRole('row', { name: /E2E 已发布文章/ })
  page.once('dialog', (dialog) => dialog.accept())
  await updatedRow.getByRole('button', { name: '删除' }).click()

  await expect(page.getByText('文章已删除')).toBeVisible()
  await expect(page.getByRole('row', { name: /E2E 已发布文章/ })).toHaveCount(0)
})

test('分类和标签支持完整 CRUD', async ({ page, apiMock }) => {
  await apiMock.authenticate()
  await page.goto('/secret-admin/categories')

  const alignedNames = await page.locator('.admin-table__category-name strong').evaluateAll(
    (elements) => elements.map((element) => element.getBoundingClientRect().x),
  )
  expect(Math.max(...alignedNames) - Math.min(...alignedNames)).toBeLessThan(1)

  await page.getByRole('button', { name: '新建分类' }).click()
  await page.getByLabel(/^分类名/).fill('E2E 分类')
  await page.getByLabel(/^slug/).fill('e2e-category')
  await page.getByRole('button', { name: '创建分类' }).click()
  await expect(page.getByRole('row', { name: /E2E 分类/ })).toBeVisible()

  await page.getByRole('row', { name: /E2E 分类/ })
    .getByRole('button', { name: '编辑' })
    .click()
  await page.getByLabel(/^分类名/).fill('E2E 分类已更新')
  await page.getByRole('button', { name: '保存修改' }).click()
  await expect(page.getByRole('row', { name: /E2E 分类已更新/ })).toBeVisible()

  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('row', { name: /E2E 分类已更新/ })
    .getByRole('button', { name: '删除' })
    .click()
  await expect(page.getByText('分类已删除')).toBeVisible()
  await expect(page.getByRole('row', { name: /E2E 分类已更新/ })).toHaveCount(0)

  await page.getByRole('link', { name: '标签管理' }).click()
  await page.getByRole('button', { name: '新建标签' }).click()
  await page.getByLabel(/^标签名/).fill('E2E 标签')
  await page.getByLabel(/^slug/).fill('e2e-tag')
  await page.getByRole('button', { name: '创建标签' }).click()
  await expect(page.getByRole('row', { name: /E2E 标签/ })).toBeVisible()

  await page.getByRole('row', { name: /E2E 标签/ })
    .getByRole('button', { name: '编辑' })
    .click()
  await page.getByLabel(/^标签名/).fill('E2E 标签已更新')
  await page.getByRole('button', { name: '保存修改' }).click()
  await expect(page.getByRole('row', { name: /E2E 标签已更新/ })).toBeVisible()

  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('row', { name: /E2E 标签已更新/ })
    .getByRole('button', { name: '删除' })
    .click()
  await expect(page.getByText('标签已删除')).toBeVisible()
  await expect(page.getByRole('row', { name: /E2E 标签已更新/ })).toHaveCount(0)
})

test('分类编辑加载态不会改变表格列位置', async ({ page, apiMock }) => {
  await apiMock.authenticate()
  await page.goto('/secret-admin/categories')
  await page.route('**/api/admin/categories/1', async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 500))
    await route.continue()
  })

  const row = page.getByRole('row', { name: /技术笔记/ })
  const typeCell = row.locator('td').nth(1)
  const editButton = row.getByRole('button', { name: '编辑' })
  const before = await typeCell.boundingBox()

  await editButton.click()
  await expect(editButton).toHaveAttribute('aria-busy', 'true')
  await expect(editButton).toHaveText('编辑')

  const during = await typeCell.boundingBox()
  expect(Math.abs(during.x - before.x)).toBeLessThan(1)
})

test('文章编辑器的分类和标签不拆字且超过一页时分页', async ({ page, apiMock }) => {
  await apiMock.authenticate()
  apiMock.state.categories.push(
    ...Array.from({ length: 15 }, (_, index) => ({
      id: 100 + index,
      name: `扩展分类${index + 1}号`,
      slug: `category-${index + 1}`,
      type: 'NOTE',
      parentId: null,
      sortOrder: index + 10,
    })),
  )
  apiMock.state.tags.push(
    ...Array.from({ length: 15 }, (_, index) => ({
      id: 100 + index,
      name: `扩展标签${index + 1}号`,
      slug: `tag-${index + 1}`,
    })),
  )
  await page.goto('/secret-admin/contents/new')

  const categoryGroup = page.getByRole('group', { name: '分类' })
  const tagGroup = page.getByRole('group', { name: '标签' })

  await expect(categoryGroup.locator('.admin-choice-list label')).toHaveCount(12)
  await expect(tagGroup.locator('.admin-choice-list label')).toHaveCount(12)
  await expect(categoryGroup.getByRole('navigation', { name: '分类分页' })).toBeVisible()
  await expect(tagGroup.getByRole('navigation', { name: '标签分页' })).toBeVisible()

  for (const text of ['技术笔记', '长期主义']) {
    const label = page.locator('.admin-choice-list label', { hasText: text })
    const box = await label.locator('span').boundingBox()
    expect(box.height).toBeLessThan(24)
    expect(box.width).toBeGreaterThan(box.height)
  }

  await categoryGroup.getByRole('button', { name: '下一页' }).click()
  await expect(categoryGroup.locator('.admin-choice-list label')).toHaveCount(5)

  await tagGroup.getByRole('button', { name: '下一页' }).click()
  await expect(tagGroup.locator('.admin-choice-list label')).toHaveCount(6)
})

test('metadata 更多说明可以展开常用字段', async ({ page, apiMock }) => {
  await apiMock.authenticate()
  await page.goto('/secret-admin/contents/new')

  const details = page.locator('.admin-field__details')
  await details.getByText('更多', { exact: true }).click()

  await expect(details).toHaveAttribute('open', '')
  await expect(details.getByText('预计阅读分钟数')).toBeVisible()
  await expect(details.getByText('原文链接')).toBeVisible()
  await expect(details.getByText('JSON 不支持注释')).toBeVisible()
})

test('从 Markdown front matter 预填并创建文章', async ({ page, apiMock }) => {
  await apiMock.authenticate()
  await page.goto('/secret-admin/contents/new')

  await page.getByRole('button', { name: '导入 Markdown' }).click()
  await page.getByLabel('选择 Markdown 文件').setInputFiles({
    name: 'front-matter-note.md',
    mimeType: 'text/markdown',
    buffer: Buffer.from([
      '---',
      'title: 导入的文章',
      'slug: imported-note',
      'summary: 来自 front matter',
      'type: NOTE',
      'status: DRAFT',
      'categorySlugs: [notes]',
      'tagSlugs: [vue]',
      'metadata:',
      '  readingTime: 8',
      '---',
      '# 导入的文章',
      '',
      '正文内容。',
      '',
      '![本地图](./local.png)',
    ].join('\n')),
  })

  await expect(page.getByRole('status', { name: 'Markdown 导入结果' })).toContainText(
    '已读取 front-matter-note.md',
  )
  await expect(page.getByLabel(/^标题/)).toHaveValue('导入的文章')
  await expect(page.getByLabel(/^slug/)).toHaveValue('imported-note')
  await expect(page.getByLabel('摘要')).toHaveValue('来自 front matter')
  await expect(page.getByLabel('metadata')).toHaveValue('{\n  "readingTime": 8\n}')
  await expect(page.getByText('图片引用不会自动上传：./local.png')).toBeVisible()
  await expect(page.locator('.admin-editor-pane--preview').getByRole('heading', {
    name: '导入的文章',
  })).toBeVisible()

  await page.getByRole('button', { name: '创建文章' }).click()
  await expect(page).toHaveURL(/\/secret-admin\/contents\?saved=1/)
  await expect(page.getByText('文章已保存')).toBeVisible()
  const imported = apiMock.state.contents.find((content) => content.slug === 'imported-note')
  expect(imported).toMatchObject({
    title: '导入的文章',
    summary: '来自 front matter',
    type: 'NOTE',
    status: 'DRAFT',
    categoryIds: [1],
    tagIds: [1],
    metadata: { readingTime: 8 },
  })
  expect(imported.body).toContain('![本地图](./local.png)')
})

test('非法 Markdown 不覆盖当前文章表单', async ({ page, apiMock }) => {
  await apiMock.authenticate()
  await page.goto('/secret-admin/contents/new')
  await page.getByLabel(/^标题/).fill('保留当前草稿')

  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('button', { name: '导入 Markdown' }).click()
  await page.getByLabel('选择 Markdown 文件').setInputFiles({
    name: 'broken.md',
    mimeType: 'text/markdown',
    buffer: Buffer.from('---\ntitle: [broken\n---\n# 正文'),
  })

  await expect(page.getByRole('alert')).toContainText('YAML 解析失败')
  await expect(page.getByLabel(/^标题/)).toHaveValue('保留当前草稿')
})

test('Markdown 导入遇到重复 slug 时留在编辑器等待修正', async ({ page, apiMock }) => {
  await apiMock.authenticate()
  await page.goto('/secret-admin/contents/new')

  await page.getByRole('button', { name: '导入 Markdown' }).click()
  await page.getByLabel('选择 Markdown 文件').setInputFiles({
    name: 'duplicate.md',
    mimeType: 'text/markdown',
    buffer: Buffer.from([
      '---',
      'title: 重复文章',
      'slug: first-public',
      '---',
      '# 重复文章',
    ].join('\n')),
  })
  await page.getByRole('button', { name: '创建文章' }).click()

  await expect(page.getByRole('alert')).toContainText('文章 slug 已存在')
  await expect(page).toHaveURL(/\/secret-admin\/contents\/new$/)
  await expect(page.getByLabel(/^slug/)).toHaveValue('first-public')
})

test('Markdown 导入错误阻止提交并在修正后允许创建', async ({ page, apiMock }) => {
  await apiMock.authenticate()
  await page.goto('/secret-admin/contents/new')

  await page.getByRole('button', { name: '导入 Markdown' }).click()
  await page.getByLabel('选择 Markdown 文件').setInputFiles({
    name: 'invalid-metadata.md',
    mimeType: 'text/markdown',
    buffer: Buffer.from([
      '---',
      'title: 错误 metadata',
      'slug: invalid-metadata',
      'categorySlugs: [missing-category]',
      'metadata: [not, an, object]',
      '---',
      '# 错误 metadata',
    ].join('\n')),
  })
  const contentTypeCount = apiMock.state.contents.length

  await page.getByRole('button', { name: '创建文章' }).click()

  await expect(page.getByRole('alert')).toContainText('请先修正 Markdown 导入错误')
  await expect(page).toHaveURL(/\/secret-admin\/contents\/new$/)
  expect(apiMock.state.contents).toHaveLength(contentTypeCount)

  await page.getByLabel('metadata').fill('{"readingTime":3}')
  await page.getByRole('group', { name: '分类' }).getByLabel('技术笔记').check()
  await page.getByRole('button', { name: '创建文章' }).click()

  await expect(page).toHaveURL(/\/secret-admin\/contents\?saved=1/)
  expect(apiMock.state.contents).toHaveLength(contentTypeCount + 1)
})

test('站点设置保存后刷新公开站点缓存', async ({ page, apiMock }) => {
  await apiMock.authenticate()
  await page.goto('/secret-admin/options')

  await page.getByLabel('站点标题').fill('Umo 已更新站')
  await page.getByRole('button', { name: '保存全部修改' }).first().click()
  await expect(page.getByText('站点设置已保存')).toBeVisible()

  await page.goto('/')
  await expect(page.getByRole('banner').getByText('Umo 已更新站')).toBeVisible()
})

test('修改密码成功后清理 token 并要求重新登录', async ({ page, apiMock }) => {
  await apiMock.authenticate()
  await page.goto('/secret-admin/password')

  await page.getByLabel(/^旧密码/).fill('admin123')
  await page.getByLabel(/^新密码/).fill('new-admin123')
  await page.getByLabel(/^确认新密码/).fill('new-admin123')
  await page.getByRole('button', { name: '修改密码' }).click()

  await expect(page).toHaveURL(/\/secret-admin\/login\?changed=1/)
  await expect(page.getByText('密码已修改，请重新登录')).toBeVisible()
  expect(await page.evaluate(() => localStorage.getItem('token'))).toBeNull()
})

test('图片管理支持引用筛选、删除和 409 保护', async ({ page, apiMock }) => {
  await apiMock.authenticate()
  await page.goto('/secret-admin/images')

  await expect(page.getByRole('heading', { name: '图片管理' })).toBeVisible()
  await expect(page.getByText('used-image.png')).toBeVisible()
  await expect(page.getByText('orphan-image.png')).toBeVisible()

  await page.getByRole('button', { name: '未引用' }).click()
  await expect(page.getByText('used-image.png')).toHaveCount(0)
  await expect(page.getByText('orphan-image.png')).toBeVisible()

  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('button', { name: '删除 orphan-image.png' }).click()
  await expect(page.getByText('图片已删除')).toBeVisible()
  await expect(page.getByText('orphan-image.png')).toHaveCount(0)
  expect(apiMock.state.images).toHaveLength(1)

  await page.getByRole('button', { name: '使用中' }).click()
  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('button', { name: '删除 used-image.png' }).click()
  await expect(page.getByRole('alert')).toContainText('无法删除：图片仍被内容引用')
  expect(apiMock.state.images).toHaveLength(1)
})

test.describe('390px 管理端布局', () => {
  test.use({
    viewport: { width: 390, height: 844 },
    isMobile: true,
    hasTouch: true,
  })

  test('文章表格在容器内横向滚动且页面没有溢出', async ({ page, apiMock }) => {
    await apiMock.authenticate()
    await page.goto('/secret-admin/contents')

    await expect(page.getByRole('row', { name: /第一篇公开文章/ })).toBeVisible()
    const dimensions = await page.evaluate(() => {
      const tableWrap = document.querySelector('.admin-table-wrap')
      return {
        pageOverflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
        tableOverflow: tableWrap.scrollWidth - tableWrap.clientWidth,
      }
    })

    expect(dimensions.pageOverflow).toBeLessThanOrEqual(1)
    expect(dimensions.tableOverflow).toBeGreaterThan(0)
  })

  test('图片网格在移动端保持单列且页面没有溢出', async ({ page, apiMock }) => {
    await apiMock.authenticate()
    await page.goto('/secret-admin/images')

    await expect(page.getByRole('heading', { name: '图片管理' })).toBeVisible()
    const dimensions = await page.evaluate(() => {
      const grid = document.querySelector('.admin-image-grid')
      return {
        pageOverflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
        columns: getComputedStyle(grid).gridTemplateColumns.split(' ').length,
      }
    })

    expect(dimensions.pageOverflow).toBeLessThanOrEqual(1)
    expect(dimensions.columns).toBe(1)
  })
})
