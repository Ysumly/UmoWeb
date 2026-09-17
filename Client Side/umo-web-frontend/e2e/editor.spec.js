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

test('编辑器保存草稿并在刷新后恢复', async ({ page, apiMock }) => {
  void apiMock
  await page.goto('/editor')

  const textarea = page.getByLabel('Markdown 正文')
  await textarea.fill('# 浏览器草稿\n\n刷新后仍应恢复。')
  await expect(page.getByText(/已保存 \d{2}:\d{2}/)).toBeVisible()

  await page.reload()

  await expect(textarea).toHaveValue('# 浏览器草稿\n\n刷新后仍应恢复。')
  await expect(page.getByText(/已恢复 \d{2}:\d{2}/)).toBeVisible()
})

test('编辑器页头标记工具入口为当前栏目', async ({ page, apiMock }) => {
  void apiMock
  await page.goto('/editor')

  const toolsLink = page
    .getByRole('navigation', { name: '公开端主导航' })
    .getByRole('link', { name: '工具' })
  await expect(toolsLink).toBeVisible()
  await expect(toolsLink).toHaveAttribute('href', '/tools')
  await expect(toolsLink).toHaveClass(/is-active/)
})

test('编辑器下载 Markdown 并安全预览', async ({ page, apiMock }) => {
  void apiMock
  await page.goto('/editor')

  const textarea = page.getByLabel('Markdown 正文')
  const fileName = page.getByLabel('文件名')
  await fileName.fill('回归笔记')
  await fileName.blur()
  await textarea.fill([
    '# 安全预览',
    '',
    '<script>window.__unsafe = true</script>',
    '',
    '[危险链接](javascript:alert(1))',
    '',
    '`inline code`',
  ].join('\n'))

  const preview = page.locator('.editor-pane--preview .markdown-body')
  await expect(preview.getByRole('heading', { name: '安全预览' })).toBeVisible()
  await expect(preview).toContainText('<script>window.__unsafe = true</script>')
  await expect(preview.locator('script')).toHaveCount(0)
  await expect(preview.locator('a', { hasText: '危险链接' })).toHaveCount(0)
  await expect(preview).toContainText('危险链接')
  expect(await page.evaluate(() => window.__unsafe)).toBeUndefined()

  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('button', { name: '下载 .md' }).click()
  const download = await downloadPromise

  expect(download.suggestedFilename()).toBe('回归笔记.md')
  expect(await download.createReadStream()).not.toBeNull()
})

test('桌面编辑器预览等高并按比例双向同步滚动', async ({ page, apiMock }) => {
  void apiMock
  await page.goto('/editor')

  const textarea = page.getByLabel('Markdown 正文')
  const previewPane = page.locator('.editor-pane--preview')
  await textarea.fill(longMarkdown())
  await expect(previewPane.getByRole('heading', { name: '章节 80' })).toBeAttached()

  const [textareaBox, previewBox] = await Promise.all([
    textarea.boundingBox(),
    previewPane.boundingBox(),
  ])
  expect(Math.abs(textareaBox.y + textareaBox.height - previewBox.y - previewBox.height))
    .toBeLessThanOrEqual(1)

  await textarea.evaluate((element) => {
    element.scrollTop = (element.scrollHeight - element.clientHeight) * 0.4
  })
  await waitForAnimationFrames(page)
  expect(Math.abs(await readScrollRatio(previewPane) - 0.4)).toBeLessThanOrEqual(0.02)

  await previewPane.evaluate((element) => {
    element.scrollTop = (element.scrollHeight - element.clientHeight) * 0.2
  })
  await waitForAnimationFrames(page)
  expect(Math.abs(await readScrollRatio(textarea) - 0.2)).toBeLessThanOrEqual(0.02)

  const settled = [
    await readScrollRatio(textarea),
    await readScrollRatio(previewPane),
  ]
  await waitForAnimationFrames(page)
  const stable = [
    await readScrollRatio(textarea),
    await readScrollRatio(previewPane),
  ]
  expect(Math.abs(settled[0] - stable[0])).toBeLessThanOrEqual(0.001)
  expect(Math.abs(settled[1] - stable[1])).toBeLessThanOrEqual(0.001)
})

test('编辑器导入 Markdown 前确认替换并可清空草稿', async ({ page, apiMock }) => {
  void apiMock
  await page.goto('/editor')

  const textarea = page.getByLabel('Markdown 正文')
  const fileInput = page.locator('input[type="file"]')
  await textarea.fill('原内容')

  page.once('dialog', async (dialog) => {
    expect(dialog.message()).toContain('导入会替换当前编辑内容')
    await dialog.accept()
  })
  await fileInput.setInputFiles({
    name: '导入稿.md',
    mimeType: 'text/markdown',
    buffer: Buffer.from('# 导入内容\n\n来自文件。', 'utf8'),
  })

  await expect(textarea).toHaveValue('# 导入内容\n\n来自文件。')
  await expect(page.getByText('已导入 导入稿.md')).toBeVisible()

  page.once('dialog', async (dialog) => {
    expect(dialog.message()).toContain('确定清空当前编辑内容')
    await dialog.accept()
  })
  await page.getByRole('button', { name: '清空' }).click()

  await expect(textarea).toHaveValue('')
  await expect.poll(() => page.evaluate(() => {
    return localStorage.getItem('umo-editor-draft-v1')
  })).toBeNull()
})

test.describe('390px 在线编辑器', () => {
  test.use({
    viewport: { width: 390, height: 844 },
    isMobile: true,
    hasTouch: true,
  })

  test('编辑与预览标签切换', async ({ page, apiMock }) => {
    void apiMock
    await page.goto('/editor')

    const textarea = page.getByLabel('Markdown 正文')
    const previewPane = page.locator('.editor-pane--preview')
    await textarea.fill(longMarkdown())
    await page.getByRole('tab', { name: '预览' }).click()

    await expect(page.getByRole('tab', { name: '预览' })).toHaveAttribute('aria-selected', 'true')
    await expect(previewPane).toHaveClass(/is-mobile-active/)
    await expect(page.locator('.editor-pane--input')).toBeHidden()
    await previewPane.evaluate((element) => {
      element.scrollTop = (element.scrollHeight - element.clientHeight) * 0.4
    })
    await waitForAnimationFrames(page)
    expect(await readScrollRatio(textarea)).toBe(0)
  })
})
