import { expect, test } from './support/apiMock.js'

async function expectNoHorizontalOverflow(page) {
  const overflow = await page.evaluate(() => {
    return document.documentElement.scrollWidth - document.documentElement.clientWidth
  })
  expect(overflow).toBeLessThanOrEqual(1)
}

test('首页展示接口返回的公开内容', async ({ page, apiMock }) => {
  void apiMock
  await page.goto('/')

  await expect(page.getByRole('heading', { name: '本期刊首' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '第一篇公开文章' })).toBeVisible()
  const stats = page.locator('.home-stats')
  await expect(stats.getByText('12', { exact: true })).toBeVisible()
  await expect(stats.getByText('篇公开内容', { exact: true })).toBeVisible()
})

test('书库筛选与分页同步 URL', async ({ page, apiMock }) => {
  void apiMock
  await page.goto('/library')

  await expect(page.getByRole('heading', { name: '按主题，慢慢翻阅。' })).toBeVisible()
  await expect(page.locator('.content-card')).toHaveCount(6)

  await page.locator('.filter-group').first()
    .getByRole('button', { name: /技术笔记/ })
    .click()
  await expect(page).toHaveURL(/type=NOTE/)
  await expect(page.locator('.content-card')).toHaveCount(4)

  await page.getByRole('button', { name: '清除全部筛选' }).click()
  await expect(page).toHaveURL(/\/library$/)
  await page.getByRole('button', { name: '下一页' }).click()
  await expect(page).toHaveURL(/page=2/)
  await expect(page.locator('.content-card')).toHaveCount(6)
})

test('搜索支持成功、空结果和 429 倒计时', async ({ page, apiMock }) => {
  await page.goto('/search')
  const input = page.getByLabel('搜索关键词')

  await input.fill('公开')
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await expect(page).toHaveURL(/q=%E5%85%AC%E5%BC%80/)
  await expect(page.locator('.content-card')).toHaveCount(6)

  await input.fill('不存在')
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await expect(page.getByRole('heading', { name: '没有找到匹配内容' })).toBeVisible()

  apiMock.rateLimitNextSearch()
  await input.fill('限流')
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await expect(page.getByText(/请求过于频繁，请在 \d+ 秒后重试。/)).toBeVisible()
  await expect(page.getByRole('button', { name: /\d+ 秒后重试/ })).toBeDisabled()
})

test('文章详情展示正文、分类标签和前后文章', async ({ page, apiMock }) => {
  void apiMock
  await page.goto('/post/first-public')

  await expect(page.locator('.post-header h1')).toHaveText('第一篇公开文章')
  await expect(page.locator('.post-header__taxonomy').getByText('技术笔记')).toBeVisible()
  await expect(page.locator('.post-header__taxonomy').getByText('# Vue')).toBeVisible()
  await expect(page.getByRole('navigation', { name: '前后文章' }).getByText('公开文章 02')).toBeVisible()
  await expect(page.locator('.markdown-body')).toContainText('这是一篇 E2E 正文。')
})

test('Markdown 标题在公开页和管理端预览使用相同排版', async ({ page, apiMock }) => {
  await page.goto('/post/first-public')

  const publicHeadingStyle = await page.locator('.markdown-body h1').evaluate((element) => {
    const style = getComputedStyle(element)
    return {
      fontSize: style.fontSize,
      fontWeight: style.fontWeight,
      lineHeight: style.lineHeight,
    }
  })

  await apiMock.authenticate()
  await page.goto('/secret-admin/contents/1/edit')
  const previewHeadingStyle = await page
    .locator('.admin-editor-pane--preview .markdown-body h1')
    .evaluate((element) => {
      const style = getComputedStyle(element)
      return {
        fontSize: style.fontSize,
        fontWeight: style.fontWeight,
        lineHeight: style.lineHeight,
      }
    })

  expect(previewHeadingStyle).toEqual(publicHeadingStyle)
})

test('书库接口失败时显示可重试错误态', async ({ page, apiMock }) => {
  void apiMock
  await page.route('**/api/public/contents?*', (route) => {
    return route.fulfill({
      status: 500,
      contentType: 'application/json',
      body: JSON.stringify({ code: 500, message: '模拟书库故障' }),
    })
  })

  await page.goto('/library')

  await expect(page.getByRole('heading', { name: '书库加载失败' })).toBeVisible()
  await expect(page.getByText('模拟书库故障')).toBeVisible()
  await expect(page.getByRole('button', { name: '重新加载' })).toBeVisible()
})

test('页脚隐私入口展示实际保留策略', async ({ page, apiMock }) => {
  void apiMock
  await page.goto('/')
  await page.getByRole('navigation', { name: '页脚导航' }).getByRole('link', { name: '隐私' }).click()

  await expect(page).toHaveURL(/\/privacy$/)
  await expect(page.getByRole('heading', { name: /只保留维护安全/ })).toBeVisible()
  await expect(page.getByText('当前原始日志保留 30 天')).toBeVisible()
  await expect(page.getByText('匿名聚合保留 180 天')).toBeVisible()
  await expect(page.getByText(/不记录查询参数、请求体、Cookie、Authorization/)).toBeVisible()
})

test.describe('390px 公开端布局', () => {
  test.use({
    viewport: { width: 390, height: 844 },
    isMobile: true,
    hasTouch: true,
  })

  test('首页和书库没有横向溢出', async ({ page, apiMock }) => {
    void apiMock
    await page.goto('/')
    await expect(page.getByRole('heading', { name: '本期刊首' })).toBeVisible()
    await expectNoHorizontalOverflow(page)

    await page.goto('/library')
    await expect(page.getByRole('heading', { name: '按主题，慢慢翻阅。' })).toBeVisible()
    await expectNoHorizontalOverflow(page)

    await page.goto('/privacy')
    await expect(page.getByRole('heading', { name: /只保留维护安全/ })).toBeVisible()
    await expectNoHorizontalOverflow(page)
  })
})
