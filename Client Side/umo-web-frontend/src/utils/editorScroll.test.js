import assert from 'node:assert/strict'
import test from 'node:test'

import {
  calculateScrollRatio,
  extractMarkdownHeadingLines,
  interpolateBetweenAnchors,
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

test('interpolates continuously between equal-sized anchors', () => {
  assert.equal(interpolateBetweenAnchors([0, 100], [0, 200], 50), 100)
})

test('interpolates continuously between anchors with different heights', () => {
  const sourceOffsets = [0, 100, 300]
  const targetOffsets = [0, 300, 400]

  assert.equal(interpolateBetweenAnchors(sourceOffsets, targetOffsets, 50), 150)
  assert.equal(interpolateBetweenAnchors(sourceOffsets, targetOffsets, 200), 350)
})

test('interpolates across explicit leading and trailing anchors', () => {
  const sourceOffsets = [0, 120, 360, 500]
  const targetOffsets = [0, 180, 420, 640]

  assert.equal(interpolateBetweenAnchors(sourceOffsets, targetOffsets, 60), 90)
  assert.equal(interpolateBetweenAnchors(sourceOffsets, targetOffsets, 430), 530)
})

test('clamps interpolation at anchor boundaries', () => {
  assert.equal(interpolateBetweenAnchors([10, 110], [20, 220], -100), 20)
  assert.equal(interpolateBetweenAnchors([10, 110], [20, 220], 999), 220)
})

test('returns null for missing or unmatched anchors', () => {
  assert.equal(interpolateBetweenAnchors([], [0, 100], 50), null)
  assert.equal(interpolateBetweenAnchors([0, 100], [0], 50), null)
  assert.equal(interpolateBetweenAnchors(null, [0, 100], 50), null)
})
