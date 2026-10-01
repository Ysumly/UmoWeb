# OpenClaw QQ 私人管家分阶段路线图

> **状态:** 2026-09-30 已确认阶段边界，阶段 1 待实施。

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development`
> (recommended) or `superpowers:executing-plans` to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 以最小风险验证 OpenClaw 能否在现有 ECS 上稳定提供私人 QQ 只读查询与总结，稳定后再开放显式生成新笔记草稿，并长期禁止高风险 CRUD。

**Architecture:** OpenClaw 是私人 QQ 聊天主体和用户快照保存者，UmoWeb 是 OpenClaw 调用的笔记工具集。UmoWeb 保持笔记唯一事实源，OpenClaw 通过回环 Agent API 访问；阶段 1 只复用现有公开读取能力，阶段 2 只允许显式生成新 DRAFT。

**Tech Stack:** Spring Boot 4.1、Java 17、MyBatis、MySQL 8.4、OpenClaw 2026.8.33、腾讯官方 QQBot 插件 2.0.4、Node.js 24.21.0、systemd。

**Spec:** [阶段 0 内存前置计划](2026-10-01-openclaw-memory-preflight.md)

## Global Constraints

- 当前代码是唯一事实来源；阶段 1 不得修改数据库 Schema、不得写 UmoWeb、不得修改 `content_search`。
- OpenClaw 只作为聊天和笔记工具调度器，不提供 coding、代码生成、仓库编辑或软件工程工作流。
- 聊天历史、会话摘要和用户快照保存在 OpenClaw 私有运行数据中，不写入 UmoWeb。
- 用户快照只保存长期偏好、回复风格、稳定事实和话题兴趣，不保存笔记正文、密钥或任意代码仓库内容。
- `/new` 只清空当前工作上下文，不自动删除长期用户快照；删除快照必须通过用户明确指令。
- 阶段 1 只读取 `PUBLISHED` 内容，避免草稿可见性和未发布检索语义提前扩大范围。
- 普通聊天、普通附件和摘要生成不得创建或修改 UmoWeb 数据。
- 阶段 2 只有明确出现“导入、生成草稿、创建草稿”意图时才允许写入。
- AI 只能生成“新草稿”；不能更新、删除、归档或发布任何已有笔记。
- AI 可以读取分类标签并生成建议，但不能创建、改名、删除或调整分类标签。
- 新草稿分类和标签只允许关联现有唯一匹配项；未匹配时留空并报告。
- AI 不得拥有 shell、宿主文件系统、Docker、数据库、浏览器自动化、代码编辑或任意宿主命令工具。
- 任何 Schema 和 `content_search` 语义修改都不在当前授权范围内，除非用户另行明确批准新项目。
- OpenClaw 在 ECS 上以独立 `openclaw` 系统用户和受限原生 systemd 服务运行，不加入 UmoWeb Compose。
- OpenClaw 固定使用：Node `24.21.0`、`openclaw 2026.8.33`、`@tencent-connect/openclaw-qqbot 2.0.4`。
- Node `v24.21.0` Linux x64 官方 SHA-256 为 `fd8e59d5a511510f6a298afb548f18c7d2b1be404d8b4a27d94fbe49f56cb2d6`。
- OpenClaw npm 包 integrity 为 `sha512-y1ti0TLEXk/vDB03XDPVICUE5DJWpp0EwoJHvQaWFKB4NZjyrIu9WDGSpcU+bIYwIpeqK9nlzQVjU8S3XkrHKA==`。
- QQBot npm 包 integrity 为 `sha512-DkzwP2zUguoj8Gn0ZOcvcAHAT0hsZTMY8acB9Pl/lVtaw4tvzTX5YPF6GWMR0OIWFQspAWUn8RaMypNbBSDIsg==`。
- OpenClaw systemd 上限为 `MemoryHigh=256M`、`MemoryMax=384M`、`MemorySwapMax=1G`、`CPUQuota=75%`、`Nice=10`、`OOMScoreAdjust=500`。
- Gate 0 通过前禁止安装 Node、OpenClaw、QQBot 或创建 OpenClaw systemd 单元。
- Gate 0 要求 UmoWeb 运行状态下 `MemAvailable` 每 5 分钟采样一次，连续 72 小时全部大于
  480 MiB；2026-10-01 经明确确认从原始 500 MiB 调整，实测优化峰值为 489.4 MiB。
- Gate 0 通过后只允许 OpenClaw 最小配置和空载连接；空载测试完成前不得注册 UmoWeb 工具或处理用户笔记请求。
- 每个阶段必须独立验收、独立提交、独立回滚文档；上一阶段失败时不得开工下一阶段。

## 阶段 0：内存优化与基线测量

**目标:** 不安装 OpenClaw，先确认当前 ECS 在保持 UmoWeb 生产稳定的前提下，能提供持续超过 480 MiB 的可用内存。

**范围:**

- 只增加只读测量脚本和报告，不安装 Node、OpenClaw、QQBot 或新增常驻服务。
- 每 5 分钟记录宿主 `MemAvailable`、swap、Docker 容器内存、进程 RSS、systemd OOM 和 UmoWeb 健康状态。
- 只有在初始基线不达标时，才逐项分析可逆优化候选；每项修改前后都要重新采样并记录。
- 增加用于后续 OpenClaw 的 2 GiB 独立 swap，但 swap 不计入 `MemAvailable > 480 MiB` 的通过条件。

**Gate 0 通过标准:**

- 连续 72 小时、每 5 分钟一个样本，共至少 864 个样本。
- 所有 `MemAvailable` 样本都严格大于 480 MiB。
- 采样窗口内没有宿主 OOM kill，也没有 UmoWeb 容器因内存压力重启。
- UmoWeb 前后 31/31 冒烟通过，三个容器 healthy。
- 报告记录优化项、回滚方式和未解释的内存变化；没有任何未完成的临时优化。

**未通过处理:**

- 不安装 OpenClaw。
- 保持只读采样或停止后续项目，记录无法提供 480 MiB RAM 的直接证据。

## 阶段 1：OpenClaw 最小空载与私人 QQ 只读验证

**目标:** Gate 0 通过后，先验证 OpenClaw 最小配置在空闲状态下能稳定运行 72 小时，再接入 UmoWeb 只读工具和私人 QQ 查询总结。

**范围:**

- 保留私人 QQ 普通聊天能力。
- 使用 OpenClaw 私有运行数据保存会话历史、会话摘要和用户快照。
- 用户快照只记录长期偏好、回复风格、稳定事实和话题兴趣，并可被用户查看或明确清除。
- 只查询已发布笔记的标题、摘要、分类、标签、可选正文和站点 About/Project。
- 支持按关键词、类型、分类、标签和分页查询。
- 支持用户明确要求时读取完整正文并总结。
- 空载测试期间不注册 UmoWeb 工具，不处理用户笔记请求。
- 空载测试通过后才启用私人 QQ 只读查询和总结。
- 不创建草稿、不上传图片、不更新索引、不提供任何 `/api/agent` 写入接口。
- 不提供 coding、代码生成、文件编辑、终端、仓库操作或浏览器自动化工具。

**Gate 1 通过标准:**

- 私人 QQ 真实消息往返成功，群聊事件完全忽略。
- OpenClaw 重启后会话历史和用户快照仍可恢复，UmoWeb 中没有聊天或快照数据。
- WebSocket 断线后自动重连，重复消息不重复回复。
- 最小配置空载连续运行至少 72 小时，无 OOM kill、无 systemd 重启循环。
- cgroup 内存峰值低于 384 MiB，UmoWeb 31/31 冒烟通过且容器健康。
- Agent API 所有测试通过，数据库行数、`content_search` 行数和 Markdown 文件在查询前后不变。
- 管理界面不监听公网，安全组无新增公网端口。

**未通过处理:**

- 停止并禁用 OpenClaw systemd 单元，保留日志和离线安装包，不进入阶段 2。
- 如果内存超限，不调整 UmoWeb 或放宽到影响生产站的限制；只能继续裁剪 OpenClaw 功能或停止接入。

## 阶段 2：显式草稿导入

**目标:** 在阶段 1 稳定后，允许明确指令触发的笔记包导入，创建 `DRAFT`，但暂不扩大 CRUD 和搜索索引边界。

**范围:**

- 新增 `umo-note-organizer` 和包校验脚本。
- 新增单包 `POST /api/agent/notes/import`，multipart 上传 ZIP，最大 50 MiB。
- 复用现有图片服务和 `ContentManageService.create` 创建 `DRAFT`。
- 生成相关笔记信息，包括摘要、关键词、来源清单、现有分类标签建议和相关已发布笔记建议。
- 分类和标签只关联现有唯一名称或 slug；未匹配项只保留在 manifest 和回复中报告。
- 新草稿不进入 `content_search`，只通过返回的 ID/slug 详情和后台人工入口确认。
- 不更新已有笔记，不批量修改，不创建分类标签，不发布，不归档，不删除。

**Gate 2 通过标准:**

- 10 次合法单包导入全部创建 `DRAFT`，标题、正文、图片和来源可追溯。
- 缺失图片、非法路径、非法状态、重复 slug 和 taxonomy 未匹配均按设计阻断或报告。
- 失败导入不留下数据库孤儿、未关联图片、临时 Markdown 或搜索索引行。
- 普通聊天、普通附件、摘要请求和未包含明确写入词的请求保持零写入。
- 公开搜索和现有 31/31 冒烟继续通过，`content_search` 仍只处理 `PUBLISHED`。
- AI 工具列表中不存在更新、删除、归档、发布、taxonomy 写入或宿主命令工具。

**未通过处理:**

- 关闭 Agent 导入路由和 OpenClaw 导入工具，保留只读阶段。
- 清理失败导入产生的孤儿文件和图片记录后再调查。

## AI 长期权限边界

**允许:**

- 普通私人聊天、会话摘要和用户快照管理。
- 查找和读取已发布笔记。
- 在用户明确要求时读取正文并总结。
- 生成摘要、关键词、来源清单和相关笔记信息。
- 生成新的 `DRAFT` 笔记包并导入。
- 读取分类标签并在新草稿中建议或关联唯一现有项。

**明确禁止:**

- 更新任何已有笔记，包括 Agent 自己创建的草稿。
- 批量修改、删除、恢复、归档或发布内容。
- 创建、改名、删除或调整分类和标签。
- 自动创建缺失 taxonomy。
- 修改 `content_search` 或让未发布内容进入公开搜索。
- 执行 shell、宿主文件、Docker、数据库或网络探测命令。
- coding、代码生成、仓库编辑、补丁、测试执行、构建、部署或浏览器自动化。
- 群聊、多用户、多 Agent、向量库、视频理解和 UmoWeb AI 模式调用。

任何扩大上述权限的请求都必须作为新的独立项目和新的风险评审处理。

## 阶段切换规则

1. 阶段完成后先提交代码、测试证据、部署记录和未验证风险。
2. Gate 由人工确认，不由 OpenClaw 或自动任务自行解锁。
3. 未通过 Gate 时，下一阶段相关路由、工具、Skill 和配置必须保持禁用。
4. 每个阶段只保留一个可回滚生产基线，回滚后不得自动重试写入。
5. 阶段 2 完成后不再自动进入 CRUD 阶段，AI 权限保持冻结。

---

## 当前执行入口

当前只执行 [阶段 0 内存前置计划](2026-10-01-openclaw-memory-preflight.md)。

Gate 0 通过后才执行 [阶段 1 只读查询计划](2026-09-30-openclaw-qq-readonly-phase-1.md)。

阶段 2 的详细实现计划必须在 Gate 1 通过后创建，不能在当前阶段预先生成或预先启用写入代码。
阶段 2 完成后保持新草稿生成、查询和总结边界，不创建后续 CRUD 计划。
