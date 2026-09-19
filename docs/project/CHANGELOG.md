# CHANGELOG

## 2026-09-19 — AI 工作流布局与滚动体验修复

- AI 模式卡片把启用状态固定在右上角，把启用/停用操作移到左下角；整张主卡片仍可点击切换模式。
- 启用状态改为与校验策略等高、顶部对齐的表单控件，展开策略说明后仍保持同一水平位置。
- AI 设置的操作按钮和历史版本整体移到左侧栏，位于转换模式列表下方，不再跟随右侧系统提示词向下滚动。
- AI 转换抽屉的模式选择框增加明确边框、背景和强调阴影，视觉上恢复为可操作控件。
- 管理端文章编辑器恢复为默认同步滚动，但改为按最近标题锚点对齐，不再按整页高度比例映射，
  避免长文 H1/H2 重排时出现“左 2、右 2.4”的错位；移除了多余的同步开关。
- 标题解析、编辑器测量节点和预览标题位置增加缓存，拖动滚动时不再重复解析全文或重建测量 DOM。
- 更新 Windows 与 Linux 的 AI 设置和 AI 抽屉桌面/移动视觉基线。
- 验证：前端 Node 122/122、Windows Playwright 119/119（85 functional + 34 visual）和生产构建通过。

## 2026-09-19 — AI 模式设置可用性修复

- AI 设置页的转换模式目录由横向表格改为纵向卡片列表，桌面和移动端都不再隐藏策略、状态、
  排序或版本信息；点击卡片主区域即可切换当前模式，启停按钮保持独立。
- 模式说明明确标注“仅用于管理端识别和说明，不会发送给模型”；系统提示词明确标注会作为
  `system prompt` 发送给大模型。
- 校验策略增加可展开说明，解释严格内容一致、翻译保真、轻度扩写和仅基础校验的实际行为。
- 更新 Windows 与 Linux 桌面/移动端 AI 设置视觉基线；新增 2 个 functional 用例。
- 验证：前端 Node 120/120、Playwright 119/119（85 functional + 34 visual）和生产构建通过。

## 2026-09-19 — 发布前移动端与界面缺陷修复

- 管理端移动侧栏关闭时不再进入 Tab 顺序或可访问性树；打开后锁定背景滚动、聚焦首个导航项、
  将 Tab 限制在侧栏内，并支持 Escape 关闭后把焦点还给菜单按钮。
- 管理端侧栏增加内部纵向滚动，`844×390` 和 `667×320` 低高度横屏下仍可访问最后几个导航项
  和“退出登录”。
- 管理端与公开端移动导航点击当前页导航项时直接关闭；公开导航补齐
  `aria-controls`、状态相关的打开/关闭名称和 Escape 关闭。
- 公开编辑器、管理端 Markdown 导入和图片上传的隐藏文件输入移出 Tab 顺序，并补齐可访问名称。
- 图片列表在缩略图加载失败时显示“图片文件不可用”的无障碍占位，不再依赖浏览器原生破图。
- 验证：前端 Node 120/120、Windows Playwright 117/117（83 functional + 34 visual）、
  生产构建通过；本机 Docker 全栈在 320×568、390×844、667×320、844×390 和 1440×900 复验通过。
- 未修改后端 API、数据库 Schema、AI 契约或 ECS。后端仍只按文件签名校验上传，且图片一致性扫描
  只检查文件存在性；签名有效但内容损坏的文件可能存在并显示前端占位。

## 2026-09-18 — 5.1E 管理端文章 AI 抽屉

- 在文章编辑器接入 AI 转换抽屉，支持能力探测、正文带入、模式选择、执行/取消、结果编辑、
  只读预览、复制回退、重新转换确认和浮动恢复。
- 源草稿使用 `sessionStorage["umo-admin-ai-source-v1"]`，结果使用
  `localStorage["umo-admin-ai-result-v1"]`；结果不自动插入、替换、保存或发布文章。
- 输入按 Unicode code point 限制最多 20,000 字符，并覆盖 429/502/503/504 错误提示；
  取消只中止浏览器请求和等待，不承诺供应商停止处理或计费。
- 新增 10 个 AI 抽屉 Node 测试、9 个 functional 用例和 2 张 Windows 视觉基线；
  API 函数由 28 增至 30，管理端接口总数保持 32。
- 后端移除与 Service code point 校验重复的 Jakarta `@Size` 注解，使 20,000 字符上限对
  emoji 等补充平面字符保持一致；不修改数据库 Schema 或 ECS。
- 远端 AI 结果图片不会在预览中自动请求；Unicode 输入按 code point 限制，浏览器返回或
  SPA 离开时由抽屉状态参与确认。
- 验证：前端 Node 120/120、后端 237 个测试且 0 失败/18 跳过、Windows Playwright 110/110
  （76 functional + 34 visual）和生产构建通过；Linux 视觉基线由 5.1F 统一生成和审查。

## 2026-09-18 — 5.1D 管理端 AI 转换运行时

- 新增 `GET /api/admin/ai/settings`、`GET /api/admin/ai/capabilities` 和
  `POST /api/admin/ai/transform`，管理端接口总数由 29 增至 32，API 总数由 37 增至 40。
- 新增供应商无关 `AiTransformProvider` 和 DeepSeek OpenAI-compatible 适配器；删除未使用的
  Spring AI BOM、OpenAI Starter 和 `spring.ai.openai` 配置，改用 Spring `RestClient`。
- 新增 180 秒超时、10 分钟最多 5 次、同时最多 1 个请求的内存控制，以及
  `EXACT_CONTENT`、`TRANSLATION`、`LIGHT_EXPANSION`、`NONE` 四类结果保真校验。
- AI 默认关闭且不影响现有站点；启用时要求 DeepSeek 密钥和模型。日志只记录元数据，
  不记录正文、结果、提示词或密钥，转换结果不写入文章。
- 验证：后端 `mvn test` 通过，236 个测试、0 失败、0 错误、18 跳过；其中 17 个真实
  MySQL 测试由环境门控。本次不修改数据库 Schema、前端文章编辑器或 ECS。

## 2026-09-18 — 5.1C 管理端 AI 模式设置页

- 新增管理端 AI 设置页面，支持模式列表、新建、复制、编辑、快速启停、排序、历史提示词预览和回滚。
- 页面消费 5.1B 的六个模式接口；提示词或校验策略变化才增加版本，409 冲突保留本地表单。
- 新增 AI 模式表单纯函数、API 封装和浏览器 Mock；新建/复制默认停用，历史回滚生成新版本。
- 验证：前端 Node 109/109、Windows Playwright 99/99（67 functional + 32 visual）和生产构建通过。
  Linux 下 AI 设置的两张视觉基线由 5.1F 统一审查提交；本次不修改后端和 ECS。

