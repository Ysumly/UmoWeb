# 审计日志

## 审计 #50 - 2026-09-17 — Task 4.4 滚动协同 ECS 发布

### 范围

- 合并 PR #32 并发布 Task 4.4 的前端导航、工具中心、编辑器滚动协同和目录抽屉修复。
- 不修改后端 API、数据库 Schema、Markdown 存储格式、认证或部署拓扑。

### 发布证据

| 项 | 结果 |
|---|---|
| 发布版本 | `v1.0.0-rc.7` |
| Git commit | `eace1351adc882638656b91396f7e1078964ddcf` |
| Push CI | run `35225558649`，五个 job 全部成功 |
| 发布前备份 | `umoweb-backup-20260917T131428Z-unknown.tar.gz`，8029102 字节，SHA-256 `f12d88eb33e180ca7a23449a1d1b1eca541ddf167a77ca758e3573b602869de6` |
| 后端 image ID | `sha256:d12137c905e8568cd8140fed9c37dab7faf6d72418776cf7a2915bd999ed70ca` |
| 前端 image ID | `sha256:acd2618cfd95a8ab76aadf1cc87e09f03c3232676e6772109a563683bb432d38` |
| 发布归档 | 175685632 字节，SHA-256 `1d5b474d3154958722aefc6c1452abf4fa6e61de06393014f89781f1a5778382` |

### 验收

- 发布前备份通过外层 SHA-256 和内层 `SHA256SUMS` 校验，rc.6 本地归档的大小、哈希、
  manifest 和双镜像 ID 均与回滚制品一致。
- annotated tag `v1.0.0-rc.7` 已推送，并通过 GitHub API 验证其目标提交为发布 commit。
- 独立 `Verify` 返回 rc.7、manifest image ID 和 `operation=deploy`。
- ECS `api-smoke.py` 为 31/31，覆盖认证保护、草稿隔离、搜索限流、图片生命周期、
  图片一致性、定时隔离和批量操作。
- 首页、书库、搜索、About、Project、工具中心、在线编辑器、隐私说明、游戏中心及四条游戏路由
  均返回 HTTP 200。

### 边界与回滚

- 当前生产版本为 `v1.0.0-rc.7`，正式回滚目标为 `v1.0.0-rc.6`；开发机只保留这两个归档。
- 本次没有新增迁移，发布脚本仍按其幂等流程检查迁移状态和正文索引。
- 发布期间 GitHub `github.com` 的 Git over HTTPS 出现传输故障；仓库发布脚本本身未修改，
  最终标签目标已通过独立 GitHub API 校验，发布制品和回滚边界不受影响。

## 审计 #49 - 2026-09-17 — 抽屉目录隐藏占位修复

### 范围

- 修复代码审查发现的桌面目录抽屉布局问题：隐藏目录仍保留完整高度，导致侧栏粘性区域异常增高。
- 不修改后端 API、数据库、Markdown 格式、目录交互或编辑器行为。

### 实现

- `.post-toc--drawer` 从不参与交互但仍保留布局的 `visibility: hidden` 改为 `display: none`，
  保持原有测量顺序：侧栏目录可见时完成高度判定并锁定模式，之后目录折叠不会触发反向切换。
- 长目录 Playwright 用例新增断言：抽屉模式下隐藏目录高度为 `0`，可见侧栏高度小于半屏，
  滚动后侧栏上下边界保持在当前视口内；抽屉面板继续验证不超过 `50vh`。

### 验证

| 验证 | 结果 |
|---|---|
| Node 测试 | 99/99 通过 |
| 公开端 functional | 22/22 通过 |
| Windows 桌面与移动视觉回归 | 30/30 通过 |
| 生产构建 | Vite 8.1.0 通过 |

### 剩余风险

1. 抽屉判定仍依赖浏览器布局测量；未来调整侧栏结构与内容组成时需要同步扩展视口回归。

## 审计 #48 - 2026-09-17 — Task 4.4 编辑器滚动协同与本地工具入口

### 范围

- 统一公开在线编辑器、管理端文章编辑器和 About/Project 设置编辑器的桌面高度与双向滚动。
- 新增公开端共享导航、`/tools` 工具中心、首页工具模块和“工具”栏目激活态。
- 不修改后端 API、数据库、Markdown 文件格式、认证和 `umo-editor-draft-v1` 草稿键。

### 实现

- 新增 `calculateScrollRatio` 和 `scrollTopForRatio` 纯函数，空值、负值、越界值和不可滚动面板
  分别按 `0` 或可达范围处理。
- `useSyncedScroll` 监听主从面板滚动，通过 `requestAnimationFrame` 合并连续事件，并使用程序化
  目标比例容差阻止双向回弹；媒体查询、元素更换和组件卸载时解绑。
- 公开编辑器仅在 `min-width: 981px` 启用同步，管理端文章编辑器和 About/Project 设置仅在
  `min-width: 701px` 启用；三处工作区输入、textarea 和预览面板保持等高，移动端继续单面板切换。
- `publicNavigation` 统一渲染页头、移动菜单和页脚；`toolCatalog` 与 `ToolsPage.vue` 提供工具中心，
  `/tools`、`/editor` 共享 `section: tools`，首页 `home-tools` 可直接进入工具中心或编辑器。
- Windows 基线更新为 30 张；Linux 工作流 `35216777902` 生成 30 张 artifact，审查确认新增工具中心、
  首页工具模块和移动端页脚变化符合预期。

### 验证

| 验证 | 结果 |
|---|---|
| Node 测试 | 99/99 通过 |
| Windows Playwright | 95/95 通过（65 functional + 30 visual） |
| Linux 视觉基线工作流 | run `35216777902` 成功，30 张快照人工审查通过 |
| 生产构建 | Vite 8.1.0 通过 |
| 文档检查 | 路线图、规格、架构、测试指南、代码记忆、状态快照和审计同步 |

### 剩余风险

1. 双向同步按整体滚动比例近似，不保证 Markdown 源行与渲染块逐行对齐。
2. 工具中心当前只有 Markdown 编辑器，后续新增工具需要继续复用共享导航和本地存储边界。
3. Linux 快照仍绑定 runner 字体与 Chromium 版本，浏览器或 runner 升级后必须重新生成并审查。

## 审计 #47 - 2026-09-17 — Task 4.4 Task 1 文章目录滚动与半屏抽屉

### 范围

- 修复公开文章目录在桌面滚动时无法稳定跟随的问题。
- 目录自然高度严格超过视口一半时，自动从侧栏降级为半屏抽屉。
- 不修改后端、数据库、Markdown 存储、认证、编辑器或工具中心。

### 实现

