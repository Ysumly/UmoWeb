export const publicNavigation = [
  { to: '/', label: '首页', name: 'home' },
  { to: '/library', label: '书库', name: 'library' },
  { to: '/search', label: '搜索', name: 'search' },
  { to: '/project', label: '项目', name: 'project' },
  { to: '/games', label: '游戏', name: 'games' },
  { to: '/tools', label: '工具', name: 'tools' },
  { to: '/about', label: '关于', name: 'about' },
]

export const footerNavigation = [
  ...publicNavigation,
  { to: '/privacy', label: '隐私收集' },
]
