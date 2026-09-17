# UmoWeb Docker 全栈运行指南

> 更新日期: 2026-09-12
> 入口: `http://localhost:8080`（本机默认；端口冲突时可修改）
> 组成: MySQL 8.4、Spring Boot API、Nginx + Vue 静态构建

---

## 1. 首次启动

确认 Docker Desktop 已启动，然后在仓库根目录执行：

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\docker-up.ps1
```

脚本在缺少 `.env.docker` 时自动生成数据库密码、JWT secret 和管理员密码，并执行：

```text
docker compose --env-file .env.docker up -d --build --wait
```

成功后输出站点地址、管理端地址、管理员用户名和密码。真实凭据保存在被 Git 忽略的
`.env.docker` 中，示例字段见 `.env.docker.example`。

如果 Docker Desktop 的 BuildKit 在 `auth.docker.io` IPv6 地址上超时，可先通过守护进程
代理预拉取基础镜像，再重试脚本：

```powershell
docker pull mysql:8.4
docker pull maven:3.9.11-eclipse-temurin-17
docker pull eclipse-temurin:17-jre-jammy
docker pull node:24.12-alpine
docker pull nginx:1.29-alpine
```

如果 `8080` 已被其他程序占用，修改 `.env.docker` 中的 `APP_PORT` 和对应
`CORS_ALLOWED_ORIGINS`，再重新执行启动脚本。例如改为 `18080`：

```text
APP_PORT=18080
CORS_ALLOWED_ORIGINS=http://localhost:18080
```

---

## 2. 容器拓扑

| 服务 | 镜像/构建 | 宿主端口 | 持久化 |
|---|---|---|---|
| `frontend` | Node 24.12 构建 + Nginx 1.29 | `APP_PORT:80` | 无 |
| `backend` | Maven/JDK 17 构建 + JRE 17 | 不暴露 | `app_data:/app/data` |
| `mysql` | `mysql:8.4` | 不暴露 | `mysql_data:/var/lib/mysql` |

Nginx 代理 `/api/**` 和 `/images/**`，并对其他路径执行 SPA fallback。上传请求上限为
52MB，后端继续按 50MB 进行最终校验。

Compose 默认使用 `172.30.0.0/24` 专用网络，Nginx 固定为 `172.30.0.10`。后端只信任
`172.30.0.10/32` 转发的 `X-Forwarded-For`，因此搜索和登录限流按真实客户端 IP 计算。
修改 `DOCKER_SUBNET` 时必须同步修改 `FRONTEND_IP` 和 `TRUSTED_PROXIES`。

---

## 3. 初始数据

MySQL 数据卷首次创建时依次执行：

1. `docs/design/schema.sql`
2. `docs/design/seed-data.sql`

两个脚本均显式使用 `utf8mb4`，避免容器客户端默认字符集导致中文乱码。

后端运行后创建 `.env.docker` 中配置的管理员。后端镜像同时包含 6 篇演示 Markdown，
首次创建 `app_data` 卷时复制到 `/app/data`。因此启动后公开端有 5 篇已发布文章，
管理端还能看到 1 篇草稿。

演示数据仅用于本机验证。正式部署前应替换种子数据、站点配置和管理员凭据，并确认
`site_options`、内容 Markdown 和上传图片来自经过备份的真实数据。

---

## 4. 常用操作

查看状态：

```powershell
docker compose --env-file .env.docker ps
```

查看日志：

```powershell
docker compose --env-file .env.docker logs -f backend
docker compose --env-file .env.docker logs -f frontend
docker compose --env-file .env.docker logs -f mysql
```

停止并保留数据：

```powershell
docker compose --env-file .env.docker down
```

清空数据库、Markdown 和上传图片后重新初始化：

```powershell
docker compose --env-file .env.docker down -v
.\scripts\docker-up.ps1
```

`down -v` 会永久删除两个命名卷，只能在确认不需要现有数据时执行。

---

## 5. 验证

容器全部健康后，可通过 Nginx 入口执行现有真实接口冒烟：

```powershell
$password = ((Get-Content .env.docker |
    Where-Object { $_ -like "INIT_ADMIN_PASS=*" }) -replace "^INIT_ADMIN_PASS=", "")

.\Server Side\UmoWebBackend\scripts\api-smoke.ps1 `
  -BaseUrl "http://127.0.0.1:18080" `
  -Username "admin" `
  -Password $password
```

该脚本覆盖公开 8 个和管理 22 个接口，并验证 401、改密旧 token 失效、搜索 429、图片完整生命周期
和图片一致性来源定位。脚本会临时修改管理员密码和 `site_title`，完成后恢复并清理测试资源。

---

## 6. 正式部署注意事项

- 使用独立强密码和 JWT secret，不提交 `.env.docker`。
- 将 `CORS_ALLOWED_ORIGINS` 改为正式 HTTPS 域名。
- 如前置 Cloudflare 或其他代理，需要扩展真实 IP 处理；当前 Nginx 只覆盖直接代理场景。
- 生产环境不要自动导入演示数据，应在空库后导入真实备份或由管理员创建内容。
- 定期备份 `mysql_data` 和 `app_data`，并在副本验证恢复流程。
- 当前限流仍是单实例内存状态；多后端实例部署需要共享限流存储或网关限流。

## 7. 阿里云 ECS 公网测试部署（脱敏）

### 7.1 环境基线

- 单台阿里云 ECS，Ubuntu 24.04，Docker Engine 与 Compose Plugin 已安装。
- 部署目录为 `/opt/umoweb`，`.env.docker` 仅 root 可读，权限为 `600`。
- 当前通过宿主机 TCP `80` 直接提供 HTTP 服务，尚未绑定域名或启用 HTTPS。
- 仅前端端口暴露到宿主；MySQL 和后端只位于 Compose 网络内。
- 真实实例标识、公网地址、随机管理路径、密码和密钥不写入仓库。

### 7.2 受限网络下的部署

该 ECS 无法稳定访问 Docker Hub、npm 官方仓库和 Maven Central，因此不能直接执行
`compose up --build`。已验证流程是在开发机构建前后端镜像，校验归档 SHA-256 后传到 ECS：

```powershell
docker build --build-arg VITE_ADMIN_PATH=<管理端路径> `
  -t umoweb-frontend:latest -f docker/frontend/Dockerfile .
docker build -t umoweb-backend:latest -f docker/backend/Dockerfile .
docker save -o umoweb-images.tar umoweb-backend:latest umoweb-frontend:latest mysql:8.4
```

在 ECS 导入镜像后使用已存在镜像启动，避免再次访问镜像仓库：

```bash
cd /opt/umoweb
docker load -i umoweb-images.tar
docker compose --env-file .env.docker up -d --no-build --wait
```

如果改用可访问的阿里云 ACR 或个人镜像加速器，应同步评估镜像来源、认证和更新流程。

### 7.3 安全组与验证

- ECS 安全组入方向仅需放行 `TCP 80`；不要暴露 MySQL `3306` 和后端 `8080`。
- 系统 UFW 同样需要允许 `80/tcp`，安全组与主机防火墙两层必须同时放行。
- 验证顺序：容器 healthy、`127.0.0.1` 首页 200、公开 API 正常 JSON、管理端登录 200、
  再从公网访问首页为 200。

### 7.4 凭据语义与当前风险

- `INIT_ADMIN_USER` 和 `INIT_ADMIN_PASS` 只在 `users` 表为空时创建管理员。
- 数据库已有管理员后，修改 `.env.docker` 不会自动修改密码或用户名。
- 旧密码可用时优先调用改密接口；旧密码未知时，应先备份管理员记录，再更新 BCrypt
  `password_hash` 并递增 `token_version`，使旧 JWT 失效。
- 当前已导入正式内容并轮换数据库、JWT 和管理员凭据；仍未配置域名或 HTTPS。
- 镜像构建和传输目前是人工流程；服务器若恢复仓库访问能力，应改为可审计的 CI/CD。

## 8. 备份与恢复

### 8.1 自动备份

ECS 使用 `systemd` timer 每周日 `03:30`（Asia/Shanghai）执行备份，允许最多 10 分钟随机延迟，
服务器错过执行时间后会在下次启动补跑：

```bash
systemctl status umoweb-backup.timer
systemctl list-timers umoweb-backup.timer
systemctl start umoweb-backup.service
journalctl -u umoweb-backup.service -n 120 --no-pager
```

备份脚本位于 `scripts/backup/`，默认输出到 `/opt/umoweb/backups`。每次备份会短暂停止
`frontend` 和 `backend`，确保 MySQL 与 `app_data` 来自同一时点；无论成功或失败都会尝试恢复
服务并等待健康检查。

归档格式为：

```text
umoweb-backup-<UTC时间>-<commit>.tar.gz
umoweb-backup-<UTC时间>-<commit>.tar.gz.sha256
```

归档包含 MySQL 逻辑 dump、`app_data` 压缩包、逐文件 SHA-256 清单和数据库版本/表行数等元数据。
默认保留最多 6 份，且总大小不超过 8GiB；容量达到任一上限时从最旧完整备份开始清理。
元数据同时保存前后端和 helper 镜像 ID；非 Git 部署可在 `/etc/umoweb/backup.env` 中显式设置
`GIT_COMMIT`，避免归档标识继续使用 `unknown`。前后端镜像标签以当前 `.env.docker` 为准，
不再固定依赖 `:latest` 标签；发布入口会同步备份脚本并刷新 timer 配置。

### 8.2 手动校验与导出

```bash
/opt/umoweb/scripts/backup/verify-backup.sh \
  /opt/umoweb/backups/umoweb-backup-<时间>.tar.gz

/opt/umoweb/scripts/backup/export-backup.sh \
  /opt/umoweb/backups/umoweb-backup-<时间>.tar.gz \
  /path/to/export-directory
```

导出只复制归档和校验文件，不删除源备份。归档未加密，导出后由管理员负责目标位置权限和数据保护。

### 8.3 隔离恢复

恢复脚本拒绝使用生产项目名 `umoweb`，只允许 `umoweb-restore-*`，并使用独立 Compose 项目、
`172.31.0.0/24` 网络和 `127.0.0.1:18080`：

```bash
/opt/umoweb/scripts/backup/restore-backup.sh \
  /opt/umoweb/backups/umoweb-backup-<时间>.tar.gz
```

脚本会先校验外层和内部 SHA-256，再从空数据卷恢复数据库与文件，比较表行数和文件清单。
恢复后的管理员密码来自备份中的密码哈希，不包含在归档元数据中，需要由管理员在仓库外提供。

验证完成后只清理隔离项目：

```bash
/opt/umoweb/scripts/backup/cleanup-restore.sh umoweb-restore-<时间>
```

`cleanup-restore.sh` 只接受 `umoweb-restore-*` 项目名，不会删除生产 `umoweb` 项目。

### 8.4 当前边界

- 备份只保存在同一台 ECS；归档可人工导出，但尚未自动上传 OSS 或其他异地存储。
- 归档和校验文件权限为 `0600`，目录权限为 `0700`；当前不做归档内加密。
- 自动备份会在秒级到分钟级内停止写入，当前个人博客规模接受该维护窗口。
- 正式内容导入后的最终备份已重新完成空卷恢复演练，发布清单 `REL-04` 已具备证据。

### 8.5 正式内容候选包

候选包生成器位于 `scripts/content-import/`。它读取 `<notes-root>` 和 `catalog.json`，输出
`umoweb-content-*.tar.gz` 及对应 `.sha256`，不会修改源笔记或生产环境：

```powershell
python scripts/content-import/build_content_backup.py `
  --source "<notes-root>" `
  --catalog scripts/content-import/catalog.json `
  --output Downloads/content-import/umoweb-content-<date>.tar.gz
```

候选包先通过现有 `restore-backup.sh` 在 `umoweb-restore-*` 项目恢复，并执行
`Server Side/UmoWebBackend/scripts/api-smoke.py`。只有 31/31 通过后，才允许使用提升脚本：

```bash
/opt/umoweb/scripts/content-import/promote-content-backup.sh \
  --confirm /opt/umoweb/imports/umoweb-content-<date>.tar.gz
```

提升脚本会先创建生产备份，替换正式数据库与 `app_data`，轮换 MySQL/JWT/管理员凭据，最后再次
执行 31/31。归档和 `.env.docker` 始终不进入仓库。

## 9. 本地版本化镜像发布与回滚

当前正式环境没有长期镜像仓库。开发机负责构建、校验和保留版本归档，ECS 只保存当前运行镜像、
`/opt/umoweb/releases/current.json` 和发布期间的上传目录。

### 9.1 发布前准备

- 开发机可运行 Docker、PowerShell 7、GitHub CLI 和 Workbench CLI。
- `gh auth status` 正常，当前 commit 存在成功的 `CI` push 运行。
- Workbench 凭据和目标 ECS 实例已配置；实例标识、管理路径和凭据只通过参数或服务器侧配置传入。
- 当前分支为 `master`，工作区干净，目标版本标签在本地和远端均不存在。
- 开发机保留至少一个可回滚版本；数据库迁移前应通过 ECS 备份入口完成并校验一份最新备份。

首次接入新流程时，先保存 ECS 当前运行镜像作为回滚基线：

```powershell
pwsh -NoProfile -File .\scripts\release\umoweb-release.ps1 `
  -Action CaptureBaseline `
  -Name baseline-20260913 `
  -InstanceId "<instance-id>"
```

该命令只给当前前后端镜像增加基线标签并导出归档，不修改 `.env.docker` 或重建容器。

### 9.2 发布版本

```powershell
pwsh -NoProfile -File .\scripts\release\umoweb-release.ps1 `
  -Action Publish `
  -Version v1.0.0-rc.1 `
  -InstanceId "<instance-id>" `
  -PublicBaseUrl "<https://public-host-or-http-ip>"
```

发布流程固定执行以下步骤：

1. 校验 `master`、干净工作区、版本格式及当前 commit 的成功 push CI。
2. 从 ECS 私密读取 `VITE_ADMIN_PATH` 构建前端；日志只显示该值的 SHA-256。
3. 同步 `compose.yaml`、访问脚本、全部 `docs/design/migrations/*.sql` 和便携接口冒烟脚本。
4. 构建前后端镜像，同时写入版本标签和 `sha-<12位commit>` 标签。
5. 保存镜像 tar、SHA-256 和 `manifest.json`，上传后再次校验 image ID。
6. 切换镜像前按文件名顺序执行全部幂等迁移，并校验两张新增表和正文 ngram 索引存在。
7. 原子更新 ECS `.env.docker` 中的 `BACKEND_IMAGE`、`FRONTEND_IMAGE` 并重建后端与前端。
8. 执行正文索引回填，要求 `content_search` 行数与 `PUBLISHED` 内容数一致，否则自动恢复旧镜像。
9. 验证 MySQL/backend healthy、frontend running、首页、公开站点信息、管理员登录和访问服务。
10. 成功后写入 `current.json`、删除 ECS 上传归档、创建并推送版本 Git 标签。

验证当前 ECS 版本：

```powershell
pwsh -NoProfile -File .\scripts\release\umoweb-release.ps1 `
  -Action Verify `
  -InstanceId "<instance-id>" `
  -PublicBaseUrl "<https://public-host-or-http-ip>"
```

### 9.3 回滚版本

回滚不重新构建，直接使用开发机保存的版本目录或 tar：

```powershell
pwsh -NoProfile -File .\scripts\release\umoweb-release.ps1 `
  -Action Rollback `
  -Artifact .\Downloads\releases\baseline-20260913 `
  -InstanceId "<instance-id>" `
  -PublicBaseUrl "<https://public-host-or-http-ip>"
```

远端脚本会重新校验归档、载入旧镜像、按 manifest 中的 image ID 校验、切换配置并执行同样的
运行健康检查。发布验证失败时，`remote-release.sh` 也会自动恢复执行前的镜像标签和容器。

### 9.4 本地归档与边界

- 开发机归档目录为 `Downloads/releases/<release-id>/`，默认只保留最近两个成功版本。
- `manifest.json` 记录 release ID、Git commit、CI run、镜像标签、image ID、归档 SHA-256 和
  管理路径哈希，不记录管理路径原值。
- ECS 不长期保存旧镜像归档；回滚必须使用开发机保留的归档。
- 发布、验证和回滚必须传入 `-PublicBaseUrl` 或设置 `UMOWEB_PUBLIC_BASE_URL`，远端会从 ECS
  和该公网入口各验证一次首页与公开 API。
- `.env.docker` 中的 `INIT_ADMIN_PASS` 必须与当前管理员密码一致；远端会在切换镜像前先验证
  管理员登录，凭据失效时在发布前终止。
- 显式回滚只重新载入并切换历史镜像，不重复执行迁移；新增表和索引保持向后兼容。
- 完整 ECS 验收使用 `/opt/umoweb/scripts/smoke/api-smoke.py`，目标为 31/31；脚本在失败路径
  也会优先恢复原管理员密码，再清理临时内容。
- Workbench 单文件上传上限为 1 GiB；发布脚本在归档超过 1,000,000,000 字节时停止上传。

### 9.5 2026-09-13 演练记录

- `v1.0.0-rc.1` 由提交 `f6f5ce170b3c` 构建，成功 push CI run 为 `34739475146`。
- 后端 image ID 为 `sha256:fa443c568cfbbdfcc855d9e3db0b8b061833d0c0b289cb371b24c73ea219590c`，
  前端 image ID 为 `sha256:c67094ec1a5f4eac74f5b2928b3b378ee4dc7fb5e33b21dffca9824a0250bd8f`。
- 发布归档 SHA-256 为
  `a25ac88f8efc06c13db317a8c6a3576395233ae35c8e2b766c12d8f196ea0653`，大小 175,557,120 字节。
- `baseline-20260913` 恢复的前后端 image ID 与发布前一致；归档 SHA-256 为
  `4d320a5ca115d63a71afe644493b99ee51d59350f057223f50c4827389bf93d8`。
- 发布、baseline 回滚和 rc.1 恢复分别约 170、120、122 秒。每一步都通过 ECS 本地容器、
  首页、公开 API、管理员登录和公网入口验证。

### 9.6 Task 2.5 最终发布

- `v1.0.0-rc.3` 从提交 `59c6971200c5` 构建，发布 CI run `34745585756`；归档 SHA-256 为
  `d41e5428006df342ffdaac74ba3b86a5c96fa51b479f44271fc8867d85f9450a`。
- 后端 image ID 为
  `sha256:a8064b459234ecf2679eeeca1fc9d560dc7975fb1b1b84dee19bb1d9807241d9`，前端 image ID 为
  `sha256:eae3df2ed622e5bd7574b914493345cf196859d5ddbf57af3f719a2099ea18b2`。
- rc.2 演练发现 ECS Compose 未随归档更新、Nginx 日志 umask、访问脚本权限和验证输出混杂问题；
  修复后发布流程会同步 `compose.yaml`，前端镜像设置 `umask 0027`，验证输出写 stderr。
- rc.3 发布后独立 `Verify`、ECS 27/27 冒烟、六字段日志、权限和回环报表验收通过；
  `current.json` 的 operation 为 `deploy`。
- `v1.0.0-rc.2` 仅保留为失败候选审计记录，不应作为回滚目标。

### 9.7 Task 3 最终发布

- `v1.0.0-rc.4` 从提交 `c9c9f8ee50e5` 构建，发布 CI run `34807582609`；annotated tag
  `v1.0.0-rc.4` 已推送。
- 发布前备份 `umoweb-backup-20260914T045534Z-unknown.tar.gz` 通过 SHA-256 与内层清单校验，
  归档 SHA-256 为 `bca75e57789565998689064f2205e04284811101168a83088b9d8b42406d2ac9`。
- ECS 在切换镜像前完成全部 SQL 迁移，校验 `image_cleanup_queue`、`content_search` 和
  `ft_content_search_body` FULLTEXT 索引；新后端回填 28 篇正文，索引计数一致。
- 后端 image ID 为
  `sha256:72fcfc5f1ed687afc32a6e9c1078b7b1c212c9932f5c5bddc8b818d29e1552c7`，前端 image ID 为
  `sha256:474d5fb446463eb0fc11557c84344e8218838c9dc3981a379f16527606c5e8c9`。
- 发布归档 SHA-256 为
  `e5ee704873b23de00b8b0f1ff4614f8378ebbdcfe3f7e2ac15bcf53a758dac6f`，大小 175642112 字节。
- ECS `api-smoke.py` 29/29 通过；管理员原密码恢复后仍为 200，临时内容、标签、分类和图片全部回收。
- 首页、书库、搜索、编辑器、隐私、游戏中心和四条游戏路由均返回 200；
  `content_search=28`、`image_cleanup_queue=0`，备份、访问维护和报表服务均 active。
- 当前生产版本为 `v1.0.0-rc.4`；开发机保留 rc.3 和 rc.4 归档，rc.3 为正式回滚目标。

### 9.8 Stroop 修复发布

- `v1.0.0-rc.5` 从提交 `2019c7521844` 构建，发布 CI run `34813314026`；annotated tag
  `v1.0.0-rc.5` 已推送。
- 后端 image ID 为
  `sha256:5facf2c38922884123da1272b3e4cb50c0d597fdb12bc17929f88efe87c4a937`，前端 image ID 为
  `sha256:4d365dbae44b54f65fa0448243c69be2055f754732be3f770a6dba0465be4f58`。
- 发布归档 SHA-256 为
  `5ae8b7f215fc3f3bc4a28826895951aa5508816c044d681041ced8b93bcbdee2`，大小 175642112 字节。
- ECS 回填 28 篇已发布正文，独立 `Verify` 返回 rc.5 和 manifest image ID，
  `api-smoke.py` 29/29 通过。
- 当前生产版本为 `v1.0.0-rc.5`；开发机保留 rc.4 和 rc.5 归档，rc.4 为正式回滚目标。

### 9.9 Phase 4 发布

- `v1.0.0-rc.6` 从提交 `67b3c9f866d4` 构建，发布 CI run `35183870810`；annotated tag
  `v1.0.0-rc.6` 已推送。
- 发布前备份 `20260917T044123Z` 通过 SHA-256 和内层清单校验，归档 SHA-256 为
  `4a3f1e8ec2b62c9adee381cfc60b32bfc6f27891e41ead02d0d515567e3e1c98`。
- 后端 image ID 为
  `sha256:6623a3d10fbda56c582ede53dab4cb65350412595268bb21c62cd7076b096fd5`，前端 image ID 为
  `sha256:dc609dca4c3250ab832791d58b1d97a7096c210f9c5c6733357eded21654f86a`。
- 发布归档 SHA-256 为
  `ce0a606209763ae6878101c580418ec540bfeed67c692dbebe7fae69898ca428`，大小 175683584 字节。
- 一次性正文回填显式关闭调度器后正常退出；后端回填 29 篇已发布正文，独立 `Verify`
  和 ECS `api-smoke.py` 31/31 通过。
- 当前生产版本为 `v1.0.0-rc.6`；开发机保留 rc.5 与 rc.6 归档，rc.5 为正式回滚目标。

## 10. 访问安全日志与报表

### 10.1 日志边界

Nginx 将每个请求写成一行 JSON，只包含：

```text
time, ip, method, path, status, bytes
```

`path` 是真实请求路由，不包含 `?` 后的查询参数。请求体、Cookie、Authorization、
Referer 和 User-Agent 永远不进入该日志。默认 `ACCESS_TRUSTED_PROXIES` 为空；
只有在最终上游代理拓扑确定后，才在 `/etc/umoweb/access.env` 填写可信 IP 或 CIDR。

### 10.2 安装与检查

发布入口会同步 `scripts/access/` 并执行：

```bash
bash /opt/umoweb/scripts/access/install-access-timer.sh
```

默认策略：

- 原始日志保留 30 天，允许配置 7–30 天。
- 每日删除超期原始日志，长期匿名聚合默认保留 180 天。
- systemd timer 每日 `02:40` 后运行，允许 5 分钟随机延迟并支持补跑。
- 报表服务以专用非 root 账户运行，只监听 `127.0.0.1:7890`。

配置和运行目录：

```text
/etc/umoweb/access.env
/opt/umoweb/access/logs/
/opt/umoweb/access/aggregates/
/opt/umoweb/access/reports/
```

目录权限目标为 `0750`，文件目标权限为 `0640`。修改 `/etc/umoweb/access.env` 后重新执行安装脚本，
它会重新生成 `/privacy-config.json` 并重启报表服务。

### 10.3 运维命令

```bash
systemctl status umoweb-access-maintenance.timer
systemctl list-timers umoweb-access-maintenance.timer
systemctl start umoweb-access-maintenance.service
journalctl -u umoweb-access-maintenance.service -n 120 --no-pager

systemctl status umoweb-access-report.service
curl --fail http://127.0.0.1:7890/healthz
```

查看报表时不新增公网端口。在开发机建立 SSH 隧道：

```bash
ssh -L 7890:127.0.0.1:7890 <deployment-user>@<server>
```

然后在开发机打开 `http://127.0.0.1:7890/`。报表包含保留期内的原始 IP，只能由管理员访问，
并随原始日志保留策略删除；长期聚合文件仍然不含 IP。

### 10.4 部署验收

```bash
bash /opt/umoweb/scripts/access/verify-access-deployment.sh http://127.0.0.1:80
```

该脚本验证公开隐私配置、目录/文件权限、报表回环监听、六字段日志、真实路径和无查询参数，
以及非可信 `X-Forwarded-For` 不会被采用。发布 manifest 含 `accessPolicy` 时，
远端版本切换会自动运行该验收；缺少该字段的旧 manifest 仍可用于回滚。
