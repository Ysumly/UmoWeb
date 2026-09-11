import test from 'node:test'
import assert from 'node:assert/strict'

import {
  buildCategoryParentOptions,
  categoryDetailToForm,
  diffSiteOptions,
  formatOptionSaveFailure,
  getCategoryDeleteError,
  getTagDeleteError,
  validateCategoryForm,
  validatePasswordForm,
  validateSiteOptions,
  validateTagForm,
} from './adminManagement.js'

test('maps category detail fields required by the edit form', () => {
  assert.deepEqual(
    categoryDetailToForm({
      id: 2,
      name: 'Java',
      slug: 'java',
      type: 'NOTE',
      parentId: 1,
      sortOrder: 3,
    }),
    {
      name: 'Java',
      slug: 'java',
      type: 'NOTE',
      parentId: 1,
      sortOrder: 3,
    },
  )
})

test('builds same-type category parent options without self or descendants', () => {
  const tree = [
    {
      id: 1,
      name: '编程',
      slug: 'programming',
      type: 'NOTE',
      children: [
        {
          id: 2,
          name: 'Java',
          slug: 'java',
          type: 'NOTE',
          children: [
            {
              id: 3,
              name: 'Spring',
              slug: 'spring',
              type: 'NOTE',
              children: [],
            },
          ],
        },
      ],
    },
    {
      id: 4,
      name: '小说',
      slug: 'novel',
      type: 'NOVEL',
      children: [],
    },
    {
      id: 5,
      name: '工具',
      slug: 'tools',
      type: 'NOTE',
      children: [],
    },
  ]

  assert.deepEqual(
    buildCategoryParentOptions(tree, { type: 'NOTE', editingId: 1 }).map(({ id, depth }) => ({
      id,
      depth,
    })),
    [{ id: 5, depth: 0 }],
  )

  assert.deepEqual(
    buildCategoryParentOptions(tree, { type: 'NOVEL' }).map(({ id, depth }) => ({ id, depth })),
    [{ id: 4, depth: 0 }],
  )
})

test('validates category fields against backend constraints', () => {
  assert.deepEqual(
    validateCategoryForm({
      name: '',
      slug: 'bad slug',
      type: 'INVALID',
      sortOrder: '1.5',
    }),
    {
      name: '分类名不能为空',
      slug: 'slug 只能包含字母、数字、下划线和连字符',
      type: '分类类型无效',
      sortOrder: '排序值必须是整数',
    },
  )

  assert.deepEqual(
    validateCategoryForm({
      name: 'Java',
      slug: 'java',
      type: 'NOTE',
      sortOrder: '2',
    }),
    {},
  )
})

test('validates tag fields against backend constraints', () => {
  assert.deepEqual(validateTagForm({ name: '', slug: '-bad' }), {
    name: '标签名不能为空',
    slug: 'slug 只能包含字母、数字、下划线和连字符',
  })

  assert.deepEqual(validateTagForm({ name: 'Java', slug: 'java' }), {})
})

test('returns ordered site option changes only', () => {
  const original = {
    site_title: 'Umo Blog',
    site_subtitle: '代码 · 阅读 · 创作',
    about_page: '## 关于',
    project_page: '## 项目',
  }

  assert.deepEqual(
    diffSiteOptions(original, {
      ...original,
      site_title: 'Umo Notes',
      project_page: '## 新项目',
    }),
    [
      { key: 'site_title', value: 'Umo Notes' },
      { key: 'project_page', value: '## 新项目' },
    ],
  )
})

test('requires every site option to be nonblank', () => {
  assert.deepEqual(
    validateSiteOptions({
      site_title: '',
      site_subtitle: ' ',
      about_page: '## 关于',
      project_page: '',
    }),
    {
      site_title: '站点标题不能为空',
      site_subtitle: '站点副标题不能为空',
      project_page: 'Project 页面不能为空',
    },
  )
})

test('validates password fields and confirmation', () => {
  assert.deepEqual(
    validatePasswordForm({
      oldPassword: '',
      newPassword: '12345',
      confirmPassword: '54321',
    }),
    {
      oldPassword: '旧密码不能为空',
      newPassword: '新密码至少 6 位',
      confirmPassword: '两次输入的新密码不一致',
    },
  )

  assert.deepEqual(
    validatePasswordForm({
      oldPassword: 'admin123',
      newPassword: 'newPass666',
      confirmPassword: 'newPass666',
    }),
    {},
  )
})

test('formats partial option save results with saved and pending keys', () => {
  assert.equal(
    formatOptionSaveFailure({
      savedKeys: ['site_title', 'site_subtitle'],
      failedKey: 'about_page',
      remainingKeys: ['project_page'],
    }),
    '已保存：站点标题、站点副标题；保存失败：About 页面；未保存：Project 页面',
  )
})

test('maps protected category and tag deletion errors', () => {
  assert.equal(
    getCategoryDeleteError({
      response: {
        status: 409,
        data: { message: 'Cannot delete category: it has 2 child category(ies)' },
      },
    }),
    '无法删除：该分类仍有子分类',
  )
  assert.equal(
    getTagDeleteError({
      response: {
        status: 409,
        data: { message: 'Cannot delete tag: it is associated with 1 content(s)' },
      },
    }),
    '无法删除：该标签仍有关联文章',
  )
})
