# OpenClaw 内存前置与基线测量

> 状态日期: 2026-10-01
> 当前阶段: Task 1-4 已完成，480 MiB Gate 0 的 72 小时采样进行中

## 目标

在任何 OpenClaw、Node、QQBot 或 Agent API 工具安装前，证明 UmoWeb 正常运行期间
宿主 `MemAvailable` 能连续 72 小时严格大于 480 MiB。原始 500 MiB 标准已于
2026-10-01 经人工确认调整为 480 MiB；阈值变更和风险记录见下文。Gate 0 通过前
不得进入后续阶段。

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

## Task 3 基线分析

2026-10-01 使用 `scripts/openclaw/preflight/analyze-memory.py` 分析下载后的
`baseline.csv`：

```json
{
  "sample_count": 13,
  "min_mem_available_kib": 327100,
  "p05_mem_available_kib": 327100,
  "median_mem_available_kib": 368300,
  "below_threshold_count": 13,
  "threshold_kib": 512000,
  "required_sample_count": 864,
  "pass": false
}
```

Gate 0 尚未开始，以上结果只说明未优化的生产环境不满足门槛。分析仅使用
`/proc/meminfo`、`docker stats`、`systemd-cgtop`、`systemctl`、`multipath -ll`、
`lsblk` 和 `ss` 的只读输出，没有停止或修改 ECS 服务。

### 必须保留

- MySQL、backend、frontend 和 Docker/containerd 容器运行时。
- SSH、systemd、systemd-networkd、systemd-resolved、journald、chrony 和 UFW。
- Aegis、云监控、自动安全更新、阿里云备份及其更新服务。
- UmoWeb 访问报表、每周备份 timer 和内存前置采样 timer。

### 已批准的优化候选

下表占用值来自 2026-10-01 的 cgroup 快照，实际收益必须在 Task 4 中逐项测量。

| 服务 | 当前占用 | 风险与理由 | 回滚与验证 |
|---|---|---|---|
| `bt.service` | 约 147 MiB | 宝塔面板和任务进程；停止后关闭公网 `8888`，站点仍由 Docker 和 SSH/Workbench 管理 | `systemctl enable --now bt.service`；若生成单元不能启用则执行 `/etc/init.d/bt start` 和 `update-rc.d bt enable`；确认 `22/80` 可用、`8888` 关闭 |
| `site_total.service` | 约 8 MiB | 宝塔站点统计辅助进程，不参与 UmoWeb 运行 | `systemctl enable --now site_total.service` |
| `multipathd.service` | 约 23 MiB | 当前只有单块 `vda`，`multipath -ll` 无映射；云盘使用普通分区 | `systemctl enable --now multipathd.service`；确认根文件系统仍为 `vda3` |
| `fwupd.service` | 约 9 MiB | 云主机不执行本地固件更新；静态单元需防止 DBus 重新拉起 | `systemctl unmask fwupd.service && systemctl start fwupd.service` |
| `ModemManager.service` | 约 3 MiB | 实例没有蜂窝调制解调器 | `systemctl enable --now ModemManager.service` |
| `udisks2.service` | 约 3 MiB | 服务器不需要桌面磁盘管理 | `systemctl unmask udisks2.service && systemctl start udisks2.service` |
| `networkd-dispatcher.service` | 约 11 MiB | 实际网络由 systemd-networkd 维持；停用只移除网络事件脚本分发 | `systemctl enable --now networkd-dispatcher.service`；确认 `eth0`、默认路由和 Docker 网桥不变 |
| `tuned.service` | 约 16 MiB | 当前仅使用 `virtual-guest` 配置；停止后保留已应用的内核值 | `systemctl enable --now tuned.service`；对比 `sysctl` 关键值和负载 |

每项按列表顺序单独执行，至少观察 30 分钟；任一安全服务、容器、公开接口或回环
访问报表异常时立即回滚该步骤并停止后续调整。

## Task 4 执行结果

2026-10-01 已创建并启用 `/swapfile-openclaw`：