## 2026-09-18 — 5.1B 管理端 AI 模式目录后端

- 新增 `ai_transform_modes` 和 `ai_transform_mode_versions`，提供五个默认停用模式及
  version 1；提示词只存在于 Schema、种子和幂等迁移 SQL。
- 新增六个管理端模式接口，支持列表、新建、复制、编辑、历史版本查询和回滚；更新使用
  `expectedVersion` 并发控制，每个模式保留最近 10 版。
- API 总数由 31 调整为 37，数据库表由 10 张调整为 12 张；后端新增 Service、Controller、
  边界和 MySQL 门控测试。
- 本地全量后端测试 `195/195` 通过，其中 `17` 个真实 MySQL 8.4 测试由 CI 环境门控；
  本次不包含真实模型调用、前端 AI 设置页或 ECS 发布。
- Push CI run `35307939024` 五个 job 全部成功；MySQL 8.4 从旧库路径执行两次 AI 迁移，
  并通过门控测试和 31/31 兼容接口冒烟。

## 2026-09-18 — 5.1A 管理端 AI 产品与数据契约

- 完成管理端 AI 总索引、产品、前端、状态快照和会话入口的契约拆分，明确 `5.1A` 已完成，
  `5.1B–5.1F` 仍待实施。
- 冻结五个默认模式、四个校验策略、九个管理端接口、四个环境变量、两个浏览器存储键，
  以及模式、版本、能力、转换请求、结果和 Token 使用字段。
- 确认 AI 草稿和结果只保存在管理员浏览器，AI 结果不自动写入文章，真实模型与隐私策略
  尚未进入运行时代码；本阶段未修改 Java、Vue、数据库 Schema、API 或运行时配置。

## 2026-09-17 — 正式版 `v1.0.0`

- 将当前 `master` 发布为正式版 `v1.0.0`，发布提交为
  `fe008c59cb71c69f6cdf8e46be54bf124ee50c1b`，发布 CI run `35232962034`；annotated tag
  `v1.0.0` 已推送并确认 peel 后指向发布 commit。
- 后端 image ID 为
  `sha256:0df8ad67702518c36490b6fb13d645946ac73a1e22c133bff1f2fafae9c7a82e`，前端 image ID 为
  `sha256:4ef6cb550c655e32506816861399169500ae3ee15f7516ea366febce6a4d3e73`。
- 发布归档 SHA-256 为
  `481c0a4ab1738dd5537a118caface215dc45ee0a20154152794a76b087751b68`，
  大小 175685632 字节。
- 独立 `Verify` 返回正式版、manifest 双镜像 ID 和 `operation=deploy`；ECS `api-smoke.py`
  31/31 通过。公网浏览器再次确认目录常驻、列表加粗和双主题页面无回归。
- 当前生产版本为 `v1.0.0`；开发机保留 v1.0.0 与 rc.8 归档，rc.8 为正式回滚目标。
  本次没有 API、数据库、Markdown 存储或认证契约变更。

## 2026-09-17 — ECS 发布 `v1.0.0-rc.8`

- `v1.0.0-rc.8` 从提交 `12bc8111cf36` 构建，发布 CI run `35231477064`；annotated tag
  `v1.0.0-rc.8` 已推送并指向发布 commit。
- 后端 image ID 为
  `sha256:3f1bdf43c4eea8308d34e2c25c25c10ca0de105a070a4875f7ce236a34f69f53`，前端 image ID 为
  `sha256:afa918d2ece757c35349a72482784929770559fd5c7d7a06015881963bb5f0a9`。
- 发布归档 SHA-256 为
  `137405c8e6e5927169cc17d4dfd52ed3c8c0e27e10ddc617c131283dbc3080e5`，
  大小 175685632 字节。
- 独立 `Verify` 返回 rc.8、manifest 双镜像 ID 和 `operation=deploy`；ECS `api-smoke.py`
  31/31 通过。公网浏览器验证目录滚动前后常驻且 1219px 不显示抽屉，目标列表加粗字重为
  `700`，正文无字面 `**`。
- 当前生产版本为 `v1.0.0-rc.8`；开发机保留 rc.8 与 rc.7 归档，rc.7 为正式回滚目标。
  本次没有 API、数据库、Markdown 存储或认证契约变更。

## 2026-09-17 — 文章目录常驻与邻接 Markdown 加粗修复

- 桌面文章目录始终保留在 sticky 侧栏，目录最高 `50vh` 并在侧栏内部滚动；移除了按自然高度
  切换桌面抽屉的测量逻辑，`980px` 及以下继续使用可关闭的悬浮目录。
- Markdown 渲染新增邻接加粗兼容：`**内容，**紧接正文` 等写法会正常输出 `<strong>`，
  不再在无序列表中显示字面星号。
- 验证：Node 96/96、Windows Playwright 95/95（65 functional + 30 visual）和生产构建通过；
  API、数据库、Markdown 存储格式和认证契约未修改。

## 2026-09-17 — ECS 发布 `v1.0.0-rc.7`

- `v1.0.0-rc.7` 从提交 `eace1351adc8` 构建，发布 CI run `35225558649`；annotated tag
  `v1.0.0-rc.7` 已推送并确认指向合并提交。
- 发布前备份 `umoweb-backup-20260917T131428Z-unknown.tar.gz` 通过外层 SHA-256
  `f12d88eb33e180ca7a23449a1d1b1eca541ddf167a77ca758e3573b602869de6`
  和内层清单校验，归档大小 8029102 字节。
- 后端 image ID 为
  `sha256:d12137c905e8568cd8140fed9c37dab7faf6d72418776cf7a2915bd999ed70ca`，前端 image ID 为
  `sha256:acd2618cfd95a8ab76aadf1cc87e09f03c3232676e6772109a563683bb432d38`。
- 发布归档 SHA-256 为
  `1d5b474d3154958722aefc6c1452abf4fa6e61de06393014f89781f1a5778382`，
  大小 175685632 字节。
- 独立 `Verify` 返回 rc.7、manifest 双镜像 ID 和 `operation=deploy`；ECS `api-smoke.py`
  31/31 通过，公开端、工具中心、游戏中心与四条游戏路由均返回 200。
- 当前生产版本为 `v1.0.0-rc.7`；开发机保留 rc.7 与 rc.6 归档，rc.6 为正式回滚目标。
  本次没有 API、数据库、Markdown 存储或认证契约变更。

## 2026-09-17 — 抽屉模式隐藏目录占位修复

