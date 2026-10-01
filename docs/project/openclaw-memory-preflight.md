# OpenClaw 内存前置与基线测量

> 状态日期: 2026-10-01
> 当前阶段: Task 1 已完成，尚未在 ECS 上安装 timer 或开始采样

## 目标

在任何 OpenClaw、Node、QQBot 或 Agent API 工具安装前，证明 UmoWeb 正常运行期间
宿主 `MemAvailable` 能连续 72 小时严格大于 500 MiB。Gate 0 通过前不得进入后续阶段。

## 只读采样器

`scripts/openclaw/preflight/memory-sample.sh` 每次向指定 CSV 文件追加一行，默认目标为
`/var/log/umoweb/openclaw-preflight/baseline.csv`。CSV 字段固定为：

```text
timestamp,mem_total_kib,mem_available_kib,swap_total_kib,swap_free_kib,umoweb_frontend_bytes,umoweb_backend_bytes,umoweb_mysql_bytes,umoweb_health,openclaw_service_state
```

采样器只执行以下读取行为：

- 从 `MEMINFO_PATH` 读取主机内存和 swap，默认使用 `/proc/meminfo`。
- 使用 `docker stats --no-stream` 读取三个 UmoWeb 容器内存。
- 使用 `docker compose ps --format json` 读取容器健康状态。
- 使用 `systemctl cat openclaw.service` 检查 OpenClaw 单元是否已存在。

它不会停止、重启、删除或修改 Docker、systemd 或 UmoWeb 资源。唯一写入目标是采样器
接收的 CSV 文件。Docker 不可用时，三个容器内存写 `0`，健康状态写 `unknown`，采样仍继续。

## 当前验证

```bash
bash scripts/openclaw/preflight/tests/memory-sample-test.sh
```

2026-10-01 使用 Git Bash 执行通过，测试覆盖：

- `/proc/meminfo` fixture 数值解析。
- CSV 字段数量、顺序和 ISO 8601 UTC 时间格式。
- Docker 人类可读内存单位到字节的转换。
- Docker 不可用时降级但不中断采样。
- Docker/systemd 写入命令与 mutation 调用的静态和运行时拒绝。

本文件目前只记录 Task 1 的代码级验证。ECS 初始基线、优化候选和 72 小时 Gate 0
结果将在后续任务中追加，不能把本次本地测试视为 Gate 0 通过。
