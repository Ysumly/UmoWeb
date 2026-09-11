export function resolveAuthNavigation(to, token) {
  if (to.meta?.requiresAuth === true && !token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return null
}