- 新增 `shouldUseOutlineDrawer` 纯函数，非法尺寸按 `0` 处理，恰好半个视口仍保留侧栏。
- 文章详情使用内容包装层和 `ResizeObserver` 测量目录自然高度；首次进入抽屉后锁定，
  slug、标题集合或视口尺寸变化时重新计算并清理旧 observer/frame。
- `ArticleOutline` 新增 `drawerMode` prop；宽屏抽屉复用已有 trigger/panel 视觉与键盘、
  外部点击、选择后关闭和焦点恢复交互。
- 侧栏增加 `50vh` 高度上限和内部滚动；抽屉状态解除高度限制；移动端侧栏网格迁移到
  包装层以避免视觉变化；`body` 使用 `overflow-x: clip` 恢复 sticky 定位。

### 验证

| 验证 | 结果 |
|---|---|
| Node 测试 | 93/93 通过 |
| 定向公开端 Playwright | 18/18 通过 |
| Windows Playwright | 85/85 通过（57 functional + 28 visual） |
| 生产构建 | Vite 8.1.0 通过 |
| 视觉基线 | 原有 28 张 Windows 基线无需更新 |

### 剩余风险

1. Task 4.4 的编辑器滚动协同和工具中心仍未实施。
2. 目录高度依赖浏览器布局测量；若未来引入折叠动画或多个并排侧栏，需要扩展回归覆盖。

## 审计 #46 - 2026-09-17 — Phase 4 `v1.0.0-rc.6` ECS 发布

### 范围

- 将 Task 4.1–4.3 和后续浏览器测试稳定性修复发布到现有单机 ECS。
- 发布前创建并校验备份，发布后执行独立 `Verify` 和完整真实接口冒烟。
- 不开放新端口，不改变生产拓扑，不推送实例标识、公网地址、管理路径或凭据。

### 实现

- `v1.0.0-rc.6` 从提交 `67b3c9f866d4` 构建，发布 CI run `35183870810`；annotated tag
  指向同一提交。
- 发布前备份 `20260917T044123Z` 通过内层清单校验，SHA-256 为
  `4a3f1e8ec2b62c9adee381cfc60b32bfc6f27891e41ead02d0d515567e3e1c98`。
- 后端 image ID 为
  `sha256:6623a3d10fbda56c582ede53dab4cb65350412595268bb21c62cd7076b096fd5`，前端 image ID 为
  `sha256:dc609dca4c3250ab832791d58b1d97a7096c210f9c5c6733357eded21654f86a`。
- 发布归档 SHA-256 为
  `ce0a606209763ae6878101c580418ec540bfeed67c692dbebe7fae69898ca428`，大小 175683584 字节。
- 首次发布暴露 `remote-release.sh` 的一次性正文回填未关闭调度器，导致远端命令 300 秒超时；
  修复为显式传入 `--app.scheduling.enabled=false` 并增加 PowerShell/Bash 回归断言。
- 超时后确认旧发布进程仅阻塞在一次性回填，终止该进程树并清理精确校验后的 stale release lock；
  随后复用重新构建的 rc.6 制品完成部署。

### 验证

| 验证 | 结果 |
|---|---|
| 发布前备份 | SHA-256 与内层清单通过 |
| 独立 `Verify` | rc.6、manifest image ID、管理员登录和容器健康通过 |
| ECS 真实接口冒烟 | Python `api-smoke.py` 31/31，exit code 0 |
| 正文索引 | 29 篇 `PUBLISHED` 内容回填完成并正常退出 |
| 发布工具回归 | PowerShell/Bash release unit 全部通过 |

### 剩余风险

1. 当前仍是无域名 HTTP 测试部署，HTTPS、正式 DNS 和混合内容验收不在本次发布范围。
2. 发布超时后的进程树清理依赖人工判断 PID 与唯一 release lock；remote-release 后续仍应继续
   强化超时和锁生命周期。

## 审计 #45 - 2026-09-17 — 稳定相关阅读移动端布局回归

### 范围

- 修复 Task 4.1 相关阅读移动端用例在交错入场动画期间读取 `boundingBox()` 导致的 1px 抖动。
- 不修改公开端布局、动画、视觉基线或产品行为。

### 实现

- 测试改用 `offsetLeft`、`offsetTop` 和 `offsetHeight` 验证三张卡片同列并依次向下排列。
- 布局断言不再包含 CSS transform，避免入场动画时序改变坐标结果。

### 验证

| 验证 | 结果 |
|---|---|
| 目标用例重复运行 | 20/20 通过 |
| Node 测试 | 89/89 通过 |
| Windows Playwright | 81/81 通过（53 functional + 28 visual） |

## 审计 #44 - 2026-09-16 — Task 4.3 批量管理与定时发布

### 范围

- 将文章生命周期扩展为 `DRAFT`、`SCHEDULED`、`PUBLISHED`、`ARCHIVED`。
- 新增当前页批量分类/标签、归档/恢复和单篇定时发布。
- 保持公开端只暴露 `PUBLISHED`，不引入文章修订历史。

### 实现

- `contents` 新增可空 `scheduled_at DATETIME(6)` 和 `(status, scheduled_at)` 索引；
  新库写入 `schema.sql`，旧库使用 `20260915_content_schedule.sql` 幂等迁移。
- 仅未发布草稿可设置未来 `scheduledAt`；调度器默认每 30 秒扫描到期内容，使用状态与时间
  条件更新保证最多发布一次，计划和停机补发均以原计划时间写入 `published_at`。
- 调度发布与正文索引同步在同一事务内完成；Markdown 读取、数据库或索引失败时保留
  `SCHEDULED` 并记录单行警告，后续轮询重试。
- 调度配置支持 `app.scheduling.enabled`；无 Web 的正文索引回填进程显式关闭调度，
  避免调度线程阻止 CLI 进程退出。健康检查增加 5 秒超时、代理绕过和进度日志。
- 根元素使用 `overflow-x: clip`，消除 Linux Chromium 中 `documentElement.scrollWidth`
  的 10px 溢出游标；不引入横向滚动容器或视觉变化。
- 新增 `POST /api/admin/contents/bulk`，支持分类/标签添加与移除、归档和恢复草稿；
  整批预检后在单事务提交，失败返回 `failures`，不产生部分更新。
- 管理端文章列表增加当前页勾选、批量操作和待发布/归档徽标；编辑器按状态显示
  `datetime-local` 计划时间控件。Windows 桌面与移动端视觉基线已更新。
- Python 与 PowerShell 冒烟统一为 31/31，新增待发布公开隔离和批量操作验证。

### 验证

