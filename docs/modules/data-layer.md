# Data 层实现

> 基线日期: 2026-09-18
> 路径: `model/`、`mapper/`、`resources/mapper/`

---

## 1. 模型数量

| 类别 | 数量 |
|---|---:|
| Entity | 9 |
| DTO | 15 |
| VO | 18 |
| Mapper 接口 | 12 |
| Mapper XML | 12 |

---

## 2. Entity

### 2.1 User

```java
Long id;
String username;
String passwordHash;
Integer tokenVersion;
LocalDateTime createdAt;
LocalDateTime updatedAt;
```

对应 `users`。

### 2.2 Category

```java
Long id;
String name;
String slug;
Long parentId;
String type;
Integer sortOrder;
LocalDateTime createdAt;
LocalDateTime updatedAt;
List<Category> children;
```

`children` 不是数据库字段，用于树形组装。

### 2.3 Tag

```java
Long id;
String name;
String slug;
LocalDateTime createdAt;
```

### 2.4 Content

```java
Long id;
String title;
String slug;
String bodyPath;
String summary;
String type;
String status;
String metadata;
LocalDateTime createdAt;
LocalDateTime updatedAt;
LocalDateTime publishedAt;
LocalDateTime scheduledAt;
String searchBody;
Integer relationScore;
```

`type` 和 `status` 在 Entity 中仍是字符串；`searchBody` 只承接搜索查询的索引正文，
`relationScore` 只承接相关文章查询的排序分，二者都不是 `contents` 表字段。
`status` 实际值为 `DRAFT`、`SCHEDULED`、`PUBLISHED` 或 `ARCHIVED`。

### 2.5 Image

```java
Long id;
String originalName;
String storedName;
String path;
Long size;
String contentType;
Integer width;
Integer height;
LocalDateTime createdAt;
```

当前上传不解析宽高，因此 `width`、`height` 始终为 `null`。

### 2.6 SiteOption

```java
Long id;
String optionKey;
String optionValue;
LocalDateTime createdAt;
LocalDateTime updatedAt;
```

### 2.7 ImageCleanupTask

```java
Long id;
Long imageId;
String path;
Integer attempts;
String lastError;
LocalDateTime createdAt;
LocalDateTime updatedAt;
```

待清理队列不设置指向 `images` 的外键，因为图片记录和队列任务在同一事务中写入，
前者的删除是预期行为。

### 2.8 AiTransformMode

```java
Long id;
String modeKey;
String name;
String description;
Boolean enabled;
Integer sortOrder;
Integer currentVersion;
LocalDateTime createdAt;
LocalDateTime updatedAt;
```

对应 `ai_transform_modes`。`mode_key` 创建后不可修改；`current_version` 指向当前启用的
提示词版本。

### 2.9 AiTransformModeVersion

```java
Long id;
Long modeId;
Integer versionNo;
String systemPrompt;
AiValidationProfile validationProfile;
LocalDateTime createdAt;
```

对应 `ai_transform_mode_versions`。历史版本不可改写；删除模式时级联删除版本。

---

## 3. DTO

| DTO | 字段 | 校验 |
|---|---|---|
| `LoginRequest` | `username`、`password` | 两者 `@NotBlank` |
| `ChangePasswordRequest` | `oldPassword`、`newPassword` | 均必填，新密码至少 6 位 |
| `ContentSaveRequest` | `title`、`slug`、`body`、`summary`、`type`、`status`、`categoryIds`、`tagIds`、`metadata` | 必填、长度、安全 slug、枚举和 JSON 对象校验 |
| `ContentQuery` | `page`、`size`、`type`、`categoryId`、`includeDescendants`、`tagId`、`status`、`sort`、`q` | page >= 1，size 1-100，type/status 枚举，q <= 200，后代筛选依赖 categoryId |
| `ImageQuery` | `page`、`size`、`usage` | page >= 1，size 1-100，usage 为 `REFERENCED` 或 `ORPHANED` |
| `CategorySaveRequest` | `name`、`slug`、`parentId`、`type`、`sortOrder` | 必填、长度、安全 slug、类型枚举 |
| `TagSaveRequest` | `name`、`slug` | 必填、长度、安全 slug |
| `OptionSaveRequest` | `value` | 必填 |
| `PageResult<T>` | `items`、`page`、`size`、`total` | 分页包装 |
| `AiModeCreateRequest` | `modeKey`、`name`、`description`、`systemPrompt`、`validationProfile`、`enabled`、`sortOrder` | `modeKey` 大写格式、名称/说明/提示词长度和枚举校验 |
| `AiModeCopyRequest` | `modeKey`、`name` | `modeKey` 大写格式和名称长度校验 |
| `AiModeUpdateRequest` | `name`、`description`、`systemPrompt`、`validationProfile`、`enabled`、`sortOrder`、`expectedVersion` | 文本长度、枚举和乐观锁版本 |

