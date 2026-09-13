# 审计日志

## 审计 #25 - 2026-09-13 — 本地镜像发布与回滚

### 范围

- 在不引入 ACR/GHCR 的前提下，建立开发机版本归档、Workbench 传输和 ECS 本地发布流程。
- 强制当前 commit 的成功 push CI，校验归档、镜像 ID、前端管理路径、公开入口和容器健康。
- 完成 rc.1 发布、发布前 baseline 回滚和 rc.1 恢复演练。

### 实现

- `scripts/release/umoweb-release.ps1` 提供 `CaptureBaseline`、`Publish`、`Rollback` 和 `Verify`。
- 前后端镜像同时使用版本标签和 `sha-<12位commit>` 标签；manifest 记录 image ID、CI run、
  归档 SHA-256/大小和管理路径哈希。
- 远端脚本提供发布锁、陈旧锁恢复、原子 env 更新、失败自动恢复、公网检查、旧镜像清理和状态记录。
- 首次真实发布暴露 ECS `.env.docker` 未显式配置镜像标签的问题；修复为回退 Compose 默认值
  `umoweb-backend:latest`/`umoweb-frontend:latest` 后完成发布。
- Verify 公网日志曾污染 JSON stdout；日志改到 stderr，并补充本地已验证归档的幂等恢复路径。

### 验证

| 验证 | 结果 |
|---|---|
| 发布候选 | `v1.0.0-rc.1`，提交 `f6f5ce170b3c`，CI run `34739475146` |
| 发布耗时 | 170 秒，ECS 与公网检查通过 |
| 回滚候选 | `baseline-20260913`，恢复原前后端 image ID |
| 回滚耗时 | 120 秒，ECS 与公网检查通过 |
| rc.1 恢复 | 122 秒，最终 `Verify` 返回 `v1.0.0-rc.1` |
| 最终工具链 CI | `5a41e81` push CI run `34740201125` 通过 |
| 脚本自测与敏感扫描 | 通过 |

### 剩余风险

1. 当前仍是单机 Compose 和单实例内存限流；多实例部署需要共享限流状态。
2. ECS 无长期镜像仓库，回滚依赖开发机保存并校验的版本归档。
3. Workbench 单文件上限为 1 GiB；当前归档约 167.4 MB，超过上限时发布脚本会拒绝。
4. 公网入口仍是无域名 HTTP 测试部署，HTTPS 阻断项未关闭。

## 审计 #24 - 2026-09-13 — 真实 MySQL 集成验证

### 范围

- 将 MySQL 8.4 Schema、种子数据、兼容迁移和真实后端接口链纳入 GitHub Actions。
- 补齐 Python 与 PowerShell 冒烟脚本的查询语义、关联和前后文章断言。
- 校验临时上传存储、测试数据清理和失败诊断边界。

### 实现

- `.github/workflows/ci.yml` 新增独立 `mysql-integration` job 和 MySQL 8.4 服务容器；
  失败时上传后端日志。
- 新增 `scripts/ci/mysql-integration.sh`，从空库执行 Schema、种子和兼容迁移，
  连续执行迁移验证幂等性，并校验字符集、排序规则、列、索引、外键、种子行数和孤儿关系。
- 脚本使用 Java 17 构建并启动 `prod` profile 后端，使用独立临时存储和运行时生成的
  JWT/管理员测试凭据执行 `api-smoke.py`。
- 两个冒烟脚本新增公开类型/分类/标签筛选、详情分类/标签、前后文章顺序断言；
  临时上一篇内容在主内容删除步骤一并清理，不能绕过标签关联保护。
- 冒烟后校验图片记录和临时存储中的 PNG，job 结束销毁进程、数据库和临时文件。

### 验证

| 验证 | 结果 |
|---|---|
| 本地 MySQL 8.4 集成脚本 | 通过，Schema、种子、迁移幂等和后端启动成功 |
| 本地 `api-smoke.py` | 27/27，通过筛选、关联、前后文章、上传和 429 |
| 本地 `api-smoke.ps1` | 27/27，与 Python 覆盖一致 |
| 后端完整测试 | 83 tests / 0 failures / 0 errors |
| 敏感信息扫描器自测 | 通过 |
| 敏感信息全仓扫描 | 通过 |
| Shell、Python 和 PowerShell 语法检查 | 通过 |
| PR #3 第一轮 CI | `34736391676` 五个 job 全部通过，MySQL integration 1m14s |
| PR #3 第二轮 CI | 同一 run 重跑五个 job 全部通过，MySQL integration 1m21s |

### 剩余风险

1. CI 使用当前 Schema 验证兼容迁移幂等性；历史旧版 Schema 升级仍引用既有 MySQL 5.7
   人工演练证据。
2. MySQL service、runner 镜像和 `mysql:8.4` 标签升级后需要重新观察 CI 稳定性。
3. 搜索和登录限流仍是单实例内存状态。

## 审计 #23 - 2026-09-13 — Linux Playwright 与独立视觉基线

### 范围

- 将已通过检查的 Task 2.1 CI 以 merge commit 合入 `master`。
- 在 Ubuntu GitHub Actions 中运行与 Windows 相同的 Playwright functional 和视觉断言。
- 新增不覆盖 `win32` 文件的 Linux 视觉基线及人工审查更新流程。

### 实现

- PR #1 的三项检查全部通过后，以 merge commit 合入 `master`，合并提交为 `8781c44`。
- Playwright 增加 `PLAYWRIGHT_CHANNEL` 覆盖；Windows 默认 Chrome channel，Linux 使用
  `npm ci` 锁定的 Playwright 1.63.0 Chromium。
- `.github/workflows/ci.yml` 新增独立 `browser` job，安装 Linux Chromium 后执行
  `npm run test:e2e`，失败时上传报告、trace 和失败截图。
- 新增手动 `.github/workflows/playwright-linux-baselines.yml`，通过
  `workflow_dispatch` 执行 `npm run test:e2e:update`，只上传 `*-linux.png` artifact；
  工作流没有仓库写权限，不会自动提交快照。
- 首次生成时使用同分支 PR 临时 job 产出 artifact；下载后人工核对并提交 14 张 Linux 快照，
  随后移除临时 job，避免仓库未具备基线时主 CI 必然失败。
- 同步更新测试指南、开发工作流、代码基线、状态快照、变更记录和四阶段路线图。

### 验证

| 验证 | 结果 |
|---|---|
| Windows `npm test` | 46 tests / 0 failures |
| Windows `npm run test:e2e` | 34 passed，原有 14 张 `win32` 快照无变化 |
| Linux 基线生成 | `34733989235` 通过，artifact 包含 14 张非空 `*-linux.png` |
| PR #2 第一轮 CI | `34734144015` 四个 job 全部通过，Browser 1m26s |
| PR #2 第二轮 CI | 同一 run attempt 2 四个 job 全部通过，Browser 1m17s |
| Linux 浏览器检查 | 两轮均为 20 functional + 14 visual，34/34 |
| 快照隔离 | 仓库包含 14 张 `win32` 和 14 张 `linux`，平台后缀未互相覆盖 |
| 应用契约 | API、Schema、请求响应和前端业务行为均未修改 |

### 剩余风险

