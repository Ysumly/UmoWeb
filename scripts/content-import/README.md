# 正式内容导入

该目录把经过整理的 Markdown 目录转换为 UmoWeb 可恢复候选包。源笔记、正式凭据和归档不进入 Git。

## 文件

- `catalog.json`：文章、分类、标签和站点配置映射。
- `build_content_backup.py`：生成 `umoweb-content-*.tar.gz` 和 `.sha256`。
- `promote-content-backup.sh`：备份生产后替换正式数据并轮换凭据，必须显式传入 `--confirm`。
- `tests/`：摘要、链接、图片、去重、Linux 所有权和错误输入测试。

## 生成候选包

```powershell
python scripts/content-import/build_content_backup.py `
  --source "<notes-root>" `
  --catalog scripts/content-import/catalog.json `
  --output Downloads/content-import/umoweb-content-<date>.tar.gz
```

## 本地测试

```powershell
python -m unittest discover -s scripts\content-import\tests -v
```

## 发布约束

1. 先把候选包恢复到 `umoweb-restore-*`，表行数、文件清单和 27/27 接口冒烟全部通过。
2. 再执行 `promote-content-backup.sh --confirm <archive>`。
3. 提升脚本会保留提升前备份和旧 `.env.docker`；失败时按服务器侧发布记录回滚。