| 验证 | 结果 |
|---|---|
| 后端完整测试 | 164/164，0 failures，0 errors；11 个 MySQL 门控默认跳过 |
| MySQL 8.4 Mapper 门控 | 11/11 通过 |
| 真实接口冒烟 | Python `api-smoke.py` 31/31；PowerShell 脚本语法通过 |
| Node 测试 | 89/89 通过 |
| Windows Playwright | 81/81 通过（53 functional + 28 visual） |
| 生产构建 | Vite 8.1.0 通过 |
| 迁移幂等 | Schema、种子和全部迁移连续执行通过；调度字段与索引断言通过 |
| 差异与安全检查 | `git diff --check` 和敏感信息扫描通过 |
| Linux Playwright | 81/81 通过，基线 workflow run `34992907506` |

### 剩余风险

1. 调度器采用数据库条件更新保证幂等，但当前部署仍是单实例，多实例调度延迟未做压测。
2. 批量操作只选择当前页，单次最多 100 篇；跨页全量操作需要后续独立设计。
3. 到期内容按 30 秒轮询周期发布，公开时间允许最多约 30 秒延迟。

## 审计 #43 - 2026-09-15 — Task 4.2 图片一致性检查

### 范围

- 新增只读管理端图片一致性检查，覆盖文章和固定页引用、图片数据库记录与磁盘普通文件。
- 在现有图片管理页提供按需报告、来源定位、异常汇总和清理失败状态。
- 不新增数据库表、迁移、自动修复或报告持久化。

### 实现

- `ImageReferenceService` 新增严格来源扫描；正文返回内容 ID/标题，About/Project 返回固定页名称。
  原有列表与删除接口继续使用容错扫描，单个 Markdown 读取失败不会改变既有行为。
- `ImageIntegrityServiceImpl` 将异常分为断裂引用、记录缺文件和磁盘孤立文件；被引用但无数据库记录的
  文件只计为断裂引用，已有记录但未被引用仍保持 `ORPHANED`。
- 新增 `GET /api/admin/images/integrity`，三类数组始终非空返回，扫描失败返回 500。
- 新增普通文件枚举并排除符号链接；不存在的空目录按空库存处理，存在但不可作为目录读取时返回 500。
- 图片管理页删除成功后清除旧报告，并通过请求代次忽略删除前发出的在途扫描响应。

### 验证

| 验证 | 结果 |
|---|---|
| 后端测试 | 145/145，0 failures，0 errors；10 个 MySQL 门控默认跳过 |
| MySQL 8.4 集成 | 10/10 门控测试与 30/30 API 冒烟通过 |
| Node 测试 | 82/82 通过 |
| Windows Playwright | 79/79 通过（51 functional + 28 visual） |
| Linux Playwright | 79/79 通过，CI run `34978269471` |
| 生产构建 | Vite 8.1.0 通过 |
| Python/PowerShell 语法 | `api-smoke.py` 编译和 `api-smoke.ps1` 解析通过 |

### 剩余风险

1. 扫描结果是一次即时快照；并发上传或删除期间不提供强一致冻结视图。
2. 当前每次检查读取全部 Markdown 和图片目录；内容量显著增长后需要复评显式引用索引。

## 审计 #42 - 2026-09-15 — Task 4.1 相关阅读收口

### 范围

- 在现有公开文章详情响应内增加最多 4 篇确定性相关阅读，不新增接口或数据库表。
- 排除当前文章及 `previous`、`next`，并明确正文内检索由浏览器原生查找承担。
- 保持管理端详情响应结构不变，更新 Windows/Linux 详情视觉基线。

### 实现

- `ContentMapper.findRelatedPublished` 按共享标签 × 3、共享分类 × 2、同类型 1 分排序，
  仅查询 `PUBLISHED` 内容，按分数、发布时间和 ID 倒序限制为 4 篇。
- 服务层先解析前后文章，再排除当前及前后篇 ID；相关查询异常记录警告并返回空数组，
  正文和前后导航保持可用。
- `ContentDetailVO.related` 仅由公开服务赋值；管理端 mapper 保持 null 并在 JSON 中省略，
  不改变管理端详情契约。
- 公开详情在前后导航后展示“相关阅读”，复用 `ContentCard`；桌面双列、700px 以下单列。
- 浏览器 Mock 与真实排序公式同步，新增渲染、跳转、前后篇排除、空数组隐藏和移动端溢出回归。
- 将 Linux 基线工作流暴露的搜索提交竞态改为等待目标 `q` 的搜索响应，不改变生产搜索逻辑。

### 验证

| 验证 | 结果 |
|---|---|
| 后端测试 | 132/132，0 failures，0 errors；9 个 MySQL 门控默认跳过 |
| MySQL 8.4 门控集成 | 9/9 通过 |
| 真实接口冒烟 | `api-smoke.py` 29/29；PowerShell 脚本语法通过 |
| Node 测试 | 82/82 通过 |
| Windows Playwright | 76/76 通过（48 functional + 28 visual） |
| Linux Playwright 基线工作流 | 76/76 通过，run `34966941391` |
| 生产构建 | Vite 8.1.0 通过 |
| 内容工具测试 | Python 10/10 通过 |
| 差异与安全检查 | `git diff --check` 和敏感信息扫描通过 |

### 剩余风险

1. 自定义站内正文搜索 UI 未实现；当前仅由浏览器原生查找处理已加载正文。
2. 每次公开详情会额外执行一次相关文章排序查询及分类/标签批量读取；当前规模可接受，
   内容量显著增长后需要复评执行计划。

## 审计 #41 - 2026-09-14 — Task 4.1 文章目录与阅读进度

### 范围

- 在公开文章详情中增加自动目录、滚动章节高亮和阅读进度。
- 保证标题 ID 可重复生成，并兼容已有旧版标点锚点和受限的显式标题 span。
- 修复视觉夹具问题，避免目录功能测试数据改变原有文章详情视觉基线。

### 实现

- Markdown 与目录共用 marked token 流；首个文档 H1 不作为目录节点，目录只展示 H1-H3，
  并在少于两个章节时隐藏。
- 标题使用规范化、兼容别名和重复序号；原始 HTML 继续转义，只保留严格匹配的安全标题 span ID。
- 桌面端目录进入篇章信息侧栏，窄屏使用悬浮面板；目录支持分支展开、当前/祖先高亮、
  深链接和移动端焦点恢复。
- 阅读进度由文章边界和滚动位置计算，只在正文足够长时显示；滚动、resize 和内容尺寸变化
  通过 RAF 合并更新，并在组件卸载时清理。
- 新增 `ArticleOutline`、`ArticleOutlineList` 和纯函数目录工具；长文 mock 仅用于目录功能用例，
  原有 28 张视觉基线保持不变。
- 新增已知旧锚点迁移工具；Apply 必须指定外部备份目录，写入使用同目录临时文件和替换。

### 验证

