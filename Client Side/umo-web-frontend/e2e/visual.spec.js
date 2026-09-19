import { expect, test } from './support/apiMock.js'

async function prepareScreenshot(page, theme = 'light') {
  await page.addInitScript((nextTheme) => {
    localStorage.setItem('umo-theme', nextTheme)
  }, theme)
}

async function waitForStablePage(page) {
  await page.evaluate(() => document.fonts.ready)
  await page.locator('#app').waitFor({ state: 'visible' })
}

test.beforeEach(({ page }) => {
  page.on('console', (message) => {
    if (message.type() === 'error') {
      throw new Error(`Browser console error: ${message.text()}`)
    }
  })
})

test('首页亮色视觉基线', async ({ page, apiMock }) => {
  void apiMock
  await prepareScreenshot(page, 'light')
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '本期刊首' })).toBeVisible()
  await waitForStablePage(page)

  await expect(page).toHaveScreenshot('home-light.png', { fullPage: true })
})

test('首页暗色视觉基线', async ({ page, apiMock }) => {
  void apiMock
  await prepareScreenshot(page, 'dark')
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '本期刊首' })).toBeVisible()
  await waitForStablePage(page)

  await expect(page).toHaveScreenshot('home-dark.png', { fullPage: true })
})

test('书库视觉基线', async ({ page, apiMock }) => {
  void apiMock
  await prepareScreenshot(page)
  await page.goto('/library')
  await expect(page.locator('.content-card')).toHaveCount(6)
  await waitForStablePage(page)

  await expect(page).toHaveScreenshot('library.png', { fullPage: true })
})

test('文章详情视觉基线', async ({ page, apiMock }) => {
  void apiMock
  await prepareScreenshot(page)
  await page.goto('/post/first-public')
  await expect(page.locator('.post-header h1')).toHaveText('第一篇公开文章')
  await waitForStablePage(page)

  await expect(page).toHaveScreenshot('post-detail.png', { fullPage: true })
})

test('在线编辑器视觉基线', async ({ page, apiMock }) => {
  void apiMock
  await prepareScreenshot(page)
  await page.goto('/editor')
  await expect(page.getByRole('heading', { name: 'Markdown 编辑器' })).toBeVisible()
  await waitForStablePage(page)

  await expect(page).toHaveScreenshot('editor.png', { fullPage: true })
})

test('工具中心视觉基线', async ({ page, apiMock }) => {
  void apiMock
  await prepareScreenshot(page)
  await page.goto('/tools')
  await expect(page.getByRole('heading', { name: '本地工具台' })).toBeVisible()
  await waitForStablePage(page)

  await expect(page).toHaveScreenshot('tools-index.png', { fullPage: true })
})

test('管理端登录视觉基线', async ({ page, apiMock }) => {
  void apiMock
  await prepareScreenshot(page)
  await page.goto('/secret-admin/login')
  await expect(page.getByRole('heading', { name: '管理员登录' })).toBeVisible()
  await waitForStablePage(page)

  await expect(page).toHaveScreenshot('admin-login.png', { fullPage: true })
})

test('管理端文章列表视觉基线', async ({ page, apiMock }) => {
  await prepareScreenshot(page)
  await apiMock.authenticate()
  await page.goto('/secret-admin/contents')
  await expect(page.getByRole('row', { name: /第一篇公开文章/ })).toBeVisible()
  await waitForStablePage(page)

  await expect(page).toHaveScreenshot('admin-contents.png', { fullPage: true })
})

test('管理端 AI 设置视觉基线', async ({ page, apiMock }) => {
  await prepareScreenshot(page)
  await apiMock.authenticate()
  await page.goto('/secret-admin/ai-settings')
  await expect(page.getByRole('heading', { name: 'AI 设置' })).toBeVisible()
  await expect(page.locator('.admin-ai-mode-item')).toHaveCount(5)
  await waitForStablePage(page)

  await expect(page).toHaveScreenshot('admin-ai-settings.png', { fullPage: true })
})

test('管理端 AI 转换抽屉视觉基线', async ({ page, apiMock }) => {
  await prepareScreenshot(page)
  await apiMock.authenticate()
  await page.goto('/secret-admin/contents/new')
  await page.getByLabel('Markdown 正文').fill('# 视觉基线正文\n\n用于检查 AI 抽屉布局。')
  await page.getByRole('button', { name: 'AI 转换' }).click()

  const dialog = page.getByRole('dialog', { name: 'AI 转换' })
  await dialog.getByRole('button', { name: '带入当前正文' }).click()
  await dialog.getByRole('button', { name: '开始转换' }).click()
  await expect(dialog.getByLabel('AI 转换结果')).toHaveValue(
    '转换结果：# 视觉基线正文\n\n用于检查 AI 抽屉布局。',
  )
  await waitForStablePage(page)

  await expect(page).toHaveScreenshot('admin-ai-drawer.png')
})

test('游戏中心视觉基线', async ({ page, apiMock }) => {
  void apiMock
  await prepareScreenshot(page)
  await page.goto('/games')
  await expect(page.getByRole('heading', { name: '脑力训练馆' })).toBeVisible()
  await waitForStablePage(page)

  await expect(page).toHaveScreenshot('games-hub.png', { fullPage: true })
})

test('Stroop 视觉基线', async ({ page, apiMock }) => {
  void apiMock
  await prepareScreenshot(page)
  await page.goto('/games/stroop')
  await expect(page.getByRole('heading', { name: '色词测试' })).toBeVisible()
  await waitForStablePage(page)

  await expect(page).toHaveScreenshot('game-stroop.png', { fullPage: true })
})

test('Stroop 暗色视觉基线', async ({ page, apiMock }) => {
  void apiMock
  await prepareScreenshot(page, 'dark')
  await page.goto('/games/stroop')
  await expect(page.getByRole('heading', { name: '色词测试' })).toBeVisible()
  await waitForStablePage(page)

  await expect(page).toHaveScreenshot('game-stroop-dark.png', { fullPage: true })
})

test('倒背数字视觉基线', async ({ page, apiMock }) => {
  void apiMock
  await prepareScreenshot(page)
  await page.goto('/games/digit-span')
  await expect(page.getByRole('heading', { name: '倒背数字训练' })).toBeVisible()
  await waitForStablePage(page)

  await expect(page).toHaveScreenshot('game-digit-span.png', { fullPage: true })
})

test('扑克牌记忆视觉基线', async ({ page, apiMock }) => {
  void apiMock
  await prepareScreenshot(page)
  await page.goto('/games/poker-memory')
  await expect(page.getByRole('heading', { name: '扑克牌记忆训练' })).toBeVisible()
  await waitForStablePage(page)

  await expect(page).toHaveScreenshot('game-poker-memory.png', { fullPage: true })
})

test('扑克牌记忆暗色视觉基线', async ({ page, apiMock }) => {
  void apiMock
  await prepareScreenshot(page, 'dark')
  await page.goto('/games/poker-memory')
  await expect(page.getByRole('heading', { name: '扑克牌记忆训练' })).toBeVisible()
  await expect(page.locator('.playing-card.is-face-down')).toHaveCount(2)
  await waitForStablePage(page)

  await expect(page).toHaveScreenshot('game-poker-memory-dark.png', { fullPage: true })
})

test('舒尔特视觉基线', async ({ page, apiMock }) => {
  void apiMock
  await prepareScreenshot(page)
  await page.goto('/games/schulte')
  await expect(page.getByRole('heading', { name: '舒尔特方格' })).toBeVisible()
  await waitForStablePage(page)

  await expect(page).toHaveScreenshot('game-schulte.png', { fullPage: true })
})
