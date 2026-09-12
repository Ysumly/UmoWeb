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

该脚本覆盖公开 8 个和管理 19 个接口，并验证 401、改密旧 token 失效、搜索 429 和图片上传。
脚本会临时修改管理员密码和 `site_title`，完成后恢复；上传的测试图片当前没有删除接口。

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
- 当前仍使用首次启动导入的演示数据，没有域名、HTTPS、自动备份或恢复演练。
- 镜像构建和传输目前是人工流程；服务器若恢复仓库访问能力，应改为可审计的 CI/CD。