| 验证 | 结果 |
|---|---|
| Node 测试 | 82/82 通过 |
| 内容工具 Python 测试 | 10/10 通过 |
| Windows Playwright | 74/74 通过（46 functional + 28 visual） |
| 公开端焦点回归 | 40/40 通过 |
| 生产构建 | Vite 8.1.0 通过 |
| 视觉基线 | 28 张 `win32` 快照无需更新 |
| 差异检查 | `git diff --check` 通过 |

### 剩余风险

1. 正文内搜索和相关文章推荐尚未实现，继续保持为 Task 4.1 候选能力。
2. 旧锚点迁移脚本只处理当前确认的四个历史目标；其他正文锚点异常需要先补充映射和测试。

## 审计 #40 - 2026-09-14 — 录入 Post-v1 后续路线

### 范围

- 将已审核的阅读体验、编辑效率和管理端 AI 规划录入仓库。
- 调整旧四阶段路线与新路线的入口关系，避免“第四阶段”定义冲突。

### 实现

- 新增 `docs/superpowers/plans/2026-09-14-post-v1-roadmap.md`。
- 第四阶段只包含非 AI 阅读与编辑效率；文章修订历史明确列为非目标。
- 第五阶段只处理管理端 AI，且整体后置；草稿和转换结果只保存在浏览器本地。
- 公开语义搜索、原文问答和知识图谱继续后置，作为独立项目评审。
- 更新前端架构、前端实施提示、状态快照、会话摘要、代码基线记忆和变更记录。

### 验证

| 验证 | 结果 |
|---|---|
| 路线图链接 | 新路线和第一至第三阶段历史路线均可访问 |
| 能力状态 | 计划能力未写成已实现能力 |
| AI 数据边界 | 明确无数据库、无服务器备份、无文章正文自动写入 |
| 修订历史 | 在 Post-v1 路线中明确排除 |

### 剩余风险

1. 本变更只录入规划，不包含功能设计、接口、Schema、代码或测试实现。
2. 后续每个功能仍需独立设计并通过实现前评审。

## 审计 #39 - 2026-09-14 — `v1.0.0-rc.5` ECS 发布

### 范围

- 将 Stroop 五色显示判定修复从提交 `2019c7521844` 发布到当前 ECS。
- 使用现有开发机构建、Workbench 传输、ECS 原子切换和失败自动回滚流程。

### 实现

- 发布 CI run `34813314026` 成功，满足当前 commit 的成功 push CI 前置条件。
- 从 ECS 私密读取前端管理路径配置，构建后端和前端版本化镜像。
- 远端先校验归档 SHA-256 和 manifest image ID，再载入并切换 rc.5。
- 发布完成后创建并推送 annotated tag `v1.0.0-rc.5`。
- 开发机保留 rc.4 和 rc.5 两个归档，rc.4 作为当前回滚目标。

### 验证

| 验证 | 结果 |
|---|---|
| 发布 CI | run `34813314026` 全绿 |
| 后端 image ID | `sha256:5facf2c38922884123da1272b3e4cb50c0d597fdb12bc17929f88efe87c4a937` |
| 前端 image ID | `sha256:4d365dbae44b54f65fa0448243c69be2055f754732be3f770a6dba0465be4f58` |
| 发布归档 | SHA-256 `5ae8b7f215fc3f3bc4a28826895951aa5508816c044d681041ced8b93bcbdee2` |
| 正文索引 | 28 篇已发布正文回填完成 |
| 独立 Verify | rc.5、operation `deploy`、ECS 与公网入口检查通过 |
| ECS 接口 | `api-smoke.py` 29/29，exit code 0 |
| 当前状态 | MySQL、backend healthy，frontend running |

### 剩余风险

1. ECS 仍为单机 HTTP 测试部署，正式域名、HTTPS 和正式来源 CORS 尚未启用。
2. 当前限流仍是单实例内存实现，多后端实例需要共享限流存储或网关限流。

## 审计 #38 - 2026-09-14 — 修复 Stroop 色词显示判定

### 范围

- 修复黄色刺激使用未定义变量后回退为正文色，但判定仍要求选择黄色的问题。
- 修复暗色主题黑色刺激使用浅色正文墨色，与黑色答案按钮和判定名称不一致的问题。
- 修复长按数字键时浏览器自动重复事件继续作答后续试次的问题。

### 实现

- 五色刺激统一使用对应按钮色源；新增 `--game-yellow`，继续由主题 `--gold` 驱动。
- 黑色刺激统一使用 `--game-black-key`；亮色主题使用略暗于游戏板背景的
  `--game-black-outline`，暗色主题使用略亮于背景的同名变量，并以 2px 文字描边呈现。
- `handleKeydown` 在入口过滤 `event.repeat`，规则仍保持作答后立即进入下一试次。
- Playwright 新增五色与按钮同色、双主题黑色描边和自动重复键盘事件回归。

### 验证

| 验证 | 结果 |
|---|---|
| 红灯回归 | 两项新增测试在修复前分别命中黄色回退色/黑色缺少描边和重复按键推进 |
| Node 测试 | 72/72 通过 |
| Playwright | 71/71 通过（43 functional + 28 visual） |
| Windows 视觉检查 | 28 张 `win32` 快照全部通过 |
| 生产构建 | `npm run build` 通过 |
| 视觉抽查 | 亮色黑色字使用深色填充和较暗描边，暗色使用深灰填充和较亮描边 |

### 剩余风险

1. 暗色黑色刺激采用深灰按钮色而非纯黑，以兼顾背景对比度和色词识别，这是当前视觉基线
   的明确取舍。

## 审计 #37 - 2026-09-14 — Task 3 `v1.0.0-rc.4` ECS 发布

### 范围

- 合并 Task3 发布链路和备份兼容修复后，在 ECS 原地升级 Task3 数据库 Schema 和前后端镜像。
- 验证正文全文索引回填、29/29 接口、公开路由、备份恢复能力和最终发布证据。

### 实现

- 发布前使用修复后的备份 timer 创建并校验 `20260914T045534Z` 备份，SHA-256
  `bca75e57789565998689064f2205e04284811101168a83088b9d8b42406d2ac9`。
- `v1.0.0-rc.4` 从提交 `c9c9f8ee50e5` 构建，发布 CI run `34807582609`；归档 SHA-256
  `e5ee704873b23de00b8b0f1ff4614f8378ebbdcfe3f7e2ac15bcf53a758dac6f`。
- 部署在镜像切换前执行全部幂等迁移并校验两张表和 ngram FULLTEXT 索引，切换后回填 28 篇
  已发布正文，要求索引计数一致。
- 发布入口同步备份脚本和 timer，ECS 的备份、访问维护和报表 systemd 服务保持 active。
- 发布后执行独立 Verify、完整 29/29，冒烟结束后再次以原管理员密码登录确认恢复成功。

### 验证