1. 视觉基线仍绑定操作系统、Chromium 版本和字体环境；Playwright 或 runner 镜像升级后必须重新生成并审查 Linux 快照。
2. 当前私有仓库套餐仍不支持分支保护或规则集，CI 失败不能自动阻止合并；Task 2.4 必须在发布流程中显式检查 CI 结果。
3. 手动 Linux 基线工作流需要先存在于默认分支后才能在 GitHub Actions 中直接发现；合并后使用无此限制。
4. 真实 MySQL 集成仍由 Task 2.3 承接，Playwright 继续只使用状态化 Mock API。
5. Task 1.4 的域名与 HTTPS 仍未完成。

## 审计 #22 - 2026-09-13 — CI 基础流水线与任务合并

### 范围

- Task 1.2 备份恢复和 Task 1.3 正式内容导入的独立合并提交。
- GitHub Actions 基础质量检查、敏感信息扫描和失败门禁边界。

### 实现

- 在 `master` 上分别创建 Task 1.2、Task 1.3 的 `--no-ff` merge commit，并推送远端。
- 新增 `.github/workflows/ci.yml`，PR 和 `master` push 时运行仓库检查、后端测试、前端测试和构建。
- 新增无第三方依赖的 `scripts/ci/scan-sensitive-info.sh`，按模式批量扫描全部已跟踪文件。
- 扫描覆盖公开 IPv4、阿里云 ECS 实例 ID、阿里云/AWS/GitHub Token、JWT 形态、私钥头和
  误提交的 `.env*` 文件；允许私有、回环、CGNAT、文档专用地址和已有明确占位值。
- 新增扫描器 Bash 自测，验证允许场景和各类拒绝场景，错误输出只包含规则与文件行号。
- 将 Python `__pycache__` 和 `.pyc` 加入忽略规则，避免本地验证污染工作区。

### 验证

| 验证 | 结果 |
|---|---|
| 合并前后端 `mvn test` | 83 tests / 0 failures / 0 errors |
| 合并前前端 `npm test` | 46 tests / 0 failures |
| 合并前前端 `npm run build` | Vite 8.1.0 构建通过 |
| 内容导入器 Python 测试 | 6 tests / 0 failures |
| 备份 Bash 单元测试 | 通过 |
| 敏感信息扫描器自测 | 通过 |
| 敏感信息全仓扫描 | 通过 |
| master 合并内容 | 与 Task 1.3 分支树差异为 0 |

### 剩余风险

1. 私有仓库当前 GitHub 计划不支持分支保护或规则集，CI 失败不能自动阻止合并。
2. CI 尚未运行 Playwright Linux 基线和真实 MySQL 集成，分别由 Task 2.2、Task 2.3 承接。
3. Task 1.4 仍未完成；当前无正式域名，ECS 对 Let’s Encrypt 生产及测试 ACME 端点连接超时。
4. 敏感信息扫描器不是通用历史 secret scanner，只覆盖当前约定的高置信度模式和已跟踪文件。

## 审计 #21 - 2026-09-12 — 正式内容导入与凭据轮换

### 范围

- 从无 front matter 的 Markdown 目录生成正式内容候选包、分类标签和站点配置。
- 本地图片收集、Markdown 内链改写、内容哈希去重与 Linux 文件所有权。
- 生产数据库和 `app_data` 替换、MySQL/JWT/管理员凭据轮换和最终备份恢复。

### 实现

- 新增 `scripts/content-import/` 候选包生成器、目录映射、生产提升脚本和候选包说明。
- 正式环境导入 28 篇内容、18 个分类、22 个标签和 91 张本地图片；两篇导航索引不作为文章。
- 28 篇公开摘要全部改为人工整理，并写入 `catalog.json`；自动提取只作为缺失摘要的回退。
- 本地图片复制到 `app_data`，Markdown 图片和文章内链改写为站内 URL；相同图片按 SHA-256 去重。
- 候选归档写入 `umo:umo` 文件所有权，恢复脚本只接受小写 `umoweb-restore-*` 项目名。
- 新增无第三方依赖的 `api-smoke.py`，与 PowerShell 版本执行相同的 27 个接口和失败路径。
- 生产提升先创建一致性备份，再替换数据库与文件、轮换 MySQL/JWT/管理员凭据并执行冒烟。

### 发现与修复

| # | 级别 | 问题 | 修复 |
|---|---|---|---|
| 1 | P0 | 在 1 GiB 主机上同时运行生产和隔离恢复栈会触发 OOM，Workbench 通道超时。 | 改为停止生产栈后再运行隔离恢复；强制重启后清理残留 `umoweb-restore-*` 项目。 |
| 2 | P1 | 默认恢复项目名含大写 UTC 字符，Docker Compose 拒绝。 | 恢复项目名统一转小写，测试明确拒绝大写名称。 |
| 3 | P1 | Windows 生成归档时 `app_data` 文件属于 root，后端 UID 10001 无法写入。 | 归档目录和文件显式写入 `10001:10001` / `umo:umo`，并覆盖所有权回归测试。 |
| 4 | P2 | Markdown 水平分隔线被提取成公开摘要 `---`。 | 摘要解析跳过 `---` 等水平线，并重新同步 28 篇摘要。 |
| 5 | P2 | 便携冒烟默认用户名 `admin`，无法用于自定义管理员名。 | 未显式传入 username 时从 `INIT_ADMIN_USER` 读取。 |

### 验证

| 验证 | 结果 |
|---|---|
| 内容导入器 Python 测试 | 6 tests / 0 failures |
| 备份 Bash 测试 | 通过，含恢复项目名和归档校验 |
| 候选包隔离恢复 | 表行数和 119 个文件逐项 SHA-256 一致 |
| 候选包接口冒烟 | 27/27 通过 |
| 正式生产接口冒烟 | 27/27 通过 |
| 正式最终备份空卷恢复 | 表行数和文件清单一致，27/27 通过 |
| 公网检查 | 首页 HTTP 200；公开内容 `total=28`；`---` 摘要为 0 |
| 人工摘要核对 | 28/28 非空且与目录映射逐篇一致 |
| 容器健康 | MySQL、backend、frontend 均 healthy |

### 剩余风险

1. 单台 ECS 内存有限，后续隔离恢复仍需维护窗口并先停止生产栈。
2. 本机原始笔记路径、正式管理员密码、数据库密码和 JWT secret 不进入仓库。
3. 冒烟上传图片因当前没有图片删除接口会留在数据库中，后续由第三阶段媒体能力处理。
4. 域名、HTTPS、真实来源 CORS 和可信代理验证仍属于 Task 1.4。

## 审计 #20 - 2026-09-12 — 备份与恢复闭环

### 范围

- MySQL 与 `app_data` 的一致性备份、SHA-256 校验、人工导出和隔离恢复。
- ECS systemd 每周调度、短时写入暂停、服务自动恢复和恢复环境接口验收。
- `api-smoke.ps1` 去除演示 slug 与固定内容数量依赖。

### 实现

- 新增 `scripts/backup/` Bash 运维链路，包含创建、校验、导出、隔离恢复、清理和 timer 安装入口。
- 备份暂存后原子发布；归档包含 MySQL dump、`app_data` 压缩包、逐文件 SHA-256 清单、
  数据库版本、表行数、文件数量、耗时和镜像 ID。
- Compose 后端与前端增加显式 `BACKEND_IMAGE`、`FRONTEND_IMAGE`，恢复项目使用独立卷、网络、
  临时凭据和回环端口，并通过项目名前缀阻止清理或恢复操作命中生产项目。
