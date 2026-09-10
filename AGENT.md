# 项目工作规范

1. 修改前先阅读 `docs/project/codebase-memory.md` 和实际代码；文档以当前代码行为为准。
2. 每次执行做好与改动范围匹配的完整测试，测试无误后再提交项目给用户。
3. 每次对项目修改后都提交并上传到 GitHub 仓库。

# 项目记忆

## 目录

- `Client Side/umo-web-frontend/`：Vue 3 + Vite 前端。
- `Server Side/UmoWebBackend/`：Spring Boot + MyBatis 后端。
- `docs/design/`：产品、架构、接口和数据库基线。
- `docs/modules/`：分层实现说明。
- `docs/project/`：工作流、测试、状态和审计记录。
- `Downloads/`：敏感目录，禁止提交。

## 当前技术基线

| 项目 | 实际值 |
|---|---|
| 后端 | Spring Boot 4.1.0、Java 17、Maven |
| 数据层 | MyBatis 4.0.1、MySQL |
| 认证 | JJWT 0.12.6、BCrypt、`Authorization: Bearer <token>` |
| AI | Spring AI BOM 2.0.0-M4、OpenAI Starter（尚未接入业务代码） |
| 前端 | Vue 3.5、Vite 8、Vue Router 5、Pinia 3、Axios 1.18 |
| Markdown | marked 18、highlight.js 11；不使用 CodeMirror |
| 样式 | Tailwind CSS 4 |

## 关键事实

- 正常响应不套 `{ code, data }`，Controller 直接返回 VO、数组、Map 或 `PageResult`。
- 异常响应才是 `{ code, message }`。
- API 共 27 个：公开端 8 个，管理端 19 个（含修改密码）。
- 管理端新建资源统一返回 HTTP 200，不返回 201；删除和配置更新返回 204。
- 公开内容只返回 `PUBLISHED`；管理端列表可查全部状态。
- 搜索只匹配 `title` 和 `summary`，不搜索 Markdown 正文；限流为同 IP 10 秒 1 次。
- `categoryId` 只精确匹配该分类，不自动包含子分类。
- Markdown 正文保存在 `app.storage-path`，数据库只存 `body_path`；图片限制 50MB。
- 管理端路径在前端硬编码为 `/secret-admin`，`app.admin-path` 当前未被代码使用。
- 后端测试目前是 20 个独立 MockMvc 边界测试，没有真实 MySQL 集成测试。
- 前端登录页和管理布局已实现，其余业务页面基本仍是占位页。

## 常用命令

```powershell
cd "Server Side\UmoWebBackend"
mvn test

cd "Client Side\umo-web-frontend"
npm run build
npm run dev
```

若机器级 Maven `settings.xml` 的仓库路径不可写，应使用一份隔离的临时 global/user settings 执行 Maven；不要修改系统级 Maven 安装目录。

## 修改约束

- 不得把计划中的组件、接口或页面写成“已实现”。
- 新增或修改接口后，必须同步更新 `docs/design/api-reference.md`、对应模块文档和审计日志。
- 修改数据库后，必须同步 `docs/design/schema.sql`、数据层文档和种子数据说明。
- 修改前端依赖或目录后，必须同步前端架构和依赖文档。
- 提交前至少执行受影响端的构建/测试；无法执行时必须记录原因和未验证风险。
