# Common 层实现

> 基线日期: 2026-09-11
> 路径: `com.ysumly.umowebbackend.common`

---

## 1. 文件清单

```text
common/
├── constant/
│   ├── ContentType.java
│   └── ContentStatus.java
├── exception/
│   ├── BusinessException.java
│   ├── UnauthorizedException.java
│   ├── ForbiddenException.java
│   ├── NotFoundException.java
│   └── GlobalExceptionHandler.java
├── validation/
│   ├── ValidJsonObject.java
│   └── ValidJsonObjectValidator.java
└── util/
    ├── JwtUtil.java
    └── FileUtil.java
```

---

## 2. 枚举

```java
public enum ContentType {
    NOTE,
    NOVEL,
    BOOK_REVIEW
}
```

```java
public enum ContentStatus {
    DRAFT,
    SCHEDULED,
    PUBLISHED,
    ARCHIVED
}
```

这些枚举目前只在业务实现中用于字符串转换和路径生成，Entity 与数据库字段仍使用 `String`。

---

## 3. 异常体系

```text
RuntimeException
└── BusinessException
    ├── UnauthorizedException  -> 401
    ├── ForbiddenException     -> 403
    └── NotFoundException      -> 404
```

`BusinessException` 持有 HTTP 语义状态码 `code` 和消息。`BulkOperationException`
在此基础上携带逐项 `failures`，仅用于批量文章预检失败。

### 3.1 GlobalExceptionHandler

当前处理：

| 异常 | HTTP | 响应 |
|---|---:|---|
| `HttpMessageNotReadableException` | 400 | 请求体不能为空 |
| `MethodArgumentNotValidException` | 400 | 第一条字段校验错误 |
| `MaxUploadSizeExceededException` | 413 | 文件大小超过限制 |
| `DuplicateKeyException` | 409 | 资源已存在 |
| `BusinessException` | 自定义 code | `{ code, message }` |
| `Exception` | 500 | `Internal Server Error` |

异常响应示例：

```json
{
  "code": 404,
  "message": "Content not found: no-such-slug"
}
```

项目没有 `ResponseBodyAdvice`，因此正常响应不会被包装成 `{ code, message, data }`。

---

## 4. JwtUtil

配置：

```yaml
app:
  jwt:
    secret: ${JWT_SECRET:change-me-in-production-this-is-a-default-only}
    expiration-hours: 24
```

实现行为：

- 对 secret 做 SHA-256，得到固定 256-bit HMAC key。
- `generateToken(username)` 将 username 写入 `subject`。
- 包含签发时间和过期时间。
- `validate(token)` 捕获解析异常并返回 boolean。
- `parseUsername(token)` 从合法 token 读取 subject。

算法由 JJWT `signWith(SecretKey)` 选择 HMAC；代码没有显式声明 `HS256`。

---

## 5. FileUtil

配置：

```yaml
app:
  storage-path: ./data
```

### 5.1 Markdown

| 方法 | 行为 |
|---|---|
| `readMarkdown(path)` | UTF-8 读取 |
| `writeMarkdown(path, content)` | UTF-8 写入并创建父目录 |
| `deleteMarkdown(path)` | 删除已存在的文件 |

### 5.2 路径生成

```java
switch (type) {
    case NOTE -> "contents/NOTE/" + slug + ".md";
    case BOOK_REVIEW -> "contents/BOOK_REVIEW/" + slug + ".md";
    case NOVEL -> "contents/NOVEL/" + bookSlug + "/" + slug + ".md";
}
```

图片：

```text
images/{YYYY}/{MM}/{uuid}.{ext}
```

扩展名由 `ImageServiceImpl` 根据校验后的 MIME 映射生成，不采用客户端原始扩展名。

### 5.3 路径安全

`FileUtil` 对相对路径执行 normalize，并检查最终路径仍位于 storage root 内；调用方同时限制
`slug` / `bookSlug` 字符集。越界路径会被拒绝。

---

## 6. metadata 校验

`ValidJsonObjectValidator` 使用 Jackson 3 的 `tools.jackson.databind.ObjectMapper` 解析字符串：

- null 或空白值视为合法，由业务层规范化为 `null`。
- 合法 JSON 对象返回 true。
- 数组、标量或非法 JSON 返回 false，由 Bean Validation 返回 400。

---

## 7. 验证现状

- `BoundaryTest` 覆盖全局异常处理的主要 4xx 场景。
- `JwtUtilTest` 覆盖过期、tokenVersion 和解析行为。
- `FileUtilTest` 覆盖路径穿越、读写、临时文件、回滚恢复和原子替换。
- `MapperConfigurationTest` 覆盖 Mapper XML 别名解析。