- 桌面文章进入目录抽屉模式后，隐藏目录改为不参与布局，侧栏高度只保留可见篇章信息；
  滚动后粘性边界不再被隐藏目录撑出视口。
- 长目录浏览器回归新增隐藏目录零高度、侧栏小于半屏及视口内粘性边界断言。
- 验证：Node 99/99、公开端 functional 22/22、Windows 桌面与移动视觉回归 30/30 和生产构建通过；
  未修改 API、数据库、Markdown 格式或目录交互。

## 2026-09-17 — Task 4.4 编辑器滚动协同与本地工具入口

- 公开在线编辑器、管理端文章编辑器和 About/Project 设置编辑器统一为等高工作区；桌面分栏按
  可滚动比例双向同步，连续事件通过 `requestAnimationFrame` 合并，移动端单面板不启用同步。
- 公开导航抽为共享配置，新增与“游戏”同级的“工具”入口和 `/tools` 工具中心；工具中心与
  在线编辑器保持工具栏目激活态，首页新增独立工具模块，当前只包含 Markdown 编辑器。
- 补齐 2 张 Linux 工具中心快照并更新首页、移动端页脚等受影响基线；Linux 基线工作流
  `35216777902` 生成 artifact 后完成人工审查。
- 验证：Node 99/99、Windows Playwright 95/95（65 functional + 30 visual）、Linux 基线工作流
  成功和生产构建通过；未修改后端 API、数据库、Markdown 存储格式或编辑器草稿键。

## 2026-09-17 — Task 4.4 Task 1 文章目录滚动与半屏抽屉

- 桌面文章目录保持 sticky，目录自然高度严格超过 `50vh` 时锁定为半屏抽屉；目录隐藏后
  不反复切换模式，文章或视口变化时重新测量。
- `980px` 以下继续使用原悬浮目录，桌面抽屉复用相同 trigger/panel、Escape、点击外部关闭、
  选择后关闭和焦点恢复行为。
- `body` 横向裁剪由 `hidden` 改为 `clip`，恢复正常的窗口滚动和 sticky 定位；窄屏侧栏
  网格迁移到内容包装层，保持原有移动端视觉。
- 验证：Node 93/93、Windows Playwright 85/85（57 functional + 28 visual）和生产构建通过，
  原有 28 张视觉基线无需更新。

## 2026-09-17 — Phase 4 ECS 发布 `v1.0.0-rc.6`

- `v1.0.0-rc.6` 从提交 `67b3c9f866d4` 构建，发布 CI run `35183870810`；annotated tag
  `v1.0.0-rc.6` 已推送。
- 发布前备份 `20260917T044123Z` 通过外层 SHA-256 和内层清单校验，备份 SHA-256 为
  `4a3f1e8ec2b62c9adee381cfc60b32bfc6f27891e41ead02d0d515567e3e1c98`。
- 修复发布脚本一次性正文回填未禁用调度器导致进程不退出；修复后回填 29 篇已发布正文并正常结束。
- 独立 `Verify` 返回 rc.6 和 manifest image ID，ECS `api-smoke.py` 31/31 通过。
- 当前生产版本为 `v1.0.0-rc.6`；开发机保留 rc.5 与 rc.6 归档，rc.5 为当前回滚目标。

## 2026-09-17 — 稳定相关阅读移动端布局回归

- 相关阅读移动端测试改用不受入场动画影响的布局坐标，消除 Linux Chromium 上 1px 时序抖动。
- 验证：目标用例 20/20、Node 89/89、Windows Playwright 81/81 通过。

## 2026-09-16 — Task 4.3 批量管理与定时发布

- 内容状态扩展为 `DRAFT`、`SCHEDULED`、`PUBLISHED`、`ARCHIVED`，新增 `scheduled_at`
  字段与 `(status, scheduled_at)` 索引。
- 管理端文章列表支持当前页批量添加/移除分类与标签、归档和恢复草稿；请求整批预检并在单事务提交，
  失败返回逐项 `failures`，不产生部分更新。
- 文章编辑器支持仅未发布草稿设置未来 `scheduledAt`；调度器默认每 30 秒扫描到期内容，
  记录原计划发布时间并同步正文索引，停机补发和重复轮询保持幂等。
- `api-smoke.py`/`api-smoke.ps1` 升级为 31/31，覆盖待发布公开隔离、批量操作和既有完整链路。
- 验证：后端 164/164（11 个 MySQL 环境门控）、MySQL 8.4 Mapper 11/11 与 31/31 冒烟、
  89 个 Node 测试、Windows/Linux Playwright 81/81（53 functional + 28 visual）通过。

## 2026-09-15 — Task 4.2 图片一致性检查

- 新增管理端 `GET /api/admin/images/integrity`，按需检查断裂引用、数据库记录缺文件和磁盘孤立文件；
  异常结果返回可定位的文章或固定页来源，不自动修复也不持久化历史。
- 一致性扫描严格读取全部文章正文、About/Project、`images` 记录和存储目录普通文件；读取或遍历失败
  返回 500，避免输出可能误导的“无异常”报告。
- 图片管理页新增检查按钮、三类计数、来源明细、扫描时间和错误态；删除图片后旧报告立即失效。
- 新增后端单元、控制器、MySQL 门控和 Playwright 回归，并把 `api-smoke.py`/`api-smoke.ps1` 升级为
  30/30。相关阅读 PR 合并后，本功能分支改指向 `master`。
- 验证：后端 145/145、MySQL 8.4 集成 10/10 与 30/30 冒烟、Node 82/82、
  Windows/Linux Playwright 79/79 和生产构建通过。

## 2026-09-15 — Task 4.1 收口：相关阅读

- 公开文章详情新增最多 4 篇 `related`，只返回 `PUBLISHED`，并排除当前及前后篇。
- 相关排序固定为共享标签数 × 3、共享分类数 × 2、同类型 1 分，再按发布时间和 ID 倒序；
  查询失败只隐藏推荐区，不影响正文和前后导航。
- 文章详情新增响应式“相关阅读”，桌面双列、700px 以下单列；管理端详情不返回该字段。
- 正文内检索明确由浏览器原生查找承担，本轮不新增站内搜索 UI 或服务端依赖。
- 验证：后端 132/132（9 个 MySQL 环境门控）、MySQL 8.4 门控测试 9/9 与 29/29 冒烟、
  Node 82/82、Windows/Linux Playwright 各 76/76（48 functional + 28 visual）和生产构建通过；
  已更新 `post-detail` Windows/Linux 视觉基线。

## 2026-09-14 — Task 4.1 文章目录与阅读进度

