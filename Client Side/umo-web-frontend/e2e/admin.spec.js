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
})