- ECS 安装每周日 03:30 的 systemd timer，启用 `Persistent=true` 和最多 10 分钟随机延迟。
- 冒烟脚本改为通过管理接口创建临时分类、标签、草稿与已发布内容，完成公开详情、搜索、草稿隔离
  和并发限流验证后尽力清理临时数据。

### 验证

| 验证 | 结果 |
|---|---|
| Bash 语法与单元测试 | 通过，覆盖保留数量、8GiB 上限、校验和篡改、项目名护栏和导出 |
| 后端基线 | 83 tests / 0 failures / 0 errors |
| 前端基线 | 46 tests / 0 failures |
| 本地一致备份 | 成功，7,955 字节，12 秒；归档含 6 个 app 文件、5 篇 Markdown 和 8 张表元数据 |
| 本地空环境恢复 | 表行数和逐文件 SHA-256 清单一致 |
| 本地恢复冒烟 | 27/27 通过 |
| ECS timer | 已启用，下次按计划时间执行 |
| ECS 手动备份 | 成功，服务自动恢复且三个容器 healthy；归档 1,105,772 字节，服务耗时 16.756 秒 |
| ECS 归档校验 | 外层、内部 SHA-256 和 `0600 root:root` 权限通过 |
| ECS 备份元数据 | 8 张表、5 篇 Markdown、1 张图片；前后端与 helper 镜像 ID 已记录 |
| 跨主机恢复 | ECS 归档下载到开发机后从空环境恢复成功 |
| 跨主机恢复冒烟 | 27/27 通过 |
| 隔离环境清理 | 只删除 `umoweb-restore-*` 项目和卷，生产项目未触碰 |

### 剩余风险

1. 备份与正式数据仍在同一台 ECS，没有自动异地复制或对象存储生命周期。
2. 归档当前不加密；导出后需要管理员自行保护目标目录和介质。
3. ECS 部署目录不是 Git 检出，当前 `GIT_COMMIT` 为 `unknown`；镜像 ID 已记录，后续部署应在
   `/etc/umoweb/backup.env` 显式设置 `GIT_COMMIT`。
4. 恢复演练使用当前演示数据；Task 1.3 导入正式内容后必须重跑，发布清单 `REL-04` 才能勾选。
5. 每周备份允许最多分钟级维护窗口，恢复点目标最坏情况约为一周。

## 审计 #19 - 2026-09-12 — v1 正式发布范围冻结

### 范围

- 冻结第一阶段正式发布范围、职责、阻断条件、发布记录和回滚边界。
- 明确当前阿里云 ECS 原地升级为正式环境的路径，保留单机 Docker Compose 拓扑。
- 不修改后端、前端、数据库、Compose 或部署配置。

### 实现

- 新增 `docs/project/release-checklist-v1.md`，纳入七个公开路由、六个管理业务页和 27 个接口。
- 将 Markdown 导入、子分类筛选、图片删除、全文搜索、训练游戏、访问统计、CI/CD、多实例限流、
  RBAC、AI 和搜索收录优化明确排除在 v1 之外。
- 定义四类发布职责和 14 个 `P0/P1` 阻断检查项，每项包含唯一责任角色、证据、通过条件和级别。
- 定义数据与安全事件即时回滚、核心入口一次修复失败后回滚，以及其他阻断项 30 分钟无法修复时回滚。
- 同步更新四阶段路线、代码基线记忆、状态快照和变更记录。

### 验证

| 验证 | 结果 |
|---|---|
| 发布检查项数量 | 14 个，全部通过结构检查 |
| 检查项字段 | 14/14 同时包含责任、证据、通过条件和 `P0/P1` 级别 |
| 范围数量 | 公开路由 7 个、管理业务页 6 个 |
| API 基线 | 公开 8、管理 19、总计 27，与 `api-reference.md` 一致 |
| 本地 Markdown 链接 | 4/4 可解析 |
| 占位符扫描 | 无 `TODO`、`TBD` 或“待定” |
| 敏感值定向扫描 | 未发现真实域名、IP、Token 或密钥模式 |
| `git diff --check` | 通过，仅有既有 Windows LF/CRLF 提示 |

本任务仅修改文档，未执行后端测试、前端测试或浏览器测试。

### 剩余风险

1. 当前 ECS 仍使用演示数据，没有域名、HTTPS 或正式凭据，Task 1.2 至 Task 1.4 尚未执行。
2. 14 个发布阻断项尚未在正式环境执行，当前文件只完成范围冻结，不代表发布已通过。
3. 实际人员、域名、证书、实例标识和凭据仍需保存在服务器侧发布台账中。

## 审计 #18 - 2026-09-12 — 阿里云 ECS 公网测试部署

### 范围

- 单台阿里云 ECS 上 Docker Compose 全栈的公网测试部署。
- 受限网络下的镜像交付、安全组边界、`.env.docker` 凭据边界和部署验收。
- 文档只保留脱敏环境事实，不上传任何真实身份或凭据。

### 实现

- 在 Ubuntu 24.04 ECS 创建 `/opt/umoweb`，写入仅 root 可读且权限为 `600` 的
  `.env.docker`，未把文件加入版本库。
- ECS 无法稳定访问 Docker Hub、npm 官方仓库和 Maven Central，因此改为在开发机构建
  `umoweb-frontend`、`umoweb-backend` 镜像并传送到 ECS。
- 镜像归档传输后先校验 SHA-256，再通过 `docker load` 导入；启动使用
  `docker compose up -d --no-build --wait`，避免服务器重新访问外部仓库。
- 安全组和 UFW 仅新增公网 TCP 80，MySQL 3306 与后端 8080 未暴露。
- 无域名环境使用 HTTP；公网地址、实例 ID、随机管理路径和全部密码/密钥未写入仓库。
- 发现并确认 `INIT_ADMIN_USER` / `INIT_ADMIN_PASS` 只影响空 `users` 表首次初始化，
  修改 `.env.docker` 不会自动更新已有管理员。

### 验证

| 验证 | 结果 |
|---|---|
| Compose 健康状态 | MySQL、backend、frontend 均为 healthy |
| 内部首页 | HTTP 200 |
| 公开站点信息 API | 返回预期 JSON |
| 管理员登录 | 重置数据库 BCrypt 哈希后 HTTP 200 |
| 会话失效 | `token_version` 递增，旧 JWT 失效 |
| 公网首页 | 安全组放行 TCP 80 后 HTTP 200 |
| 开机恢复 | Docker 服务 `enabled`，容器策略为 `unless-stopped` |

### 剩余风险

1. 当前无域名和 HTTPS，公网入口仍为明文 HTTP。
2. 首次启动导入了演示数据，尚未替换为正式内容。
3. 没有自动备份、保留策略和恢复演练。
4. 单实例内存限流仍不适用于多实例横向扩展。
5. 镜像交付是人工流程，私有仓库认证和 CI/CD 尚未配置。

## 审计 #17 - 2026-09-12 — Docker 全栈启动

### 范围

- MySQL 8.4、Spring Boot 后端和 Nginx 前端的三服务 Compose 栈。
- 自动凭据引导、演示数据库/Markdown、SPA 代理、大文件上传和命名卷持久化。
- 可信代理在 Docker 网络中的客户端 IP 与限流语义。

### 实现

