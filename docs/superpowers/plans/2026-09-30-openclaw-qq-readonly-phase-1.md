# OpenClaw QQ 只读查询阶段 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development`
> (recommended) or `superpowers:executing-plans` to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变数据库、不写 UmoWeb、不修改 `content_search` 的前提下，让私人 QQ 通过受限 OpenClaw 进行聊天、保存用户快照、查询和总结已发布笔记，并验证其在 ECS 上可稳定运行。

**Architecture:** OpenClaw 是聊天主体，使用自己的私有运行数据保存会话历史和用户快照；UmoWeb 只是 OpenClaw 的只读笔记工具。UmoWeb 新增只读 Agent API，使用独立环境变量 Token 验证请求，业务实现直接复用现有公开查询服务。backend 仅增加宿主回环端口 `127.0.0.1:8080`；OpenClaw 以受限原生 systemd 服务运行，只注册只读 UmoWeb 工具。

**Tech Stack:** Spring Boot 4.1、Java 17、MyBatis、MySQL 8.4、JUnit 5、Mockito、MockMvc、OpenClaw 2026.8.33、QQBot 2.0.4、Node.js 24.21.0、systemd。

**Spec:** [OpenClaw QQ 私人管家分阶段路线图](2026-09-30-openclaw-qq-agent-index.md)

## Global Constraints

- 不修改 `docs/design/schema.sql`，不新增迁移，不新增或修改数据库表、列和索引。
- 不改 `ContentSearchIndexService`，不改 `ContentSearchMapper`，不改 `ContentMapper.search` 和 `countSearch`。
- 不新增任何 Agent 写入接口、导入包、图片上传、DRAFT 创建或发布能力。
- OpenClaw 只提供聊天和笔记工具调度，不提供 coding、代码生成、仓库编辑或软件工程工作流。
- 聊天历史、会话摘要和用户快照只保存在 OpenClaw 私有运行数据中，不写入 UmoWeb。
- 用户快照只保存长期偏好、回复风格、稳定事实和话题兴趣，不保存笔记正文、密钥或代码仓库内容。
- `/new` 只清空当前工作上下文，不自动删除长期用户快照；删除快照必须由用户明确指令触发。
- Agent API 只读取 `PUBLISHED` 内容，不读取 `DRAFT`、`SCHEDULED` 或 `ARCHIVED`。
- Agent API 使用 `Authorization: Bearer <AGENT_API_TOKEN>`，不复用管理员 JWT。
- `AGENT_API_TOKEN` 为空时，生产启动必须失败，开发测试可显式注入测试值。
- 普通聊天不得把完整笔记写入 OpenClaw Memory；Memory 只允许保存长期偏好和工具约定。
- OpenClaw 不加入 `compose.yaml`，不复用 UmoWeb Docker 网络，不挂载 Docker Socket、`/opt/umoweb`、`app_data` 或 `.env.docker`。
- OpenClaw 只能通过 `http://127.0.0.1:8080/api/agent/**` 调用 UmoWeb。
- OpenClaw systemd 必须使用 `MemoryHigh=256M`、`MemoryMax=384M`、`MemorySwapMax=1G`、`CPUQuota=75%`、`Nice=10`、`OOMScoreAdjust=500`。
- QQBot 必须使用 WebSocket、`groupPolicy=disabled`、C2C 私聊和唯一 OpenID 白名单。
- 首版 OpenClaw 不启用浏览器、本地模型、向量、STT/TTS、视频、群聊和无关工具。
- OpenClaw AI 不得拥有 shell、宿主文件、Docker、数据库、浏览器自动化、代码编辑或网络探测工具。
- AI 只能读取分类标签并生成建议，不能创建、改名、删除或调整分类标签。
- 阶段 1 不提供更新、删除、归档、发布或任何 taxonomy 写入接口。
- 必须先从 [阶段 0 内存前置计划](2026-10-01-openclaw-memory-preflight.md) 获得 Gate 0 通过结论。
- OpenClaw 安装后先执行 72 小时空载测试，期间不得注册 UmoWeb 工具或处理用户笔记请求。
- 只有 72 小时稳定门禁通过后，才允许创建阶段 2 计划。

---

### Task 1: 冻结阶段 1 只读契约

**Files:**
- Create: `docs/superpowers/plans/2026-09-30-openclaw-qq-agent-index.md`
- Create: `docs/superpowers/plans/2026-09-30-openclaw-qq-readonly-phase-1.md`