`ContentQuery.getOffset()` 在合法输入下计算：

```java
(page - 1) * size
```

Controller 的 `@Valid` 会在进入 Service 前拒绝非法 page/size。

`ContentCategoryLink`、`ContentTagLink` 是批量关联查询的轻量 DTO，分别携带
`contentId/categoryId` 和 `contentId/tagId`。

---

## 4. VO

| VO | 字段 |
|---|---|
| `ContentListVO` | `id`、`title`、`slug`、`summary`、`excerpt`、`type`、`status`、`categories`、`tags`、`metadata`、`publishedAt` |
| `ContentDetailVO` | 继承列表 VO，增加 `body`、`previous`、`next`、`related`；`related` 为 null 时省略 |
| `ContentNeighborVO` | `id`、`title`、`slug`、`publishedAt` |
| `CategoryTreeVO` | `id`、`name`、`slug`、`type`、`children` |
| `CategoryVO` | `id`、`name`、`slug`、`parentId`、`type`、`sortOrder` |
| `TagVO` | `id`、`name`、`slug` |
| `ImageVO` | `id`、`url`、`originalName`、`size` |
| `ImageManageVO` | `id`、`url`、`originalName`、`size`、`contentType`、`createdAt`、`referenced` |
| `SiteInfoVO` | `siteTitle`、`siteSubtitle`、`aboutHtml`、`projectHtml` |
| `AiModeSettingsVO` | `id`、`modeKey`、`name`、`description`、`enabled`、`sortOrder`、`currentVersion`、`systemPrompt`、`validationProfile`、`createdAt`、`updatedAt` |
| `AiModeVersionVO` | `versionNo`、`systemPrompt`、`validationProfile`、`createdAt` |

`metadata` 是 `Map<String, Object>`；数据库字符串解析失败时返回空 Map。

---

## 5. Mapper

### 5.1 UserMapper

```java
User findByUsername(String username);
void insert(User user);
void updatePassword(String username, String passwordHash);
long count();
```

### 5.2 CategoryMapper

```java
List<Category> findAll();
List<Category> findByType(String type);
Category findById(Long id);
void insert(Category category);
void update(Category category);
void delete(Long id);
List<Category> findByIds(List<Long> ids);
long countChildren(Long parentId);
```

### 5.3 TagMapper

```java
List<Tag> findAll();
Tag findById(Long id);
void insert(Tag tag);
void update(Tag tag);
void delete(Long id);
List<Tag> findByIds(List<Long> ids);
List<Tag> findByContentId(Long contentId);
```

### 5.4 ContentMapper

```java
List<Content> findPublished(ContentQuery query, List<Long> categoryIds);
long countPublished(ContentQuery query, List<Long> categoryIds);
Content findBySlug(String slug);
Content findPreviousPublished(LocalDateTime publishedAt, Long id);
Content findNextPublished(LocalDateTime publishedAt, Long id);
List<Content> findRelatedPublished(Long contentId, String currentType,
                                   List<Long> excludedIds, int limit);
List<Content> findAll(ContentQuery query, List<Long> categoryIds);
long countAll(ContentQuery query, List<Long> categoryIds);
Content findById(Long id);
long countBySlug(String slug, Long excludeId);
void insert(Content content);
void update(Content content);
void delete(Long id);
List<Content> search(String q, int offset, int size);
long countSearch(String q);
List<Content> findAllPublishedForIndex();
```

### 5.5 ContentCategoryMapper

```java
void insert(Long contentId, Long categoryId);
void deleteByContentId(Long contentId);
List<Long> findCategoryIdsByContentId(Long contentId);
List<ContentCategoryLink> findLinksByContentIds(List<Long> contentIds);
long countContentsByCategoryId(Long categoryId);
```

### 5.6 ContentTagMapper

```java
void insert(Long contentId, Long tagId);
void deleteByContentId(Long contentId);
List<Long> findTagIdsByContentId(Long contentId);
List<ContentTagLink> findLinksByContentIds(List<Long> contentIds);
long countContentsByTagId(Long tagId);
```

### 5.7 ImageMapper