- 公开文章详情从 Markdown 自动生成 H1-H3 目录，忽略首个文档 H1 和“目录”章节；
  重复标题使用稳定序号，旧版标点和显式 span 锚点通过兼容别名保持可达。
- 桌面端在篇章信息侧栏展示目录，980px 以下使用悬浮面板；支持分支展开、当前章节与祖先高亮、
  Escape/点击外部关闭和关闭后焦点恢复。
- 正文滚动范围计算阅读进度，仅在内容足够长时显示固定顶部进度条；目录点击同步 URL hash、
  当前章节和进度。
- 新增受限旧版标题 span 解析、已知旧锚点 dry-run/备份/原子迁移工具及回归测试，不改变正文存储格式。
- 验证：前端 Node 82/82、内容工具 Python 10/10、Windows Playwright 74/74
  （46 functional + 28 visual）和生产构建通过；现有 28 张视觉基线无需更新。

## 2026-09-14 — 录入 Post-v1 后续路线

- 新增 Post-v1 路线，明确第四阶段为非 AI 阅读与编辑效率，第五阶段为后置的管理端 AI，
  公开 AI 能力最后单独评审。
- 阅读增强包含目录与滚动高亮、阅读进度、正文内搜索和确定性相关文章。
- 管理效率包含批量标签/分类/归档、图片引用失效检查和定时发布；文章修订历史不再纳入规划。
- 管理端 AI 草稿转换器只使用浏览器本地存储，支持小窗化、结果编辑和重新转换覆盖，
  不进入数据库、服务器备份或文章正文。

## 2026-09-14 — `v1.0.0-rc.5` ECS 发布

- `v1.0.0-rc.5` 从提交 `2019c7521844` 构建并部署，发布 CI run `34813314026`；
  annotated tag `v1.0.0-rc.5` 已推送。
- 后端 image ID 为
  `sha256:5facf2c38922884123da1272b3e4cb50c0d597fdb12bc17929f88efe87c4a937`，
  前端 image ID 为
  `sha256:4d365dbae44b54f65fa0448243c69be2055f754732be3f770a6dba0465be4f58`。
- 发布归档 SHA-256 为 `5ae8b7f215fc3f3bc4a28826895951aa5508816c044d681041ced8b93bcbdee2`，
  大小 175642112 字节。
- ECS `api-smoke.py` 29/29 通过；独立 `Verify` 返回 rc.5 和 manifest image ID，公网入口健康。
- 当前生产版本为 `v1.0.0-rc.5`；开发机保留 rc.4 和 rc.5 归档，rc.4 作为正式回滚目标。

## 2026-09-14 — 修复 Stroop 色词显示判定

- Stroop 五色文字统一使用对应按钮色源；黄色试次不再因未定义的 `--game-gold` 回退到正文色。
- 黑色试次在亮暗主题均使用黑色按钮填充，并分别增加略暗、略亮于游戏板背景的描边。
- 数字键处理忽略 `event.repeat`，长按不会自动消耗后续试次并污染正确率。
- 浏览器回归新增五色与按钮同色、黑色双主题描边和键盘自动重复场景；
  验证为 Node 72/72、Playwright 71/71（43 functional + 28 visual）及生产构建通过。

## 2026-09-14 — Task 3 ECS 发布

- `v1.0.0-rc.4` 从提交 `c9c9f8ee50e5` 构建并部署，发布 CI run `34807582609`。
- ECS 在切换镜像前执行全部幂等迁移，创建 `image_cleanup_queue`、`content_search` 和 ngram
  FULLTEXT 索引；回填 28 篇已发布正文并校验索引计数一致。
- 发布前备份完成 SHA-256 与内层清单校验；发布后 `content_search=28`、
  `image_cleanup_queue=0`，29/29 接口和全部核心公开路由通过。
- 管理员原密码在冒烟后保持有效；备份、访问维护和报表 systemd 服务均 active。
- 当前开发机归档保留 rc.3 和 rc.4，rc.3 作为回滚目标；Task3 本地和远端特性分支已清理。

## 2026-09-14 — 修复版本化发布后的 ECS 备份

- 备份脚本改为从 `.env.docker` 读取当前前后端镜像标签，不再在版本化发布清理 `:latest` 后
  因镜像缺失而失败。
- ECS 备份 timer 安装器移除旧的固定镜像环境变量；发布入口同步备份脚本和 systemd 单元，并
  在发布时刷新 timer 配置。
- 备份表行数统计将 `image_cleanup_queue` 视为可选表，使 Task3 迁移前的旧 Schema 也能完成
  发布前一致性备份。
- 备份单元测试新增「Compose 标签覆盖陈旧 timer 标签」和「缺少环境文件回退 latest」场景。

## 2026-09-14 — Task3 发布链路收尾

- 发布入口同步全部版本化 SQL 迁移，部署在切换镜像前按文件名顺序执行幂等迁移并校验新增表
  和正文 ngram 索引。
- 新后端启动后自动回填已发布正文索引，索引行数与 `PUBLISHED` 内容数不一致时回滚镜像；
  显式回滚不重复执行迁移。
- 发布入口同步便携接口冒烟脚本；Python 和 PowerShell 冒烟均增加失败时恢复原管理员密码的
  保护路径，ECS 验收目标保持完整 29/29。
- 发布脚本自测覆盖迁移顺序、迁移失败阻断、索引回填失败、回滚不迁移和发布控制文件同步。

## 2026-09-14 — 游戏交互与暗色主题细化

- 游戏页顶部改为左侧英文眉题、右侧“返回游戏中心”的同排布局。
- 游戏主次操作统一尺寸与圆角，保留实心主操作和描边次操作差异。
- 暗色主题主按钮移除黄黑偏移阴影，改用克制的深色投影；Stroop 黑色键改为深灰底浅色字。
- 倒背数字反馈不再自动消失，必须点击“继续”或按 Enter/空格后进入下一题。
- 扑克牌初始显示背面，点击开始后正面背诵三秒，再翻回背面等待一秒后提问；
  牌背与牌面颜色随亮暗主题调整。
- 返回游戏中心与游戏内容板右边缘对齐；扑克牌始终单行展示并按数量缩放；
  舒尔特结果统计实际点击次数，包含错误和重复点击。
- 页脚“隐私”文案改为“隐私收集”，增加两类暗色游戏视觉基线。
- 验证：前端 Node 72/72，Windows Playwright 69/69（41 functional + 28 visual）。

## 2026-09-14 — 四个训练游戏

- 新增 `/games` 游戏中心和 Stroop、倒背数字、扑克牌记忆、舒尔特四条游戏路由；
  主导航、页脚和首页均提供公开入口。