- 新增根 `compose.yaml`、后端/前端多阶段 Dockerfile、Nginx 配置、演示 Markdown 和 `.dockerignore`。
- 新增 `scripts/docker-up.ps1`，缺少 `.env.docker` 时生成数据库密码、JWT secret 和管理员密码，并执行 `compose up --build --wait`。
- MySQL 空数据卷首次执行 `schema.sql`、`seed-data.sql`；后端镜像将 6 篇演示 Markdown 复制到 `app_data`。
- Nginx 对 SPA 路由回退 `index.html`，代理 `/api/**`、`/images/**`，覆盖 `X-Forwarded-For`，请求体上限 52MB。
- `ClientIpResolver` 支持精确 IP 与 IPv4/IPv6 CIDR，并保留多级转发链的由右向左解析。
- Playwright 改用 `e2e/runPlaywright.js` 和轻量静态服务器，避免 Windows 上 Vite preview 无法被 Playwright 清理而残留。
- Docker 首次试运行发现中文副标题和摘要乱码，在 schema/seed 脚本加入 `SET NAMES utf8mb4`，并让内容 `ON DUPLICATE KEY UPDATE` 刷新完整字段。
- 本机 `8080` 由既有 Java 进程占用，本地 `.env.docker` 改用 `18080`，未停止用户进程。

### 验证

| 验证 | 结果 |
|---|---|
| 后端 `mvn test` | 通过，83 tests / 0 failures / 0 errors |
| 前端 `npm test` | 通过，46 tests / 0 failures |
| 前端 `npm run build` | Vite 8.1.0 生产构建通过 |
| Playwright | 34 checks 全部显示通过 |
| Docker 健康状态 | MySQL、backend、frontend 均 healthy |
| Docker 真实接口 | `api-smoke.ps1` 通过，27/27 |
| 大文件上传 | 2MB PNG 经 Nginx 返回 200，后端保存 2,097,152 字节 |
| 代理限流 | 固定 Nginx `/32`；伪造不同 XFF 的连续搜索请求仍为 200 后 429 |
| 中文内容 | 副标题、文章摘要和 Markdown 正文均正常显示 |
| 持久化 | 重启容器后 5 篇公开文章、管理员登录和 2MB 图片仍可用 |

### 剩余风险

1. Nginx 只覆盖直接代理场景，前置 Cloudflare 等代理时仍需扩展真实 IP 链配置。
2. 当前 Docker 默认包含演示数据和初始管理员；正式部署前必须替换种子数据和凭据。
3. 图片仍没有删除接口，冒烟与边界验证上传的文件会保留在测试卷中。

## 审计 #16 - 2026-09-12 — Markdown 一级标题排版统一

### 范围

- 公开文章详情页和管理端 Markdown 预览的一级标题。
- Markdown 标题样式作用域和视觉基线。

### 根因

- 共享 `.markdown-body` 缺少 `h1` 规则，公开页标题继承正文的 `17px`。
- 管理端 `.admin-page h1` 选择器误命中编辑器预览，使同一标题显示为 `48px`。

### 实现

- 为 `.markdown-body h1` 增加明确字号、字重、行高和间距，桌面为 `48px`，窄屏为 `36px`。
- 将管理端页面标题选择器收窄为 `.admin-page__header h1`，避免影响 Markdown 内容。
- 新增跨公开页和管理端预览的计算样式一致性测试。
- 更新文章详情桌面与移动端视觉基线。

### 验证

| 验证 | 结果 |
|---|---|
| 前端 `npm test` | 通过，46 tests / 0 failures |
| 前端 `npm run build` | Vite 8.1.0 生产构建通过 |
| 前端 `npm run test:e2e` | 通过，34 tests / 0 failures |
| 浏览器实测 | 两端 `h1` 均为 `48px / 500 / 55.2px`，普通正文保持 `17px` |

## 审计 #15 - 2026-09-12 — metadata 字段说明展开

### 范围

- 管理端文章新建/编辑页的 `metadata` 输入区域。
- 字段用途、常用字段解释、示例和 JSON 注释限制。

### 实现

- 在“必须是合法 JSON 对象”提示下增加原生“更多”折叠项，默认收起。
- 展开后说明 `readingTime`、`difficulty`、`author`、`source` 的用途和取值示例。
- 展示合法 JSON 示例，并明确注释只用于说明，不应复制到输入框。
- 输入框 placeholder 同步展示四个常用字段。

### 验证

| 验证 | 结果 |
|---|---|
| 前端 `npm test` | 通过，46 tests / 0 failures |
| 前端 `npm run build` | Vite 8.1.0 生产构建通过 |
| 前端 `npm run test:e2e` | 通过，33 tests / 0 failures |
| 浏览器检查 | 更多说明可展开，字段解释和示例显示正常，控制台无错误 |

## 审计 #14 - 2026-09-12 — 文章编辑器分类标签选项布局

### 范围

- 管理端文章新建/编辑页面的分类和标签多选项。
- 中文标签拆字、选项行数上限和超量分页。

### 根因

- 选项容器允许 Flex 子项收缩，中文标签会被压到单字宽度后逐字换行。
- `.admin-field input { width: 100% }` 同时作用于 checkbox，进一步挤占标签文字空间。

### 实现

- 分类和标签改为自适应网格，每个选项禁止收缩和文字换行，超长名称使用省略号与悬停标题。
- Checkbox 恢复自动宽度，不再继承文本输入框的 `100%` 宽度。
- 每页最多展示 12 项；超过后显示分类或标签分页，桌面通常为 1-2 行，390px 最多 4 行。
- Playwright 覆盖中文不拆字、每页数量和下一页行为。

### 验证

| 验证 | 结果 |
|---|---|
| 前端 `npm test` | 通过，46 tests / 0 failures |
| 前端 `npm run build` | Vite 8.1.0 生产构建通过 |
| 前端 `npm run test:e2e` | 通过，32 tests / 0 failures |
| 浏览器实测 | 真实数据全部单行；压力数据桌面 2 行、390px 4 行并显示分页 |

## 审计 #13 - 2026-09-12 — 全站品牌 Logo 替换

### 范围

- 公开端页头、管理端侧栏和浏览器 favicon。
- Logo 裁切、透明背景、响应式尺寸和亮暗主题显示。

### 实现

- 使用用户手动生成的方形开卷 `U` 字标，按透明通道裁切并生成 `512×512` 与 `64×64` PNG。
- 新增 `public/umo-logo.png` 和 `public/favicon.png`，分别用于公开端、管理端品牌位和浏览器图标。
- 保留桌面、移动端尺寸约束与轻微旋转动效，不修改现有品牌文字和导航结构。
- 更新管理端文章列表桌面视觉基线。

### 验证

| 验证 | 结果 |
|---|---|
| 前端 `npm test` | 通过，46 tests / 0 failures |
| 前端 `npm run build` | Vite 8.1.0 生产构建通过 |
| 前端 `npm run test:e2e` | 通过，31 tests / 0 failures |
| 资源请求 | `umo-logo.png` 与 `favicon.png` 均返回 200 `image/png` |
| 浏览器检查 | 公开端亮色、暗色、390px 和管理端 Logo 显示正常，控制台无错误 |

## 审计 #12 - 2026-09-11 — 分类编辑加载态列宽稳定

### 范围

- 管理端分类列表点击“编辑”后的加载态布局。
- 类型列和操作列在请求期间的位置稳定性。