```java
void insert(Image image);
Image findById(Long id);
List<Image> findAll();
void delete(Long id);
```

### 5.8 ImageCleanupTaskMapper

```java
void insert(ImageCleanupTask task);
List<ImageCleanupTask> findAll();
void delete(Long id);
void recordFailure(Long id, String error);
```

### 5.9 SiteOptionMapper

```java
List<SiteOption> findAll();
SiteOption findByKey(String optionKey);
void upsert(String key, String value);
```

### 5.10 ContentSearchMapper

```java
void upsert(Long contentId, String bodyText);
void deleteByContentId(Long contentId);
void deleteNotPublished();
long count();
```

### 5.11 AiTransformModeMapper

```java
List<AiTransformMode> findAll();
AiTransformMode findById(Long id);
AiTransformMode findByKey(String modeKey);
AiTransformMode findEnabledByKey(String modeKey);
int insert(AiTransformMode mode);
int updateMetadata(AiTransformMode mode);
int updateCurrentVersion(Long id, int expectedVersion, int newVersion);
```

### 5.12 AiTransformModeVersionMapper

```java
AiTransformModeVersion findVersion(Long modeId, int versionNo);
AiTransformModeVersion findCurrentVersion(Long modeId);
List<AiTransformModeVersion> findVersions(Long modeId);
int insertVersion(AiTransformModeVersion version);
int deleteVersionsBefore(Long modeId, int minimumVersionNo);
```

---

## 6. SQL 行为

### 6.1 排序

Mapper XML 只有两个排序分支：

```xml
<when test="sort == 'created_at_desc'">
    ORDER BY c.created_at DESC
</when>
<otherwise>
    ORDER BY c.published_at DESC
</otherwise>
```

因此除了 `created_at_desc`，任何输入都按 `published_at DESC`。

### 6.2 已发布条件

公开列表固定附加：

```sql
WHERE c.status = 'PUBLISHED'
```

公开详情固定附加：

```sql
WHERE c.slug = #{slug} AND c.status = 'PUBLISHED'
```

### 6.3 分类和标签筛选

使用解析后 ID 集合的 `EXISTS`：

```sql
EXISTS (
  SELECT 1
  FROM content_category cc
  WHERE cc.content_id = c.id
    AND cc.category_id IN (...)
)
```

`CategoryHierarchyResolver` 在默认情况下返回单元素集合，保持精确匹配；启用
`includeDescendants` 后按分类树展开全部后代，使用 `Set` 语义和 `EXISTS` 避免重复内容。
命中范围内循环或超过 32 层返回 409。排序在原有时间字段后追加 `id DESC`。

### 6.4 搜索

```sql
WHERE c.status = 'PUBLISHED'
  AND (
    MATCH(cs.body_text) AGAINST(#{q} IN NATURAL LANGUAGE MODE)
    OR c.title LIKE CONCAT('%', #{q}, '%')
    OR c.summary LIKE CONCAT('%', #{q}, '%')
  )
ORDER BY 标题命中 DESC, 摘要命中 DESC, 正文相关度 DESC,
         c.published_at DESC, c.id DESC
```

`content_search.body_text` 保存已发布 Markdown 正文，使用 ngram FULLTEXT 索引。
文章写入、撤回和删除会同步索引；旧内容由可重复的回填入口建立索引。

### 6.5 详情前后文章

`findPreviousPublished` 只返回 `PUBLISHED` 且发布时间更早的记录：

```sql
published_at < #{publishedAt}
OR (published_at = #{publishedAt} AND id < #{id})
ORDER BY published_at DESC, id DESC
LIMIT 1
```

`findNextPublished` 使用相反方向和排序，同时间以大 ID 为更晚。

### 6.6 相关文章

`findRelatedPublished` 只查询 `PUBLISHED` 内容，并排除当前及传入的前后篇 ID。
每位候选的排序分为：

```text
共享标签数 * 3 + 共享分类数 * 2 + 同类型 1 分
```

只保留得分大于 0 的候选，按得分、`published_at`、`id` 倒序稳定排序并限制为 4 篇。
分类使用精确 ID 交集，不展开层级；查询复用 `content_category` 与 `content_tag` 的现有索引。

### 6.7 调度与批量更新

调度查询按 `status='SCHEDULED'`、`scheduled_at <= now` 和计划时间顺序获取最多 100 篇。
发布使用条件更新：

```sql
UPDATE contents
SET status = 'PUBLISHED',
    published_at = #{publishedAt},
    scheduled_at = NULL
WHERE id = #{id}
  AND status = 'SCHEDULED'
  AND scheduled_at = #{publishedAt}
```

