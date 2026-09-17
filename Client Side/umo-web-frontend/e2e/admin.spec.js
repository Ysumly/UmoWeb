import { expect, test } from './support/apiMock.js'

function longMarkdown() {
  return Array.from({ length: 80 }, (_, index) => {
    return `## 章节 ${index + 1}\n\n这是用于验证编辑器和预览滚动协同的第 ${index + 1} 段正文。`
  }).join('\n\n')
}

async function waitForAnimationFrames(page) {
  await page.evaluate(() => new Promise((resolve) => {
    requestAnimationFrame(() => requestAnimationFrame(resolve))
  }))
}

async function readScrollRatio(locator) {
  return locator.evaluate((element) => {
    const maxScroll = Math.max(0, element.scrollHeight - element.clientHeight)
    return maxScroll ? element.scrollTop / maxScroll : 0
  })
}

async function expectSyncedWorkspace(page, workspace, editor) {
  const previewPane = workspace.locator('.admin-editor-pane--preview')
  await editor.fill(longMarkdown())
  await expect(previewPane.getByRole('heading', { name: '章节 80' })).toBeAttached()

  const [editorBox, previewBox] = await Promise.all([
    editor.boundingBox(),
    previewPane.boundingBox(),
  ])
  expect(Math.abs(editorBox.y + editorBox.height - previewBox.y - previewBox.height))
    .toBeLessThanOrEqual(1)

  await editor.evaluate((element) => {
    element.scrollTop = (element.scrollHeight - element.clientHeight) * 0.4
  })
  await waitForAnimationFrames(page)
  expect(Math.abs(await readScrollRatio(previewPane) - 0.4)).toBeLessThanOrEqual(0.02)

  await previewPane.evaluate((element) => {
    element.scrollTop = (element.scrollHeight - element.clientHeight) * 0.2
  })
  await waitForAnimationFrames(page)
  expect(Math.abs(await readScrollRatio(editor) - 0.2)).toBeLessThanOrEqual(0.02)

  const settled = [
    await readScrollRatio(editor),
    await readScrollRatio(previewPane),
  ]
  await waitForAnimationFrames(page)
  const stable = [
    await readScrollRatio(editor),
    await readScrollRatio(previewPane),
  ]
  expect(Math.abs(settled[0] - stable[0])).toBeLessThanOrEqual(0.001)
  expect(Math.abs(settled[1] - stable[1])).toBeLessThanOrEqual(0.001)
}

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