| 验证 | 结果 |
|---|---|
| 发布前备份 | SHA-256 和内层清单通过 |
| 发布 CI | run `34807582609` 全绿 |
| 迁移 Schema | `image_cleanup_queue`、`content_search`、FULLTEXT 索引存在 |
| 正文索引 | `PUBLISHED=28`、`content_search=28`、清理队列 `0` |
| ECS 接口 | `api-smoke.py` 29/29，exit code 0 |
| 管理员凭据 | 冒烟后原密码登录 HTTP 200 |
| 核心路由 | 首页、书库、搜索、编辑器、隐私、游戏中心和四条游戏路由均 200 |
| 运行状态 | backend/frontend image ID 与 manifest 一致，三个 systemd 服务 active |

### 剩余风险

1. ECS 仍是单机 HTTP 测试部署，正式 v1 的 HTTPS、正式来源 CORS 和最终核心页面人工验收仍需
   在域名条件具备后完成。
2. 限流状态仍为单实例内存实现，多后端实例需要共享限流存储或网关限流。

## 审计 #36 - 2026-09-14 — 兼容 Task3 迁移前 Schema 的备份

### 范围

- 修复镜像标签后再次执行发布前备份，发现表行数查询无条件引用尚未创建的
  `image_cleanup_queue`。
- 目标是让同一备份脚本同时支持 Task3 迁移前旧库和迁移后新库。

### 实现

- 基础表行数查询不再直接访问 `image_cleanup_queue`。
- 备份先通过 `information_schema.TABLES` 判断可选表是否存在；存在时追加独立计数，不存在时
  正常完成旧 Schema 备份，异常探测结果明确失败。
- 其余 dump、文件清单、归档校验和一致性恢复元数据格式保持不变。

### 验证

| 验证 | 结果 |
|---|---|
| 备份脚本语法 | 通过 |
| 备份脚本自测 | 通过 |
| ECS 迁移前真实备份 | 通过，见审计 #37 |

### 剩余风险

1. 新可选表未创建时不会出现在 `TABLE_COUNT_image_cleanup_queue`；发布后已由审计 #37
   确认该表存在且计数为 0。

## 审计 #35 - 2026-09-14 — 修复版本化发布后的 ECS 备份

### 范围

- rc.4 发布前备份演练发现 `umoweb-backup.service` 仍使用 `umoweb-backend:latest`。
- 版本化发布完成旧镜像清理后 `:latest` 不存在，导致本次和周备份均无法执行。

### 实现

- `scripts/backup/lib/common.sh` 优先从 `.env.docker` 读取 `BACKEND_IMAGE` 和
  `FRONTEND_IMAGE`，仅在环境文件缺失或未配置时回退 `:latest`。
- `install-backup-timer.sh` 不再生成固定镜像标签，并清理已有 `backup.env` 中的旧
  `BACKEND_IMAGE`/`FRONTEND_IMAGE` 行。
- 发布入口同步 `scripts/backup/` 与 systemd 单元，并在发布时重新安装备份 timer。
- 备份单元测试覆盖 Compose 标签覆盖陈旧 timer 值，以及缺少环境文件时的回退行为。

### 验证

| 验证 | 结果 |
|---|---|
| 备份脚本自测 | 通过 |
| PowerShell 发布脚本自测 | 通过 |
| Bash 发布脚本自测 | 通过 |
| `git diff --check` | 通过，仅保留 Windows LF/CRLF 提示 |

### 剩余风险

1. ECS `/etc/umoweb/backup.env` 已由发布入口刷新，发布前备份和 timer 验证由审计 #37 关闭。

## 审计 #34 - 2026-09-14 — Task3 发布链路与 ECS 冒烟保护

### 范围

- 将 Task3 新增的 ECS 数据库迁移和正文索引回填纳入发布/回滚控制链路。
- 加固 Python 与 PowerShell 接口冒烟在失败路径上的管理员密码恢复。
- 补充发布脚本自测、发布指南、测试指南和代码基线记录。

### 实现

- 本地发布入口同步 `docs/design/migrations/*.sql` 和 `api-smoke.py` 到 `/opt/umoweb`。
- `remote-release.sh` 在部署模式下先执行全部迁移，校验 `image_cleanup_queue`、`content_search`
  和 ngram FULLTEXT 索引，再载入并切换镜像；迁移失败不会触碰运行容器。
- 新后端启动后以无 Web 应用模式执行正文回填，并比较 `content_search` 行数与
  `PUBLISHED` 内容数；不一致时恢复旧镜像配置。
- 显式 rollback 跳过迁移和回填，只恢复历史镜像；新增表对 rc.3 保持向后兼容。
- 两个冒烟脚本记录密码变更状态，在 `finally` 中优先用临时密码恢复原密码并重新登录；
  无法确认恢复时明确输出警告，不打印任何密码值。
- PowerShell 发布测试验证迁移文件和冒烟脚本上传；Bash 测试验证迁移排序、失败阻断、
  回填一致性、部署顺序和 rollback 不迁移。

### 验证

| 验证 | 结果 |
|---|---|
| PowerShell 发布脚本自测 | 通过 |
| Bash 发布脚本自测 | 通过 |
| Bash 语法检查 | 通过 |
| Python 冒烟脚本编译 | 通过 |
| 敏感信息扫描器自测 | 通过 |
| `git diff --check` | 通过，仅保留 Windows LF/CRLF 提示 |

### 剩余风险

1. 本地 Bash 测试环境若没有 `python` 别名，需要安装对应转发或改用 CI 执行；测试脚本已支持
   自动选择 `python3`/`python`。
2. 管理员密码恢复仍依赖目标 API 可达；如果 ECS 在改密后同时失去网络或后端，需从服务器侧
   凭据配置执行人工恢复。

## 审计 #33 - 2026-09-14 — 游戏交互与暗色主题细化

### 范围

- 游戏页顶部返回/眉题布局、主次操作按钮、暗色主题按钮对比度。
- 倒背数字反馈确认、扑克牌记忆阶段顺序、主题化牌面。
- 页脚隐私入口文案和新增暗色视觉基线。

### 实现

- `GameShell` 顶部改为左右两端布局，英文眉题在左，返回链接在右，保持同一水平行。
- 暗色主题全局主按钮阴影改为深色柔和投影；Stroop 黑色键使用独立深灰背景和浅色文字，
  对比度测试阈值不低于 4.5:1。
- 倒背数字移除自动推进定时器，反馈持续显示，用户点击“继续”或按 Enter/空格才进入下一题。
- 扑克牌初始显示背面；开始后显示正面并背诵三秒，随后翻回背面等待一秒再进入提问。
  牌面和牌背均使用主题变量，暗色环境不再使用亮色牌背。