- 四款游戏迁移为 Vue 状态与渲染，保留原计分、试次、升级阈值、随机逻辑和三组旧
  `localStorage` 成绩键；倒背数字继续不持久化成绩。
- Stroop 作答后立即进入下一试次；倒背数字正确/错误反馈分别约 500/1000ms；
  扑克牌保留三秒记忆倒计时并将翻牌与反馈压缩到 200/700ms；结果弹窗立即出现。
- 游戏路由取消全屏擦除动画，页面使用自然流和容器相对尺寸；支持 320×568、
  390×844、844×390 横屏、768×1024 和 1440×900。
- 增加可注入随机源的纯逻辑测试，以及游戏中心、四款流程、旧成绩兼容、高密度网格、
  10 张牌、10 位数字、减少动态和结果彩纸浏览器回归。
- 修复四个游戏页面多根节点导致返回游戏中心时路由转场卡住的问题，并增加返回路径回归。
- 调整游戏页标题层级、主次操作按钮和舒尔特数字字号，统一视觉家族并保持操作层级差异。
- 验证：前端 Node 72/72，Windows Playwright 64/64（40 functional + 24 visual），
  Linux Chromium 24 张基线生成并审查，生产构建通过；Task 3.3–3.5 已按 PR #14、#15、#16
  顺序合并 master。

## 2026-09-13 — Markdown 正文全文搜索

- 新增 `content_search` 表和 MySQL 8.4 `FULLTEXT ... WITH PARSER ngram` 正文索引，
  同步更新建库脚本与幂等迁移。
- 文章发布或更新时在内容事务内同步正文索引；转为草稿时删除索引，删除文章由外键级联清理。
- 新增可重复执行的正文回填入口和脚本，正文文件缺失时保留标题/摘要搜索并记录警告。
- 公开搜索覆盖标题、摘要和 Markdown 正文，只返回 `PUBLISHED`；正文命中时列表返回 `excerpt`。
- 搜索页和内容卡片支持正文命中摘要及移动端无横向溢出回归。
- MySQL 8.4 CI 从空库验证迁移、两次回填、索引行数、中文 ngram 查询和现有 29/29 接口冒烟。

## 2026-09-13 — 图片删除与引用保护

- 新增管理端图片列表 `GET /api/admin/images` 与删除 `DELETE /api/admin/images/{id}`；
  支持全部、使用中和未引用筛选，删除成功返回 204，被引用返回 409。
- 引用扫描覆盖全部草稿和已发布文章，以及 About/Project 固定页，只识别规范 `/images/...` 路径。
- 新增 `image_cleanup_queue` 和幂等迁移；删除事务登记待删文件，提交后清理，失败记录次数与错误，
  并在应用启动及后续图片操作前重试。
- 新增管理端图片管理页、引用状态、缩略图、分页、删除确认和 409 提示。
- 扩展 Java、MySQL 8.4、Node、Playwright 和 Python/PowerShell 冒烟覆盖；
  当前接口总数由 27 调整为 29。
- 修复不存在静态资源被通用异常处理器转为 500 的问题，已删除图片现在按契约返回 404。

## 2026-09-13 — 分类筛选包含子分类

- 公开端和管理端文章列表新增 `includeDescendants`；默认 `false` 保持 `categoryId` 精确匹配，
  启用后包含当前分类的全部后代。
- 新增共享 `CategoryHierarchyResolver`，一次读取分类树、去重、检测循环并限制最多 32 层；
  命中循环或超深层级返回 409，分类不存在仍返回 200 空结果。
- Mapper 使用解析后的分类 ID 集合和 `EXISTS IN`，内容不重复；排序在时间字段后追加
  `id DESC` 保证稳定次序。
- 书库选择分类时 URL 和 API 默认携带 `includeDescendants=true`，显式 `false` 可保持精确匹配。
- 新增后端解析/Service/Mapper 测试、真实 MySQL 集成测试、Python/PowerShell 冒烟断言和
  Playwright 子分类内容用例。

## 2026-09-13 — 管理端 Markdown 导入

- 新建文章页新增单文件 `.md`/`.markdown` 导入，解析 YAML front matter 并预填现有编辑器。
- 缺少 front matter 字段时从首个 H1、文件名和默认值回退；类型默认 `NOTE`，状态默认 `DRAFT`。
- 支持 `title`、`slug`、`summary`、`type`、`status`、`categorySlugs`、`tagSlugs` 和 `metadata`，
  同时接受 `categories`、`tags` 别名。
- Markdown 图片只允许 HTTP(S) 和 `/images/...`；相对路径及其他协议显示非阻断警告。
- 导入继续使用现有文章创建接口和文件事务，不新增 API、Schema 或 Docker 配置；重复 slug 不覆盖。
- 导入错误会阻止提交，修正对应字段后解除；分类 slug 必须与内容类型一致，H1 检测忽略代码块，表格图片同样检查。
- 新增 `yaml` 2.9 前端依赖、Node 解析测试、Playwright 导入流程和后端成功创建回归测试。
- 验证：后端 84/84、前端 Node 58/58、Windows Playwright 39/39、生产构建通过。

## 2026-09-13 — 访问信息统计与安全日志

- Nginx 新增六字段 JSON 访问日志，只记录 IP、ISO 8601 时间、方法、无查询字符串的真实路径、
  状态码和响应字节数，不记录请求体、Cookie、Authorization、Referer 或 User-Agent。
- 新增主机级访问维护脚本：每日轮转、gzip、7–30 天原始日志清理、默认 180 天匿名聚合、
  管理员权限短期报表和仅监听 `127.0.0.1` 的专用非 root 报表服务。
- 新增公开 `/privacy` 说明和运行时 `/privacy-config.json`，保留期取自服务器访问配置；
  配置读取失败时页面不展示未经确认的天数。
- 发布 manifest 增加可选 `accessPolicy`，同时保持旧 manifest 和 rc.1 回滚兼容。
- `v1.0.0-rc.3` 由提交 `59c6971200c5` 构建并部署；ECS 27/27 接口、公网入口、文件权限、
  六字段日志和回环报表均通过。
- rc.2 首次发布暴露 ECS 未同步 Compose、Nginx 创建日志的 umask 和同路径脚本权限问题；
  PR #9 修复后，发布入口会同步 Compose 与访问脚本，前端镜像设置 `umask 0027`。

## 2026-09-13 — 本地镜像发布与回滚

