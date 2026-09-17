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
| Markdown | marked 18、highlight.js 11、yaml 2.9；不使用 CodeMirror |
| 样式 | Tailwind CSS 4 |
| 本地容器 | Docker Compose、MySQL 8.4、Nginx；Node 24.12 构建前端 |

## 关键事实

- 正常响应不套 `{ code, data }`，Controller 直接返回 VO、数组、Map 或 `PageResult`。
- 异常响应才是 `{ code, message }`。
- API 共 31 个：公开端 8 个，管理端 23 个（含修改密码和批量文章操作）。
- 管理端新建资源统一返回 HTTP 200，不返回 201；删除和配置更新返回 204。
- 公开内容只返回 `PUBLISHED`；管理端列表可查全部状态。
- 搜索匹配 `title`、`summary` 和 Markdown 正文；正文由 MySQL 8.4 ngram 索引支持，
  正文命中返回可选 `excerpt`。限流为同 IP 10 秒 1 次，并仅信任显式配置的代理。
- `categoryId` 默认只精确匹配该分类；`includeDescendants=true` 时包含全部后代，
  层级循环或超过 32 层返回 409。
- Markdown 正文保存在 `app.storage-path`，数据库只存 `body_path`；创建/更新使用临时文件和回滚恢复策略，图片限制 50MB。
- 管理端前端路径由 `VITE_ADMIN_PATH` 控制，默认 `/secret-admin`。
- 后端当前有 164 个单元/边界/容器装配与真实 MySQL 环境门控测试；默认测试不连接真实 MySQL。
- 2026-09-11 已用隔离 MySQL 5.7 副本完成旧库迁移演练和 27/27 接口冒烟，脚本位于
  `Server Side/UmoWebBackend/scripts/api-smoke.ps1`。
- 2026-09-12 已建立 Docker Compose 全栈，MySQL 8.4 首次启动导入演示数据，前端由 Nginx
  提供并通过 `/api`、`/images` 反向代理；入口和运维说明见 `docs/project/docker-guide.md`。
- 2026-09-12 已在单台阿里云 ECS 完成无域名公网测试部署（Ubuntu 24.04、Docker Compose、
  安全组放行 TCP 80）。部署目录为 `/opt/umoweb`，当前未启用 HTTPS；
  实例标识、公网地址、管理路径和凭据均保留在服务器侧，不进入仓库。
- 2026-09-12 已建立 MySQL 与 `app_data` 的每周自动备份、SHA-256 校验、人工导出和隔离恢复；
  ECS timer 已启用，正式内容导入后的最终归档已从空卷恢复并再次通过 27/27 接口冒烟。
- 2026-09-12 已将 28 篇正式 Markdown、18 个分类、22 个标签和 91 张本地图片导入 ECS，
  演示内容和管理员已不再作为业务数据；数据库、JWT 与管理员凭据已轮换。
- 2026-09-13 已完成 Task 2.4：使用开发机版本归档和 Workbench 传输完成本地镜像发布、
  ECS 回滚和 rc.1 恢复演练，不需要长期镜像仓库；发布入口强制校验当前 commit 的成功 push CI。
- 2026-09-13 已完成 Task 2.5：六字段访问日志、主机级聚合/报表、回环访问边界和公开隐私说明
  已在 `v1.0.0-rc.3` 发布并通过 ECS、27/27 接口及访问安全验收。
- 2026-09-13 已完成 Task 3.1：管理端新建文章支持单文件 Markdown 导入，解析 YAML front matter
  并使用 H1/文件名回退；图片相对引用只警告，最终仍通过现有文章创建接口落库。
- 2026-09-13 已完成 Task 3.2：公开端和管理端文章列表新增 `includeDescendants`，默认保持
  `categoryId` 精确匹配；启用后展开全部后代，书库分类筛选默认包含子分类。
- 2026-09-13 至 2026-09-14 已完成 Task 3.3–3.5：图片删除与引用保护、正文 ngram
  全文搜索和公开端四款训练游戏；游戏保留原规则与旧版本地成绩键，并覆盖多分辨率布局。
- 2026-09-15 已完成 Task 4.2 的图片一致性检查：管理端可按需扫描断裂引用、记录缺文件和
  磁盘孤立文件，返回来源定位与空报告；不自动修复，也不改动 Schema。
- 2026-09-16 已完成 Task 4.3：内容生命周期扩展为 `DRAFT`、`SCHEDULED`、`PUBLISHED`、
  `ARCHIVED`；管理端支持当前页批量分类/标签、归档/恢复，调度器默认每 30 秒发布到期内容。
- 2026-09-17 已发布 `v1.0.0-rc.6` 到 ECS：Phase 4 全部能力通过独立 `Verify`、31/31
  真实接口冒烟和发布工具回归；当前回滚目标为 `v1.0.0-rc.5`。
- 前端公开阅读主路径、公开在线 Markdown 编辑器、管理端文章/分类/标签/站点设置/修改密码均已实现；
  编辑器草稿只保存在当前浏览器的 `umo-editor-draft-v1`。

## 常用命令

```powershell
cd "Server Side\UmoWebBackend"
mvn test

cd "Client Side\umo-web-frontend"
npm test
npm run build
npm run dev
npm run test:e2e

# 仓库根目录：Docker 全栈
.\scripts\docker-up.ps1
docker compose --env-file .env.docker ps

# 阿里云 ECS 远程运维；PATH 未包含该目录，必须使用完整路径
& 'C:\Program Files\workbench\workbench.exe' version
```

若机器级 Maven `settings.xml` 的仓库路径不可写，应使用一份隔离的临时 global/user settings 执行 Maven；不要修改系统级 Maven 安装目录。

## 修改约束

- 不得把计划中的组件、接口或页面写成“已实现”。
- 新增或修改接口后，必须同步更新 `docs/design/api-reference.md`、对应模块文档和审计日志。
- 修改数据库后，必须同步 `docs/design/schema.sql`、数据层文档和种子数据说明。
- 修改前端依赖或目录后，必须同步前端架构和依赖文档。
- 提交前至少执行受影响端的构建/测试；无法执行时必须记录原因和未验证风险。