### 实现

- 操作按钮统一预留 `36px` 宽度并居中显示，避免“编辑”等状态文案切换时撑开最后一列。
- 编辑、删除操作保持文案不变，请求期间通过禁用态和 `aria-busy` 表达加载状态。
- 新增延迟详情接口的 Playwright 回归，断言加载前后按钮文字和类型列横坐标不变。

### 验证

| 验证 | 结果 |
|---|---|
| 前端 `npm test` | 通过，46 tests / 0 failures |
| 前端 `npm run build` | Vite 8.1.0 生产构建通过 |
| 分类管理 Playwright 回归 | 通过，7 tests / 0 failures |
| 浏览器实测 | 编辑请求前后按钮始终显示“编辑”，所有单元格横坐标和按钮宽度保持一致 |

## 审计 #11 - 2026-09-11 — 管理端分类层级对齐

### 范围

- 管理端分类列表的父级、子级名称和 slug 对齐。
- 分类层级视觉标记及浏览器布局回归。

### 实现

- 分类名移除基于深度的动态 `padding-left`，父级和子级名称统一从相同横坐标开始。
- 使用固定宽度的 `·` / `└` 标记表达根分类和子分类关系，slug 与分类名左边缘对齐。
- `e2e/admin.spec.js` 增加分类名称横坐标一致性断言，防止后续布局回归。

### 验证

| 验证 | 结果 |
|---|---|
| 前端 `npm test` | 通过，46 tests / 0 failures |
| 前端 `npm run build` | Vite 8.1.0 生产构建通过 |
| 分类管理 Playwright 回归 | 通过，6 tests / 0 failures，包含横坐标一致性断言 |
| 浏览器实测 | 10 条分类的名称与 slug 横坐标一致，控制台无错误 |

### 剩余风险

1. 当前层级标记只区分“根分类”和“子分类”；若未来支持三层以上，需要增加明确的深度或父级信息。

## 审计 #10 - 2026-09-11 — Playwright 浏览器 E2E 与视觉回归

### 范围

- 公开端首页、书库、搜索、详情、错误态和 390px 布局。
- 在线编辑器草稿、导入确认、下载、安全预览和移动端编辑/预览切换。
- 管理端认证、文章生命周期、分类/标签 CRUD、站点设置和改密会话失效。
- 首页亮暗主题、书库、详情、编辑器、登录和管理列表的桌面与 390px 视觉基线。

### 实现

- 新增 Playwright 1.63 与 `functional`、`visual-desktop`、`visual-mobile` 三个本地项目。
- 使用本机 Chrome channel 和 Vite preview，不下载 Playwright 独立浏览器。
- 新增状态化 Mock API fixture，覆盖现有公开端与管理端 API 契约，测试间状态隔离。
- 功能测试可直接自动化原生 confirm、Blob download、localStorage 草稿与 429 倒计时。
- 视觉测试固定 `zh-CN`、`Asia/Shanghai`、单 worker、减少动态偏好和稳定截图参数。
- 修复 lockfile 中的 `postcss` / `nanoid` 传递依赖漏洞。

### 验证

| 验证 | 结果 |
|---|---|
| 前端 `npm test` | 通过，46 tests / 0 failures |
| 前端 `npm run build` | Vite 8.1.0 生产构建通过 |
| `npm run test:e2e` | 通过，16 functional + 14 visual |
| 视觉稳定性 | 14 张基线连续两次比较通过 |
| `npm audit` | 0 vulnerabilities |

### 剩余风险

1. 浏览器 E2E 使用 Mock API，不验证 Spring Boot、MySQL、Mapper SQL 或文件系统组合行为。
2. 视觉基线绑定当前 Windows 与本机 Chrome；Linux 或浏览器升级后需要重新生成和审查基线。
3. 尚未接入 GitHub Actions 或其他 CI，测试仍需本地显式执行。

## 审计 #9 - 2026-09-11 — 公开在线编辑器

### 范围

- `/editor` 纯浏览器 Markdown 编辑、预览、导入、下载、清空和本地草稿恢复。
- 编辑器文件/草稿纯函数、安全 Markdown 预览、桌面与 390px 响应式样式。
- 前端单元测试、生产构建、亮暗主题和浏览器交互验证。

### 实现

- 新增 `src/utils/editor.js`，统一文件名安全化、`.md` 扩展名、Markdown 文件识别、v1 草稿解析/序列化和 UTF-8 Blob。
- `EditorPage.vue` 使用原生 textarea 和现有 `MarkdownArticle`，桌面分屏、窄屏编辑/预览切换，不调用后端 API。
- 草稿写入 `localStorage["umo-editor-draft-v1"]`，结构为 `{ version, content, fileName, updatedAt }`；300ms 防抖保存并在路由离开/`pagehide` 前刷新。
- 损坏或旧版草稿被忽略并清理；存储失败保留当前内存内容并显示错误，离页前要求确认。
- 内容非空时导入 `.md` 前确认替换；下载文件名补充 `.md`；清空同步删除本地草稿。
- 预览复用现有 marked/highlight.js 安全渲染，原始 HTML 被转义，危险 URL 协议被降级。

### 验证

| 验证 | 结果 |
|---|---|
| 前端 `npm test` | 通过，46 tests / 0 failures |
| 前端 `npm run build` | Vite 8.1.0 生产构建通过 |
| 桌面浏览器 | 编辑/预览同步、代码高亮、刷新恢复、导入替换路径通过 |
| 安全预览 | `<script>` 不进入 `innerHTML`，危险链接无 `href` |
| 390px 浏览器 | 编辑/预览切换、工具栏、亮暗主题和无横向溢出通过 |
| Markdown 下载 | UTF-8 Blob 与文件名规则通过单元测试；in-app browser 未派发自动化 download 事件 |
| 导入确认 | 替换流程已执行；in-app browser 未捕获原生 confirm 对话框 |

### 剩余风险

1. 浏览器验证仍为手工编排，不是可重复的 E2E 工程。
2. 当前 in-app browser 无法稳定观测原生 confirm 和 Blob download 事件，缺少对应浏览器自动化断言。
3. 桌面和移动端视觉回归尚未工程化。

## 审计 #8 - 2026-09-11 — 管理端业务闭环

### 范围

- 管理端分类、标签、站点设置和修改密码页面。
- 管理表单校验、分类父级防循环、配置部分保存反馈和凭据类 401 会话处理。
- 前端单元测试、生产构建和桌面/390px 浏览器检查。

### 实现

- 分类管理增加类型筛选、树形列表、父级和排序字段、增改删、未保存保护及关联内容/子分类删除提示。
- 标签管理增加列表增改删、slug/name 校验、重复冲突和关联内容删除提示。
- 站点设置增加四项配置的统一读取与按变更项顺序保存、Markdown 预览、部分失败清单和 `site` store 强制刷新。
- 新增 `/secret-admin/password` 页面；改密成功后删除 token，并在登录页提示“密码已修改，请重新登录”。
- Axios 仅在业务请求 401 时清理会话；登录和“旧密码错误”保留在对应页面展示，改密接口的 token 失效错误仍会清理会话。
- 审查修复：分类编辑改用详情接口读取 `parentId`、`sortOrder`，避免树 VO 缺字段导致编辑时移动到根分类并重置排序。
- 审查修复：Markdown 链接和图片仅允许 HTTP、HTTPS、Mailto 及相对 URL，阻止 `javascript:`、`data:` 等协议。
- 审查修复：Markdown 图片 alt 属性执行 HTML 转义，并让新建分类动作取消未完成的详情请求。
- 审查修复：分类、标签和站点表单在保存期间禁用输入，分类筛选增加未保存确认和请求序列保护，避免保存竞态和旧响应覆盖。

