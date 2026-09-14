import { expect, test } from './support/apiMock.js'

async function expectNoHorizontalOverflow(page) {
  const overflow = await page.evaluate(() => {
    return document.documentElement.scrollWidth - document.documentElement.clientWidth
  })
  expect(overflow).toBeLessThanOrEqual(1)
}

async function useDeterministicRandom(page) {
  await page.addInitScript(() => {
    Math.random = () => 0
  })
}

test('游戏中心展示四款训练入口', async ({ page, apiMock }) => {
  void apiMock
  await page.goto('/games')

  await expect(page.getByRole('heading', { name: '脑力训练馆' })).toBeVisible()
  await expect(page.getByRole('link', { name: /Stroop 色词测试/ })).toBeVisible()
  await expect(page.getByRole('link', { name: /倒背数字训练/ })).toBeVisible()
  await expect(page.getByRole('link', { name: /扑克牌记忆训练/ })).toBeVisible()
  await expect(page.getByRole('link', { name: /舒尔特方格/ })).toBeVisible()
})

test('游戏路由不显示全屏擦除动画', async ({ page, apiMock }) => {
  void apiMock
  await page.goto('/games/stroop')

  await expect(page.locator('.route-wipe')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '开始挑战' })).toBeVisible()
})

test('游戏页面返回游戏中心不会卡在路由转场', async ({ page, apiMock }) => {
  void apiMock
  await page.goto('/games/schulte')
  await expect(page.getByRole('heading', { name: '舒尔特方格' })).toBeVisible()

  await page.getByRole('link', { name: '返回游戏中心' }).click()
  await expect(page).toHaveURL(/\/games$/)
  await expect(page.getByRole('heading', { name: '脑力训练馆' })).toBeVisible()
  await expect(page.getByRole('link', { name: /Stroop 色词测试/ })).toBeVisible()
})

test('舒尔特标题层级、操作按钮和数字保持清晰分工', async ({ page, apiMock }) => {
  void apiMock
  await page.goto('/games/schulte')

  const back = await page.getByRole('link', { name: '返回游戏中心' }).boundingBox()
  const eyebrow = await page.locator('.game-heading .editorial-eyebrow').boundingBox()
  expect(back.y + back.height).toBeLessThanOrEqual(eyebrow.y)

  const reset = page.getByRole('button', { name: '重新洗牌' })
  const start = page.getByRole('button', { name: '开始挑战' })
  const resetBox = await reset.boundingBox()
  const startBox = await start.boundingBox()
  expect(Math.abs(resetBox.height - startBox.height)).toBeLessThanOrEqual(1)

  const buttonStyles = await page.evaluate(() => {
    const read = (label) => {
      const button = [...document.querySelectorAll('.game-actions button')]
        .find((item) => item.textContent.trim() === label)
      const style = getComputedStyle(button)
      return {
        background: style.backgroundColor,
        border: style.borderColor,
        radius: style.borderRadius,
      }
    }
    return { reset: read('重新洗牌'), start: read('开始挑战') }
  })
  expect(buttonStyles.reset.radius).toBe(buttonStyles.start.radius)
  expect(buttonStyles.reset.background).not.toBe(buttonStyles.start.background)
  expect(buttonStyles.reset.border).not.toBe(buttonStyles.start.border)

  const numberSize = await page.locator('.schulte-grid button').first().evaluate((element) => {
    return Number.parseFloat(getComputedStyle(element).fontSize)
  })
  expect(numberSize).toBeGreaterThanOrEqual(18)
})

test('Stroop 完成 84 试次并保存正确率与反应时', async ({ page, apiMock }) => {
  void apiMock
  await useDeterministicRandom(page)
  await page.goto('/games/stroop')

  await page.getByRole('button', { name: '开始挑战' }).click()
  for (let trial = 0; trial < 84; trial += 1) {
    await page.locator('.stroop-color-button').first().click()
    await page.waitForTimeout(1)
  }

  const result = page.getByRole('dialog', { name: '84 试次完成' })
  await expect(result).toBeVisible()
  await expect(result.getByText('正确率')).toBeVisible()
  const records = await page.evaluate(() => JSON.parse(localStorage.getItem('stroop_84_parchment')))
  expect(records.bestAccuracy).toBeGreaterThanOrEqual(0)
  expect(records.bestAvgRT).toBeGreaterThan(0)
})

test('倒背数字按规则显示、隐藏、判题并在反馈后继续', async ({ page, apiMock }) => {
  void apiMock
  await useDeterministicRandom(page)
  await page.clock.install()
  await page.goto('/games/digit-span')

  await page.getByRole('button', { name: '显示数字' }).click()
  await expect(page.locator('.digit-display')).toHaveText('1000')
  await page.clock.fastForward(4800)

  const input = page.getByLabel('倒序答案')
  await expect(input).toBeEnabled()
  await input.fill('0001')
  await page.getByRole('button', { name: '确认' }).click()
  await expect(page.getByText(/正确/).first()).toBeVisible()
  expect(await page.evaluate(() => localStorage.getItem('digit_span_best'))).toBeNull()

  await page.clock.fastForward(500)
  await expect(page.getByRole('button', { name: '显示数字' })).toBeEnabled()
})

