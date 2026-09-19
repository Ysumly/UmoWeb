import assert from 'node:assert/strict'
import test from 'node:test'

import {
  buildSlugFromTitle,
  syncSlugFromTitle,
  syncSlugFromUserInput,
} from './adminSlug.js'

test('builds a lowercase pinyin slug from a Chinese title', () => {
  assert.equal(buildSlugFromTitle('Vue 3 快速入门'), 'vue-3-kuai-su-ru-men')
  assert.equal(buildSlugFromTitle('移动端阅读体验'), 'yi-dong-duan-yue-du-ti-yan')
})

test('collapses punctuation and whitespace into single hyphens', () => {
  assert.equal(
    buildSlugFromTitle('Hello, world!  --  Umo Blog'),
    'hello-world-umo-blog',
  )
})

test('limits generated slugs to the backend maximum without a trailing hyphen', () => {
  const generated = buildSlugFromTitle('a'.repeat(199) + ' 尾部')

  assert.ok(generated.length <= 200)
  assert.ok(generated.length >= 199)
  assert.equal(generated.endsWith('-'), false)
})

test('falls back to article when the title has no usable characters', () => {
  assert.equal(buildSlugFromTitle('！？……'), 'article')
})

test('title updates keep generating while the slug remains automatic', () => {
  assert.deepEqual(
    syncSlugFromTitle({
      title: '自动标题',
      slug: 'old-title',
      autoSync: true,
      lastGeneratedSlug: 'old-title',
    }),
    {
      slug: 'zi-dong-biao-ti',
      autoSync: true,
      lastGeneratedSlug: 'zi-dong-biao-ti',
    },
  )
})

test('manual slug edits lock further title synchronization', () => {
  assert.deepEqual(
    syncSlugFromUserInput({
      value: 'custom-slug',
      generatedSlug: 'zi-dong-biao-ti',
    }),
    {
      slug: 'custom-slug',
      autoSync: false,
      lastGeneratedSlug: 'zi-dong-biao-ti',
    },
  )
})

test('clearing a manually edited slug restores automatic synchronization', () => {
  assert.deepEqual(
    syncSlugFromUserInput({
      value: '   ',
      generatedSlug: 'zi-dong-biao-ti',
    }),
    {
      slug: '',
      autoSync: true,
      lastGeneratedSlug: '',
    },
  )
})
