# UmoWeb 接口与构建测试指南

> 基线日期: 2026-09-18
> 接口数: 公开 8 个，管理 32 个，共 40 个
> 关键约定: 正常响应没有 `{ code, data }` 包装层

---

## 1. 环境准备

### 1.1 运行环境

当前开发机可用：

| 工具 | 版本 |
|---|---|
| Java | 21.0.6 |
| Maven | 3.9.11 |
| Node.js | 24.12.0 |
| npm | 11.6.2 |
| Playwright Test | 1.63.0 |

后端 `pom.xml` 的编译目标仍是 Java 17。

### 1.2 环境变量

```powershell
$env:DB_USER = "root"
$env:DB_PASS = "<本机 MySQL 密码>"
```

开发默认 profile 为 `dev`，允许项目自带默认 JWT/管理员值以便本地启动。生产必须：

```powershell
$env:SPRING_PROFILES_ACTIVE = "prod"
$env:JWT_SECRET = "<至少 32 字符的独立 secret>"
$env:INIT_ADMIN_USER = "<管理员用户名>"
$env:INIT_ADMIN_PASS = "<强管理员密码>"
$env:CORS_ALLOWED_ORIGINS = "https://<正式域名>"
```

AI 默认关闭。只有需要真实验证时才设置：

```powershell
$env:APP_AI_ENABLED = "true"
$env:DEEPSEEK_BASE_URL = "https://api.deepseek.com"
$env:DEEPSEEK_API_KEY = "<仅本地或服务器侧密钥>"
$env:DEEPSEEK_MODEL = "<当前模型标识>"
```

CI 不读取真实密钥；DeepSeek 协议和错误分类由 Mock HTTP Server 验证。

### 1.3 数据库

```powershell
mysql -u root -p umo_blog
```

```sql
SOURCE docs/design/schema.sql;
SOURCE docs/design/seed-data.sql;
```

执行路径需按当前工作目录调整。

### 1.4 启动后端

```powershell
cd "Server Side\UmoWebBackend"
mvn spring-boot:run
```

如果机器级 Maven `settings.xml` 的仓库路径不可写，应通过 `-gs` 和 `-s` 指定隔离的临时 settings 文件。

项目自带 `mvnw.cmd`，但当前 Windows/PowerShell 环境的 wrapper 启动曾失败；优先使用系统 `mvn`。

开发环境 `users` 表为空时，后端会根据 `app.init.*` 自动创建管理员，默认：

```text
username: admin
password: admin123
```

不需要手动插入固定 BCrypt hash。

### 1.5 启动前端

```powershell
cd "Client Side\umo-web-frontend"
npm install
npm run dev
```

访问 `http://localhost:5173`。

---

## 2. 自动化构建

### 2.1 后端

```powershell
cd "Server Side\UmoWebBackend"
mvn test
```

当前完整测试共 237 个，包含 `BoundaryTest`、文件/路径工具、VO 批量组装、JWT、
Mapper XML 别名解析、构造器注入、Jackson 自动配置、拦截器、登录限流、分类层级解析、
正文索引、摘要提取、图片清理、图片一致性、AI 模式目录、DeepSeek Provider、请求限流和
结果保真校验测试。
其中 17 个真实 MySQL 测试由 `MYSQL_INTEGRATION=true` 启用，本地默认跳过；MockMvc 边界测试
不连接 MySQL，`UmoWebBackendApplicationTests` 仍是一条空测试。

### 2.2 数据库迁移副本 + 全接口冒烟

2026-09-11 使用独立临时 MySQL 5.7 实例完成真实副本演练：

- 使用提交 `8369ac0` 的旧版 `schema.sql` 建立 8 张表和 6 篇内容。
- 额外注入 4 条孤儿关联和 1 条悬空父分类，验证迁移清理路径。
- `mysqldump` 生成 12,881 字节备份，SHA-256 为
  `70C8E7E33DB2815EFF5BB17EE1E5FA620AB594BE4E81F2D013E023A96E4C7C9A`。
- 将备份还原为 `umo_blog_copy`，仅对副本执行兼容迁移，并连续执行两次验证幂等性。
- 迁移后 `token_version` 1 个、新增索引 3 个、新增外键 5 个；孤儿关联和悬空父级均为 0。
- 源库仍无 `token_version`，证明演练没有触碰源库。

后端连接 `umo_blog_copy` 启动后，执行：

```powershell
cd "Server Side\UmoWebBackend"
.\scripts\api-smoke.ps1 `
  -BaseUrl "http://127.0.0.1:18080" `
  -Username "smoke_admin" `
  -Password "<current-password>"