test('文章编辑器在桌面保持等高并按比例双向同步滚动', async ({ page, apiMock }) => {
  await apiMock.authenticate()
  await page.goto('/secret-admin/contents/new')

  const workspace = page.locator('.admin-editor-workspace')
  const editor = workspace.getByLabel('Markdown 正文')
  await expectSyncedWorkspace(page, workspace, editor)
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

test('About 和 Project 设置编辑器在桌面保持等高并双向同步滚动', async ({ page, apiMock }) => {
  await apiMock.authenticate()
  await page.goto('/secret-admin/options')

  const workspaces = page.locator('.admin-option-workspace')
  await expect(workspaces).toHaveCount(2)

  for (const [index, label] of ['About 页面 Markdown', 'Project 页面 Markdown'].entries()) {
    const workspace = workspaces.nth(index)
    await expectSyncedWorkspace(page, workspace, workspace.getByLabel(label))
  }
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

test('文章列表支持当前页批量标签、归档和恢复', async ({ page, apiMock }) => {
  await apiMock.authenticate()
  await page.goto('/secret-admin/contents')

  await page.getByLabel('选择 第一篇公开文章').check()
  await page.getByLabel('选择 公开文章 02').check()
  await page.locator('.admin-bulk-bar').getByLabel('操作').selectOption('ADD_TAGS')
  await page.locator('.admin-bulk-bar select').nth(1).selectOption('3')
  await page.locator('.admin-bulk-bar').getByRole('button', { name: '添加标签' }).click()

  await expect(page.getByText('批量操作完成：更新 2 篇，未变化 0 篇')).toBeVisible()
  expect(apiMock.state.contents.find((content) => content.id === 1).tagIds).toContain(3)
  expect(apiMock.state.contents.find((content) => content.id === 2).tagIds).toContain(3)

  await page.getByLabel('选择 第一篇公开文章').check()
  await page.locator('.admin-bulk-bar').getByLabel('操作').selectOption('ARCHIVE')
  page.once('dialog', (dialog) => dialog.accept())
  await page.locator('.admin-bulk-bar').getByRole('button', { name: '归档' }).click()
  await expect(page.getByRole('row', { name: /第一篇公开文章/ })).toContainText('已归档')

  await page.locator('.admin-filters').getByLabel('状态').selectOption('ARCHIVED')
  await expect(page.getByRole('row', { name: /第一篇公开文章/ })).toBeVisible()
  await page.getByLabel('选择 第一篇公开文章').check()
  await page.locator('.admin-bulk-bar').getByLabel('操作').selectOption('RESTORE_DRAFT')
  await page.locator('.admin-bulk-bar').getByRole('button', { name: '恢复为草稿' }).click()
  await expect(page.getByRole('row', { name: /第一篇公开文章/ })).toHaveCount(0)
})

test('编辑器支持未来定时发布且已发布文章不能反向排期', async ({ page, apiMock }) => {
  await apiMock.authenticate()
  await page.goto('/secret-admin/contents/new')

  await page.getByLabel(/^标题/).fill('E2E 定时文章')
  await page.getByLabel(/^slug/).fill('e2e-scheduled-content')
  await page.getByLabel('Markdown 正文').fill('# 定时发布')
  await page.getByLabel(/^状态/).selectOption('SCHEDULED')
  await page.getByLabel(/计划发布时间/).fill('2099-09-20T10:00')
  await page.getByRole('button', { name: '创建文章' }).click()

  await expect(page).toHaveURL(/\/secret-admin\/contents\?saved=1/)
  await page.locator('.admin-filters').getByLabel('状态').selectOption('SCHEDULED')
  await expect(page.getByRole('row', { name: /E2E 定时文章/ })).toContainText('待发布')

  await page.goto('/secret-admin/contents/1/edit')
  await expect(page.getByLabel(/^状态/).locator('option[value="SCHEDULED"]')).toHaveCount(0)
})

test('图片一致性检查展示三类问题与来源，删除后报告失效', async ({ page, apiMock }) => {
  await apiMock.authenticate()
  await page.goto('/secret-admin/images')

  await page.getByRole('button', { name: '检查一致性' }).click()

  await expect(page.getByRole('heading', { name: '图片一致性报告' })).toBeVisible()
  await expect(page.getByText('检查完成：发现 4 项问题')).toBeVisible()
  await expect(page.getByText('引用断裂').first()).toBeVisible()
  await expect(page.getByText('记录缺文件').first()).toBeVisible()
  await expect(page.getByText('磁盘孤立文件').first()).toBeVisible()
  await expect(page.getByText('/images/2026/09/missing-record.png')).toBeVisible()
  await expect(page.getByText('文章：第一篇公开文章')).toBeVisible()
  await expect(page.getByText('固定页：About 页面')).toBeVisible()
  await expect(page.getByText('missing-file.png', { exact: true })).toBeVisible()
  await expect(page.getByText('/images/2026/09/untracked-file.png')).toBeVisible()
  await expect(page.getByText(/扫描时间：2026年09月15日/)).toBeVisible()

  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('button', { name: '删除 orphan-image.png' }).click()
  await expect(page.getByText('图片已删除')).toBeVisible()
  await expect(page.getByRole('heading', { name: '图片一致性报告' })).toHaveCount(0)
})

test('图片一致性检查失败时保留错误提示且不展示报告', async ({ page, apiMock }) => {
  await apiMock.authenticate()
  apiMock.state.imageIntegrityError = true
  await page.goto('/secret-admin/images')

  await page.getByRole('button', { name: '检查一致性' }).click()

  await expect(page.getByRole('alert')).toContainText('图片一致性检查失败')
  await expect(page.getByRole('heading', { name: '图片一致性报告' })).toHaveCount(0)
})

test('删除图片后，在途一致性扫描不会恢复旧报告', async ({ page, apiMock }) => {
  await apiMock.authenticate()
  apiMock.state.imageIntegrityDelayMs = 600
  await page.goto('/secret-admin/images')

  await page.getByRole('button', { name: '检查一致性' }).click()
  await expect(page.getByRole('button', { name: '检查中...' })).toBeDisabled()

  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('button', { name: '删除 orphan-image.png' }).click()
  await expect(page.getByText('图片已删除')).toBeVisible()

  await page.waitForTimeout(800)
  await expect(page.getByRole('heading', { name: '图片一致性报告' })).toHaveCount(0)
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
    await page.getByRole('button', { name: '检查一致性' }).click()
    await expect(page.getByRole('heading', { name: '图片一致性报告' })).toBeVisible()
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