- 游戏顶部返回链接与内容板右边缘对齐；扑克牌改为单行 flex 布局并按数量缩放；
  倒背输入使用事件停止传播避免 Enter 同时触发提交和继续；舒尔特结果记录真实点击次数。
- 页脚公开入口从“隐私”改为“隐私收集”，链接目标和隐私内容不变。

### 验证

| 验证 | 结果 |
|---|---|
| 前端 Node 测试 | 72 tests / 0 failures |
| Windows Playwright | 69 passed（41 functional + 28 visual） |
| 标题布局 | 眉题在左、返回在右，同一行误差不超过 3px |
| 暗色对比 | 主按钮无黄黑偏移阴影；Stroop 黑色键达到 4.5:1 最低对比度 |
| 交互流程 | 倒背反馈手动继续；扑克牌背面/正面/背面/提问顺序通过 |
| 细节回归 | Enter 提交保留反馈；10 张牌保持单行；含错误点击时结果计数准确 |
| 视觉基线 | 新增 Stroop 与扑克牌暗色桌面/移动基线 |

### 剩余风险

1. 倒背反馈改为手动继续会略增加操作步骤，但避免答案一闪而过。
2. 扑克牌翻面等待固定一秒，后续若用户反馈节奏不合适可配置化。

## 审计 #32 - 2026-09-14 — 四个训练游戏

### 范围

- 四份固定 SHA-256 静态游戏 HTML 迁移为公开端 Vue 路由、共享游戏外壳和结果弹窗。
- 原游戏规则、计分、升级阈值、随机逻辑和旧版本地成绩兼容。
- 游戏节奏、短屏/横屏/高密度布局、减少动态偏好和浏览器回归。

### 实现

- 新增 `/games`、`/games/stroop`、`/games/digit-span`、`/games/poker-memory` 和
  `/games/schulte`；公开导航、页脚和首页增加入口。
- 游戏规则与渲染分离：随机试次、数字生成、牌组洗牌、目标选择、评级、时长格式化和旧存储
  解析集中在 `src/games/gameLogic.js`，使用注入随机源进行确定性测试。
- 继续兼容 `stroop_84_parchment`、`poker_memory_best_span` 和
  `schulte_parchment_best`；倒背数字不新增成绩存储。
- 游戏路由使用即时转场，不显示全屏 route wipe；Stroop 下一试次立即开始，
  倒背数字反馈最多 1000ms，扑克牌仅保留三秒规则记忆并将结果等待压缩。
- Schulte 使用容器约束的方形网格和 CSS 网格列；扑克牌按牌数换列换行；
  数字与 Stroop 文案受容器限制，长序列和高密度状态不造成页面横向溢出。
- 组件卸载、重开和路由离开时清理 `setTimeout`、`requestAnimationFrame` 和全局键盘监听；
  减少动态偏好停用彩纸、脉冲和翻转动画。
- 未修改后端 Controller、Service、Mapper、Schema、数据库和 Docker 配置。

### 验证

| 验证 | 结果 |
|---|---|
| 前端 Node 测试 | 72 tests / 0 failures |
| Windows Playwright | 64 passed（40 functional + 24 visual） |
| 转场回归 | 四款游戏从游戏页返回游戏中心均正常挂载主体 |
| 视觉细节 | 返回链接与眉题分行；主次按钮统一尺寸、保留描边/实心差异；舒尔特数字放大且 10×10 无格内溢出 |
| 核心游戏流程 | 四款开始、作答、正确/错误、重开、成绩保存与刷新恢复通过 |
| 旧成绩兼容 | 三组旧键结构读取和错误数据降级通过 |
| 响应式场景 | 5 种视口、10×10 网格、10 位数字、10 张牌无页面横向溢出 |
| 节奏与动效 | 游戏路由无全屏擦除；减少动态下无结果彩纸，规则计时不受影响 |
| 前端生产构建 | Vite 8.1.0 通过 |
| 源码输入 | 四份 HTML SHA-256 与路线图记录一致 |
| Linux 基线 | workflow `34794012956` 生成 24 张非空快照，下载哈希一致并人工抽查 |
| 合并 | PR #14、#15、#16 按依赖顺序合并 master，第三阶段出口条件满足 |

### 剩余风险

1. 成绩只保存在当前浏览器，清理站点数据后无法恢复；这是第三阶段明确边界。
2. 高等级扑克牌和 10×10 舒尔特依赖触控精度；当前通过 CSS 缩放避免溢出，但极端低端设备
   仍可能操作困难。

## 审计 #31 - 2026-09-13 — Markdown 正文全文搜索

### 范围

- `content_search` Schema、MySQL 8.4 ngram 全文索引和可重复回填入口。
- 文章创建、更新、发布、撤回和删除时的索引同步。
- 公开搜索标题、摘要、正文，正文命中摘要和草稿隔离。
- MySQL 8.4、Node、Playwright、仓库检查和文档同步。

### 实现

- 新增独立正文索引表，以外键关联 `contents` 并级联删除；建表脚本和兼容迁移均使用
  `FULLTEXT ... WITH PARSER ngram`。
- 内容事务在创建或更新后同步正文索引；转草稿删除索引，直接删除依赖外键级联。
- 回填入口读取全部 `PUBLISHED` 内容，可连续执行；Markdown 缺失时写入空正文并记录单行警告。
- `GET /api/public/contents/search` 非空 `q` 使用正文全文匹配或标题/摘要子串匹配，
  排序优先标题、摘要、正文相关度、发布时间和 ID；空查询保持原有已发布列表语义。
- `ContentListVO` 增加可选 `excerpt`，由正文命中上下文生成并清理 Markdown 标记。
- 搜索页文案和内容卡片接入正文摘要；Playwright 覆盖正文命中、空结果、429 和移动端布局。

### 验证

| 验证 | 结果 |
|---|---|
| 后端默认测试 | 128 tests / 0 failures / 0 errors / 7 MySQL 门控跳过 |
| MySQL 8.4 集成 | 7 tests / 0 failures，覆盖分类、正文索引和图片管理 |
| 索引回填 | 连续两次执行成功，索引行数为 5，中文 ngram 正文命中 |
| 真实接口冒烟 | `api-smoke.py` 29/29；PowerShell 脚本覆盖相同契约 |
| 前端 Node 测试 | 62 tests / 0 failures |
| Windows Playwright | 42 passed（28 functional + 14 visual） |
| 前端生产构建 | Vite 8.1.0 通过 |

### 剩余风险

1. 直接替换磁盘 Markdown 文件不会自动更新索引，必须执行回填脚本。
2. 正文全文搜索依赖 MySQL 8.4 ngram；单字正文受最小 token 限制，标题和摘要仍可匹配。
3. 当前单实例规模不需要独立搜索服务；内容量或查询复杂度显著增长后再评估。

## 审计 #30 - 2026-09-13 — 图片删除与引用保护