**Interfaces:**
- Produces: 阶段 1 的接口范围、禁止项、Gate 1 和回滚边界。
- Produces: 阶段 2、3 必须单独计划的明确入口。

- [ ] **Step 1: 检查计划中不存在阶段 1 写入接口**

Run:

```powershell
rg -n "POST /api/agent|PUT /api/agent|DELETE /api/agent|notes/import|content_search" docs/superpowers/plans/2026-09-30-openclaw-qq-readonly-phase-1.md
```

Expected: 只出现禁止项、现有 `content_search` 不变约束和门禁文本，不存在阶段 1 写入接口定义。

- [ ] **Step 2: 检查已确认版本和资源上限完全一致**

Run:

```powershell
rg -n "24\.21\.0|2026\.8\.33|2\.0\.4|MemoryHigh=256M|MemoryMax=384M|72 小时" docs/superpowers/plans -g "2026-09-30-openclaw-qq-*.md"
```

Expected: 两份计划中的版本、内存和稳定时长一致。

- [ ] **Step 3: 提交契约**

```powershell
git add docs/superpowers/plans/2026-09-30-openclaw-qq-agent-index.md docs/superpowers/plans/2026-09-30-openclaw-qq-readonly-phase-1.md
git commit -m "docs: plan read-only openclaw qq integration"
```

### Task 2: 增加独立 Agent Token 和回环访问边界

**Files:**
- Create: `Server Side/UmoWebBackend/src/main/java/com/ysumly/umowebbackend/config/AgentTokenInterceptor.java`
- Create: `Server Side/UmoWebBackend/src/main/java/com/ysumly/umowebbackend/config/AgentSecurityConfigValidator.java`
- Create: `Server Side/UmoWebBackend/src/test/java/com/ysumly/umowebbackend/config/AgentTokenInterceptorTest.java`
- Create: `Server Side/UmoWebBackend/src/test/java/com/ysumly/umowebbackend/config/AgentSecurityConfigValidatorTest.java`
- Modify: `Server Side/UmoWebBackend/src/main/java/com/ysumly/umowebbackend/config/WebConfig.java`
- Modify: `Server Side/UmoWebBackend/src/main/resources/application.yml`
- Modify: `Server Side/UmoWebBackend/src/main/resources/application-prod.yml`
- Modify: `compose.yaml`

**Interfaces:**
- Consumes: 环境变量 `AGENT_API_ENABLED:false` 和 `AGENT_API_TOKEN`。
- Produces: `/api/agent/**` Bearer Token 验证和每分钟 120 次固定窗口保护。
- Produces: 生产 profile 下 `AGENT_API_ENABLED=true` 且 Token 为空时启动失败。

- [ ] **Step 1: 写 Token 验证失败测试**

测试必须覆盖：缺失 Header、错误 Token、正确 Token、空配置关闭、常量时间比较调用、限额后 429。

```java
@Test
void rejectsMissingToken() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/agent/notes/recent");

    assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
        .isInstanceOf(UnauthorizedException.class);
}

@Test
void acceptsCorrectToken() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/agent/notes/recent");
    request.addHeader("Authorization", "Bearer test-agent-token");

    assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
}
```

Run:

```powershell
cd "Server Side\UmoWebBackend"
mvn -Dtest=AgentTokenInterceptorTest,AgentSecurityConfigValidatorTest test
```

Expected: FAIL，因为两个类尚不存在。

- [ ] **Step 2: 实现最小 Token 拦截器**

实现规则：

```text
AGENT_API_ENABLED=false  -> /api/agent/** 返回 404
AGENT_API_ENABLED=true   -> 只接受 Authorization: Bearer <AGENT_API_TOKEN>
Agent Token 不等于管理员 JWT，不允许访问 /api/admin/**
每分钟最多 120 个请求，超限返回 429
日志不得记录 Token
```

`MessageDigest.isEqual` 使用 UTF-8 字节比较 Token，避免普通字符串短路比较。

- [ ] **Step 3: 注册拦截器并配置生产校验**

`WebConfig` 只对 `/api/agent/**` 注册 `AgentTokenInterceptor`，不得扩展到公开或管理接口。

`application.yml` 增加：

```yaml
app:
  agent:
    enabled: ${AGENT_API_ENABLED:false}
    token: ${AGENT_API_TOKEN:}
    rate-limit-per-minute: 120
```

`application-prod.yml` 增加：

```yaml
app:
  agent:
    enabled: ${AGENT_API_ENABLED:false}
```