### 验证

| 验证 | 结果 |
|---|---|
| 前端 `npm test` | 通过，41 tests / 0 failures |
| 前端 `npm run build` | Vite 8.1.0 生产构建通过 |
| 分类管理桌面 | 树形层级、类型筛选、编辑父级候选排除自身/后代通过 |
| 标签管理桌面 | 列表加载和重复 slug 的 409 提示通过 |
| 分类管理 390px | 页面无横向溢出，表格使用横向滚动 |
| 站点设置桌面/390px | 四项配置加载、Markdown 编辑/预览、移动标签切换和统一保存通过 |
| 公开端缓存刷新 | 保存站点标题后首页品牌与页脚使用新标题 |
| 修改密码页面 | 路由、表单与登录成功提示通过；实际密码提交未在浏览器执行 |

### 剩余风险

1. 浏览器验证使用临时 mock API 和手工步骤，仍不是可重复的 E2E 工程。
2. 修改密码的真实 token 失效流程已有后端测试覆盖，但本次未在图形界面执行最终提交。
3. 公开在线 Markdown 编辑器仍未实现。

## 审计 #7 - 2026-09-11 — 管理端文章管理闭环

### 范围

- 管理端登录路由、响应式布局和文章列表/编辑器。
- `ContentListVO.status` 增量契约及真实接口冒烟断言。
- 前端表单规则、图片插入、未保存保护和桌面/移动浏览器验证。

### 实现

- 登录页改为独立顶层路由，不再嵌套在 `AdminLayout` 内。
- 管理布局使用纸本主题，桌面常驻侧栏，900px 以下使用顶部菜单和抽屉导航。
- 文章列表支持类型、状态、分类、标签和排序筛选，分页、删除、空数据、错误和成功提示。
- 文章编辑器支持新建/编辑、分类标签、metadata 校验、Markdown 分屏预览和移动端编辑/预览切换。
- 图片支持选择、拖拽、粘贴上传，并在 textarea 当前光标处插入 Markdown；客户端预检类型和 50MB 上限。
- 使用路由离开守卫和 `beforeunload` 保护未保存内容。
- `ContentListVO` 新增 `status`，由 `ContentVOMapper` 填充；不修改路径、状态码或数据库结构。
- 修复图片静态资源路径，使 `${app.storage-path}/images/` 转换为实际绝对 URI。

### 修复

| # | 级别 | 问题 | 修复 |
|---|---|---|---|
| 1 | P2 | 管理端列表无法可靠区分草稿和发布后撤回的草稿，因为响应没有 `status`。 | 为共享 `ContentListVO` 增加 `status`，公开查询固定为 `PUBLISHED`。 |
| 2 | P2 | 登录页位于 `AdminLayout` 子路由中，访问登录页会同时渲染管理导航。 | 将登录页提升为独立顶层路由。 |
| 3 | P2 | 管理布局固定宽侧栏，在移动端不可用。 | 增加移动顶部栏、抽屉导航和遮罩关闭。 |
| 4 | P2 | `/images/**` 返回 500，资源处理器把 `${app.storage-path}` 当成字面量。 | 注入存储路径并生成规范化绝对 URI。 |

### 验证

| 验证 | 结果 |
|---|---|
| 后端完整 `mvn test` | 通过，79 tests / 0 failures / 0 errors |
| 前端 `npm test` | 通过，29 tests / 0 failures |
| 前端 `npm run build` | Vite 8.1.0 生产构建通过 |
| 隔离 MySQL 5.7 + `api-smoke.ps1` | 通过，27/27；规则：公开只返回 PUBLISHED，管理端返回 DRAFT/PUBLISHED |
| 桌面浏览器 | 登录、筛选、创建草稿、编辑发布、删除、metadata 校验、Markdown 预览、图片上传插入通过 |
| 390px 浏览器 | 移动菜单、筛选布局、文章表格横向浏览、编辑/预览切换通过 |
| 图片静态资源 | 现有存储图片请求返回 200 `image/png` |

### 剩余风险

1. 浏览器验证仍为手工编排，不是可重复执行的 E2E 工程。
2. 图片上传通过后没有删除接口，联调测试会在测试存储和 images 表留下记录。
3. 分类、标签、站点设置和修改密码页面仍未实现。
4. 公开在线 Markdown 编辑器仍未实现。

## 审计 #6 - 2026-09-11 — 公开端真实 API 闭环

### 范围

- 后端公开详情前后文章契约。
- 前端首页、书库、搜索、详情、About、Project 的公开 API 联调。
- 加载、空数据、错误、404、429、离线无兜底和响应式验证。

### 实现

- `ContentDetailVO` 新增 `previous`、`next`；邻居只含 `id/title/slug/publishedAt`。
- `ContentMapper` 新增两个定点查询，只处理 `PUBLISHED`，按发布时间和 ID 稳定排序。
- 前端移除 `src/demo/content.js`、`src/demo/catalog.js` 及其测试，新增查询规范化、错误解析、日期格式和公共状态组件。
- 书库由后端执行精确筛选和分页；搜索显式提交并处理 429；详情直接渲染后端前后文章。
- 首页并发读取最新内容与三类总数；About/Project 分别读取配置页接口。

### 验证

| 验证 | 结果 |
|---|---|
| 后端完整 `mvn test` | 通过，77 tests / 0 failures / 0 errors |
| 前端 `npm test` | 通过，21 tests / 0 failures |
| 前端 `npm run build` | Vite 8.1.0 生产构建通过 |
| 隔离 MySQL 5.7 + 临时 Markdown 存储 | schema/seed 初始化通过 |
| `api-smoke.ps1` | 通过，27/27；详情邻居顺序通过 |
| 桌面浏览器 | 首页、筛选、搜索 429、详情导航、About、Project、404 通过 |
| 390px 浏览器 | 首页、书库、详情无横向溢出，移动导航通过 |
| 后端离线 | API 错误态和重试可见，无静态 fixture 回退 |

### 剩余风险

1. 浏览器验证仍为手工编排，不是可重复执行的 E2E 工程。
2. 详情为两个额外邻居查询，当前公告规模可接受；数据量显著增长后可再评估合并 SQL。
3. 搜索/登录限流仍是单实例内存状态。
4. 在线编辑器和管理端业务页仍未实现。

## 审计 #5 - 2026-09-11 — 公开端静态视觉 MVP

### 范围

- 前端公开端布局、首页、书库、文章详情、About 和 404。
- 双主题、静态 fixtures、Markdown/代码高亮和公开端动效。
- 前端 Node 测试、生产构建及桌面/移动浏览器检查。

### 实现

