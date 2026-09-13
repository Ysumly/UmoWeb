# UmoWeb 开发工作流

> 原则: 代码是事实来源，文档负责准确描述代码和明确标记未实现目标

---

## 1. 开始修改前

1. 阅读 [codebase-memory.md](codebase-memory.md)。
2. 检查 `git status`，不要覆盖用户已有改动。
3. 阅读目标模块代码和对应文档。
4. 区分以下三种状态：
   - 已实现且已有测试。
   - 已实现但无测试或存在缺陷。
   - 尚未实现，仅有设计目标。

---

## 2. 修改流程

```text
读取代码
  -> 明确当前行为
  -> 修改代码
  -> 更新事实文档
  -> 补充或调整测试
  -> 执行构建/测试
  -> 更新审计和变更记录
  -> 提交并推送
```

不允许只依据旧设计文档修改代码。若设计与代码冲突，先确认期望行为，再决定改代码还是修文档；当前任务要求以代码为主时，文档必须服从代码。

---

## 3. 文档同步矩阵

| 修改内容 | 必须同步 |
|---|---|
| Controller 路径、DTO、VO、状态码 | `docs/design/api-reference.md`、对应模块文档 |
| Mapper SQL、查询参数语义 | `docs/modules/data-layer.md`、接口文档 |
| Schema 或字段 | `docs/design/schema.sql`、`data-layer.md`、种子数据说明 |
| 前端依赖或目录 | `frontend-architecture.md`、`Dependencies.md` |
| 前端页面状态 | `frontend-srs.md`、`frontend-implementation-prompt.md`、`codebase-memory.md` |
| 安全配置 | `security-config.md`、`audit-log.md` |
| 测试命令或覆盖范围 | `testing-guide.md`、`codebase-memory.md` |
| CI 工作流 | `testing-guide.md`、`codebase-memory.md`、`audit-log.md` |
| 完成里程碑 | `CHANGELOG.md`、`.state-snapshot.md` |

---

## 4. 验证要求

### 后端

```powershell
cd "Server Side\UmoWebBackend"
mvn test
```

涉及真实数据库、文件系统或拦截器链时，仅有 MockMvc 边界测试不够，需补充集成验证或明确记录未验证风险。

### 前端

```powershell
cd "Client Side\umo-web-frontend"
npm test
npm run build
npm run test:e2e
```

页面功能或布局变更后，functional 与视觉 Playwright 套件都应保持通过；视觉范围变化时同步审查或更新基线。

### CI

`.github/workflows/ci.yml` 负责基础质量检查，不包含 Playwright 和真实 MySQL 集成。
本地修改敏感信息扫描器时必须先运行：

```bash
bash scripts/ci/tests/scan-sensitive-info-test.sh
bash scripts/ci/scan-sensitive-info.sh
```

当前私有仓库套餐不支持分支保护或规则集，CI 结果不会由 GitHub 自动阻止合并。
发布流程必须显式要求 CI 成功，该要求由 Task 2.4 固化。

### 文档

- 搜索旧版本号、错误的响应包装和“已实现”措辞。
- 所有 API 数量应保持：公开 8、管理 19、总计 27。
- 所有新建接口状态应为 200。
- 搜索应描述为 title/summary，不是全文/Markdown。

---

## 5. Git 与敏感信息

- 不提交 `Downloads/`、真实数据库密码或生产 JWT secret。
- 默认管理员密码只用于开发。
- 提交前检查 `git status`、`git diff` 和 `bash scripts/ci/scan-sensitive-info.sh`。
- 文档变更也应有清晰 commit message。

---

## 6. 完成标准

- 代码与文档描述一致。
- 受影响端构建通过。
- 测试通过，或明确记录失败原因和风险。
- `codebase-memory.md`、`CHANGELOG.md`、`audit-log.md` 按需更新。
- 没有把计划能力写成已实现能力。
