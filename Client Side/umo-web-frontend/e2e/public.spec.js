import { expect, test } from './support/apiMock.js'

async function mockOutlineArticle(page) {
  await page.route('**/api/public/contents/outline-public', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 98,
        slug: 'outline-public',
        title: '长文目录测试',
        summary: '用于验证目录、阅读进度和长文滚动行为。',
        type: 'NOTE',
        status: 'PUBLISHED',
        body: [
          '# 长文目录测试',
          '',
          '这是一篇用于验证目录的正文。',
          '',
          '## 第一节',
          '',
          '第一节的第一段正文，用于确保文章具有足够的阅读长度。'.repeat(3),
          '',
          '### 子节一',
          '',
          '子节一正文。'.repeat(20),
          '',
          '### 子节二',
          '',
          '子节二正文。'.repeat(20),
          '',
          '## 第二节',
          '',
          '第二节正文。'.repeat(24),
          '',
          '### 子节三',
          '',
          '子节三正文。'.repeat(24),
          '',
          '```js',
          'console.log("Umo")',
          '```',
          '',
          '结尾阅读段落。'.repeat(60),
        ].join('\n'),
        metadata: {},
        publishedAt: '2026-09-12T10:00:00',
        categories: [],
        tags: [],
        previous: null,
        next: null,
      }),
    }),
  )
}

async function mockTallOutlineArticle(page) {
  const body = ['# 超高目录测试', '', '用于验证目录超过半屏后的抽屉降级。', '']

  for (let index = 1; index <= 24; index += 1) {
    body.push(
      `## 第 ${index} 节`,
      '',
      `第 ${index} 节正文。`.repeat(16),
      '',
    )
  }

  await page.route('**/api/public/contents/tall-outline-public', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 97,
        slug: 'tall-outline-public',
        title: '超高目录测试',
        summary: '用于验证目录超过半屏后的抽屉降级。',
        type: 'NOTE',
        status: 'PUBLISHED',
        body: body.join('\n'),
        metadata: {},
        publishedAt: '2026-09-12T10:00:00',
        categories: [],
        tags: [],
        previous: null,
        next: null,
      }),
    }),
  )
}

async function mockRelatedArticle(page) {
  const related = [
    {
      id: 201,
      slug: 'related-one',
      title: '相关阅读一',
      summary: '与当前文章共享标签的第一篇内容。',
      type: 'NOTE',
      status: 'PUBLISHED',
      metadata: { readingTime: 6 },
      publishedAt: '2026-09-10T10:00:00',
      categories: [{ id: 1, name: '技术笔记', slug: 'notes', type: 'NOTE' }],
      tags: [{ id: 1, name: 'Vue', slug: 'vue' }],
    },
    {
      id: 202,
      slug: 'related-two',
      title: '相关阅读二',
      summary: '与当前文章共享分类的第二篇内容。',
      type: 'NOTE',
      status: 'PUBLISHED',
      metadata: { readingTime: 8 },
      publishedAt: '2026-09-09T10:00:00',
      categories: [{ id: 1, name: '技术笔记', slug: 'notes', type: 'NOTE' }],
      tags: [],
    },
    {
      id: 203,
      slug: 'related-three',
      title: '相关阅读三',
      summary: '与当前文章同类型的第三篇内容。',
      type: 'NOTE',
      status: 'PUBLISHED',
      metadata: { readingTime: 4 },
      publishedAt: '2026-09-08T10:00:00',
      categories: [],
      tags: [],
    },
  ]

  await page.route('**/api/public/contents/related-public', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 200,
        slug: 'related-public',
        title: '相关文章主篇',
        summary: '用于验证详情页相关阅读和前后篇去重。',
        type: 'NOTE',
        status: 'PUBLISHED',
        body: '# 相关文章主篇\n\n正文内容。',
        metadata: {},
        publishedAt: '2026-09-12T10:00:00',
        categories: [],
        tags: [],
        previous: {
          id: 204,
          slug: 'related-previous',
          title: '上一篇文章',
          publishedAt: '2026-09-11T10:00:00',
        },
        next: null,
        related,
      }),
    }),
  )
}

async function expectNoHorizontalOverflow(page) {
  const overflow = await page.evaluate(() => {
    return document.documentElement.scrollWidth - document.documentElement.clientWidth
  })
  expect(overflow).toBeLessThanOrEqual(1)
}

async function submitPublicSearch(page, input, value) {
  await input.fill(value)
  await expect(input).toHaveValue(value)
  const response = page.waitForResponse((candidate) => {
    const url = new URL(candidate.url())
    return (
      url.pathname === '/api/public/contents/search'
      && url.searchParams.get('q') === value
    )
  })
  await input.press('Enter')
  await response
}