受影响行数为 0 时表示其他工作线程已发布或管理员已改期，不再同步正文索引。批量归档与恢复
同样使用状态条件更新；分类和标签添加使用 `INSERT IGNORE`，移除使用内容 ID 与目标 ID 两组
`IN` 条件，避免部分更新。

### 6.8 站点配置更新

```sql
INSERT INTO site_options (option_key, option_value)
VALUES (#{key}, #{value})
ON DUPLICATE KEY UPDATE option_value = VALUES(option_value)
```

可以插入数据库中尚不存在的配置 key。

### 6.9 AI 模式版本

模式元数据更新使用 `current_version = expectedVersion` 和数据库加载时的
`updated_at` 作为 CAS 条件。元数据更新不会增加提示词版本，但首次写入会更新微秒级
`updated_at`，因此同一快照上的并发元数据写入只有第一个成功，第二个返回 409。
提示词或校验策略变化时：

```sql
INSERT INTO ai_transform_mode_versions
    (mode_id, version_no, system_prompt, validation_profile)
VALUES
    (#{modeId}, #{newVersion}, #{systemPrompt}, #{validationProfile});

UPDATE ai_transform_modes
SET current_version = #{newVersion}
WHERE id = #{id}
  AND current_version = #{expectedVersion};
```

条件更新影响 0 行时返回 409 并回滚事务。新版本写入后删除
`version_no < currentVersion - 9` 的历史记录。回滚读取历史版本并复制为新版本。

---

## 7. 关联删除

内容更新和删除仍按 `contentId` 清理关联：

```java
deleteByContentId(contentId)
```

分类和标签删除不删除关联，而是分别通过：

```java
countContentsByCategoryId(categoryId)
countContentsByTagId(tagId)
```

有关联时返回 409。`CategoryMapper.countChildren(parentId)` 阻止删除仍有子分类的父分类。

### 7.1 批量关联查询

`findLinksByContentIds(contentIds)` 一次查询多篇文章的分类/标签映射。
`ContentVOMapper` 每个列表请求固定执行：

```text
1 次 content_category 批量查询
1 次 content_tag 批量查询
1 次 categories findByIds
1 次 tags findByIds
```

查询次数不随文章数量增加；`ObjectMapper` 也由共享组件单例注入。

### 7.2 数据库约束

`schema.sql` 已加入：

- `content_category.category_id` 索引和双向外键。
- `content_tag.tag_id` 索引和双向外键。
- `contents.published_at` 索引。
- `contents(status, scheduled_at)` 调度索引。
- `categories.parent_id` 自引用外键。
- `ai_transform_modes(mode_key)` 唯一键和 `(enabled, sort_order, id)` 索引。
- `ai_transform_mode_versions(mode_id, version_no)` 唯一键和模式外键级联删除。

其中内容外键使用 `ON DELETE CASCADE`，分类和标签外键使用 `ON DELETE RESTRICT`。
已有数据库使用 `docs/design/migrations/20260911_integrity_security.sql` 先清理孤儿行、再补列、
索引和外键。`users.token_version` 也由该脚本兼容添加；
`docs/design/migrations/20260913_image_cleanup_queue.sql` 通过 `CREATE TABLE IF NOT EXISTS`
兼容新增图片清理队列表；`docs/design/migrations/20260915_content_schedule.sql`
兼容新增调度时间和状态索引；`docs/design/migrations/20260918_admin_ai_modes.sql`
以幂等方式新增 AI 模式表、五个默认停用模式和 version 1。

---

## 8. 测试现状

单元测试覆盖路径、文件事务、JWT、限流、VO 批量组装和上传签名；Controller 边界测试仍 Mock Service。
GitHub Actions 的 MySQL 8.4 job 另行启动真实后端并验证 Mapper SQL：

- 没有 Entity 与 Schema 的自动一致性测试。
- 没有 SQL 注入和分页边界测试。

2026-09-18 起 CI 会从空库执行 Schema、种子数据和全部兼容迁移，再执行当前 31/31 兼容接口冒烟；
新增 `AiModeCatalogIntegrationTest` 覆盖默认模式、停用过滤、条件版本更新和级联删除；
既有覆盖包括 Mapper 查询、分类/标签关联、详情前后文章、相关文章排序与排除、图片生命周期与清理队列；
2026-09-11 隔离 MySQL 5.7 副本记录继续保留。

构建和测试命令见 [codebase-memory.md](../project/codebase-memory.md)。
