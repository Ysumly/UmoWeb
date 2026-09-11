import { createRouter, createWebHistory } from 'vue-router'
import { resolveAuthNavigation } from './guard.js'

const adminPath = (import.meta.env.VITE_ADMIN_PATH || '/secret-admin').replace(/\/+$/, '')

const routes = [
  // ===== 公开端 =====
  { path: '/',              name: 'home',     component: () => import('@/views/public/HomePage.vue') },
  { path: '/library',       name: 'library',  component: () => import('@/views/public/LibraryPage.vue') },
  { path: '/search',        name: 'search',   component: () => import('@/views/public/SearchPage.vue') },
  { path: '/post/:slug',    name: 'post',     component: () => import('@/views/public/PostDetailPage.vue') },
  { path: '/about',         name: 'about',    component: () => import('@/views/public/AboutPage.vue') },
  { path: '/project',       name: 'project',  component: () => import('@/views/public/ProjectPage.vue') },
  { path: '/editor',        name: 'editor',   component: () => import('@/views/public/EditorPage.vue') },

  // ===== 管理端 =====
  {
    path: adminPath,
    component: () => import('@/components/admin/AdminLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '',             redirect: `${adminPath}/contents` },
      { path: 'login',        name: 'login',           component: () => import('@/views/admin/LoginPage.vue'),   meta: { requiresAuth: false } },
      { path: 'contents',     name: 'admin-contents',  component: () => import('@/views/admin/ContentListPage.vue') },
      { path: 'contents/new', name: 'content-new',     component: () => import('@/views/admin/ContentEditPage.vue') },
      { path: 'contents/:id/edit', name: 'content-edit', component: () => import('@/views/admin/ContentEditPage.vue') },
      { path: 'categories',   name: 'admin-cats',      component: () => import('@/views/admin/CategoryManagePage.vue') },
      { path: 'tags',         name: 'admin-tags',      component: () => import('@/views/admin/TagManagePage.vue') },
      { path: 'options',      name: 'admin-options',   component: () => import('@/views/admin/OptionPage.vue') },
    ]
  },

  // ===== 404 =====
  { path: '/:pathMatch(.*)*', component: () => import('@/views/public/NotFoundPage.vue') },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 管理端路由守卫
router.beforeEach((to, from, next) => {
  const redirect = resolveAuthNavigation(to, localStorage.getItem('token'))
  if (redirect) {
    next(redirect)
    return
  }
  next()
})

export default router