- 新增“当代古籍纸本”视觉 tokens、公开端页头页脚、内容卡片和主题切换。
- 新增 `light | dark` 双主题，写入 `data-theme` 并持久化到 `umo-theme`。
- 首页、书库、文章详情、About 和 404 使用静态数据完成视觉 MVP。
- 书库实现类型、分类、标签的本地筛选和分页，URL 同步筛选参数。
- Markdown 禁用原始 HTML，代码高亮按需注册 Java、JavaScript、SQL 和 Bash。
- 公开展示页支持电影化入场与滚动揭示；正文与减少动态偏好使用克制版本。
- 路由切换和主题色幕布退场统一为水平方向，并根据主路径层级区分前进与返回。

### 修正

| # | 级别 | 问题 | 修复 |
|---|---|---|---|
| 1 | P2 | 书库内容卡片默认 `opacity: 0`，不是所有布局都会触发首页入场动画，导致结果卡片不可见。 | 为书库卡片增加 `v-reveal`，由 IntersectionObserver 在进入视口时揭示。 |
| 2 | P3 | 完整 `highlight.js` 导入产生约 956 KB chunk 和构建体积警告。 | 改用 `highlight.js/lib/core` 并注册当前使用的语言，chunk 降至约 80 KB。 |

### 验证

| 验证 | 结果 |
|---|---|
| 前端 `npm test` | 通过，15 tests / 0 failures |
| 前端 `npm run build` | 通过，无 chunk 体积警告 |
| 桌面浏览器 | 首页、书库筛选、文章详情、About、404 和亮暗主题通过 |
| 390px 浏览器 | 首页、移动导航、书库卡片、文章详情、About 和 404 通过 |

### 剩余风险

1. 当前仅使用静态 fixtures，尚未接入现有 8 个公开 API。
2. 搜索、Project、在线编辑器和管理端仍为占位或原有实现。
3. 尚未建立可重复执行的浏览器 E2E 和视觉回归。

## 审计 #1 - 2026-09-10

### 范围

- 后端 Controller、DTO、VO、Service、Mapper XML、Config。
- 前端路由、API、Store、页面和 `package.json`。
- `docs/` 下全部文档。
- 后端边界测试和前端生产构建。

### 发现

| # | 严重度 | 类别 | 文件/模块 | 描述 | 状态 |
|---|---|---|---|---|---|
| 1 | 高 | 数据完整性 | `CategoryManageServiceImpl.delete` | 把分类 ID 当作内容 ID 调用 `findCategoryIdsByContentId`，分类删除保护不能可靠工作。 | 审计 #2 已修复 |
| 2 | 高 | 数据完整性 | `TagManageServiceImpl.delete` | 把标签 ID 当作内容 ID 调用 `findTagIdsByContentId`，标签删除保护不能可靠工作。 | 审计 #2 已修复 |
| 3 | 高 | 文件安全 | `FileUtil` + 内容保存 | `slug`、`bookSlug` 未检查路径穿越，最终路径可能越出 storage root。 | 审计 #2 已修复 |
| 4 | 中 | 响应语义 | `ContentServiceImpl.getBySlug` | 公开详情未组装分类和标签，接口实际返回 `null`。 | 审计 #2 已修复 |
| 5 | 中 | 事务一致性 | `ContentManageServiceImpl.update` | 先删除旧文件再写新文件，数据库事务无法回滚文件系统。 | 审计 #2 已修复 |
| 6 | 中 | 业务语义 | `ContentManageServiceImpl.resolveBookSlug` | 小说目录使用第一个分类 slug，未校验书级分类。 | 审计 #2 已修复 |
| 7 | 中 | 参数校验 | `ContentQuery` | `page`、`size` 没有边界校验，`getOffset()` 可产生负偏移。 | 审计 #2 已修复 |
| 8 | 中 | 限流 | `RateLimitInterceptor` | IP 记录不清理；无条件信任 `X-Forwarded-For`。 | 审计 #2 已修复 |
| 9 | 中 | 数据库 | `schema.sql` | 无外键和级联约束，Service 漏删会产生孤儿记录。 | 审计 #2 已修复 |
| 10 | 中 | 前端能力 | 多个 Vue 页面 | 文档曾把页面写成已实现，实际除登录/布局/404 外均为占位。 | 文档已修正，功能待实现 |
| 11 | 中 | 接口契约 | `api-reference.md` 等 | 旧文档错误描述正常响应包装、201 状态码、全文搜索和子分类筛选。 | 文档已修正 |
| 12 | 低 | 配置 | `app.admin-path` | 配置项未使用，前端硬编码 `/secret-admin`。 | 审计 #2 已修复 |
| 13 | 低 | 上传校验 | `ImageServiceImpl` | 只检查客户端 MIME，不检查文件签名。 | 审计 #2 已修复 |
| 14 | 低 | 爬虫控制 | `index.html`/`public` | 有 `noindex`，没有 `robots.txt`。 | 待修复 |
| 15 | 低 | 测试 | 后端测试 | `UmoWebApplicationTests` 是空测试；没有真实 MySQL/文件系统集成测试。 | 部分修复，真实 MySQL 仍待补充 |

### 已确认的正确事实

- 公开端 8 个接口，管理端 19 个接口。
- 正常响应直接返回数据，不套 `{ code, data }`。
- 所有新建接口成功返回 200。
- 删除、修改密码、更新配置成功返回 204。
- 搜索只匹配 `title` 和 `summary`。
- `categoryId` 精确匹配，不自动包含子分类。
- 图片最大 50MB。
- 前端技术栈为 Vue 3 + Vite 8 + Vue Router 5 + Pinia 3。
- 公开详情当前分类和标签为 `null`，不能按列表响应结构推断。

### 验证记录

| 验证 | 结果 |
|---|---|
| `npm run build` | 通过，Vite 8.1.0 构建成功 |
| `mvn test` | 通过。使用隔离临时 Maven settings，21 个测试全部通过 |
| 真实 MySQL | 未执行 |
| 浏览器 E2E | 未执行 |

### 后续处理建议

1. 修正分类和标签删除保护的查询。
2. 为 Markdown 和 slug 路径增加 storage root 边界检查。
3. 统一文件与数据库失败处理策略。
4. 增加 `page`、`size` 校验。
5. 增加真实 Mapper 集成测试。
6. 完成前端页面后补浏览器回归。

## 审计 #2 - 2026-09-11

### 范围

- 前端公开路由守卫和管理路径配置。
- 内容 Markdown 文件与数据库一致性。
- 分类/标签删除保护、路径穿越、上传签名。
- JWT tokenVersion、登录限流、搜索限流可信代理。
- 分页/枚举/metadata 校验、公开详情组装、列表批量查询。
- SQL 索引、外键和旧库兼容迁移。

### 修复

| # | 级别 | 结果 |
|---|---|---|
| 1 | P0 | 公开路由仅在 `requiresAuth === true` 时要求 token；登录页、404 和无 meta 公开路由可访问。 |
| 2 | P0 | 创建拒绝覆盖已有 Markdown；更新使用临时文件、原子替换、备份恢复和提交后清理；删除采用数据库优先策略。 |
| 3 | P1 | 分类/标签按各自 ID 统计关联并返回 409；有子分类的父分类禁止删除；保存文章验证关联目标存在。 |
| 4 | P1 | slug/path 使用安全字符、normalize、storage root 边界检查；上传校验 MIME + 文件签名并使用固定扩展名。 |
| 5 | P1 | `dev` 明确允许默认凭据，`prod` 遇到默认 JWT secret/管理员密码拒绝启动；日志不输出密码或 secret。 |
| 6 | P1 | JWT 包含 tokenVersion；改密递增版本使旧 token 失效；登录失败按 username+IP 限流。 |
| 7 | P1 | 搜索限流使用原子窗口更新和过期清理，仅信任配置的代理，不再无条件信任 XFF。 |
| 8 | P2 | page/size、内容和分类/标签枚举、metadata JSON、长度和 slug 校验；非法输入返回 400。 |
| 9 | P2 | 公开详情与列表共享 `ContentVOMapper`，返回 categories/tags；列表按 contentIds 批量查询，消除 N+1。 |
| 10 | P2 | `schema.sql` 增加索引、外键和级联策略；新增兼容迁移清理孤儿并补列/索引/外键，无法添加时明确失败。 |
| 11 | 可维护性 | 管理分类/标签接口返回 VO；前端 `changePassword` API 已补；管理路径改为 `VITE_ADMIN_PATH`。 |

