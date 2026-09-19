# UmoWeb 管理端 AI 计划总索引

> 状态：2026-09-19 质量验收已完成，`5.1A–5.1E` 已完成，`5.1F` 正在执行发布收口。

**目标:** 在不影响现有文章编辑、发布、搜索和公开端的前提下，交付管理员专用、提示词可配置、
转换模式可扩展、正文与结果仅保存在浏览器本地或请求内存中的 AI 工具链。

**架构:** AI 设置与转换模式存入 MySQL，并通过管理端 CRUD 和版本回滚维护。后端通过供应商无关
接口调用模型，首版使用全局 DeepSeek 配置。文章编辑器通过 AI 抽屉调用无状态转换接口，结果不自动
写入文章。

**技术栈:** Spring Boot 4.1、Java 17、MyBatis 4.0.1、MySQL 8.4、Vue 3.5、Vite 8、
Node test、Playwright 1.63、PowerShell/Bash 发布脚本。

## 子计划

| 顺序 | 计划 | 状态 | 独立交付物 |
|---|---|---|---|
| 5.1A | [产品与数据契约](2026-09-18-admin-ai-5.1a-product-contract.md) | 已完成 | 冻结产品边界、隐私策略、接口和数据结构；只改文档 |
| 5.1B | [模式目录后端](2026-09-18-admin-ai-5.1b-mode-catalog-backend.md) | 已完成 | 模式、提示词版本、CRUD、复制、启停、排序和回滚 API |
| 5.1C | [模式设置页](2026-09-18-admin-ai-5.1c-mode-settings-ui.md) | 已完成 | 管理端“AI 设置”页面和完整浏览器回归 |
| 5.1D | [转换运行时](2026-09-18-admin-ai-5.1d-transform-runtime.md) | 已完成 | 能力查询、DeepSeek 适配、转换接口、限流、超时和脱敏日志 |
| 5.1E | [文章 AI 抽屉](2026-09-18-admin-ai-5.1e-editor-drawer.md) | 已完成 | 正文带入、执行、取消、结果编辑/复制和本地存储 |
| 5.1F | [质量与发布](2026-09-18-admin-ai-5.1f-quality-release.md) | 进行中 | 假供应商回归、受控真实评测已通过；文档收口和发布门禁进行中 |

## 依赖

```text
5.1A -> 5.1B -> 5.1C
              -> 5.1D -> 5.1E -> 5.1F
```

- `5.1A` 是所有实现工作的共同前置条件。
- `5.1B` 完成前，不得开始 `5.1C` 或 `5.1D`。
- `5.1C` 与 `5.1D` 在 `5.1B` 完成后可以并行，但两者都必须保持本索引冻结的接口名称和字段。
- `5.1E` 只能消费 `5.1D` 已交付的转换接口，不得自行新增供应商调用。
- `5.1F` 只有在 A–E 全部通过自身测试后才能执行真实模型质量验收和发布收口。

## 统一约束

- 具体系统提示词只存在数据库记录和幂等种子 SQL 中，Java、Vue、测试断言不得包含默认模式提示词。
- 首版模式固定为 `STRUCTURE_CLEANUP`、`MODERN_TO_CLASSICAL`、`ENGLISH_TO_CHINESE`、
  `CHINESE_TO_ENGLISH`、`LIGHT_NOVELIZATION`，但全部可通过管理端编辑、停用、复制和回滚。
- 全局配置为 `APP_AI_ENABLED=false`、`DEEPSEEK_BASE_URL=https://api.deepseek.com`、
  `DEEPSEEK_API_KEY`、`DEEPSEEK_MODEL`；启用时密钥和模型必填。
- 单次输入最多 20,000 字符，输出最多 60,000 字符，超时 180 秒，同时一个请求，
  10 分钟最多 5 次；不实现费用硬上限。
- 原始草稿只存 `sessionStorage["umo-admin-ai-source-v1"]`，当前结果只存
  `localStorage["umo-admin-ai-result-v1"]`。
- AI 结果不得自动插入、替换、保存或发布文章；重新转换覆盖手动修改结果前必须确认。
- 模式提示词最近保留 10 版；回滚通过复制历史版本生成新版本，不覆盖历史。
- 后端日志不得包含正文、转换结果、系统提示词、API Key 或完整提示词。
- 真实 DeepSeek 调用只用于人工控制的质量验收，CI 始终使用假供应商。

## 冻结数据字段

以下类型和字段名是 `5.1B–5.1E` 的固定契约，后续计划、Mapper、Controller 和前端不得自行改名：

```text
AiModeSettingsVO: id, modeKey, name, description, enabled, sortOrder,
                  currentVersion, systemPrompt, validationProfile,
                  createdAt, updatedAt
AiModeVersionVO: versionNo, systemPrompt, validationProfile, createdAt
AiCapabilityModeVO: modeKey, name, description
AiTransformRequest: modeKey, content
AiTransformResultVO: requestId, modeKey, modeVersion, content,
                     model, usage
AiTokenUsageVO: inputTokens, outputTokens, totalTokens
```

## 冻结接口

管理端接口使用现有 JWT 拦截器，正常响应直接返回 VO 或数组，异常返回 `{ code, message }`。

```text
GET    /api/admin/ai/settings
GET    /api/admin/ai/modes
POST   /api/admin/ai/modes
POST   /api/admin/ai/modes/{id}/copy
PUT    /api/admin/ai/modes/{id}
GET    /api/admin/ai/modes/{id}/versions
POST   /api/admin/ai/modes/{id}/rollback/{versionNo}
GET    /api/admin/ai/capabilities
POST   /api/admin/ai/transform
```

## 完成条件

- 六个子计划全部完成并通过各自验证，没有把设计能力写成已实现能力。
- 管理员可新增一个模式并只填写名称、唯一 `modeKey`、系统提示词和校验策略，启用后无需改代码或重启即可使用。
- 修改提示词会生成新版本，保留最近 10 版并可一键回滚。
- 结构整理、文言文、双向翻译和轻度小说化均通过固定样本人工验收。
- 假供应商、MySQL 8.4、Node、Playwright、生产构建、敏感信息扫描和受控真实模型检查全部通过。
- `api-reference.md`、`schema.sql`、模块文档、测试指南、代码基线、状态快照、会话摘要、
  变更记录和审计日志与实际代码一致。

## 状态维护

- 每个子计划完成后，只更新该行状态和对应计划内的复选框，不在本索引复制实现细节。
- 子计划失败或暂缓时，在对应计划头部记录阻断原因；依赖它的计划保持“待实施”。
- 若冻结接口需要变更，先更新 `5.1A` 并完成文档审查，再修改其他计划和代码。