生产启用 Agent API 且 Token 为空时必须抛出 `IllegalStateException`，错误信息只说明缺少配置，不打印 Token。

- [ ] **Step 4: 为 backend 增加仅回环端口**

`compose.yaml` 的 backend 增加：

```yaml
ports:
  - "127.0.0.1:8080:8080"
```

不得修改 frontend 公网端口、Docker 网络或数据库端口。

- [ ] **Step 5: 运行后端测试**

Run:

```powershell
cd "Server Side\UmoWebBackend"
mvn -Dtest=AgentTokenInterceptorTest,AgentSecurityConfigValidatorTest,WebConfigTest test
```

Expected: PASS。

- [ ] **Step 6: 提交**

```powershell
git add "Server Side/UmoWebBackend" compose.yaml
git commit -m "feat: add read-only agent token boundary"
```

### Task 3: 增加只读 Agent API，复用现有公开查询服务

**Files:**
- Create: `Server Side/UmoWebBackend/src/main/java/com/ysumly/umowebbackend/controller/agent/AgentReadController.java`
- Create: `Server Side/UmoWebBackend/src/main/java/com/ysumly/umowebbackend/service/agent/AgentReadService.java`
- Create: `Server Side/UmoWebBackend/src/main/java/com/ysumly/umowebbackend/service/impl/agent/AgentReadServiceImpl.java`
- Create: `Server Side/UmoWebBackend/src/test/java/com/ysumly/umowebbackend/controller/AgentReadControllerTest.java`
- Create: `Server Side/UmoWebBackend/src/test/java/com/ysumly/umowebbackend/service/impl/agent/AgentReadServiceImplTest.java`
- Verify: `Server Side/UmoWebBackend/src/main/java/com/ysumly/umowebbackend/service/open/ContentService.java`
- Verify: `Server Side/UmoWebBackend/src/main/java/com/ysumly/umowebbackend/service/open/CategoryService.java`
- Verify: `Server Side/UmoWebBackend/src/main/java/com/ysumly/umowebbackend/service/open/TagService.java`
- Verify: `Server Side/UmoWebBackend/src/main/java/com/ysumly/umowebbackend/service/open/SiteOptionService.java`

**Interfaces:**
- Produces: `GET /api/agent/notes/search`
- Produces: `GET /api/agent/notes/{slug}`
- Produces: `GET /api/agent/notes/recent`
- Produces: `GET /api/agent/taxonomy`
- Produces: `GET /api/agent/pages/{key}`，仅允许 `about` 和 `project`
- Consumes: `ContentService.search(ContentQuery)`、`ContentService.getBySlug(String)`、`CategoryService`、`TagService`、`SiteOptionService`

- [ ] **Step 1: 写只读 Controller 契约失败测试**

测试必须断言：

```text
GET /api/agent/notes/search?page=1&size=10       -> 200
GET /api/agent/notes/{published-slug}            -> 200
GET /api/agent/notes/{draft-or-missing-slug}     -> 404
GET /api/agent/notes/recent                      -> 200，size 最多 20
GET /api/agent/taxonomy                          -> 200，包含 categories 和 tags
GET /api/agent/pages/about                       -> 200
GET /api/agent/pages/project                     -> 200
GET /api/agent/pages/site                        -> 400
GET /api/agent/notes/import                      -> 404 或 405，且不写数据库
```

Run:

```powershell
cd "Server Side\UmoWebBackend"
mvn -Dtest=AgentReadControllerTest,AgentReadServiceImplTest test
```

Expected: FAIL，因为只读 API 尚不存在。

- [ ] **Step 2: 实现只读服务组合**

实现规则：

```text
search: 直接调用 ContentService.search
detail: 直接调用 ContentService.getBySlug
recent: 强制 page=1、size<=20、sort=created_at_desc，再调用 ContentService.listPublished
taxonomy: Map.of("categories", categoryService.getTree(null), "tags", tagService.getAll())
page: about -> SiteOptionService.getPage("about_page")
page: project -> SiteOptionService.getPage("project_page")
```

只读服务不得注入 `ContentManageService`、Mapper 写方法、`FileUtil` 写方法或 `ContentSearchIndexService`。

- [ ] **Step 3: 实现 Controller**

Controller 正常响应沿用项目规则，不增加 `{code,data}` 包装。非法 `page`、`size` 和未知固定页 key 使用现有异常处理返回 400。

- [ ] **Step 4: 验证源码中没有写入依赖**

Run:

