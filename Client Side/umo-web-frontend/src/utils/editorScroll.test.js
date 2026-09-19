import assert from 'node:assert/strict'
import test from 'node:test'

import {
  calculateScrollRatio,
  extractMarkdownHeadingLines,
  findActiveHeadingIndex,
  scrollTopForRatio,
} from './editorScroll.js'

test('calculates ratio at the top, middle, and bottom', () => {
  const dimensions = { scrollHeight: 1100, clientHeight: 100 }

  assert.equal(calculateScrollRatio({ scrollTop: 0, ...dimensions }), 0)
  assert.equal(calculateScrollRatio({ scrollTop: 500, ...dimensions }), 0.5)
  assert.equal(calculateScrollRatio({ scrollTop: 1000, ...dimensions }), 1)
})

test('clamps calculated ratios to the scrollable range', () => {
  assert.equal(
    calculateScrollRatio({
      scrollTop: -20,
      scrollHeight: 1100,
      clientHeight: 100,
    }),
    0,
  )
  assert.equal(
    calculateScrollRatio({
      scrollTop: 1200,
      scrollHeight: 1100,
      clientHeight: 100,
    }),
    1,
  )
})

test('returns zero when an element cannot scroll', () => {
  assert.equal(
    calculateScrollRatio({
      scrollTop: 50,
      scrollHeight: 100,
      clientHeight: 100,
    }),
    0,
  )
})

test('treats invalid dimensions as zero', () => {
  assert.equal(calculateScrollRatio(), 0)
  assert.equal(
    calculateScrollRatio({
      scrollTop: '500',
      scrollHeight: null,
      clientHeight: undefined,
    }),
    0,
  )
})

test('converts a ratio to a clamped scroll offset', () => {
  const dimensions = { scrollHeight: 1100, clientHeight: 100 }

  assert.equal(scrollTopForRatio(0, dimensions), 0)
  assert.equal(scrollTopForRatio(0.5, dimensions), 500)
  assert.equal(scrollTopForRatio(1, dimensions), 1000)
  assert.equal(scrollTopForRatio(-1, dimensions), 0)
  assert.equal(scrollTopForRatio(2, dimensions), 1000)
})

test('returns zero target offset for invalid or non-scrollable dimensions', () => {
  assert.equal(scrollTopForRatio(0.5), 0)
  assert.equal(
    scrollTopForRatio(0.5, {
      scrollHeight: 100,
      clientHeight: 100,
    }),
    0,
  )
  assert.equal(
    scrollTopForRatio('0.5', {
      scrollHeight: 1100,
      clientHeight: 100,
    }),
    0,
  )
})

test('extracts heading lines while ignoring fenced code blocks', () => {
  const source = [
    '# 文档标题',
    '',
    '```md',
    '## 代码中的标题',
    '```',
    '',
    '## 第一章',
    '正文',
    '',
    '### 子节',
  ].join('\n')

  assert.deepEqual(extractMarkdownHeadingLines(source), [
    { line: 0, level: 1, text: '文档标题' },
    { line: 6, level: 2, text: '第一章' },
    { line: 9, level: 3, text: '子节' },
  ])
})

test('finds the nearest heading at or above the scroll position', () => {
  const offsets = [0, 100, 300]

  assert.equal(findActiveHeadingIndex(offsets, 0), 0)
  assert.equal(findActiveHeadingIndex(offsets, 130), 1)
  assert.equal(findActiveHeadingIndex(offsets, 500), 2)
  assert.equal(findActiveHeadingIndex([], 100), -1)
})
