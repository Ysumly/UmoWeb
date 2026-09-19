import { createRouter, createWebHistory } from 'vue-router'
import { resolveAuthNavigation } from './guard.js'
import { ADMIN_PATH, adminPath } from '@/config/adminPath'
import PublicLayout from '@/layouts/PublicLayout.vue'

const routes = [
  // ===== 公开端 =====
  {
    path: '/',
    component: PublicLayout,
    children: [
      { path: '',           name: 'home',    component: () => import('@/views/public/HomePage.vue'),       meta: { motion: 'cinematic', order: 0 } },
      { path: 'library',    name: 'library', component: () => import('@/views/public/LibraryPage.vue'),    meta: { motion: 'cinematic', order: 1 } },
      { path: 'search',     name: 'search',  component: () => import('@/views/public/SearchPage.vue'),     meta: { motion: 'focused', order: 3 } },
      { path: 'post/:slug', name: 'post',    component: () => import('@/views/public/PostDetailPage.vue'), meta: { motion: 'focused', order: 2 } },
      { path: 'about',      name: 'about',   component: () => import('@/views/public/AboutPage.vue'),      meta: { motion: 'focused', order: 2 } },
      { path: 'project',    name: 'project', component: () => import('@/views/public/ProjectPage.vue'),    meta: { motion: 'focused', order: 3 } },
      { path: 'tools',      name: 'tools',   component: () => import('@/views/public/ToolsPage.vue'),     meta: { motion: 'focused', order: 4, section: 'tools' } },
      { path: 'editor',     name: 'editor',  component: () => import('@/views/public/EditorPage.vue'),     meta: { motion: 'focused', order: 4, section: 'tools' } },
      { path: 'privacy',    name: 'privacy', component: () => import('@/views/public/PrivacyPage.vue'),   meta: { motion: 'focused', order: 5 } },
      { path: 'games',      name: 'games',   component: () => import('@/views/public/GamesPage.vue'),     meta: { motion: 'focused', order: 6, section: 'games', instantTransition: true } },
      { path: 'games/stroop', name: 'game-stroop', component: () => import('@/views/public/StroopGamePage.vue'), meta: { motion: 'focused', order: 6, section: 'games', instantTransition: true } },
      { path: 'games/digit-span', name: 'game-digit-span', component: () => import('@/views/public/DigitSpanGamePage.vue'), meta: { motion: 'focused', order: 6, section: 'games', instantTransition: true } },
      { path: 'games/poker-memory', name: 'game-poker-memory', component: () => import('@/views/public/PokerMemoryGamePage.vue'), meta: { motion: 'focused', order: 6, section: 'games', instantTransition: true } },
      { path: 'games/schulte', name: 'game-schulte', component: () => import('@/views/public/SchulteGamePage.vue'), meta: { motion: 'focused', order: 6, section: 'games', instantTransition: true } },
      { path: ':pathMatch(.*)*', name: 'not-found', component: () => import('@/views/public/NotFoundPage.vue'), meta: { motion: 'focused', order: 6 } },
    ],
  },

  // ===== 管理端 =====
  {
    path: adminPath('login'),
    name: 'login',
    component: () => import('@/views/admin/LoginPage.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: ADMIN_PATH,
    component: () => import('@/components/admin/AdminLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '',             redirect: adminPath('contents') },
      { path: 'contents',     name: 'admin-contents',  component: () => import('@/views/admin/ContentListPage.vue') },
      { path: 'contents/new', name: 'content-new',     component: () => import('@/views/admin/ContentEditPage.vue') },
      { path: 'contents/:id/edit', name: 'content-edit', component: () => import('@/views/admin/ContentEditPage.vue') },
      { path: 'categories',   name: 'admin-cats',      component: () => import('@/views/admin/CategoryManagePage.vue') },
      { path: 'tags',         name: 'admin-tags',      component: () => import('@/views/admin/TagManagePage.vue') },
      { path: 'images',       name: 'admin-images',    component: () => import('@/views/admin/ImageManagePage.vue') },
      { path: 'ai-settings',  name: 'admin-ai-settings', component: () => import('@/views/admin/AiSettingsPage.vue') },
      { path: 'options',      name: 'admin-options',   component: () => import('@/views/admin/OptionPage.vue') },
      { path: 'password',     name: 'admin-password',  component: () => import('@/views/admin/ChangePasswordPage.vue') },
    ]
  },

]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    }
    if (
      to.name === 'library'
      && from.name === 'library'
      && String(to.query.page || '1') === String(from.query.page || '1')
    ) {
      return false
    }
    return { top: 0, behavior: 'smooth' }
  },
})

// 管理端路由守卫
router.beforeEach((to) => {
  const redirect = resolveAuthNavigation(to, localStorage.getItem('token'))
  return redirect || true
})

export default router
