import test from 'node:test'
import assert from 'node:assert/strict'

import {
  createEditorDraft,
  createMarkdownBlob,
  isMarkdownFile,
  normalizeEditorFileName,
  parseEditorDraft,
} from './editor.js'

test('normalizes editable Markdown filenames', () => {
  assert.equal(normalizeEditorFileName(' 我的 笔记.MARKDOWN '), '我的 笔记.md')
  assert.equal(normalizeEditorFileName('../draft:final.md'), 'draft-final.md')
  assert.equal(normalizeEditorFileName(''), 'untitled.md')
  assert.equal(normalizeEditorFileName('...'), 'untitled.md')
})

test('accepts only Markdown files by extension', () => {
  assert.equal(isMarkdownFile({ name: 'note.md', type: 'text/markdown' }), true)
  assert.equal(isMarkdownFile({ name: 'note.MARKDOWN', type: '' }), true)
  assert.equal(isMarkdownFile({ name: 'note.txt', type: 'text/markdown' }), false)
  assert.equal(isMarkdownFile(null), false)
})

test('creates and parses a versioned editor draft', () => {
  const draft = createEditorDraft({
    content: '# 标题',
    fileName: 'notes.markdown',
    updatedAt: 123456789,
  })

  assert.deepEqual(draft, {
    version: 1,
    content: '# 标题',
    fileName: 'notes.md',
    updatedAt: 123456789,
  })
  assert.deepEqual(parseEditorDraft(JSON.stringify(draft)), draft)
})

test('rejects malformed and unsupported stored drafts', () => {
  assert.equal(parseEditorDraft('{bad json'), null)
  assert.equal(parseEditorDraft(JSON.stringify({
    version: 2,
    content: '# 标题',
    fileName: 'notes.md',
    updatedAt: 123456789,
  })), null)
  assert.equal(parseEditorDraft(JSON.stringify({
    version: 1,
    content: 42,
    fileName: 'notes.md',
    updatedAt: 123456789,
  })), null)
  assert.equal(parseEditorDraft(''), null)
})

test('creates a UTF-8 Markdown download blob', async () => {
  const blob = createMarkdownBlob('# 中文标题')

  assert.equal(blob.type, 'text/markdown;charset=utf-8')
  assert.equal(await blob.text(), '# 中文标题')
})