```

脚本覆盖公开端 8 个和管理端 23 个接口，结果为 `31/31` 通过；同时验证：

- 无有效 JWT 的管理端请求返回 401。
- 修改密码返回 204，旧 token 立即失效。
- 搜索首次返回 200，10 秒内重复请求返回 429。
- 详情 `previous` 为更早文章、`next` 为更新文章，首尾边界为 `null`。
- PNG 上传同时通过 MIME 和文件签名校验。
- 图片列表能识别未引用状态；被草稿引用时删除返回 409，解除引用后删除返回 204，
  文件不可再通过 `/images/**` 访问。
- 图片一致性接口能定位临时断裂引用，并返回稳定的三类数组和来源信息。
- 公开列表只返回 `PUBLISHED`；管理列表和详情同时暴露 `DRAFT` 与 `PUBLISHED` 状态。
- 测试创建的分类、标签、草稿文章和临时图片全部删除，密码和 `site_title` 恢复原值。

脚本运行前要求后端已启动并使用真实 MySQL。脚本会临时修改管理员密码和 `site_title`，
最后恢复；图片上传、引用保护、删除和磁盘清理均由脚本回收。

2026-09-13 起，同一链路已由 GitHub Actions 的 `mysql-integration` job 和
`scripts/ci/mysql-integration.sh` 自动化：

- 使用 MySQL 8.4 从空库执行 `schema.sql`、`seed-data.sql` 和全部兼容迁移脚本。
- 兼容迁移连续执行两次并要求幂等；校验 `token_version`、图片清理队列表、正文索引表、
  3 个既有索引、6 个外键、
  种子行数和迁移后孤儿关系为 0。
- 在真实库执行分类、正文搜索、相关文章和图片管理集成测试，覆盖根/子/孙内容、精确/后代模式、
  管理端草稿、空结果、稳定排序、循环拒绝、中文 ngram 查询、索引幂等、
  相关文章权重与排除规则、调度发布、图片排序、清理队列失败记录，以及 AI 默认模式、
  停用过滤、条件版本更新和级联删除。
- 复制演示 Markdown 后连续执行两次正文回填脚本，校验索引行数等于已发布内容数，
  并验证正文全文和标题/摘要搜索。
- 使用 Java 17 构建并启动后端，使用独立临时存储和运行时测试凭据执行 `api-smoke.py`。
- 默认兼容冒烟仍为 31/31，断言覆盖公开筛选、详情分类/标签、前后文章、相关文章、草稿/待发布隔离、密码失效、
  批量文章操作、图片完整生命周期、图片一致性来源查询和 429。
- AI 开启模式使用 `--include-ai`，新增 9 个管理端 AI 接口并报告 40/40；实际 HTTP 请求数
  只作为独立诊断输出，不与接口数混淆。AI 场景覆盖五个默认模式、模式创建、提示词版本、
  旧版本冲突、复制停用、回滚生成新版本、能力查询、假 Provider 正文回显和停用后 409。
- 冒烟通过后校验图片记录、清理队列与临时存储文件；job 退出时销毁后端进程、测试数据和临时文件。

### 2.3 前端

```powershell
cd "Client Side\umo-web-frontend"
npm run build
npm test
npm run test:e2e
```

2026-09-18 已验证：

- Vite 8.1.0 前端生产构建成功。
- 前端 120 个 Node 测试通过，覆盖路由、管理路径、主题、访问隐私配置、游戏规则与旧成绩解析、
  管理端文章/分类/标签/图片/站点/改密规则、编辑器草稿与文件规则、Markdown front matter 导入、
  API 错误解析、日期格式、书库后代参数、文章目录树/展开状态、标题 ID 与旧锚点兼容、AI 模式表单与
  AI 抽屉本地状态/字符边界、Markdown 安全、邻接正文的加粗和编辑器双向滚动比例。
- Playwright 119 个浏览器检查，其中 85 个 functional 用例覆盖公开端、文章目录、阅读进度与相关阅读、
  图片一致性检查、隐私说明、工具中心、在线编辑器与三处编辑工作区滚动协同、
  四款游戏（含高密度网格、长数字、10 张牌、旧成绩和响应式场景）、AI 模式卡片与策略说明、
  AI 抽屉模式控件、管理端同步滚动开关、移动菜单焦点与横屏滚动、隐藏文件输入和管理端核心流程，
  34 个视觉断言覆盖 17 个核心页面状态的桌面与 390px 基线。
- 浏览器 E2E 通过可控 Mock API 运行，不依赖 MySQL 或 Spring Boot；真实接口由第 2.2 节的
  MySQL 副本、`api-smoke.py`/`api-smoke.ps1` 和第 2.8 节的 CI 集成 job 验证。

2026-09-15 已验证：

- Windows 本机 Chrome 当前运行 79 个 Playwright 检查，28 张 `win32` 视觉快照通过。
- GitHub Actions Ubuntu 使用 Playwright 1.63.0 的 Chromium 运行同样的 79 个检查，
  通过独立的 `linux` 视觉快照验证。
- `browser` job 失败时会保留 Playwright HTML 报告、trace 和失败截图 artifact。

管理端 Markdown 导入用例覆盖：YAML front matter 与无 front matter 回退、字段错误、相对图片警告、
非法 YAML 不覆盖当前表单、创建 payload 落库以及重复 slug 留在编辑器修正。导入器只预填现有表单，
不新增 API，真实数据库与 Markdown 文件一致性仍由文章创建接口的服务层测试和 MySQL 冒烟覆盖。

### 2.4 Playwright 浏览器回归

```powershell
cd "Client Side\umo-web-frontend"
npm run test:e2e
```

执行流程：

1. 先运行 `npm run build` 生成生产构建。
2. `e2e/runPlaywright.js` 在 `http://127.0.0.1:4173` 启动轻量 Node 静态服务器并运行 Playwright；测试结束后关闭服务器，避免 Windows 上 Vite preview 残留进程。
3. Windows 默认使用本机稳定版 Chrome channel，Linux CI 使用锁定 Playwright 版本的
   Chromium；可通过 `PLAYWRIGHT_CHANNEL` 显式覆盖。
4. 浏览器级路由拦截 `/api/**`，每个测试使用独立的状态化 Mock API。
5. functional 项目覆盖公开阅读、在线编辑器、管理端认证、Markdown 导入与 CRUD、AI 模式设置、
   AI 转换抽屉，以及 390px 布局。
6. visual-desktop 和 visual-mobile 项目比较 34 张页面截图。

更新 Windows 视觉基线：

```powershell
npm run test:e2e:update
```

更新 Linux 视觉基线：

1. 在 GitHub Actions 手动运行 `Playwright Linux Baselines` 工作流。
2. 工作流执行 `npm run test:e2e:update`，只上传 `playwright-linux-visual-baselines` artifact。
3. 使用 `gh run download <run-id> --name playwright-linux-visual-baselines --dir <临时目录>`
   下载 artifact，人工审查新增或变化的 `*-linux.png`，确认页面布局差异符合预期。
4. 将快照提交到 `e2e/visual.spec.js-snapshots/`，随后运行常规 CI 连续验证两次。

Linux 工作流不会自动提交或推送文件。浏览器或 Playwright 升级后必须走同一流程，
不得通过放宽 `maxDiffPixelRatio` 或将失败视觉断言改为 skipped 来让 CI 通过。

运行前端全部验证：

```powershell
npm run test:all
```

视觉基线位于 `e2e/visual.spec.js-snapshots/`，当前包含 34 张 `win32` 和 34 张 `linux`
文件。平台后缀由 Playwright 自动选择，
不互相覆盖。

游戏专项验证：

- Node 测试覆盖试次生成、数字生成与倒序、升级阈值、牌组与目标选择、评级、格式化和三组旧存储。
- Playwright 覆盖四款开始、作答、正确/错误、结果、重开和刷新恢复。
- Stroop 额外覆盖五色与按钮同色、黑色刺激亮暗主题描边和键盘自动重复过滤。
- 相同用例串行检查 320×568、390×844、844×390 横屏、768×1024 和 1440×900。
- 高密度状态额外覆盖 10×10 舒尔特、10 位数字和 10 张牌。
- 减少动态偏好下确认结果彩纸停用，规则计时不受影响。

### 2.5 Docker 全栈

首次启动和日常命令见 [docker-guide.md](docker-guide.md)。核心验证命令：

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\docker-up.ps1
docker compose --env-file .env.docker ps
```

2026-09-12 在本机验证：

- MySQL 8.4、后端和 Nginx 三个容器均达到健康状态。
- 首次数据卷初始化后公开列表返回 5 篇已发布文章，管理端可见 6 篇内容（含 1 篇草稿）。
- 首页、`/library` SPA 深链接和演示文章 Markdown 正文通过 Nginx 正常加载。
- 通过 Nginx 入口执行 `api-smoke.ps1`，27/27 接口通过。
- 站点副标题和文章摘要中文显示正常。
- 2MB PNG 经 Nginx 上传返回 200，后端保存文件大小一致。
- 重启容器但不删除命名卷后，文章、管理员和上传图片仍然存在。

### 2.6 备份与恢复

Bash 单元测试：

```bash
bash scripts/backup/tests/backup-unit.sh
```

覆盖保留数量和 8GiB 容量上限、恢复项目名护栏、外层/内部校验和及导出后校验。

生产或隔离环境的端到端验证顺序：

```bash
/opt/umoweb/scripts/backup/create-backup.sh
/opt/umoweb/scripts/backup/verify-backup.sh /opt/umoweb/backups/umoweb-backup-<时间>.tar.gz
/opt/umoweb/scripts/backup/restore-backup.sh /opt/umoweb/backups/umoweb-backup-<时间>.tar.gz
```

执行恢复栈的 `api-smoke.ps1`，要求 31/31 通过，然后：

```bash
/opt/umoweb/scripts/backup/cleanup-restore.sh umoweb-restore-<时间>
```

2026-09-12 已验证：

- Bash 单元测试全部通过。
- 本地隔离源栈完成一致备份，归档包含 6 个 app 文件、5 篇 Markdown 和完整 8 张表元数据。
- 从空环境恢复后表行数和逐文件 SHA-256 清单一致，恢复栈 27/27 接口冒烟通过。
- ECS systemd 服务手动执行成功，`frontend`、`backend`、`mysql` 在备份后全部恢复为 healthy。
- 将 ECS 归档下载到开发机后可跨主机恢复，恢复栈再次执行 27/27 通过。

### 2.7 正式内容候选包

内容导入与锚点迁移工具测试：

```powershell
python -m unittest discover -s scripts\content-import\tests -v
```

当前 10 个测试覆盖显式人工摘要优先、标题/摘要提取、水平分隔线过滤、目录分类、Markdown 内链、
图片重写与去重、`umo:umo` 文件所有权和缺失素材阻断；另覆盖旧版 Markdown 锚点迁移的
dry-run、外部备份、原子写入、幂等和缺少目标阻断。

候选包和提升命令见 [docker-guide.md](docker-guide.md) 第 8.5 节。便携冒烟入口：

```powershell
python "Server Side\UmoWebBackend\scripts\api-smoke.py" `
  --base-url "http://127.0.0.1:18080" `
  --env-file ".env.docker"
```

PowerShell 版本使用 `-IncludeAI`，Python 版本使用 `--include-ai`；默认都只覆盖
原有 31 个接口，显式开启后覆盖全部 40 个接口。脚本会从 `INIT_ADMIN_USER` 和
`INIT_ADMIN_PASS` 读取管理员凭据，不输出密码值。2026-09-12 正式数据候选包、生产切换和
最终备份恢复均通过 `27/27`。

本地不启动真实后端运行冒烟脚本自测：

```bash
python3 -m unittest scripts/ci/tests/api-smoke-test.py
```

该测试使用 Python mock HTTP 响应覆盖 AI 契约、失败脱敏和接口数与请求数分离；PowerShell
与 Python 的实际一致性由启用假供应商的 MySQL CI job 验证。

### 2.8 GitHub Actions

`.github/workflows/ci.yml` 在 `pull_request` 和 `master` push 时执行五个独立 job：

- `repository`：检查变更范围空白错误，运行敏感信息扫描器、发布脚本、访问聚合/保留测试和
  Nginx 六字段日志容器测试，并扫描全部已跟踪文件。
- `backend`：使用 Temurin Java 17 执行 `mvn -B test`，覆盖 AI 模式目录、DeepSeek
  Provider、请求限流、结果校验、转换 Service、Controller 和边界规则。
- `mysql-integration`：使用 MySQL 8.4 从空库执行 Schema、种子数据和幂等迁移，运行分类层级与
  图片管理及 AI 模式 Mapper 集成测试，再启动真实后端和回环假供应商执行 `--include-ai`
  的 40/40 接口冒烟，并校验图片记录、清理队列和文件回收。
- `frontend`：使用 Node 24.12.0 执行 `npm ci`、`npm test` 和 `npm run build`。
- `browser`：使用 Node 24.12.0 安装锁定版本 Chromium，执行 `npm run test:e2e`；
  失败时上传 `playwright-report-<attempt>` artifact。

MySQL job 失败时上传后端日志 artifact；MySQL 服务容器、数据库和临时存储均由 runner 销毁，
不使用仓库 Secret 或外部数据库。

`.github/workflows/playwright-linux-baselines.yml` 仅提供 `workflow_dispatch` 手动入口，
用于生成 Linux 基线 artifact，不参与常规验证，也不提交仓库。

本地运行敏感信息扫描：

```bash
bash scripts/ci/scan-sensitive-info.sh
bash scripts/ci/tests/scan-sensitive-info-test.sh
```

扫描器允许 RFC1918、回环、链路本地、CGNAT、文档专用 IP 和已有明确占位值，
拒绝公开 IPv4、ECS 实例 ID、AccessKey、Token、私钥头及误提交的 `.env*` 文件。

当前仓库是私有仓库，GitHub 计划不支持分支保护和规则集，因此 CI 失败不能技术性地阻止合并。
本地镜像发布入口会显式查询当前 commit 的成功 push CI，并把它作为构建和发布的前置条件。

### 2.9 本地镜像发布与回滚

发布脚本自测：

```powershell
pwsh -NoProfile -File scripts\release\tests\release-unit.ps1
```

```bash
bash scripts/release/tests/release-unit.sh
```

PowerShell 测试覆盖版本和参数校验、CI run 选择、manifest 生成、敏感字段不泄露、归档保留、
Workbench 调用入口，以及迁移和便携冒烟脚本同步。Bash 测试覆盖 env 原子更新、归档
SHA-256/大小、镜像 ID、发布锁、Compose 健康解析、迁移顺序与失败阻断、索引回填一致性、
显式回滚不迁移、失败自动回滚、成功后状态记录和远端归档清理。

真实发布与回滚演练按
[docker-guide.md](docker-guide.md) 第 9 节执行。验收顺序为：

1. `CaptureBaseline` 保存当前 ECS 镜像。
2. `Publish -Version v1.0.0-rc.1 -PublicBaseUrl <公网入口>`，确认 CI gate、构建、上传、
   ECS 本地检查和公网检查通过。
3. `Verify -PublicBaseUrl <公网入口>` 确认当前 release ID、镜像标签和 image ID。
4. `Rollback -Artifact <baseline-directory> -PublicBaseUrl <公网入口>`，确认回落和健康检查通过。
5. 再次发布 `v1.0.0-rc.1`，确认生产最终停留在目标版本。

发布记录只在本仓库保留脱敏命令、耗时、版本、commit、镜像 ID 和归档 SHA-256；ECS 实例标识、
公网地址、管理路径、数据库和管理员凭据不进入仓库。

2026-09-13 真实演练结果：

- `v1.0.0-rc.1` 从提交 `f6f5ce170b3c` 构建，发布 CI run 为 `34739475146`。
- 首次发布、ECS 本地检查和公网检查合计约 170 秒。
- 回滚到 `baseline-20260913` 约 120 秒，image ID 恢复为发布前记录的原始前后端镜像。
- 从开发机 rc.1 归档恢复生产约 122 秒；最终 `Verify` 返回 rc.1 及 manifest image ID。

2026-09-13 Task 2.5 最终发布：

- `v1.0.0-rc.3` 从提交 `59c6971200c5` 构建，发布 CI run 为 `34745585756`。
- rc.2 演练暴露 Compose 未同步、日志 umask 和访问脚本权限问题；PR #9 修复后重新发布 rc.3。
- ECS 串行执行 `api-smoke.py` 为 27/27，exit code 0；`Verify` 返回 rc.3 及 manifest image ID。
- 访问 timer/report service 均 enabled/active，报表只监听 `127.0.0.1:7890`。

2026-09-14 Task 3 最终发布：

- 发布前备份 `20260914T045534Z` 完成外层 SHA-256 与内层清单校验。
- `v1.0.0-rc.4` 从提交 `c9c9f8ee50e5` 构建，发布 CI run 为 `34807582609`。
- ECS 迁移后表数量、ngram FULLTEXT 索引、`PUBLISHED=28` 与 `content_search=28` 一致，
  `image_cleanup_queue=0`。
- ECS `api-smoke.py` 29/29，exit code 0；冒烟后原管理员密码重新登录返回 200。
- 首页、书库、搜索、编辑器、隐私、游戏中心和四条游戏路由返回 200，搜索接口返回 200。
- 独立 `Verify` 返回 rc.4 及 manifest image ID；备份、访问维护和报表 timer/service 均 active。

2026-09-14 Stroop 修复发布：

- `v1.0.0-rc.5` 从提交 `2019c7521844` 构建，发布 CI run 为 `34813314026`。
- 发布归档 SHA-256 为 `5ae8b7f215fc3f3bc4a28826895951aa5508816c044d681041ced8b93bcbdee2`，
  远端 image ID 与 manifest 一致。
- ECS `api-smoke.py` 29/29，exit code 0；独立 `Verify` 返回 rc.5，ECS 与公网入口检查通过。
- 开发机保留 rc.4 和 rc.5 归档，rc.4 为当前回滚目标。

2026-09-17 Phase 4 发布：

- `v1.0.0-rc.6` 从提交 `67b3c9f866d4` 构建，发布 CI run 为 `35183870810`。
- 发布前备份 `20260917T044123Z` 通过 SHA-256 与内层清单校验。
- 一次性回填关闭调度器后正常退出；独立 `Verify` 返回 rc.6 和 manifest image ID。
- ECS `api-smoke.py` 31/31，exit code 0；容器健康、管理员登录和公开入口检查通过。
- 开发机保留 rc.5 与 rc.6 归档，rc.5 为当前回滚目标。

2026-09-17 Task 4.4 滚动协同发布：

- `v1.0.0-rc.7` 从提交 `eace1351adc8` 构建，发布 CI run 为 `35225558649`。
- 发布前备份 `umoweb-backup-20260917T131428Z-unknown.tar.gz` 通过外层 SHA-256
  `f12d88eb33e180ca7a23449a1d1b1eca541ddf167a77ca758e3573b602869de6`
  和内层 `SHA256SUMS` 校验。
- 发布归档 SHA-256 为
  `1d5b474d3154958722aefc6c1452abf4fa6e61de06393014f89781f1a5778382`，
  大小 175685632 字节；独立 `Verify` 返回 rc.7 和 manifest image ID。
- ECS `api-smoke.py` 31/31，exit code 0；公开端、工具中心、游戏中心与四条游戏路由均返回 200。
- 开发机保留 rc.7 与 rc.6 归档，rc.6 为当前回滚目标。

2026-09-17 文章目录常驻与邻接加粗发布：

- `v1.0.0-rc.8` 从提交 `12bc8111cf36` 构建，发布 CI run 为 `35231477064`。
- 发布归档 SHA-256 为
  `137405c8e6e5927169cc17d4dfd52ed3c8c0e27e10ddc617c131283dbc3080e5`，
  大小 175685632 字节；独立 `Verify` 返回 rc.8 和 manifest image ID。
- ECS `api-smoke.py` 31/31，exit code 0；后端回填 29 篇已发布正文。
- 公网浏览器确认 1219px 下目录滚动前后均可见、trigger 隐藏，目标准确列表加粗字重为
  `700`，正文无字面 `**`。
- 开发机保留 rc.8 与 rc.7 归档，rc.7 为当前回滚目标。

2026-09-17 正式版 `v1.0.0` 发布：

- `v1.0.0` 从提交 `fe008c59cb71` 构建，发布 CI run 为 `35232962034`。
- 发布归档 SHA-256 为
  `481c0a4ab1738dd5537a118caface215dc45ee0a20154152794a76b087751b68`，
  大小 175685632 字节；独立 `Verify` 返回 `v1.0.0` 和 manifest image ID。
- ECS `api-smoke.py` 31/31，exit code 0；后端回填 29 篇已发布正文。
- 公网浏览器确认 1219px 下目录滚动前后均可见、trigger 隐藏，列表加粗字重为 `700`，
  正文无字面 `**`，页面无控制台错误。
- 开发机保留 v1.0.0 与 rc.8 归档，rc.8 为正式回滚目标。

### 2.10 访问安全日志

本机单元与安装渲染测试：

```bash
bash scripts/access/tests/access-unit.sh
```

Nginx 容器级六字段测试：

```bash
bash scripts/access/tests/nginx-access-log-test.sh
```

当前 9 个 Python 测试覆盖严格六字段解析、查询参数和敏感字段拒绝、IPv4/IPv6 每日独立 IP、
7/30 天原始日志边界、聚合保留、隐私配置、可信代理生成、HTML 转义和回环报表服务。
Nginx 测试发送带查询参数、Cookie、Authorization、Token、请求体和伪造 `X-Forwarded-For`
的合成请求，断言日志只有六个字段、保留真实请求路径且不泄露任何哨兵值。

ECS 部署后的完整验收：

```bash
bash /opt/umoweb/scripts/access/install-access-timer.sh
bash /opt/umoweb/scripts/access/verify-access-deployment.sh http://127.0.0.1:80
```

该验证交叉检查公开 `/privacy-config.json` 与 `/etc/umoweb/access.env`，检查
`0750/0640` 权限、报表 `127.0.0.1` 监听边界、六字段日志和不可信转发头。发布 manifest 含
`accessPolicy` 时，`remote-release.sh` 会在切换后自动执行同一验证；旧 manifest 不要求访问服务。

---

## 3. Apifox 环境

建议变量：

| 变量 | 值 |
|---|---|
| `baseUrl` | `http://localhost:8080` |
| `token` | 登录后脚本写入 |

管理端集合统一添加 Header：

```text
Authorization: Bearer {{token}}
```

登录请求单独去掉 Header。

登录后置脚本：

```javascript
const body = pm.response.json();
if (body.token) {
  pm.environment.set("token", body.token);
}
```

注意：token 在响应根节点，不在 `body.data.token`。

---

## 4. 公开端测试

### 4.1 站点信息

```http
GET {{baseUrl}}/api/public/site-info
```

预期 200，根对象包含：

```json
{
  "siteTitle": "Umo Blog",
  "siteSubtitle": "代码 · 阅读 · 创作",
  "aboutHtml": "## 关于我\n...",
  "projectHtml": "## 项目\n..."
}
```

### 4.2 About 页面

```http
GET {{baseUrl}}/api/public/pages/about
```

预期 200：

```json
{
  "content": "## 关于我\n..."
}
```

### 4.3 Project 页面

```http
GET {{baseUrl}}/api/public/pages/project
```

预期 200：

```json
{
  "content": "## 项目\n..."
}
```

### 4.4 分类树

```http
GET {{baseUrl}}/api/public/categories
GET {{baseUrl}}/api/public/categories?type=NOTE
```

预期 200，空库为 `[]`，有数据时是顶层分类数组。

### 4.5 标签列表

```http
GET {{baseUrl}}/api/public/tags
```

预期 200，空库为 `[]`。

### 4.6 已发布文章列表

```http
GET {{baseUrl}}/api/public/contents?page=1&size=10
```

预期 200：

```json
{
  "items": [],
  "page": 1,
  "size": 10,
  "total": 0
}
```

可继续测试：

```text
type=NOTE
categoryId=<分类 ID>
categoryId=<父分类 ID>&includeDescendants=true
tagId=<标签 ID>
sort=created_at_desc
```

注意：列表不返回草稿；`categoryId` 默认精确匹配，`includeDescendants=true` 时包含全部后代。
单独传 `includeDescendants=true` 返回 400。

### 4.7 文章详情

不存在：

```http
GET {{baseUrl}}/api/public/contents/no-such-slug
```

预期 404：

```json
{
  "code": 404,
  "message": "Content not found: no-such-slug"
}
```

存在时预期 200，并包含 `body`、`categories`、`tags`、`previous` 和 `next`。如果 Markdown 文件缺失，
仍返回 200，但 `body` 为 `""`。

`previous` 指向更早发布的内容，`next` 指向更晚发布的内容；边界为 `null`。同发布时间下，
小 ID 为更早，大 ID 为更晚。

公开详情浏览器回归还验证：

1. 首个文档 H1 不计入目录，只展示至少两个 H1-H3 章节。
2. 桌面侧栏与 390px 悬浮面板可以展开分支、跟随滚动高亮并恢复移动端焦点。
3. 点击目录后 URL hash、当前章节和阅读进度同步更新。
4. 标题重复时按出现顺序生成稳定后缀，旧版标题锚点通过兼容别名继续可达。
5. 章节不足时目录入口和阅读进度条均不显示，移动端没有横向溢出。
6. 相关阅读在移动端保持同列顺序时使用布局坐标断言，不读取入场动画中的 transform。
7. 桌面目录在 `1440×900`、`1219×958`、`1024×768` 和 `981×800` 滚动后保持 sticky 且不超过
   `50vh`；超高目录在侧栏内部滚动并始终可见。
8. `980px` 及以下隐藏桌面目录并显示 trigger/panel，面板不超过视口一半，各断点没有横向溢出。

### 4.8 搜索

```http
GET {{baseUrl}}/api/public/contents/search?q=java&page=1&size=10
```

预期 200，结构与文章列表相同。

搜索匹配：

- `title`
- `summary`
- Markdown 正文

正文使用 MySQL 8.4 ngram 全文索引；正文命中时响应项包含可直接展示的 `excerpt`。

限流回归：

1. 第一次请求预期 200。
2. 10 秒内再次请求预期 429。
3. 响应消息形如 `Too many requests. Please wait N seconds.`。

不要用伪造 `X-Forwarded-For` 绕过限流；默认只有 `remoteAddr` 参与限流，
直连地址还需匹配 `TRUSTED_PROXIES` 中的精确 IP 或 CIDR 才读取转发头。

### 4.9 公开在线编辑器

访问 `/editor`，不需要登录，也不调用后端接口。验证：

1. 编辑 Markdown 后桌面同时更新预览，390px 下可在编辑/预览标签间切换。
2. 刷新页面后恢复内容和文件名；清空后草稿删除。
3. 内容非空时导入 `.md` 前出现替换确认；非 Markdown 扩展名被拒绝。
4. 下载文件名为规范化后的 `.md`，正文保持 UTF-8。
5. 原始 HTML 被转义，`javascript:` 等危险链接不会进入可执行 HTML。

---

## 5. 管理端认证

### 5.1 登录

```http
POST {{baseUrl}}/api/admin/login
Content-Type: application/json
```

```json
{
  "username": "admin",
  "password": "admin123"
}
```

预期 200：

```json
{
  "token": "eyJ...",
  "expiresAt": "2026-09-11T10:00:00"
}
```

同一用户名/IP 连续失败 5 次后，后续登录在 15 分钟窗口内预期 429。

### 5.2 无 JWT 拦截

```http
GET {{baseUrl}}/api/admin/contents
```

不带 Header 时预期 401：

```json
{
  "code": 401,
  "message": "Missing or invalid Authorization header"
}
```

### 5.3 修改密码

```http
PUT {{baseUrl}}/api/admin/change-password
```

```json
{
  "oldPassword": "admin123",
  "newPassword": "newPass666"
}
```

预期 204。旧 token 在修改密码后再次访问管理端预期 401。

---

## 6. 管理端内容测试

### 6.1 新建分类

```http
POST {{baseUrl}}/api/admin/categories
```

```json
{
  "name": "Java",
  "slug": "java",
  "type": "NOTE",
  "sortOrder": 1
}
```

预期 200，不是 201。

### 6.2 新建子分类

```json
{
  "name": "Spring",
  "slug": "spring",
  "parentId": 1,
  "type": "NOTE",
  "sortOrder": 1
}
```

### 6.3 分类树和详情

```http
GET {{baseUrl}}/api/admin/categories
GET {{baseUrl}}/api/admin/categories/1
```

预期 200。

### 6.4 新建标签

```http
POST {{baseUrl}}/api/admin/tags
```

```json
{
  "name": "Java",
  "slug": "java"
}
```

预期 200。

### 6.5 新建文章

```http
POST {{baseUrl}}/api/admin/contents
```

```json
{
  "title": "Java 集合框架详解",
  "slug": "java-collections",
  "body": "# Java 集合框架\n\n正文",
  "summary": "深入理解 Java 集合框架",
  "type": "NOTE",
  "status": "PUBLISHED",
  "categoryIds": [1],
  "tagIds": [1],
  "metadata": "{\"readingTime\":15,\"difficulty\":\"intermediate\"}"
}
```

预期 200，返回 `ContentDetailVO`。

### 6.6 管理端文章列表与详情

```http
GET {{baseUrl}}/api/admin/contents
GET {{baseUrl}}/api/admin/contents/1
```

预期 200。列表包含草稿与已发布文章，并通过 `status` 字段区分；详情包含 `status` 和 `body`。

### 6.7 编辑文章

```http
PUT {{baseUrl}}/api/admin/contents/1
```

请求体同新建。修改 slug 后会写临时文件、数据库成功后原子替换，并在提交后清理旧文件。预期 200。

### 6.8 删除文章

```http
DELETE {{baseUrl}}/api/admin/contents/1
```

预期 204。

---

## 7. 图片和站点配置

### 7.1 上传图片

```http
POST {{baseUrl}}/api/admin/images/upload
Content-Type: multipart/form-data
```

`file` 选择 png/jpg/gif/webp，且文件内容签名必须与 MIME 匹配。

预期 200：

```json
{
  "id": 1,
  "url": "/images/2026/09/uuid.png",
  "originalName": "image.png",
  "size": 12345
}
```

上传文本文件、伪造 `Content-Type` 或伪造扩展名预期 400。超过 50MB 预期 413。

### 7.2 检查图片一致性

```http
GET {{baseUrl}}/api/admin/images/integrity
```

预期 200，始终包含 `counts`、`brokenReferences`、`missingFiles` 和 `untrackedFiles`。
使用临时草稿引用不存在的 `/images/...` 路径时，对应断裂引用应返回该内容 ID、标题和 URL；
扫描失败预期 500。

### 7.3 查询配置

```http
GET {{baseUrl}}/api/admin/options
```

预期 200，直接返回 KV Map。

### 7.4 更新配置

```http
PUT {{baseUrl}}/api/admin/options/site_title
```

```json
{
  "value": "Umo 的博客"
}
```

预期 204。

---

## 8. 当前边界与已知缺陷

### 8.1 新建资源

所有 POST 新建接口成功时都返回 200。不要把 201 作为当前预期。

### 8.2 分类/标签删除保护

有关联内容时返回 409；删除仍有子分类的父分类也返回 409。

### 8.3 分类筛选

```text
categoryId=父分类 ID
categoryId=父分类 ID&includeDescendants=true
```

不传 `includeDescendants` 时不会返回子分类内容；传 `true` 后返回父分类和全部后代内容。
命中范围内循环或超过 32 层返回 409。

### 8.4 搜索范围

修改 Markdown 正文但保持 title/summary 不变时，正文关键词搜索结果和 `excerpt` 应同步变化。
直接替换磁盘正文文件后需要执行回填脚本，应用写入路径则会在同一事务内同步索引。

### 8.5 分页边界

`page < 1`、`page > 1000000`、`size < 1` 或 `size > 100` 返回 400；
非法 `type/status` 和 metadata 同样返回 400。

### 8.6 文件失败

Markdown 文件缺失时详情返回 200 和空 body。文章创建/更新使用临时文件；
数据库失败会保留或恢复旧状态，数据库提交后的文件清理失败会记录日志并保留可恢复孤儿文件。

完整风险见 [audit-log.md](audit-log.md)。