### 范围

- 管理端图片列表与删除接口、全部文章状态和 About/Project 引用扫描。
- `image_cleanup_queue` Schema、幂等迁移、提交后文件清理和失败重试。
- 管理端图片管理页、引用筛选、删除确认、409 提示和响应式布局。
- Python/PowerShell 冒烟、MySQL 集成、Node 与 Playwright 回归。

### 实现

- 新增 `GET /api/admin/images`，支持 `page`、`size`、`usage=REFERENCED|ORPHANED`，
  返回图片元信息、创建时间和 `referenced` 状态，按创建时间和 ID 倒序。
- 新增 `DELETE /api/admin/images/{id}`，扫描全部文章 Markdown 与 About/Project；
  已引用返回 409，不存在返回 404，成功返回 204。
- 删除事务同时移除 `images` 记录并写入 `image_cleanup_queue`；提交后删除文件，
  失败保留 `attempts`、`last_error`，由启动 runner 或后续图片操作重试。
- 管理端新增图片管理页和导航入口，提供缩略图、引用状态、全部/使用中/未引用筛选、
  分页、删除确认和引用保护提示。
- 修复已删除静态图片触发 `NoResourceFoundException` 时被转换成 500 的问题，
  缺失 `/images/**` 资源现在返回 404。
- 同步备份、恢复和内容导入元数据：新队列纳入表行数校验，恢复旧备份后先补跑兼容迁移，
  避免新后端读取缺少队列表的旧数据卷。

### 验证

| 验证 | 结果 |
|---|---|
| 后端默认测试 | 114 tests / 0 failures / 0 errors / 4 MySQL 门控跳过 |
| MySQL 8.4 集成 | 4 tests / 0 failures，覆盖分类层级、图片排序和清理队列失败记录 |
| 真实接口冒烟 | `api-smoke.py` 29/29；PowerShell 脚本同步覆盖相同契约 |
| 清理结果 | 冒烟后图片记录、清理队列和临时 PNG 文件均为 0 |
| 前端 Node 测试 | 62 tests / 0 failures |
| Windows Playwright | 42 passed（28 functional + 14 visual） |
| 前端生产构建 | Vite 8.1.0 通过 |

### 剩余风险

1. 引用判断按请求扫描全部正文和固定页，当前内容规模可接受；内容量显著增长后应改为显式引用表。
2. 扫描与删除之间存在并发新增引用的理论竞态；当前单管理员部署不作为阻断。
3. 清理队列没有定时后台任务，只在启动和图片操作时重试；若服务长期无图片操作，失败任务会等待下次重启或操作。

## 审计 #29 - 2026-09-13 — 分类筛选包含子分类

### 范围

- 公开端和管理端文章列表新增 `includeDescendants`，保持默认精确匹配兼容。
- 分类后代解析、循环检测、32 层上限、稳定排序和书库 URL 同步。
- 不新增接口、Schema、前端依赖或 ECS 发布。

### 实现

- 新增 `CategoryHierarchyResolver`，读取一次分类快照后按树展开后代；默认返回单分类，
  `includeDescendants=true` 时返回所有可达后代并在命中循环或超过 32 层时返回 409。
- `ContentMapper` 接受解析后的 ID 集合，使用 `IN` + `EXISTS` 过滤；时间排序后追加 `id DESC`。
- 公开端和管理端 Service 共用解析器；书库选择分类时同步
  `category=<id>&includeDescendants=true`，显式 `false` 仍可请求精确匹配。
- 扩展 MySQL 8.4 集成、Python/PowerShell 冒烟和 Playwright Mock，覆盖父/子分类分流内容。

### 验证

| 验证 | 结果 |
|---|---|
| 后端默认测试 | 98 tests / 0 failures / 0 errors / 3 MySQL 门控跳过 |
| MySQL 8.4 Mapper 集成 | 3 tests / 0 failures，覆盖根/子/孙、精确模式、草稿状态、空结果、去重、稳定排序和循环 |
| 真实接口冒烟 | `api-smoke.py` 27/27；`api-smoke.ps1` 27/27 |
| 前端 Node 测试 | 60 tests / 0 failures |
| Windows Playwright | 40 passed（26 functional + 14 visual） |
| 前端生产构建 | Vite 8.1.0 通过 |
| 仓库检查 | diff check、敏感信息扫描器自测/扫描、Bash/PowerShell 发布脚本自测通过 |

### 剩余风险

1. 分类快照按请求读取，分类量极大时仍可进一步缓存或改为递归 CTE；当前规模不构成瓶颈。
2. 跨类型父子关系仍由既有分类管理规则决定，后代展开不会额外按类型裁剪。
3. 循环检测只拒绝被当前筛选命中的循环，未在启动时全局审计历史分类数据。

## 审计 #28 - 2026-09-13 — 管理端 Markdown 导入

### 范围

- 新建文章页的单文件 `.md`/`.markdown` 导入、YAML front matter 解析和字段回退。
- 分类/标签 slug 映射、metadata 校验、图片引用警告、错误提示和重复 slug 处理。
- 不新增后端接口或数据库字段，最终写入继续复用现有文章创建与文件事务链路。

### 实现

- 新增 `src/utils/markdownImport.js` 纯函数模块，负责文件限制、front matter 解析、H1/文件名回退、
  别名冲突、分类/标签匹配和 Markdown 图片引用检查。
- 将 `yaml` 2.9.x 声明为前端直接依赖，限制别名展开、启用重复键检查，并按文件大小 10 MiB 预检。
- 新建文章页增加“导入 Markdown”入口和结果面板；成功时预填现有表单，严重 YAML 错误不清空当前内容。
- 相对图片引用只警告；导入不自动上传图片、不批量处理、不覆盖已有文章，重复 slug 仍由创建接口返回 409。
- 新增后端成功创建回归测试，验证数据库记录、Markdown 文件、分类关联和标签关联同时写入。
- 审查修正：导入错误会持续阻断提交，并在用户修正对应字段后解除；分类 slug 必须与内容类型一致。
- 审查修正：H1 回退改用 `marked` token，忽略代码块中的 H1；图片警告递归覆盖表格单元格。

### 验证

| 验证 | 结果 |
|---|---|
| 后端完整测试 | 84 tests / 0 failures / 0 errors |
| 前端 Node 测试 | 58 tests / 0 failures |
| Windows Playwright | 39 passed（25 functional + 14 visual） |
| 前端生产构建 | Vite 8.1.0 通过 |
| 导入单测 | front matter、回退、枚举、别名冲突、跨类型分类、代码块 H1、表格图片、未知分类、图片警告、非法 YAML 和文件边界 |
| 导入浏览器流程 | 有效导入、表单预填、预览、创建、非法 YAML 保留、错误阻断/修正和重复 slug 修正 |
| 敏感信息扫描 | 通过 |
| 敏感信息扫描器自测 | 通过 |