```powershell
rg -n "ContentManageService|insert\(|update\(|delete\(|sync\(" "Server Side/UmoWebBackend/src/main/java/com/ysumly/umowebbackend/controller/agent" "Server Side/UmoWebBackend/src/main/java/com/ysumly/umowebbackend/service/agent" "Server Side/UmoWebBackend/src/main/java/com/ysumly/umowebbackend/service/impl/agent"
```

Expected: 无匹配。

- [ ] **Step 5: 运行后端测试**

Run:

```powershell
cd "Server Side\UmoWebBackend"
mvn test
```

Expected: 全部 PASS。

- [ ] **Step 6: 提交**

```powershell
git add "Server Side/UmoWebBackend/src/main/java/com/ysumly/umowebbackend/controller/agent" "Server Side/UmoWebBackend/src/main/java/com/ysumly/umowebbackend/service/agent" "Server Side/UmoWebBackend/src/main/java/com/ysumly/umowebbackend/service/impl/agent" "Server Side/UmoWebBackend/src/test/java/com/ysumly/umowebbackend/controller/AgentReadControllerTest.java" "Server Side/UmoWebBackend/src/test/java/com/ysumly/umowebbackend/service/impl/agent/AgentReadServiceImplTest.java"
git commit -m "feat: add read-only agent api"
```

### Task 4: 准备 OpenClaw 离线包和受限 systemd 单元

**Files:**
- Create: `scripts/openclaw/openclaw.service`
- Create: `scripts/openclaw/openclaw.env.example`
- Create: `scripts/openclaw/install-openclaw-offline.sh`
- Create: `scripts/openclaw/verify-openclaw-readonly.sh`
- Create: `scripts/openclaw/tests/openclaw-deploy-test.sh`

**Interfaces:**
- Produces: `/opt/openclaw` 运行时目录、`/var/lib/openclaw` 数据目录和 `openclaw.service`。
- Produces: `/etc/openclaw/openclaw.env` 的 `AGENT_API_TOKEN` 和 `UMOWEB_AGENT_BASE_URL=http://127.0.0.1:8080`。

- [ ] **Step 1: 写离线部署脚本测试**

测试必须检查：

```text
拒绝 root 运行服务
拒绝缺少 Node 归档 SHA-256
拒绝缺少 OpenClaw 或 QQBot integrity
拒绝目标路径位于 /opt/openclaw 和 /var/lib/openclaw 之外
输出 systemd daemon-reload 和 enable 命令但测试模式不执行
```

Run:

```bash
bash scripts/openclaw/tests/openclaw-deploy-test.sh
```

Expected: FAIL，因为脚本尚不存在。

- [ ] **Step 2: 实现离线安装脚本**

安装内容固定为：

```text
Node   v24.21.0 linux-x64
OpenClaw 2026.8.33
QQBot 2.0.4
```

安装前校验 Global Constraints 中的 SHA-256 和 npm integrity。安装目录为 `/opt/openclaw/node`、`/opt/openclaw/app`；运行数据为 `/var/lib/openclaw`；配置文件为 `/var/lib/openclaw/.openclaw/openclaw.json`。

- [ ] **Step 3: 实现 systemd 单元**

`scripts/openclaw/openclaw.service` 必须包含：

```ini
[Service]
Type=simple
User=openclaw
Group=openclaw
WorkingDirectory=/opt/openclaw/app
EnvironmentFile=/etc/openclaw/openclaw.env
Environment=NODE_OPTIONS=--max-old-space-size=224
ExecStart=/opt/openclaw/node/bin/node /opt/openclaw/app/node_modules/openclaw/openclaw.mjs gateway
Restart=on-failure
RestartSec=5
NoNewPrivileges=true
PrivateTmp=true
PrivateDevices=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/lib/openclaw
MemoryAccounting=true
MemoryHigh=256M
MemoryMax=384M
MemorySwapMax=1G
CPUQuota=75%
Nice=10
IOSchedulingClass=idle
OOMScoreAdjust=500
```

不得加入 Docker Socket、宿主网络、特权模式或 UmoWeb 文件挂载。

- [ ] **Step 4: 验证阶段 0 已创建 2 GiB swap**

```bash
swapon --show | grep -F /swapfile-openclaw
test "$(stat -c '%a' /swapfile-openclaw)" = "600"
```

Expected: `/swapfile-openclaw` 已启用且权限为 `600`；本任务不得重复创建 swap。

- [ ] **Step 5: 运行部署脚本测试**

Run:

```bash
bash scripts/openclaw/tests/openclaw-deploy-test.sh
```

Expected: PASS。

- [ ] **Step 6: 执行 72 小时 OpenClaw 空载测试**

空载窗口必须满足：

```text
openclaw.service active
UmoWeb 工具未注册
没有私人 QQ 笔记查询、保存或导入请求
Systemd MemoryPeak < 384 MiB
NRestarts = 0
无 OOM kill
UmoWeb 三个容器始终 healthy
宿主没有因内存压力重启 backend 或 MySQL
```

每 5 分钟记录：

```bash
systemctl show openclaw.service -p MemoryCurrent -p MemoryPeak -p NRestarts
systemctl is-active openclaw.service
docker compose -p umoweb --env-file /opt/umoweb/.env.docker -f /opt/umoweb/compose.yaml ps
awk '/^MemAvailable:/ {print $2}' /proc/meminfo
```

Expected: 连续 72 小时满足全部门槛。未通过时停止并禁用 OpenClaw，不进入 Task 5。

- [ ] **Step 7: 提交**

```bash
git add scripts/openclaw
git commit -m "ops: verify constrained offline openclaw deployment"
```

### Task 5: 配置私人 QQBot 和只读工具

**Files:**
- Create: `scripts/openclaw/openclaw.json.example`
- Create: `scripts/openclaw/verify-qq-readonly.sh`
- Modify: `scripts/openclaw/openclaw.env.example`

**Interfaces:**
- Produces: QQBot WebSocket C2C 配置、群聊禁用、OpenID 白名单和一个只读 UmoWeb 工具。
- Consumes: `AGENT_API_TOKEN` 和 `http://127.0.0.1:8080/api/agent/**`

Task 5 只能在 Task 4 Step 6 的 72 小时空载测试通过后开始。

- [ ] **Step 1: 写配置静态检查**

`verify-qq-readonly.sh` 必须拒绝：

```text
transport 不是 websocket
groupPolicy 不是 disabled
allowFrom 为空
启用了 browser
启用了本地模型
启用了 vector
启用了 stt 或 tts
存在 shell/command/file-write 工具
存在 coding/editor/patch/build/deploy 工具
```

Run:

```bash
bash scripts/openclaw/verify-qq-readonly.sh /etc/openclaw/openclaw.json
```

Expected: 在配置文件不存在时 FAIL，并在日志中给出缺失路径。

- [ ] **Step 2: 写入最小 QQBot 配置样例**

配置样例必须表达以下结构：

```json
{
  "channels": {
    "qqbot": {
      "enabled": true,
      "transport": "websocket",
      "groupPolicy": "disabled",
      "allowFrom": ["YOUR_QQ_OPENID"],
      "tools": {
        "umo_notes_search": true,
        "umo_note_get": true,
        "umo_notes_recent": true,
        "umo_taxonomy_list": true,
        "umo_page_get": true
      }
    }
  }
}
```

实际配置必须使用 `0600` 权限并通过文件保存 OpenID，不得把 OpenID、AppSecret 或 Agent Token 写入仓库。

- [ ] **Step 3: 配对首个 OpenID**

首次启动时开启最小配对日志，从 gateway 日志读取本人 C2C OpenID，写入 `allowFrom` 后立即重启。配对窗口不得启用任何写入工具。

- [ ] **Step 4: 验证私聊和群聊**

Run in QQ:

```text
私人会话: 搜索 Spring
私人会话: 总结《Spring Boot 快速上手》
私人会话: 请记住我偏好简洁回答
群聊: @机器人 搜索 Spring
```

Expected:

```text
私人会话返回已发布内容摘要或正文总结
私人会话更新用户快照且不调用任何 coding 工具
群聊无回复、无工具调用、无 Memory 写入
```

- [ ] **Step 5: 验证快照持久化和无 coding 工具**

重启前后分别执行：

```text
私人会话: 你记得我的回答偏好吗？
私人会话: 帮我修改这个代码仓库
```

Expected:

```text
重启前和重启后都能回答长期回答偏好
代码仓库请求被拒绝或降级为普通说明，不出现文件、shell、补丁、构建或部署工具调用
重启没有在 UmoWeb 数据库中产生聊天、快照或 Memory 行
```

- [ ] **Step 6: 提交**

```bash
git add scripts/openclaw/openclaw.json.example scripts/openclaw/openclaw.env.example scripts/openclaw/verify-qq-readonly.sh
git commit -m "ops: configure private read-only qqbot"
```