test('首页展示接口返回的公开内容', async ({ page, apiMock }) => {
  void apiMock
  await page.goto('/')

  await expect(page.getByRole('heading', { name: '本期刊首' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '第一篇公开文章' })).toBeVisible()
  const stats = page.locator('.home-stats')
  await expect(stats.getByText('13', { exact: true })).toBeVisible()
  await expect(stats.getByText('篇公开内容', { exact: true })).toBeVisible()
})

test('公开页头与页脚工具入口进入工具中心', async ({ page, apiMock }) => {
  void apiMock
  await page.goto('/')

  const headerTools = page
    .getByRole('navigation', { name: '公开端主导航' })
    .getByRole('link', { name: '工具' })
  await expect(headerTools).toBeVisible()
  await headerTools.click()

  await expect(page).toHaveURL(/\/tools$/)
  await expect(page.getByRole('heading', { name: '本地工具台' })).toBeVisible()
  await expect(headerTools).toHaveClass(/is-active/)

  await page.goto('/')
  await page
    .getByRole('navigation', { name: '页脚导航' })
    .getByRole('link', { name: '工具' })
    .click()

  await expect(page).toHaveURL(/\/tools$/)
  await expect(page.getByRole('heading', { name: '本地工具台' })).toBeVisible()
})

test('首页工具模块直达编辑器和工具中心', async ({ page, apiMock }) => {
  void apiMock
  await page.goto('/')

  const homeTools = page.locator('.home-tools')
  await expect(homeTools.getByRole('heading', { name: '把草稿留在浏览器里。' })).toBeVisible()
  await expect(homeTools.locator('.home-tools__list a')).toHaveCount(1)

  await homeTools.getByRole('link', { name: /Markdown 编辑器/ }).click()
  await expect(page).toHaveURL(/\/editor$/)

  await page.goto('/')
  await page.locator('.home-tools').getByRole('link', { name: '进入工具中心' }).click()
  await expect(page).toHaveURL(/\/tools$/)
})

test('工具中心展示 Markdown 编辑器并在编辑页保持工具激活', async ({ page, apiMock }) => {
  void apiMock
  await page.goto('/tools')

  const toolCard = page.locator('.tool-card')
  await expect(toolCard).toHaveCount(1)
  await expect(toolCard.getByRole('heading', { name: 'Markdown 编辑器' })).toBeVisible()
  await expect(toolCard).toContainText('打开工具')

  await toolCard.click()

  await expect(page).toHaveURL(/\/editor$/)
  await expect(page.getByRole('heading', { name: 'Markdown 编辑器' })).toBeVisible()
  await expect(
    page
      .getByRole('navigation', { name: '公开端主导航' })
      .getByRole('link', { name: '工具' }),
  ).toHaveClass(/is-active/)
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
  await expect(page.locator('.content-card')).toHaveCount(5)

  await page.getByRole('button', { name: '清除全部筛选' }).click()
  await expect(page).toHaveURL(/\/library$/)
  await page.getByRole('button', { name: '下一页' }).click()
  await expect(page).toHaveURL(/page=2/)
  await expect(page.locator('.content-card')).toHaveCount(6)
})

test('书库父分类筛选包含子分类内容', async ({ page, apiMock }) => {
  void apiMock
  await page.goto('/library')

  await page.locator('.filter-group').nth(1)
    .getByRole('button', { name: /技术笔记/ })
    .click()

  await expect(page).toHaveURL(/category=1&includeDescendants=true/)
  await expect(page.locator('.content-card')).toHaveCount(5)
  await expect(page.getByRole('heading', { name: '仅属于子分类的公开文章' })).toBeVisible()
})