### 剩余风险

1. 相对图片不会自动上传或重写，用户需先上传图片并替换为站内 URL。
2. 导入只支持单文件，批量 Markdown 仍需后续任务明确错误汇总和覆盖策略。
3. 导入不自动创建分类/标签，未知 slug 必须先在管理端创建或由用户手动选择。

## 审计 #27 - 2026-09-13 — Task 2.5 ECS 发布与最终验收

### 范围

- `v1.0.0-rc.3` 镜像发布、ECS 原地切换、访问服务安装和公网入口验收。
- rc.2 发布失败暴露的 Compose 同步、日志权限、脚本权限和验证输出问题。
- Task 2.5 完成状态、生产运行版本和发布/回滚边界。

### 发布与修复

- `v1.0.0-rc.3` 从提交 `59c6971200c5` 构建，发布 CI run `34745585756`；annotated tag
  `v1.0.0-rc.3` 已推送，后端 image ID 为 `sha256:a8064b459234ecf2679eeeca1fc9d560dc7975fb1b1b84dee19bb1d9807241d9`，
  前端 image ID 为 `sha256:eae3df2ed622e5bd7574b914493345cf196859d5ddbf57af3f719a2099ea18b2`。
- rc.2 首次切换时 ECS 的 `compose.yaml` 仍是旧版本，前端缺少日志和隐私配置挂载后进入重启循环；
  修复发布入口同步 `compose.yaml`，并用同一失败归档恢复服务。
- rc.2 演练还暴露 Nginx 首次创建日志的 umask、Workbench 同路径脚本不可读、部署根目录 `0700`
  和日志目录不属于 Nginx worker 的问题；前端镜像增加 `umask 0027`，安装脚本统一目录/脚本权限，
  并在日志重开后恢复写入。
- `remote-release.sh` 的 access verification 输出改到 stderr，避免污染 `verify-current` JSON；
  修复提交 `8956f0d` 的 push CI run `34746178884` 五个 job 全部通过。

### 验证

| 验证 | 结果 |
|---|---|
| 当前发布 | `v1.0.0-rc.3`，operation `deploy`，manifest 与运行 image ID 一致 |
| ECS 接口冒烟 | 27/27，通过认证、改密失效、草稿隔离、搜索 429 和 PNG 上传 |
| 访问日志 | 六字段；真实无查询路径；Cookie、Authorization、请求体、XFF 哨兵均未写入 |
| 访问服务 | maintenance timer 与 report service 均 enabled/active |
| 报表边界 | 仅监听 `127.0.0.1:7890`，`healthz` 返回 `ok` |
| 权限 | 部署/聚合/报表目录 `0750`；访问日志 `0640`；脚本可执行 |
| 隐私配置 | 公开配置与 `/etc/umoweb/access.env` 一致，原始 30 天、聚合 180 天 |
| 归档 | SHA-256 `d41e5428006df342ffdaac74ba3b86a5c96fa51b479f44271fc8867d85f9450a`，大小 175,564,800 字节 |
| 本地回归 | 后端 83、前端 49、Playwright 35、访问 Python 9、Nginx 容器测试和发布脚本测试通过 |

### 剩余风险

1. 当前仍无域名和 HTTPS，`ACCESS_TRUSTED_PROXIES` 保持空；Task 1.4 接入最终代理后必须重新验收。
2. 报表保留原始 IP，虽只监听回环且由管理员访问，但主机被入侵时仍属于敏感运维数据。
3. 访问日志、聚合和报表不进入业务备份，丢失后只影响安全审计连续性。
4. `v1.0.0-rc.2` 保留为失败候选审计记录，不应作为发布制品使用；当前生产使用 rc.3。

## 审计 #26 - 2026-09-13 — 访问信息统计与安全日志实现

### 范围

- Nginx 六字段 JSON 访问日志、真实路径与查询参数隔离、可信上游代理边界。
- 主机级日志轮转、压缩、7–30 天原始日志清理、默认 180 天匿名聚合和静态 HTML 报表。
- 仅监听 `127.0.0.1` 的专用非 root 报表服务、公开隐私说明和发布 manifest 访问策略。

### 实现

- Nginx `$request_uri` 经 map 去除查询字符串后写入 `path`，避免 SPA fallback 把真实路由改为
  `index.html`；日志严格限制为 IP、ISO 8601 时间、方法、路径、状态码和响应字节数。
- Nginx real_ip 只信任 `/etc/umoweb/access.env` 中显式配置的 `ACCESS_TRUSTED_PROXIES`；
  默认列表为空，非可信来源的 `X-Forwarded-For` 不进入日志。
- `scripts/access/access_maintenance.py` 以零第三方依赖实现日志校验、每日独立 IP 聚合、
  路径排行、保留期清理、HTML 转义和原子写入；长期聚合不包含任何原始 IP。
- `report_server.py` 只提供 `/`、`/index.html` 和 `/healthz`，不提供目录列表或缓存，
  启动时拒绝非回环监听地址。
- systemd 安装脚本创建专用非 root 报表账户、`0750/0640` 权限、每日 `02:40` 后随机延迟执行，
  并重启回环报表服务。
- 公开 `/privacy` 从 `/privacy-config.json` 读取实际保留期；配置失败时不显示未经确认的天数。
- 发布 manifest 增加可选 `accessPolicy`；旧 manifest 和 rc.1 回滚保持兼容。

### 本地验证

| 验证 | 结果 |
|---|---|
| 后端完整测试 | 83 tests / 0 failures / 0 errors |
| 前端 Node 测试 | 49 tests / 0 failures |
| 前端生产构建 | Vite 8.1.0 通过 |
| Windows Playwright | 35 passed（21 functional + 14 visual） |
| 访问 Python 测试 | 9 tests / 0 failures |
| systemd 安装渲染测试 | 通过，无未替换占位符 |
| Nginx 容器集成 | 通过，六字段、真实路径、查询值和凭据哨兵未泄露，伪造 XFF 未采用 |
| 发布脚本自测 | PowerShell 与 Bash 均通过 |
| 敏感信息扫描 | 扫描器自测和全仓扫描通过 |

### 剩余风险

1. 本审计完成时 ECS 验收尚未执行；该风险已由审计 #27 的 rc.3 发布关闭。
2. 当前无域名和 HTTPS，`ACCESS_TRUSTED_PROXIES` 默认空；Task 1.4 接入最终代理后必须重新验收。
3. 报表保留原始 IP，虽仅由管理员回环服务访问，但丢失主机访问控制后仍属于敏感运维数据。
4. 访问日志、聚合和报表不进入业务备份，丢失后只影响安全审计连续性。

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