- 新增 `scripts/release/` 开发机发布入口和 ECS 控制脚本，以版本 + commit 双标签保存前后端镜像。
- 发布硬门禁为当前 commit 存在成功的 `CI` push 运行，并校验归档 SHA-256、image ID 和前端管理路径哈希。
- 开发机只保留最近两个版本归档；ECS 发布期间删除上传归档，只保留当前运行镜像和 `current.json`。
- 发布、回滚和恢复均执行 MySQL/backend 健康、frontend 运行、首页、公开 API 和管理员登录检查。
- 完成 `v1.0.0-rc.1` 发布、`baseline-20260913` 回滚和 rc.1 恢复演练；三种阶段均通过公网检查。

## 2026-09-13 — 真实 MySQL 集成验证

- GitHub Actions 新增 MySQL 8.4 集成 job，从空库执行 Schema、种子数据和兼容迁移，
  重复执行迁移验证幂等性，并校验列、索引、外键、种子行数和孤儿关系。
- 集成 job 使用 Java 17 构建并启动后端，以独立临时存储和运行时生成的管理员/JWT
  测试凭据执行便携接口冒烟。
- `api-smoke.py` 与 `api-smoke.ps1` 补齐公开类型/分类/标签筛选、详情关联、前后文章、
  草稿隔离、密码失效、图片上传和 429 断言。
- CI 冒烟达到 27/27 后继续校验上传图片文件和测试资源清理，MySQL 服务、数据与临时文件
  随 runner 销毁。

## 2026-09-13 — Linux Playwright 与独立视觉基线

- 将已通过检查的 Task 2.1 基础 CI 合入 `master`。
- Playwright 按平台选择浏览器：Windows 默认本机 Chrome，Linux CI 使用锁定的 Chromium；
  支持 `PLAYWRIGHT_CHANNEL` 显式覆盖。
- GitHub Actions 新增 Ubuntu `browser` job，运行 20 个 functional 和 14 个视觉断言；
  失败时上传 Playwright 报告、trace 和失败截图。
- 新增 14 张 `linux` 视觉快照，与现有 14 张 `win32` 快照并存；同一提交连续两轮 Linux CI 通过。
- 新增手动 `Playwright Linux Baselines` 工作流，生成 Linux 快照 artifact 供人工审查，
  不自动提交仓库。
- 保持浏览器测试使用 Mock API，真实 MySQL 集成仍由 Task 2.3 承接。

## 2026-09-13 — CI 基础流水线

- 将 Task 1.2 备份恢复和 Task 1.3 正式内容导入按任务拆分合并到 `master`。
- 新增 `.github/workflows/ci.yml`，在 PR 和 `master` push 时运行仓库检查、后端测试、前端测试和构建。
- 新增无第三方依赖的敏感信息扫描器及自测，覆盖公开 IP、ECS 实例 ID、AccessKey、Token、私钥和环境文件。
- GitHub Actions 固定使用 Ubuntu、Temurin Java 17、Node 24.12.0 和只读仓库权限。
- 当前私有仓库套餐不支持分支保护或规则集，CI 失败不能自动阻止合并；Task 2.4 将把 CI 成功作为发布门禁。

## 2026-09-12 — 正式内容导入与凭据轮换

- 新增 `scripts/content-import/`，支持从无 front matter 的 Markdown 目录生成可恢复候选包。
- 导入 28 篇正式笔记、18 个分类、22 个标签和 91 张本地图片；两篇导航索引转为 About/Project 配置，
  不进入公开文章。
- 为 28 篇正文逐篇人工整理公开摘要，目录映射优先使用显式摘要，不再以正文首段作为默认概要。
- 候选包重写本地图片与 Markdown 内链、按内容哈希去重图片，并显式使用 `umo:umo` 文件所有权。
- 正式环境替换演示数据库和 `app_data`，轮换数据库密码、JWT secret 和管理员凭据。
- 修复隔离恢复项目名大写兼容问题，并新增 `api-smoke.py` 便携 27/27 验收入口。
- 最终正式备份从空卷恢复后再次通过 27/27；公开首页为 HTTP 200，公网内容总数为 28。

## 2026-09-12 — 备份与恢复闭环

- 新增 `scripts/backup/` Bash 运维链路，覆盖一致性备份、SHA-256 校验、导出、隔离恢复、
  清理和 systemd timer 安装。
- 备份会短时停止 frontend/backend，逻辑导出 MySQL、归档 Markdown/图片，并记录数据库版本、
  表行数、文件数量、耗时和逐文件校验清单。
- 默认每周日 03:30 自动运行，保留最多 6 份且总大小不超过 8GiB；归档和校验文件权限为 `0600`。
- 隔离恢复使用 `umoweb-restore-*`、独立卷/网络和 `127.0.0.1:18080`，拒绝触碰生产项目。
- `compose.yaml` 为前后端增加显式镜像变量，使隔离恢复可直接复用已加载镜像。
- `api-smoke.ps1` 改为自建临时分类、标签、草稿和已发布内容，不再依赖固定演示 slug。
- 本地与 ECS 备份/恢复均完成验证，ECS 归档跨主机恢复后 27/27 冒烟通过。

## 2026-09-12 — v1 正式发布范围冻结

- 新增 `docs/project/release-checklist-v1.md`，冻结公开端、管理端和 27 个接口的 v1 发布范围。
- 明确 Markdown 导入、子分类筛选、图片删除、全文搜索、训练游戏、访问统计、CI/CD、多实例限流、
  RBAC、AI 和搜索收录优化不进入 v1 正式发布范围。
- 定义 `release-owner`、`platform-operator`、`data-verifier`、`acceptance-owner` 四类发布职责。
- 建立正式数据、凭据、备份恢复、HTTPS、可信代理、接口冒烟、草稿隔离、回滚点和敏感信息等阻断项。
- 定义数据/安全事件即时回滚、核心入口一次修复失败后回滚，以及其他阻断项 30 分钟无法修复时回滚。
- 明确当前阿里云 ECS 原地升级为正式环境的路径；Task 1.2 至 Task 1.4 尚未执行。

## 2026-09-12 — 阿里云 ECS 公网测试部署

- 在单台阿里云 ECS 的 Ubuntu 24.04 环境完成 Docker Compose 全栈部署，部署目录为
  `/opt/umoweb`，入口为 Nginx 宿主 TCP 80。
- 因 ECS 外部仓库访问受限，采用开发机构建镜像、SHA-256 校验、传输后 `docker load`
  的方式交付，并使用 `compose up -d --no-build --wait` 启动。
- 明确安全组和 UFW 只放行 TCP 80，MySQL 3306 与后端 8080 不暴露公网。
- 验证首页、公开 API、管理员登录和公网访问均为 200，三个容器健康。
- 明确 `.env.docker` 仅保留服务器侧；真实实例标识、公网地址、随机管理路径、密码和密钥
  均不进入仓库。