test('搜索支持成功、空结果和 429 倒计时', async ({ page, apiMock }) => {
  await page.goto('/search')
  const input = page.getByLabel('搜索关键词')

  await submitPublicSearch(page, input, '公开')
  await expect(page).toHaveURL(/q=%E5%85%AC%E5%BC%80/)
  await expect(page.locator('.content-card')).toHaveCount(6)

  await submitPublicSearch(page, input, 'E2E')
  await expect(page.locator('.content-card')).toHaveCount(1)
  await expect(page.locator('.content-card__summary')).toContainText('这是一篇 E2E 正文')

  await submitPublicSearch(page, input, '不存在')
  await expect(page.getByRole('heading', { name: '没有找到匹配内容' })).toBeVisible()

  apiMock.rateLimitNextSearch()
  await submitPublicSearch(page, input, '限流')
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

test('文章详情展示相关阅读并排除前后篇重复链接', async ({ page, apiMock }) => {
  void apiMock
  await mockRelatedArticle(page)
  await page.goto('/post/related-public')

  const related = page.getByRole('region', { name: '相关阅读' })
  await expect(related).toBeVisible()
  await expect(related.locator('.content-card')).toHaveCount(3)
  await expect(related.getByText('相关阅读一')).toBeVisible()
  await expect(related.getByText('相关阅读二')).toBeVisible()
  await expect(related.getByText('相关阅读三')).toBeVisible()
  await expect(related.getByText('上一篇文章')).toHaveCount(0)

  await related.getByText('相关阅读一').click()
  await expect(page).toHaveURL(/\/post\/related-one$/)
})

test('文章详情相关阅读在移动端保持单列且无横向溢出', async ({ page, apiMock }) => {
  void apiMock
  await mockRelatedArticle(page)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/post/related-public')

  const cards = page.getByRole('region', { name: '相关阅读' }).locator('.content-card')
  await expect(cards).toHaveCount(3)
  const layout = await cards.evaluateAll((items) =>
    items.map(({ offsetLeft, offsetTop, offsetHeight }) => ({
      left: offsetLeft,
      top: offsetTop,
      height: offsetHeight,
    })),
  )
  expect(new Set(layout.map(({ left }) => left)).size).toBe(1)
  expect(
    layout
      .slice(1)
      .every(
        (card, index) =>
          card.top >= layout[index].top + layout[index].height,
      ),
  ).toBe(true)
  await expectNoHorizontalOverflow(page)
})

test('文章详情目录按层级展开并同步滚动高亮和阅读进度', async ({
  page,
}) => {
  await mockOutlineArticle(page)
  await page.goto('/post/outline-public')

  const toc = page.getByRole('navigation', { name: '文章目录' })
  const firstSection = toc.getByRole('link', { name: '第一节' })
  const firstChild = toc.getByRole('link', { name: '子节一' })
  const secondSection = toc.getByRole('link', { name: '第二节' })
  const secondChild = toc.getByRole('link', { name: '子节三' })

  await expect(firstSection).toBeVisible()
  await expect(firstChild).toBeHidden()
  await expect(toc.getByRole('button', { name: '展开 第一节' })).toBeVisible()

  const progress = page.locator('.reading-progress__bar')
  const beforeProgress = await progress.evaluate(
    (element) => getComputedStyle(element).transform,
  )

  await toc.getByRole('button', { name: '展开 第一节' }).click()
  await expect(firstChild).toBeVisible()
  await secondSection.click()

  await expect
    .poll(() => decodeURIComponent(new URL(page.url()).hash))
    .toBe('#第二节')
  await expect(secondSection).toHaveAttribute('aria-current', 'location')
  await expect(firstChild).toBeVisible()
  await expect(secondChild).toBeVisible()

  await expect
    .poll(() =>
      progress.evaluate((element) => getComputedStyle(element).transform),
    )
    .not.toBe(beforeProgress)
})

for (const viewport of [
  { width: 1440, height: 900 },
  { width: 1219, height: 958 },
]) {
  test(`文章目录在 ${viewport.width}px 滚动后保持粘性且不超过半屏`, async ({
    page,
  }) => {
    await mockOutlineArticle(page)
    await page.setViewportSize(viewport)
    await page.goto('/post/outline-public')
    await expect(page.locator('.post-aside')).toBeVisible()

    const scrollTarget = await page.evaluate(() => {
      const maximum = document.documentElement.scrollHeight - window.innerHeight
      return Math.max(0, Math.min(600, maximum - 50))
    })
    expect(scrollTarget).toBeGreaterThan(250)
    await page.evaluate((target) => window.scrollTo(0, target), scrollTarget)
    await expect
      .poll(() => page.evaluate(() => window.scrollY))
      .toBeGreaterThan(scrollTarget - 5)

    const metrics = await page.locator('.post-aside').evaluate((element) => {
      const bounds = element.getBoundingClientRect()
      return {
        top: bounds.top,
        height: bounds.height,
        viewportHeight: window.innerHeight,
      }
    })

    expect(metrics.top).toBeLessThanOrEqual(120)
    expect(metrics.height).toBeLessThanOrEqual(metrics.viewportHeight / 2 + 1)
    await expectNoHorizontalOverflow(page)
  })
}

test('超高文章目录在桌面自动切换为半屏抽屉', async ({ page }) => {
  await mockTallOutlineArticle(page)

  for (const viewport of [
    { width: 1219, height: 958 },
    { width: 1024, height: 768 },
    { width: 981, height: 800 },
  ]) {
    await page.setViewportSize(viewport)
    await page.goto('/post/tall-outline-public')

    const aside = page.locator('.post-aside')
    await expect(aside).toHaveClass(/post-aside--drawer/)
    await expect(page.getByRole('navigation', { name: '文章目录' })).toBeHidden()

    const trigger = page.getByRole('button', { name: '打开文章目录' })
    await expect(trigger).toBeVisible()
    await trigger.click()

    const panel = page.getByRole('dialog', { name: '文章目录' })
    await expect(panel).toBeVisible()
    const panelHeight = await panel.evaluate((element) => {
      return element.getBoundingClientRect().height
    })
    expect(panelHeight).toBeLessThanOrEqual(viewport.height / 2 + 1)
    await expectNoHorizontalOverflow(page)

    await page.keyboard.press('Escape')
    await expect(panel).toBeHidden()
  }
})

test('文章目录在 981px 与 980px 断点保持正确模式', async ({ page }) => {
  await mockTallOutlineArticle(page)
  await page.setViewportSize({ width: 981, height: 800 })
  await page.goto('/post/tall-outline-public')

  await expect(page.locator('.post-aside')).toHaveClass(/post-aside--drawer/)
  await expect(page.getByRole('button', { name: '打开文章目录' })).toBeVisible()

  await page.setViewportSize({ width: 980, height: 800 })
  await expect(page.locator('.post-aside')).toHaveClass(/post-aside--drawer/)
  await expect(page.getByRole('navigation', { name: '文章目录' })).toBeHidden()
  await expect(page.getByRole('button', { name: '打开文章目录' })).toBeVisible()
  await expectNoHorizontalOverflow(page)
})

test('文章详情移动端使用悬浮目录并在跳转后关闭', async ({
  page,
}) => {
  await mockOutlineArticle(page)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/post/outline-public')

  const trigger = page.getByRole('button', { name: '打开文章目录' })
  await expect(trigger).toBeVisible()
  await trigger.click()

  const panel = page.getByRole('dialog', { name: '文章目录' })
  await expect(panel).toBeVisible()
  const panelHeight = await panel.evaluate((element) => {
    return element.getBoundingClientRect().height
  })
  expect(panelHeight).toBeLessThanOrEqual(844 / 2 + 1)
  await panel.getByRole('button', { name: '展开 第一节' }).click()
  await panel.getByRole('link', { name: '子节一' }).click()

  await expect
    .poll(() => decodeURIComponent(new URL(page.url()).hash))
    .toBe('#子节一')
  await expect(panel).toBeHidden()
  await expect(trigger).toBeFocused()
  await expectNoHorizontalOverflow(page)

  await trigger.click()
  await expect(panel).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(panel).toBeHidden()
  await expect(trigger).toBeFocused()
})

test('文章详情没有足够章节时不显示目录入口', async ({ page }) => {
  await page.route('**/api/public/contents/no-headings', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 99,
        slug: 'no-headings',
        title: '没有章节的文章',
        summary: '短文章。',
        type: 'NOTE',
        status: 'PUBLISHED',
        body: '# 没有章节的文章\n\n只有一段正文。',
        metadata: {},
        publishedAt: '2026-09-12T10:00:00',
        categories: [],
        tags: [],
        previous: null,
        next: null,
        related: [],
      }),
    }),
  )

  await page.goto('/post/no-headings')

  await expect(page.getByRole('navigation', { name: '文章目录' })).toHaveCount(0)
  await expect(
    page.getByRole('button', { name: '打开文章目录' }),
  ).toHaveCount(0)
  await expect(page.locator('.reading-progress')).toHaveCount(0)
  await expect(page.getByRole('region', { name: '相关阅读' })).toHaveCount(0)
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
  await page.getByRole('navigation', { name: '页脚导航' }).getByRole('link', { name: '隐私收集' }).click()

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

    await page.goto('/search?q=E2E')
    await expect(page.locator('.content-card')).toHaveCount(1)
    await expectNoHorizontalOverflow(page)
  })

  test('移动导航工具入口进入工具中心', async ({ page, apiMock }) => {
    void apiMock
    await page.goto('/')
    await page.getByRole('button', { name: '打开导航目录' }).click()

    const mobileNav = page.getByRole('navigation', { name: '移动端主导航' })
    await expect(mobileNav).toBeVisible()
    await mobileNav.getByRole('link', { name: /工具/ }).click()

    await expect(page).toHaveURL(/\/tools$/)
    await expect(page.getByRole('heading', { name: '本地工具台' })).toBeVisible()
    await expectNoHorizontalOverflow(page)
  })
})
