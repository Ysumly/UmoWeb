# OpenClaw 到 UmoWeb 的受限草稿读写接入 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development`
> (recommended) or `superpowers:executing-plans` to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不开放发布、归档、删除或管理员权限的前提下，让私人 QQ 中的 OpenClaw Agent
“祀”读取已发布内容、查询 DRAFT、创建新 DRAFT、修改现有 DRAFT，并复用已有分类和标签或新建标签。

**Architecture:** 已发布内容继续通过现有公开 HTTP API 读取。草稿与标签创建通过独立
`/api/agent/**` mTLS 入口访问，使用 scoped Token、`contents.revision` 乐观锁、一次性
`changeToken` 和 OpenClaw exec 审批。OpenClaw 侧使用本地 MCP 适配器，只暴露结构化只读、
预览和补丁工具，最终写操作由未自动放行的 `umo-draft-apply` 命令执行。

**Tech Stack:** Spring Boot 4.1、Java 17、MyBatis、MySQL 8.4、Nginx mTLS、Node.js 24、
`@modelcontextprotocol/sdk` 1.32.0、OpenClaw 2026.10.5、QQBot。

**Spec:** 2026-10-26 用户确认的受限草稿读写计划；该计划取代仅只读的阶段 1 方案。

## Summary

- “祀”直接通过现有 `/api/public/**` 查询已发布内容，不修改公开接口。
- 新增独立 `/api/agent/**` mTLS 接口，只允许读取、新建和修改 DRAFT。
- 分类只能复用现有项；标签可以复用或新建，但只能新建，不能改名、移动或删除。
- 所有草稿写入必须先预览，再由 OpenClaw exec 审批卡片人工确认。
- Agent 永远不能发布、归档、删除、批量修改、上传图片或调用管理员 API。
- 不后台同步；正文、草稿片段、搜索词和 diff 不写入长期 Memory。

## Interfaces

### 已发布内容 MCP 工具

- `umo_notes_search`、`umo_note_read`、`umo_notes_recent`、`umo_taxonomy_get`、`umo_page_read`。
- 正文按 20,000 个 Unicode 字符分块，返回 `offset`、`totalChars`、`nextOffset`。
- 搜索在本地保持 10 秒冷却，并处理公开搜索 API 的 429。

### 草稿 MCP 工具

- `umo_drafts_search(q, page, size)`：只查询 `status=DRAFT`。
- `umo_draft_read(slug, offset, limit)`：返回草稿分块正文、分类标签和 `revision`。
- `umo_draft_prepare_create(input)`：生成新草稿预览和一次性 `changeToken`，不写数据库。
- `umo_draft_prepare_update(slug, expectedRevision, patch)`：生成补丁结果预览和 `changeToken`，不写数据库。
- `patch` 支持精确替换、追加、前置插入、标题、摘要、分类、标签和 metadata；
  单次正文补丁不超过 20,000 字符，最终正文不超过 1 MiB。
- 精确替换默认必须命中且仅命中一次；零次或多次命中直接拒绝。

### 专用 HTTP 接口

- `GET /api/agent/drafts`、`GET /api/agent/drafts/{slug}`。
- `POST /api/agent/drafts/prepare-create`、`POST /api/agent/drafts/{slug}/prepare-update`。
- `POST /api/agent/draft-changes/apply`，`changeToken` 只放在请求体和一次性内存记录中，不进入 URL。
- 草稿写入强制 `status=DRAFT`、`scheduledAt=null`；更新要求目标当前仍为 DRAFT。
- 创建支持 `NOTE`、`NOVEL`、`BOOK_REVIEW`；更新时 `type` 和 `slug` 不可变。
- 分类必须从现有分类树中选择，且类型兼容；`NOVEL` 仍必须关联至少一个已有 NOVEL 分类。

## Taxonomy Rules

