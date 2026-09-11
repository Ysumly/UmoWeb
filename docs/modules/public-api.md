# 公开端 API 实现

> 基线日期: 2026-09-11
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
```

详情与列表使用同一个 `ContentVOMapper`，分类和标签按 contentIds 批量查询后组装为数组。
资源不存在时抛 `NotFoundException`。文件不存在或读取失败时，`body` 设置为空字符串，接口仍返回 200。

#### search

```text
ContentMapper.search(q, offset, size)
ContentMapper.countSearch(q)
```

SQL 只匹配 `title` 和 `summary`，不检索 Markdown 正文。

搜索限流由 `RateLimitInterceptor` 在进入 Controller 前执行。默认只信任 `remoteAddr`；
仅当直连地址在 `app.security.trusted-proxies` 中时才读取 `X-Forwarded-For`。过期记录会定期清理。

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
| `page` | 1 | 必须 >= 1 |
| `size` | 10 | 1-100 |
| `type` | null | 枚举，非法值 400 |
| `categoryId` | null | 精确匹配，不含子分类 |
| `tagId` | null | 精确匹配 |
| `sort` | `published_at_desc` | 只有 `created_at_desc` 是特殊分支 |

`status` 即使被客户端传入，也不会影响公开查询，因为 `wherePublished` 没有使用它。

### 4.2 搜索

| 参数 | 默认值 | 说明 |
|---|---|---|
| `q` | null -> `""` | title/summary 模糊匹配 |
| `page` | 1 | 必须 >= 1 |
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
- 空分类和标签返回 `[]`。
- `type` 过滤调用路径。

尚未覆盖：

- 真实 Mapper SQL。
- 分类子分类筛选语义。
- Markdown 文件读取或损坏场景。
- 搜索限流拦截器和可信代理行为已由单元测试覆盖。