### Task 6: 只读端到端与零写入验证

**Files:**
- Create: `scripts/openclaw/tests/readonly-e2e.sh`
- Create: `docs/project/openclaw-readonly-verification.md`

**Interfaces:**
- Consumes: Agent 只读 API、OpenClaw systemd、私人 QQ。
- Produces: 查询前后数据库和文件不变量报告。

- [ ] **Step 1: 记录查询前不变量**

在 ECS 执行只读查询：

```bash
cd /opt/umoweb
docker compose -p umoweb --env-file .env.docker -f compose.yaml exec -T mysql \
  sh -lc 'mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" \
  -e "SELECT COUNT(*) FROM contents; SELECT COUNT(*) FROM images; SELECT COUNT(*) FROM content_search;"'

docker compose -p umoweb --env-file .env.docker -f compose.yaml exec -T backend \
  sh -lc 'find /app/data -type f -printf "%P %s\n" | sort | sha256sum'
```

Expected: 输出当前行数和文件树哈希。

- [ ] **Step 2: 执行只读查询和总结**

依次在私人 QQ 执行：

```text
搜索 Spring
查看《Spring Boot 快速上手》
总结《Java 集合》
最近更新了哪些笔记
关于页面写了什么
```

Expected: 全部只返回已发布内容，正文只在明确查看或总结时读取。

- [ ] **Step 3: 重做不变量检查**

重复 Step 1 的两条命令。

Expected: 三张表行数和文件树 SHA-256 与查询前完全一致。

- [ ] **Step 4: 运行 UmoWeb 现有冒烟**

```powershell
cd "Server Side\UmoWebBackend"
.\scripts\api-smoke.ps1 -BaseUrl "http://127.0.0.1:8080" -Username "admin" -Password "<current-password>"
```

Expected: 31/31 通过。

- [ ] **Step 5: 提交验证记录**

```powershell
git add scripts/openclaw/tests/readonly-e2e.sh docs/project/openclaw-readonly-verification.md
git commit -m "test: verify read-only openclaw qq flow"
```

### Task 7: 72 小时稳定门禁

**Files:**
- Modify: `docs/project/openclaw-readonly-verification.md`
- Verify: `scripts/openclaw/verify-openclaw-readonly.sh`

**Interfaces:**
- Produces: Gate 1 的最终通过或不通过结论。
- Blocks: 阶段 2 计划创建和任何写入功能启用。

- [ ] **Step 1: 启动 72 小时观察**

每 5 分钟记录：

```text
systemctl show openclaw.service -p MemoryCurrent -p MemoryPeak -p NRestarts
systemctl is-active openclaw.service
docker compose -p umoweb --env-file /opt/umoweb/.env.docker -f /opt/umoweb/compose.yaml ps
```

- [ ] **Step 2: 统计失败条件**

出现任意一项即 Gate 1 失败：

```text
MemoryPeak > 384 MiB
NRestarts > 0 且不是计划内发布
OpenClaw 因 OOM 被终止
UmoWeb 任一容器 unhealthy
backend 或 MySQL 因内存压力重启
群聊产生任何回复
只读请求改变 contents/images/content_search 或 app_data
OpenClaw 产生 coding、shell、文件编辑、构建或部署工具调用
```

- [ ] **Step 3: 72 小时后运行最终冒烟**

Run:

```powershell
cd "Server Side\UmoWebBackend"
mvn test
```

Run:

```powershell
cd "Client Side\umo-web-frontend"
npm test
npm run build
```

Expected: 全部 PASS。

- [ ] **Step 4: 写入 Gate 1 结论**

`docs/project/openclaw-readonly-verification.md` 必须明确写：

```text
观察开始和结束时间
峰值内存
重启次数
QQ 私聊和群聊结果
用户快照保存与重启恢复结果
UmoWeb 中不存在聊天和快照数据
UmoWeb 冒烟结果
零写入前后哈希
通过或不通过
若失败，已执行的停用和回滚步骤
```

- [ ] **Step 5: 提交**

```powershell
git add docs/project/openclaw-readonly-verification.md
git commit -m "docs: record openclaw read-only stability gate"
```

## Gate 1 之后的动作

- 通过：创建阶段 2 独立计划，允许显式草稿导入；不得直接启用更新、批量、taxonomy 创建、全状态搜索或 `content_search` 修改。
- 不通过：保持 `openclaw.service` 停止并禁用，关闭 Agent API 或只保留回环测试入口，停止后续阶段。