- 当前仍为无域名 HTTP 测试部署，使用演示数据；HTTPS、正式内容和 CI/CD 尚未完成。

## 2026-09-12 — Docker 全栈启动与部署基线

- 新增 MySQL 8.4、Spring Boot 后端和 Nginx 前端的 Docker Compose 全栈，默认一条命令启动。
- 新增 `scripts/docker-up.ps1`，首次运行自动生成数据库密码、JWT secret 和管理员密码到被忽略的 `.env.docker`。
- MySQL 空数据卷首次启动导入 schema、种子数据；后端镜像包含 6 篇演示 Markdown。
- 新增 `mysql_data` 和 `app_data` 命名卷，分别持久化数据库与 Markdown/图片。
- Nginx 提供 SPA fallback，代理 `/api`、`/images`，覆盖客户端 `X-Forwarded-For`，支持 50MB 图片边界。
- `TRUSTED_PROXIES` 扩展为精确 IP 与 IPv4/IPv6 CIDR，保留多级可信代理链行为并对非法配置快速失败。
- Playwright 改用可清理的 Node 静态服务器包装器，修复 Windows 上 Vite preview 在测试完成后残留的问题。
- 数据库初始化脚本显式使用 `utf8mb4`，并让内容种子重复执行时刷新完整字段，修复容器首次导入后的中文乱码。
- 新增 Docker 运行的端口冲突处理、日志、备份/清空、正式部署注意事项和完整运行指南。
- 验证结果：后端 83 tests、前端 46 tests、生产构建、Playwright 34 checks、Docker MySQL 8.4 下 27/27 接口冒烟、2MB 上传和卷重启持久化通过。

## 2026-09-11 — Playwright 浏览器 E2E 与视觉回归

- 新增 `@playwright/test` 本地测试体系，使用本机 Chrome channel、Vite 生产预览和状态化 Mock API。
- 新增 16 个 functional 浏览器用例，覆盖公开端筛选/搜索/详情/错误态、在线编辑器草稿/导入/下载/安全预览，以及管理端认证、文章、分类、标签、站点设置和改密。
- 新增 14 个视觉断言，覆盖首页亮暗主题、书库、文章详情、编辑器、登录和管理列表的桌面与 390px 布局。
- 新增 `test:e2e`、`test:e2e:update` 和 `test:all`；浏览器套件不依赖 MySQL，真实接口继续由 `api-smoke.ps1` 验证。
- 修复 lockfile 中 `postcss` / `nanoid` 的高危开发依赖告警，`npm audit` 为 0 vulnerabilities。
- 验证结果：46 个 Node 测试、30 个 Playwright 检查和前端生产构建通过；视觉基线连续运行稳定。

## 2026-09-11 — 公开在线编辑器

- 将 `/editor` 从占位页升级为纯浏览器 Markdown 编辑器，不调用后端或新的编辑器依赖。
- 支持 `.md`/`.markdown` 导入、文件名规范化、已有内容替换确认、UTF-8 下载和清空。
- 使用原生 textarea 与现有安全 Markdown 渲染器实现桌面分屏预览和 390px 编辑/预览切换。
- 草稿写入 `localStorage["umo-editor-draft-v1"]`，输入后防抖保存，路由离开/页面隐藏前刷新，重新进入自动恢复。
- 新增编辑器文件与草稿单元测试；桌面、移动端、亮暗主题、刷新恢复和安全预览通过浏览器检查。
- 验证结果：前端 46 tests、生产构建通过。in-app browser 未派发原生 confirm/download 自动化事件，替换路径已执行，Blob 编码由单元测试覆盖。

## 2026-09-11 — 管理端业务闭环

- 完成分类管理：按类型筛选树形结构，支持新建、编辑、删除、父级和排序值；编辑时排除自身及后代，阻止父级循环。
- 分类编辑通过详情接口读取 `parentId` 和 `sortOrder`，避免树接口缺少字段导致父级与排序被重置。
- 完成标签管理：列表增改删、字段校验和受关联内容保护的 409 提示。
- 完成站点设置：统一读取和保存站点标题、副标题、About、Project；Markdown 编辑预览、未保存保护、部分保存反馈和站点缓存刷新已接入。
- 完成后台修改密码：独立受保护页面，成功后清理本地 token，并在登录页提示重新登录。
- 凭据类 401 不再被全局拦截器误清理；仅登录和“旧密码错误”留在当前页面，token 过期等其他 401 仍会清理会话。
- Markdown 链接和图片仅允许 HTTP、HTTPS、Mailto 与相对 URL，阻止 `javascript:`、`data:` 等可执行协议，并转义图片 alt 属性。
- 验证结果：前端 41 tests、生产构建通过；使用本地 mock API 完成桌面与 390px 分类、标签、站点设置、改密页和公开标题刷新检查。实际密码轮换未在浏览器提交。

## 2026-09-11 — 管理端文章管理闭环

- 完成管理端文章列表：类型、状态、分类、标签、排序筛选，分页、状态展示、编辑和删除。
- 完成文章编辑器：新建/编辑、分类标签、metadata JSON 校验、Markdown 实时预览、未保存保护。
- 支持图片选择、拖拽和剪贴板粘贴上传，并在 textarea 当前光标处插入 Markdown 图片语法。
- 管理端布局改为纸本主题响应式侧栏/移动抽屉，登录页改为独立顶层路由。
- `ContentListVO` 新增 `status`；公开接口固定返回 `PUBLISHED`，管理端返回 `DRAFT` 或 `PUBLISHED`。
- 修复 `/images/**` 将 `${app.storage-path}` 当作字面量导致的静态图片 500。
- 扩展真实接口冒烟脚本，断言公开和管理端状态字段。
- 验证结果：后端 79 tests、前端 29 tests、生产构建、隔离 MySQL 5.7 下 27/27 接口冒烟和桌面/390px 浏览器检查通过。

## 2026-09-11 — 公开端真实 API 闭环

- 首页、书库、搜索、文章详情、About 和 Project 接入现有 8 个公开 API。
- 移除运行时静态 fixtures，新增统一加载、空数据、错误、404、429 和重试状态。
- 搜索改为显式提交，`q/page` 同步 URL，并按后端消息显示 429 倒计时。
- 公开详情响应增加 `previous`、`next` 摘要；上一篇为更早内容，下一篇为更新内容。
- 使用隔离 MySQL 5.7 和临时 Markdown 存储完成真实联调，接口冒烟 27/27 通过。
- 浏览器验证桌面与 390px 主路径、筛选、搜索限流、详情导航和后端离线错误态。
- 后端 77 tests、前端 21 tests 和生产构建通过。