- prepare 请求可同时提交 `tagIds` 和 `newTags`；`newTags` 每次最多 5 个。
- Tag 必须提供 `name` 和安全 `slug`；先按规范化名称和 slug 检查现有项。
- 如果已存在相同或高度相似的标签，返回候选标签而不自动新建，由祀在 QQ 中让用户选择。
- 新标签与草稿创建/修改必须在同一数据库事务中提交；任一失败时全部回滚，不能留下孤儿标签。
- Agent 不提供标签改名、合并、移动或删除，也不提供任何分类创建、移动或删除接口。
- 预览必须明确列出将复用的标签、将新建的标签和当前分类。

## Security And Data

- 新增独立 `agent-gateway` Nginx 容器监听 `8443`，只代理 `/api/agent/**`，只允许 GET/POST；
  其他路径及 PUT、DELETE 一律拒绝。
- 使用绑定 UmoWeb 公网 IP 的自签名服务端证书和唯一客户端证书，不需要域名；
  安全组和 UFW 仅允许腾讯云服务器 IP 访问 `8443`。
- 使用独立 `AGENT_API_TOKEN`，以 SHA-256 后常量时间比较，不复用管理员 JWT。
- `AGENT_API_ENABLED` 默认关闭；生产启用时 Token 缺失、过短或证书配置不完整则启动失败。
- 为 `contents` 增加单调递增 `revision`；Agent 更新必须匹配 `expectedRevision`，冲突返回 409。
- 新增 `agent_operation_log`，记录请求 ID、操作、草稿 ID/slug、新旧 revision、新建标签 ID、
  变更字段、结果和错误码；不记录正文、标题、摘要、Token 或搜索词。
- `changeToken` 使用 256 位随机值，后端内存保存 5 分钟、单次消费、最多 16 条；重启后预览失效。
- MCP 进程保存证书和 Token；模型不能读取凭据。最终 apply 由
  `umo-draft-apply <changeToken>` 执行，该命令不在 exec 白名单中，必须经过 QQ 审批。
- exec 改为 `allowlist + ask-on-miss`，仅自动允许只读状态、TrendRadar 日志和 OpenClaw 状态 wrapper。
- 保留现有 `file-transfer`、Codex 配置和其他插件，并将其记录为已接受的残余风险。

## Test And Rollout

- 单元测试覆盖 Token、证书、路径和方法拒绝、强制 DRAFT、发布/删除拒绝、revision 冲突、
  Token 过期/重放和审计脱敏。
- Taxonomy 测试覆盖标签规范化、slug 冲突、近似名称候选、最多 5 个新标签、分类类型不匹配
  和事务整体回滚。
- 正文测试覆盖 replace/append/prepend、零次/多次命中、20,000 字符限制、1 MiB 上限及中文和
  emoji 分块。
- MySQL 集成测试验证迁移幂等、并发草稿更新只有一个成功、标签与草稿原子提交、
  DRAFT 不进入 `content_search`。
- 私有 QQ 实测新建草稿、复用标签、创建新标签、修改草稿、修改分类关联和拒绝发布/删除。
- 查询测试前后 `contents`、`images`、`content_search` 和文件树哈希不变；
  写测试只改变目标草稿、必要的新标签和审计表。
- 上线前备份数据库、`app_data`、OpenClaw 配置和凭据；先部署默认关闭的 Agent API，
  再安装 mTLS、MCP 和命令审批，最后启用并观察 72 小时。
- 回滚时关闭 `AGENT_API_ENABLED`、停止 `agent-gateway`、移除 `8443` 防火墙规则并恢复
  OpenClaw 配置；新增表和列保持向后兼容。

## Assumptions

- 已发布内容继续通过当前 HTTP 公开 API 读取，这是已接受风险。
- 不做文章修订历史或自动内容回滚；文件系统只保证失败事务恢复。
- 不处理已发现的宝塔 `8888` 公网监听和遗留 UFW 规则；生产主机整体安全基线仍另立任务处理。
