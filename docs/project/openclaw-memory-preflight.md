# OpenClaw 内存前置与基线测量

> 状态日期: 2026-10-01
> 当前阶段: Task 1-2 已完成，ECS 只读基线采样进行中，Gate 0 尚未开始

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

以上是 Task 1 的本地代码级验证。ECS 初始基线已追加在下方，优化候选和 72 小时
Gate 0 结果仍待后续任务完成；不能把本地测试或初始基线视为 Gate 0 通过。

## Timer 与采样文件

2026-10-01 已通过阿里云 Workbench 将以下只读采样文件同步到 ECS：

```text
/opt/umoweb/scripts/openclaw/preflight/memory-sample.sh
/opt/umoweb/scripts/openclaw/preflight/install-memory-preflight-timer.sh
/opt/umoweb/scripts/openclaw/preflight/systemd/umoweb-openclaw-preflight.service
/opt/umoweb/scripts/openclaw/preflight/systemd/umoweb-openclaw-preflight.timer
```

已安装的 timer 为 `umoweb-openclaw-preflight.timer`，每 5 分钟运行一次
`umoweb-openclaw-preflight.service`。服务以 root 运行，使用 `Nice=10` 和
`IOSchedulingClass=idle`，只执行采样脚本并追加到：

```text
/var/log/umoweb/openclaw-preflight/baseline.csv
```

目录权限为 `0700 root:root`，CSV 权限为 `0644 root:root`，采样脚本权限为 `0755`。
两个 systemd 单元均为 `0644`，`systemd-analyze verify` 对本次新增单元无报错。

本地安装契约测试位于：

```bash
bash scripts/openclaw/preflight/tests/timer-install-test.sh
```

测试覆盖模式参数、baseline/gate0 路径渲染、service 与 timer 字段、systemctl 安装命令、
已有 CSV 不被截断、拒绝已存在的 `openclaw.service`。Git Bash 使用 `bash -lc` 执行。

## 未优化基线

采样开始时间（UTC）:

```text
2026-10-01T06:47:28Z
```

首个正确分类的样本为：

```text
timestamp,mem_total_kib,mem_available_kib,swap_total_kib,swap_free_kib,umoweb_frontend_bytes,umoweb_backend_bytes,umoweb_mysql_bytes,umoweb_health,openclaw_service_state
2026-10-01T06:47:28Z,1782760,372168,1049596,1049596,4591714,244947353,599680614,healthy,not-installed
```

自动 timer 在 `2026-10-01T06:52:31Z` 追加第二个样本，间隔约 5 分 03 秒：

```text
2026-10-01T06:52:31Z,1782760,366516,1049596,1049596,3899654,249770803,596534886,healthy,not-installed
```

| 指标 | 初始值 |
|---|---|
| MemTotal | 1,782,760 KiB，约 1.70 GiB |
| MemAvailable | 372,168 KiB，约 363.4 MiB，低于 500 MiB |
| swap 总量 | 1,049,596 KiB，约 1.00 GiB |
| swap 剩余 | 1,049,596 KiB，已用为 0 |
| frontend | 4,591,714 bytes，约 4.38 MiB |
| backend | 244,947,353 bytes，约 233.6 MiB |
| mysql | 599,680,614 bytes，约 571.9 MiB |
| UmoWeb 健康 | `healthy` |
| OpenClaw 单元 | `not-installed` |

现有 `/www/swap` 保持不变，本任务没有创建额外 swap。采样发现 ECS 上运行的 frontend
没有 Docker healthcheck；采样器已将“state=running 且 Health 为空”视为健康，同时仍把
显式 `starting` 或 `unhealthy` 容器判为不健康。修正前的单行误判记录保留在
`baseline-pre-health-fix.csv`，当前 `baseline.csv` 从修正版本重新开始。

安装时 RSS 靠前的宿主进程包括：

| 进程 | 约 RSS |
|---|---|
| mysqld | 574,208 KiB |
| backend Java | 238,104 KiB |
| dockerd | 56,920 KiB |
| BT-Task | 44,192 KiB |
| BT-Panel | 37,040 KiB |
| containerd | 32,260 KiB |
| AliYunDunMonitor | 29,964 KiB |

以上数据是尚未执行任何内存优化的基线。当前 `MemAvailable` 已明显低于 Gate 0 的
500 MiB 标准，但这不等于正式 Gate 0 结论；至少还需要进入 Task 3 分析内存缺口和
可逆优化候选，再由 Task 4 逐项应用。

## 操作与回滚

重新安装或切换采样文件：

```bash
bash /opt/umoweb/scripts/openclaw/preflight/install-memory-preflight-timer.sh baseline
bash /opt/umoweb/scripts/openclaw/preflight/install-memory-preflight-timer.sh gate0
```

停用只读 timer 但保留数据：

```bash
systemctl disable --now umoweb-openclaw-preflight.timer
```

本任务未安装 Node、OpenClaw、QQBot 或 Agent API，也未修改 UmoWeb Schema、Markdown、
`app_data`、`content_search`、容器配置或公开接口。