## 2026-09-11 — 公开端静态视觉 MVP

- 新增当代古籍纸本视觉系统、公开端布局、页头页脚、内容卡片和主题切换。
- 新增 `light | dark` 双主题，支持系统偏好、本地持久化和无闪烁初始化。
- 完成首页、书库、文章详情、About 和 404 的静态视觉 MVP。
- 书库支持类型、分类、标签本地筛选、分页和 URL 查询参数同步。
- 接入 marked 与按需加载的 highlight.js，禁用 Markdown 原始 HTML。
- 首页增加墨幕、纸面扫光、标题翻起、书封落位、卡片错峰和印记落章；支持减少动态偏好。
- 路由切换改为前进/返回方向感知的左右滑动，主题色幕布从向上退场调整为横向退场。
- 新增主题、静态数据筛选、分页和 Markdown 安全边界测试。
- 验证结果：前端 15 tests 通过，生产构建通过，桌面及 390px 浏览器主路径检查通过。

## 2026-06-26 — 项目初始化

- Spring Boot 4.1.0 + Java 17 项目骨架
- Maven 多模块结构（common/config/controller/mapper/model/service）
- MyBatis + MySQL 数据访问层
- JWT 认证方案集成
- Spring AI (OpenAI) 集成
- Docs-Driven Development 工作流建立

## 2026-06-26 — 文档层完成

- 15 份设计文档：SRS ×2 / 架构设计 ×2 / DDL / 模块设计 ×5 / 依赖 / 工作流
- `docs/design/schema.sql` 可执行建表脚本（8 表 + 初始数据）
- 数据库已建库建表，site_options 初始数据已灌入

## 2026-06-26 — 后端 common 层实现

- `ContentType` / `ContentStatus` 枚举
- `BusinessException` → `UnauthorizedException` / `ForbiddenException` / `NotFoundException`
- `GlobalExceptionHandler` 统一异常响应
- `JwtUtil` JWT 签发/解析/校验
- `FileUtil` MD 文件读写 / 图片路径生成
- `application.yml` 替换 `application.properties`

## 2026-06-26 — 前端项目初始化

- Vite + Vue 3 项目骨架 (`Client Side/umo-web-frontend/`)
- 当前依赖: Vue 3 / Vite 8 / Vue Router 5 / Pinia 3 / Axios / marked / highlight.js / Tailwind CSS 4
- 路由: 7 个公开页面、6 个管理页面、管理布局和 404，管理端 Token 守卫
- API 层: Axios 拦截器 + public.js / admin.js 封装
- 状态管理: auth store / 部分 site store
- 登录页、管理布局和 404 有实际实现，其余业务页面为占位

## 2026-09-10 — 代码基线文档校正

- 新增 `AGENTS.md` 和 `docs/project/codebase-memory.md`。
- 以当前 Controller、DTO、VO、Mapper XML 和前端源码重新校准文档。
- 修正正常响应错误描述：接口直接返回业务数据，不套 `{ code, data }`。
- 修正 API 数量：公开 8 个，管理 19 个，共 27 个。
- 修正新建接口状态：当前统一返回 200，不返回 201。
- 将搜索能力修正为 title/summary，不检索 Markdown 正文。
- 将 `categoryId` 行为修正为精确匹配，不包含子分类。
- 记录公开文章详情当前不返回分类/标签数组。
- 明确前端当前只有登录、管理布局和基础 404 具备实际界面。
- 记录分类/标签删除保护、文件事务、路径安全和分页边界等已知缺陷。
- 后端 21 个测试和前端生产构建验证通过。

## 2026-09-11 — P0/P1/P2 审计修复

- 修复公开路由被错误要求登录的问题，并补充公开路由、404、登录页和受保护路由测试。
- 重构 Markdown 文件一致性：创建不覆盖、更新临时文件+原子替换+回滚恢复、删除采用数据库优先策略。
- 修复分类/标签删除保护，增加按 categoryId/tagId 统计和父分类子节点保护。
- 增加 slug/path 安全校验、storage root 边界检查、上传 MIME+签名校验和固定扩展名。
- 增加 JWT tokenVersion、改密后旧 token 失效、登录失败限流和可信代理配置。
- 修复搜索限流 XFF 绕过和内存记录泄漏，改用原子更新与定期清理。
- 增加 page/size、枚举、metadata、长度和 slug 参数校验，非法输入返回 400。
- 公开内容详情返回 categories/tags，公开端和管理端共享 `ContentVOMapper`。
- 列表关联改为按 contentIds 批量查询，消除随文章数量增长的 N+1 查询。
- 数据库增加必要索引、外键/级联策略，并提供旧库兼容迁移脚本。
- 管理分类/标签接口改用响应 VO，补充前端 `changePassword` API 和 `VITE_ADMIN_PATH`。
- 将前后端源码、配置和测试纳入 Git，继续排除构建产物、依赖、Downloads、.superpowers 和真实 secret。

## 2026-09-11 — 复审缺陷修复

- 统一前端管理路径生成，修复登录跳转、侧边栏和新建文章链接仍硬编码 `/secret-admin` 的问题。
- 限制 page 最大值为 1000000，并防止 offset 整数溢出。
- 将空白 metadata 规范化为 `null`，避免写入 MySQL JSON 列时报错。
- 新增分类父级存在性和循环校验，阻止自引用或父子循环数据。
- 登录失败 key 对用户名执行 trim 和大小写规范化，避免通过大小写/空格绕过限流。
- 登录和修改密码字段增加长度限制。
- CORS 允许来源改为 `CORS_ALLOWED_ORIGINS` 配置，并增加配置测试。

## 2026-09-11 — 数据库迁移副本演练与启动修复

- 使用隔离 MySQL 5.7、旧版 schema、种子数据和异常关系完成备份、副本还原、兼容迁移及幂等验证。
- 修复 MyBatis 未扫描 DTO 别名导致完整 Spring Context 无法启动的问题。
- 修复 `ClientIpResolver` 多构造器未显式注入导致 Bean 实例化失败的问题。
- 将业务 JSON 和校验器从 Jackson 2 迁移到 Spring Boot 4 自动配置的 Jackson 3。
- 新增 `MapperConfigurationTest`、`ClientIpResolver` 容器装配测试和 `ContentVOMapper` Jackson 自动配置测试。
- 新增 `scripts/api-smoke.ps1`，覆盖公开端 8 个和管理端 19 个接口及关键失败路径。
- 验证结果：后端 75 tests 通过，迁移副本连续执行两次通过，接口冒烟 27/27 通过。