- 大小 2 GiB，UUID `1ea79058-3cd9-44e0-aa5d-3b518c84be6f`。
- 文件权限 `0600`，fstab 条目为 `/swapfile-openclaw none swap sw 0 0`。
- `findmnt --verify` 为 0 parse errors、0 errors；现有 `/www/swap` 保持不变。
- `vm.swappiness=0`，两份 swap 均未使用。swap 不作为 Gate 0 通过条件。

服务优化严格按顺序执行，每项至少观察 30 分钟。检查内容覆盖 Aegis、云监控、
自动安全更新、UFW、SSH、Docker、访问报表、三个 UmoWeb 容器、公开 API、回环
健康端点、DNS、默认路由、根盘来源和 swap。

| 顺序 | 服务 | 检查点 `MemAvailable` | 结果 |
|---|---|---:|---|
| 0 | 未优化基线 | 327100 KiB | 全部低于 500 MiB |
| 1 | `bt.service` | 约 418 MiB | 通过；`8888` 关闭，宝塔进程为 0 |
| 2 | `site_total.service` | 425316 KiB | 通过 |
| 3 | `multipathd.service` | 451004 KiB | 通过；根盘仍为 `/dev/vda3` |
| 4 | `fwupd.service` | 464916 KiB | 通过 |
| 5 | `ModemManager.service` | 471544 KiB | 通过 |
| 6 | `udisks2.service` | 467760 KiB | 通过；期间有系统级波动 |
| 7 | `networkd-dispatcher.service` | 480212 KiB | 通过；网络和 DNS 正常 |
| 8 | `tuned.service` | 501168 KiB | 通过 |

最后一项 30 分钟检查点仍为 501168 KiB，低于门槛 10832 KiB。实际最大提升约
170 MiB，但未达到任何样本都必须严格大于 512000 KiB 的条件。优化期间三个容器
没有重启，公开 API、访问报表、SSH、安全代理和备份均保持正常。

## Gate 0 阈值修订

按原始 500 MiB 标准评审时，完成全部已批准优化后的 30 分钟检查点最高仅为
`MemAvailable=501168 KiB`，因此原始标准不通过，当时没有启动 72 小时采样。

2026-10-01 经明确确认，将本阶段 Gate 0 门槛正式调整为 480 MiB
（491520 KiB）。最终优化检查点相对新门槛保留 9648 KiB，约 9.4 MiB 余量。
这只是本次明确记录的阈值修订，不代表原始 500 MiB 标准已经满足。

新的 `gate0.csv` 必须连续覆盖 72 小时、至少 864 个样本，且每个样本的
`MemAvailable` 都严格大于 491520 KiB。采样期间不安装 Node、npm、OpenClaw 或
QQBot；只有新 Gate 0 通过后才允许讨论后续阶段。不得通过停用 Aegis、云监控、
自动安全更新、UFW、SSH、备份或访问报表来换取内存。

### 第二轮 Gate 0

- 采样开始：`2026-10-01T13:38:46Z`（北京时间 `2026-10-01 21:38:46`）。
- 目标结束：`2026-10-04T13:38:46Z`（北京时间 `2026-10-04 21:38:46`）。
- 首样本：`MemAvailable=502212 KiB`，高于 491520 KiB 门槛。
- 已再次停用 `bt`、`site_total`、`multipathd`、`fwupd`、`ModemManager`、
  `udisks2`、`networkd-dispatcher` 和 `tuned`，并停止 `multipathd.socket`，
  防止socket重新拉起服务。
- Aegis、云监控、自动安全更新、UFW、SSH、Docker、备份和访问报表保持 active。
- 采样文件为 `gate0.csv`，timer active，service 执行状态为 success、重启次数为 0。

### 生产回滚

原始 500 MiB 评审结束后，所有停用或 mask 的宿主服务曾恢复为原状态：
`bt.service`、`site_total`、`multipathd`、`fwupd`、`ModemManager`、`udisks2`、
`networkd-dispatcher` 和 `tuned` 均已恢复启用并与安全服务一起验证 active。

2 GiB 独立 swap 保留作为宿主保护缓冲。如需完全移除：

```bash
swapoff /swapfile-openclaw
sed -i '\|^/swapfile-openclaw none swap sw 0 0$|d' /etc/fstab
rm -f /swapfile-openclaw.umoweb-managed /swapfile-openclaw
systemctl daemon-reload
```

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
