# MySQL 索引优化笔记（草稿）

这篇笔记只保存在演示数据中，用于验证管理端能区分 `DRAFT` 与 `PUBLISHED`。

## 待整理

- 联合索引的最左前缀。
- 回表与覆盖索引。
- `EXPLAIN` 的执行计划字段。
- 区分度与索引选择。

```sql
EXPLAIN SELECT id, title
FROM contents
WHERE status = 'PUBLISHED'
ORDER BY published_at DESC;
```
