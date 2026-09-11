# 浏览器 E2E 与视觉回归实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 UmoWeb 前端建立可重复执行的浏览器 E2E 与视觉回归测试，覆盖公开端、在线编辑器和核心管理端工作流。

**Architecture:** Playwright 使用本机 Chrome，启动 Vite 生产预览，并通过浏览器级路由拦截提供状态化 Mock API。真实后端行为继续由现有 `api-smoke.ps1` 负责，浏览器套件不依赖 MySQL。

**Tech Stack:** Vue 3、Vite 8、Playwright Test 1.63、Node.js 24

**Spec:** `docs/project/testing-guide.md`

## Global Constraints

- 不修改生产 API、数据库结构或后端业务逻辑。
- 测试固定 `zh-CN`、`Asia/Shanghai`、单 worker 和 `prefers-reduced-motion: reduce`。
- 视觉基线只保证当前 Windows 与本机稳定 Chrome 环境。
- 不添加 GitHub Actions，仅提供本地脚本。
- 每阶段完成后运行对应验证，最终执行前端单测、构建和两次完整 E2E。

---

### Task 1: Playwright 基础与 Mock API

**Files:**
- Create: `Client Side/umo-web-frontend/playwright.config.js`
- Create: `Client Side/umo-web-frontend/e2e/support/apiMock.js`
- Modify: `Client Side/umo-web-frontend/package.json`
- Modify: `.gitignore`

- [x] 添加 Playwright 依赖和本地测试脚本。
- [x] 先写首页公开内容失败用例并确认 RED。
- [x] 实现严格匹配现有响应契约的状态化 Mock API fixture。
- [x] 运行首页用例确认 GREEN。

### Task 2: 公开端与编辑器功能回归

**Files:**
- Create: `Client Side/umo-web-frontend/e2e/public.spec.js`
- Create: `Client Side/umo-web-frontend/e2e/editor.spec.js`

- [x] 覆盖首页、书库筛选分页、搜索 429、详情前后文章。
- [x] 覆盖编辑器草稿、导入确认、下载、安全预览和移动端切换。
- [x] 覆盖错误、空数据与 390px 横向溢出。

### Task 3: 管理端功能回归

**Files:**
- Create: `Client Side/umo-web-frontend/e2e/admin.spec.js`

- [x] 覆盖未登录重定向、登录和退出。
- [x] 覆盖文章筛选、分页、新建、编辑和删除。
- [x] 覆盖分类/标签 CRUD、站点设置和修改密码 token 清理。

### Task 4: 视觉回归与稳定性

**Files:**
- Create: `Client Side/umo-web-frontend/e2e/visual.spec.js`
- Create: `Client Side/umo-web-frontend/e2e/visual.spec.js-snapshots/*`

- [x] 建立首页亮暗主题、书库、详情、编辑器、登录、管理列表的桌面与 390px 基线。
- [x] 关闭动画、隐藏光标并等待字体与内容稳定。
- [x] 连续运行两次完整 E2E 验证稳定性。

### Task 5: 文档与交付

**Files:**
- Modify: `docs/project/testing-guide.md`
- Modify: `docs/project/codebase-memory.md`
- Modify: `docs/project/CHANGELOG.md`
- Modify: `docs/project/audit-log.md`
- Modify: `docs/project/.state-snapshot.md`
- Modify: `docs/project/.session-summary.md`

- [x] 记录本地命令、覆盖范围、Mock API 与真实 MySQL 冒烟的边界。
- [x] 执行前端单测、生产构建和完整 E2E。
- [x] 检查 diff 和敏感信息，分阶段提交并推送 `master`。
