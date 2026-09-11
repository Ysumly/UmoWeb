import { expect, test } from './support/apiMock.js'

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

    await page.getByLabel('Markdown 正文').fill('## 移动预览')
    await page.getByRole('tab', { name: '预览' }).click()

    await expect(page.getByRole('tab', { name: '预览' })).toHaveAttribute('aria-selected', 'true')
    await expect(page.locator('.editor-pane--preview')).toHaveClass(/is-mobile-active/)
    await expect(page.locator('.editor-pane--preview')).toContainText('移动预览')
  })
})
