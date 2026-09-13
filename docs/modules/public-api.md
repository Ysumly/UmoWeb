# 公开端 API 实现

> 基线日期: 2026-09-13
> 前缀: `/api/public`
> 接口数: 8

---

## 1. Controller

公开端有 4 个 Controller：

| Controller | 责任 |
|---|---|
| `SiteController` | 站点信息和 About/Project |
| `ContentController` | 已发布内容列表、详情、搜索 |
| `CategoryController` | 分类树 |
| `TagController` | 标签列表 |

正常响应直接返回 VO、数组、Map 或分页对象，不套统一响应外壳。

---

## 2. 接口

### 2.1 `SiteController`

```java
@GetMapping("/site-info")
ResponseEntity<SiteInfoVO>

@GetMapping("/pages/about")
ResponseEntity<Map<String, String>>

@GetMapping("/pages/project")
ResponseEntity<Map<String, String>>
```

`SiteInfoVO`：

```java
String siteTitle;
String siteSubtitle;
String aboutHtml;
String projectHtml;
```

`aboutHtml` 和 `projectHtml` 实际是 Markdown 字符串。

### 2.2 `ContentController`

```java
@GetMapping("/contents")
ResponseEntity<PageResult<ContentListVO>>

@GetMapping("/contents/{slug}")
ResponseEntity<ContentDetailVO>

@GetMapping("/contents/search")
ResponseEntity<PageResult<ContentListVO>>
```

实现：

```java
contentService.listPublished(query)
contentService.getBySlug(slug)
contentService.search(query)
```

### 2.3 `CategoryController`

```java
@GetMapping("/categories")
ResponseEntity<List<CategoryTreeVO>>
```

可选参数 `type`。

### 2.4 `TagController`

```java
@GetMapping("/tags")
ResponseEntity<List<TagVO>>
```

---

## 3. Service 行为

### 3.1 SiteOptionService

`getSiteInfo()` 读取全部 `site_options`，按 key 组装：

- `site_title`
- `site_subtitle`
- `about_page`
- `project_page`

`getPage(key)` 找不到时返回空字符串。

### 3.2 ContentService

#### listPublished

```text
ContentMapper.findPublished(query)
ContentMapper.countPublished(query)
-> 批量查询分类/标签
-> 解析 metadata
-> PageResult
```

只读取 `PUBLISHED`。

#### getBySlug

```text
ContentMapper.findBySlug(slug)
-> FileUtil.readMarkdown(bodyPath)
-> ContentMapper.findPreviousPublished(publishedAt, id)
-> ContentMapper.findNextPublished(publishedAt, id)
```

详情与列表使用同一个 `ContentVOMapper`，分类和标签按 contentIds 批量查询后组装为数组。
资源不存在时抛 `NotFoundException`。文件不存在或读取失败时，`body` 设置为空字符串，接口仍返回 200。
邻居对象只暴露 `id/title/slug/publishedAt`；`previous` 为更早内容，`next` 为更晚内容，
同时间以较小 ID 为更早，边界返回 `null`。

#### search

```text
ContentMapper.search(q, offset, size)
ContentMapper.countSearch(q)
```

SQL 只匹配 `title` 和 `summary`，不检索 Markdown 正文。

搜索限流由 `RateLimitInterceptor` 在进入 Controller 前执行。默认只信任 `remoteAddr`；
仅当直连地址匹配 `app.security.trusted-proxies` 中的精确 IP 或 CIDR 时才读取
`X-Forwarded-For`。过期记录会定期清理。

### 3.3 CategoryService

1. `type` 非空时查对应类型，否则查全部。
2. 按 `parent_id` 在内存中组装树。
3. 无子节点时 `children` 为 `[]`。

数据库无子节点或存在循环引用时没有专门处理。

### 3.4 TagService

读取全部标签并转换为 `TagVO`。

---

## 4. 查询参数

### 4.1 内容列表

| 参数 | 默认值 | 说明 |
|---|---|---|
| `page` | 1 | 1-1000000 |
| `size` | 10 | 1-100 |
| `type` | null | 枚举，非法值 400 |
| `categoryId` | null | 默认精确匹配；配合 `includeDescendants=true` 时包含全部后代 |
| `includeDescendants` | false | 必须与 `categoryId` 同时提供，单独传 `true` 返回 400 |
| `tagId` | null | 精确匹配 |
| `sort` | `published_at_desc` | 只有 `created_at_desc` 是特殊分支 |

`status` 即使被客户端传入，也不会影响公开查询，因为 `wherePublished` 没有使用它。

### 4.2 搜索

| 参数 | 默认值 | 说明 |
|---|---|---|
| `q` | null -> `""` | title/summary 模糊匹配 |
| `page` | 1 | 1-1000000 |
| `size` | 10 | 1-100 |

---

## 5. 错误行为

| 场景 | 状态 |
|---|---:|
| slug 不存在或未发布 | 404 |
| 搜索频率超限 | 429 |
| 未处理异常 | 500 |

错误响应：

```json
{
  "code": 404,
  "message": "Content not found: no-such-slug"
}
```

---

## 6. 当前测试

`BoundaryTest` 覆盖：

- 列表无参数返回 200。
- 搜索无参数返回 200。
- 详情不存在返回 404。
- 详情分类/标签数组已由 MockMvc 断言覆盖。
- Service 测试覆盖详情前后文章与边界 `null`。
- 空分类和标签返回 `[]`。
- `type` 过滤调用路径。
- 分类子分类筛选语义由单元测试、真实 MySQL 环境门控测试和双冒烟脚本覆盖。

分类后代由共享 `CategoryHierarchyResolver` 读取一次分类快照后展开，按访问路径检测循环，
深度上限为 32；解析后的 ID 集合传给 Mapper。不存在的内容仍返回 200 空结果，内容按原有时间排序，
相同时间使用 `id DESC` 保证稳定次序。

尚未覆盖：

- Markdown 文件读取或损坏场景。