### 验证

| 验证 | 结果 |
|---|---|
| 后端完整 `mvn test` | 通过，64 tests / 0 failures / 0 errors |
| 前端 `npm run test:router` | 5 个路由守卫用例通过 |
| 前端 `npm run build` | Vite 8.1.0 生产构建通过 |
| `git diff --check` | 通过，仅有 Windows LF/CRLF 提示 |
| 敏感信息扫描 | 未发现真实 secret；命中的 `admin123`、JWT 默认值和 `sk-dummy-placeholder` 均为开发占位值 |
| 真实 MySQL 迁移 | 未执行，仓库无可用 MySQL 测试库 |

### 剩余风险

1. 未执行真实 MySQL 集成测试和迁移演练；生产执行前必须备份并先在副本验证。
2. 限流和登录失败计数仍是单实例内存状态，多实例部署需要 Redis 或网关共享限流。
3. 既有 Markdown 孤儿文件不会被迁移脚本自动扫描，文件删除失败只记录日志。

## 审计 #3 - 2026-09-11

### 复审发现与修复

| # | 级别 | 问题 | 结果 |
|---|---|---|---|
| 1 | P2 | `VITE_ADMIN_PATH` 只在路由根路径生效，登录跳转和多个管理链接仍硬编码 `/secret-admin`。 | 新增统一 `ADMIN_PATH` 工具，路由、登录页、布局和文章列表共用。 |
| 2 | P2 | 极大 `page` 与 `size` 相乘会整数溢出，导致负 offset 或 SQL 异常。 | page 限制为 1-1000000，offset 使用 long 计算并返回 400。 |
| 3 | P2 | metadata 为空白字符串时会写入 MySQL JSON 列并失败。 | 创建/更新统一将空白 metadata 规范化为 `null`。 |
| 4 | P2 | 分类更新可设置自身或祖先为父级，形成循环并导致树数据异常。 | 增加父级存在性和循环检查。 |
| 5 | P2 | 登录失败 key 区分大小写和首尾空格，可绕过失败次数限制。 | username 规范化后再作为限流 key 和查询条件。 |
| 6 | P3 | CORS 来源写死 localhost，生产部署无法通过配置切换。 | 增加 `app.cors.allowed-origins` / `CORS_ALLOWED_ORIGINS` 并测试。 |

### 验证

| 验证 | 结果 |
|---|---|
| 后端完整 `mvn test` | 通过，72 tests / 0 failures / 0 errors |
| 前端 `npm run test:router` | 7 个路由/管理路径用例通过 |
| 前端 `npm run build` | Vite 8.1.0 生产构建通过 |

### 剩余风险

1. 分类循环防护为 Service 检查，并发更新仍应结合数据库事务锁或更严格的层级模型。
2. 未执行真实浏览器 E2E 和真实 MySQL 迁移演练。
3. 限流状态仍为单实例内存实现。

## 审计 #4 - 2026-09-11 — 数据库迁移副本演练与全接口冒烟

### 环境

- 使用独立临时 MySQL 5.7 实例，端口 `3307`，未连接或修改系统 `3306` 上的真实库。
- 迁移前库由提交 `8369ac0` 的旧版 `schema.sql` 和当前种子数据建立。
- 额外注入 4 条孤儿关联和 1 条悬空父分类，用于验证兼容脚本的清理逻辑。
- 后端连接还原后的 `umo_blog_copy`，运行端口 `18080`，存储目录使用独立临时目录。

### 启动阻断与修复

| # | 级别 | 问题 | 修复 |
|---|---|---|---|
| 1 | P1 | Mapper XML 使用 `ContentCategoryLink` / `ContentTagLink` 简单别名，但 MyBatis 只扫描 entity 包，完整 Context 无法启动。 | 别名扫描扩展到 entity 和 dto，并新增 `MapperConfigurationTest`。 |
| 2 | P1 | `ClientIpResolver` 有两个构造器且生产构造器未标注注入，Spring 找不到默认构造器。 | 生产构造器显式增加 `@Autowired`，并新增容器装配测试。 |
| 3 | P1 | 业务代码注入 Jackson 2 `com.fasterxml` ObjectMapper，但 Spring Boot 4 自动配置提供的是 Jackson 3 `tools.jackson` ObjectMapper。 | 主代码和测试统一迁移到 Jackson 3，并新增自动配置容器测试。 |

### 迁移副本结果

| 验证 | 结果 |
|---|---|
| 逻辑备份 | 12,881 字节，SHA-256 `70C8E7E33DB2815EFF5BB17EE1E5FA620AB594BE4E81F2D013E023A96E4C7C9A` |
| 副本还原 | `umo_blog_copy` 包含 8 张表 |
| 首次迁移 | 成功新增 `users.token_version`、3 个索引、5 个外键 |
| 第二次迁移 | 成功，无重复列/索引/外键，验证幂等 |
| 数据清理 | 孤儿分类关联 2→0，孤儿标签关联 2→0，悬空父分类 1→0 |
| 数据保留 | users/categories/tags/contents/content_category/content_tag/images/site_options = `0/11/9/6/9/12/0/4` |
| 源库隔离 | 源库仍无 `token_version`，迁移只作用于副本 |
| 外键动作 | 2 个内容外键为 CASCADE，3 个分类/标签外键为 RESTRICT |

### 全接口冒烟

复用脚本 `Server Side/UmoWebBackend/scripts/api-smoke.ps1`，公开端 8 个和管理端 19 个接口全部通过，
结果为 `27/27`。

额外验证：

- 管理端无有效 JWT 返回 401。
- 修改密码返回 204，旧 token 随即返回 401。
- 搜索首次返回 200，10 秒内重复请求返回 429。
- 1×1 PNG 通过 MIME + 文件签名校验并返回可访问 URL。
- 测试分类、标签和草稿文章均已清理，`site_title` 和管理员密码已恢复。

### 验证记录

| 验证 | 结果 |
|---|---|
| 后端完整 `mvn test` | 通过，75 tests / 0 failures / 0 errors |
| 真实 MySQL 迁移副本 | 通过，连续执行两次 |
| 全接口冒烟 | 通过，27/27 |
| `git diff --check` | 通过，仅有 Windows LF/CRLF 提示 |

### 剩余风险

1. 真实 MySQL 验证仍是手工编排的冒烟，不是 CI 自动集成测试。
2. 测试图片上传后没有删除接口，因此会在冒烟使用的测试存储中留下 1 条图片记录和文件。
3. 搜索/登录限流仍是单实例内存状态。
