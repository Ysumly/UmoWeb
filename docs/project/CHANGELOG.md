# CHANGELOG

## 2026-06-26 — 项目初始化

- Spring Boot 4.1.0 + Java 17 项目骨架
- Maven 多模块结构（common/config/controller/mapper/model/service）
- MyBatis + MySQL 数据访问层
- JWT 认证方案集成
- Spring AI (OpenAI) 集成
- Docs-Driven Development 工作流建立

## 2026-06-26 — 文档层完成

- 15 份设计文档：SRS ×2 / 架构设计 ×2 / DDL / 模块设计 ×5 / 依赖 / 工作流
- `docs/design/schema.sql` 可执行建表脚本（8 表 + 初始数据）
- 数据库已建库建表，site_options 初始数据已灌入

## 2026-06-26 — 后端 common 层实现

- `ContentType` / `ContentStatus` 枚举
- `BusinessException` → `UnauthorizedException` / `ForbiddenException` / `NotFoundException`
- `GlobalExceptionHandler` 统一异常响应
- `JwtUtil` JWT 签发/解析/校验
- `FileUtil` MD 文件读写 / 图片路径生成
- `application.yml` 替换 `application.properties`

## 2026-06-26 — 前端项目初始化

- Vite + Vue 3 项目骨架 (`Client Side/umo-web-frontend/`)
- 当前依赖: Vue 3 / Vite 8 / Vue Router 5 / Pinia 3 / Axios / marked / highlight.js / Tailwind CSS 4
- 路由: 7 个公开页面、6 个管理页面、管理布局和 404，管理端 Token 守卫
- API 层: Axios 拦截器 + public.js / admin.js 封装
- 状态管理: auth store / 部分 site store
- 登录页、管理布局和 404 有实际实现，其余业务页面为占位

## 2026-09-10 — 代码基线文档校正

- 新增 `AGENTS.md` 和 `docs/project/codebase-memory.md`。
- 以当前 Controller、DTO、VO、Mapper XML 和前端源码重新校准文档。
- 修正正常响应错误描述：接口直接返回业务数据，不套 `{ code, data }`。
- 修正 API 数量：公开 8 个，管理 19 个，共 27 个。
- 修正新建接口状态：当前统一返回 200，不返回 201。
- 将搜索能力修正为 title/summary，不检索 Markdown 正文。
- 将 `categoryId` 行为修正为精确匹配，不包含子分类。
- 记录公开文章详情当前不返回分类/标签数组。
- 明确前端当前只有登录、管理布局和基础 404 具备实际界面。
- 记录分类/标签删除保护、文件事务、路径安全和分页边界等已知缺陷。
- 后端 21 个测试和前端生产构建验证通过。