test('扑克牌记忆保留 3 秒规则并快速进入下一题', async ({ page, apiMock }) => {
  void apiMock
  await useDeterministicRandom(page)
  await page.clock.install()
  await page.goto('/games/poker-memory')

  await page.getByRole('button', { name: '开始记忆' }).click()
  await page.clock.runFor(4000)
  await expect(page.getByText(/在第几个位置/)).toBeVisible()

  await page.getByRole('button', { name: '位置 1' }).click()
  await expect(page.getByText('正确！')).toBeVisible()
  await page.clock.fastForward(700)
  await expect(page.getByRole('button', { name: '开始记忆' })).toBeEnabled()
})

test('舒尔特完成顺序点击并保存分尺寸成绩', async ({ page, apiMock }) => {
  void apiMock
  await useDeterministicRandom(page)
  await page.goto('/games/schulte')

  await page.getByRole('button', { name: '3 × 3' }).click()
  await page.getByRole('button', { name: '开始挑战' }).click()
  for (let number = 1; number <= 9; number += 1) {
    await page.locator(`[data-number="${number}"]`).click()
  }

  await expect(page.getByRole('dialog', { name: '3 × 3 挑战完成' })).toBeVisible()
  const best = await page.evaluate(() => JSON.parse(localStorage.getItem('schulte_parchment_best')))
  expect(best['3']).toBeGreaterThan(0)
})

test('旧版本地成绩在刷新后继续展示', async ({ page, apiMock }) => {
  void apiMock
  await page.addInitScript(() => {
    localStorage.setItem('stroop_84_parchment', JSON.stringify({
      bestAccuracy: 0.875,
      bestAvgRT: 612,
    }))
    localStorage.setItem('poker_memory_best_span', '5')
    localStorage.setItem('schulte_parchment_best', JSON.stringify({ 3: 1200 }))
  })

  await page.goto('/games/stroop')
  await expect(page.getByText('87.5%').first()).toBeVisible()

  await page.goto('/games/poker-memory')
  await expect(page.getByText('5 张').first()).toBeVisible()

  await page.goto('/games/schulte')
  await page.getByRole('button', { name: '3 × 3' }).click()
  await expect(page.getByText('00:01.200').first()).toBeVisible()
})

test('游戏页面适配桌面、平板、手机与横屏尺寸', async ({ page, apiMock }) => {
  void apiMock
  const viewports = [
    { width: 320, height: 568 },
    { width: 390, height: 844 },
    { width: 844, height: 390 },
    { width: 768, height: 1024 },
    { width: 1440, height: 900 },
  ]
  const paths = [
    ['/games', '脑力训练馆'],
    ['/games/stroop', '色词测试'],
    ['/games/digit-span', '倒背数字训练'],
    ['/games/poker-memory', '扑克牌记忆训练'],
    ['/games/schulte', '舒尔特方格'],
  ]

  for (const viewport of viewports) {
    await page.setViewportSize(viewport)
    for (const [path, heading] of paths) {
      await page.goto(path)
      await expect(page.getByRole('heading', { name: heading })).toBeVisible()
      await expectNoHorizontalOverflow(page)
    }
  }
})

test('高密度与长序列状态在窄屏和横屏不溢出', async ({ page, apiMock }) => {
  void apiMock
  await useDeterministicRandom(page)
  await page.clock.install()

  await page.setViewportSize({ width: 320, height: 568 })
  await page.goto('/games/schulte')
  await page.getByRole('button', { name: '10 × 10' }).click()
  await expect(page.locator('.schulte-grid button')).toHaveCount(100)
  await expectNoHorizontalOverflow(page)
  const hasCellOverflow = await page.locator('.schulte-grid button').evaluateAll((cells) => {
    return cells.some((cell) => (
      cell.scrollWidth > cell.clientWidth || cell.scrollHeight > cell.clientHeight
    ))
  })
  expect(hasCellOverflow).toBe(false)

  await page.setViewportSize({ width: 844, height: 390 })
  await page.goto('/games/digit-span')
  for (let level = 4; level < 10; level += 1) {
    for (let correct = 0; correct < 2; correct += 1) {
      await page.getByRole('button', { name: '显示数字' }).click()
      const digits = await page.locator('.digit-display').textContent()
      await page.clock.fastForward(2000 + digits.length * 700)
      await page.locator('#digit-answer').fill([...digits].reverse().join(''))
      await page.getByRole('button', { name: '确认' }).click()
      await page.clock.fastForward(500)
    }
  }
  await page.getByRole('button', { name: '显示数字' }).click()
  await expect(page.locator('.digit-display')).toHaveText('1000000000')
  await expectNoHorizontalOverflow(page)

  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/games/poker-memory')
  for (let currentLevel = 2; currentLevel < 10; currentLevel += 1) {
    for (let attempt = 0; attempt < 3; attempt += 1) {
      await page.getByRole('button', { name: '开始记忆' }).click()
      await page.clock.runFor(4000)
      await page.getByRole('button', { name: '位置 1' }).click()
      await page.clock.fastForward(attempt < 2 ? 700 : 950)
    }
  }
  await expect(page.locator('.poker-cards .playing-card')).toHaveCount(10)
  await expectNoHorizontalOverflow(page)
})

test('减少动态偏好下不播放结果彩纸', async ({ page, apiMock }) => {
  void apiMock
  await useDeterministicRandom(page)
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/games/schulte')

  await page.getByRole('button', { name: '3 × 3' }).click()
  await page.getByRole('button', { name: '开始挑战' }).click()
  for (let number = 1; number <= 9; number += 1) {
    await page.locator(`[data-number="${number}"]`).click()
  }

  await expect(page.getByRole('dialog', { name: '3 × 3 挑战完成' })).toBeVisible()
  await expect(page.locator('.game-confetti')).toHaveCount(0)
})
