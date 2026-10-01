# OpenClaw 内存前置与基线测量 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development`
> (recommended) or `superpowers:executing-plans` to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在任何 OpenClaw 安装前，优化并测量 ECS 内存，证明 UmoWeb 正常运行时可连续 72 小时提供严格大于 500 MiB 的 `MemAvailable`。

**Architecture:** 本阶段只增加只读采样、分析、报告和可回滚的宿主内存优化。Node、OpenClaw、QQBot、Agent API 工具和 OpenClaw systemd 单元全部禁止安装或启用。

**Tech Stack:** Ubuntu 24.04、systemd、Docker Compose、Bash、Python 标准库、`/proc/meminfo`、journald。

**Spec:** [OpenClaw QQ 私人管家分阶段路线图](2026-09-30-openclaw-qq-agent-index.md)

## Global Constraints

- 不安装 Node、npm、OpenClaw、QQBot 或任何 OpenClaw 运行依赖。
- 不创建、不启动、不启用 `openclaw.service`。
- 不修改 UmoWeb Schema、Markdown、`app_data`、`content_search` 或公开接口。
- 不停止、不重启、不降配 UmoWeb 的 frontend、backend 和 MySQL。
- 不执行 `echo 3 > /proc/sys/vm/drop_caches`，因为它只影响缓存统计，不解决内存需求。
- swap 可以作为保护缓冲，但不能计入 `MemAvailable > 500 MiB` 的通过标准。
- 优化必须一次只改一项，先记录回滚命令，再执行并重新采样。
- Gate 0 通过前禁止进入 [阶段 1 只读查询计划](2026-09-30-openclaw-qq-readonly-phase-1.md)。

---

### Task 1: 建立只读内存采样器

**Files:**
- Create: `scripts/openclaw/preflight/memory-sample.sh`
- Create: `scripts/openclaw/preflight/tests/memory-sample-test.sh`
- Create: `docs/project/openclaw-memory-preflight.md`

**Interfaces:**
- Produces: 每 5 分钟一个 CSV 行。
- CSV 字段固定为：`timestamp,mem_total_kib,mem_available_kib,swap_total_kib,swap_free_kib,umoweb_frontend_bytes,umoweb_backend_bytes,umoweb_mysql_bytes,umoweb_health,openclaw_service_state`。
- `openclaw_service_state` 在 Gate 0 必须始终为 `not-installed`。

- [x] **Step 1: 写采样器测试**

测试使用临时 fixture，不访问 ECS：

```bash
bash scripts/openclaw/preflight/tests/memory-sample-test.sh
```

测试必须验证：

```text
MemAvailable 数值从 /proc/meminfo 正确读取
Docker 不可用时容器字节写 0，但脚本继续采样
输出字段顺序和数量固定
时间格式为 ISO 8601
不会停止、重启或写任何 Docker/Systemd 资源
```

Expected: FAIL，因为采样器尚不存在。

- [x] **Step 2: 实现只读采样器**

`memory-sample.sh` 只允许执行读取命令：

```bash
awk '/^MemTotal:/ {print $2}' /proc/meminfo
awk '/^MemAvailable:/ {print $2}' /proc/meminfo
awk '/^SwapTotal:/ {print $2}' /proc/meminfo
awk '/^SwapFree:/ {print $2}' /proc/meminfo
docker stats --no-stream --format '{{.Name}} {{.MemUsage}}'
docker compose -p umoweb --env-file /opt/umoweb/.env.docker -f /opt/umoweb/compose.yaml ps --format json
if systemctl cat openclaw.service >/dev/null 2>&1; then echo present; else echo not-installed; fi
```

脚本不得调用 `systemctl stop`、`systemctl restart`、`docker stop`、`docker rm`、`kill` 或任何写命令。

- [x] **Step 3: 运行采样器测试**

```bash
bash scripts/openclaw/preflight/tests/memory-sample-test.sh
```

Expected: PASS。

- [x] **Step 4: 提交**

```bash
git add scripts/openclaw/preflight docs/project/openclaw-memory-preflight.md
git commit -m "ops: add openclaw memory preflight sampler"
```

### Task 2: 收集未优化基线

**Files:**
- Create: `scripts/openclaw/preflight/install-memory-preflight-timer.sh`
- Create: `scripts/openclaw/preflight/systemd/umoweb-openclaw-preflight.service`
- Create: `scripts/openclaw/preflight/systemd/umoweb-openclaw-preflight.timer`
- Create: `scripts/openclaw/preflight/tests/timer-install-test.sh`
- Modify: `docs/project/openclaw-memory-preflight.md`
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Produces: `/var/log/umoweb/openclaw-preflight/baseline.csv`。
- Produces: timer 每 5 分钟运行一次采样。

- [x] **Step 1: 写 systemd 单元**

service 必须使用：

```ini
[Service]
Type=oneshot
User=root
ExecStart=/opt/umoweb/scripts/openclaw/preflight/memory-sample.sh /var/log/umoweb/openclaw-preflight/baseline.csv
Nice=10
IOSchedulingClass=idle
```

timer 必须使用：

```ini
[Timer]
OnBootSec=1min
OnUnitActiveSec=5min
AccuracySec=15s
Persistent=true
```

- [x] **Step 2: 安装并启动只读 timer**

```bash
bash scripts/openclaw/preflight/install-memory-preflight-timer.sh baseline
systemctl list-timers umoweb-openclaw-preflight.timer --no-pager
```

Expected: timer active，下一次触发时间不超过 5 分钟。

- [x] **Step 3: 确认首行有效**

```bash
tail -n 1 /var/log/umoweb/openclaw-preflight/baseline.csv
```

Expected: 字段数量正确，`openclaw_service_state` 为 `not-installed`。

- [x] **Step 4: 记录初始基线**

`docs/project/openclaw-memory-preflight.md` 记录：

```text
采样开始时间
当前 MemAvailable
当前 swap 使用
三个 UmoWeb 容器内存
当前已知高内存进程
尚未做任何优化
```

- [x] **Step 5: 提交**

```bash
git add scripts/openclaw/preflight docs/project/openclaw-memory-preflight.md
git commit -m "ops: collect openclaw memory baseline"
```

### Task 3: 分析内存缺口和可逆优化候选

**Files:**
- Create: `scripts/openclaw/preflight/analyze-memory.py`
- Create: `scripts/openclaw/preflight/tests/test_analyze_memory.py`
- Modify: `docs/project/openclaw-memory-preflight.md`

**Interfaces:**
- Consumes: Task 1 的 CSV 字段。
- Produces: 最小、中位数、P05 和低于 500 MiB 的样本数。
- Produces: 按 RSS 排序的高内存进程和 systemd 服务清单。

- [ ] **Step 1: 写分析器测试**

测试 fixture 至少包含：

```text
全部样本 > 500 MiB -> pass
一个样本 <= 500 MiB -> fail
swap 使用增加但 MemAvailable 持平 -> 仍按 MemAvailable 判断
空文件 -> fail
字段缺失 -> fail
```

Run:

```bash
python3 -m unittest scripts/openclaw/preflight/tests/test_analyze_memory.py
```

Expected: FAIL，因为分析器尚不存在。

- [ ] **Step 2: 实现分析器**

阈值固定为：

```python
MIN_MEM_AVAILABLE_KIB = 500 * 1024
REQUIRED_SAMPLE_COUNT = 864
```

分析器只输出统计和候选清单，不执行优化。

- [ ] **Step 3: 运行分析器测试**

```bash
python3 -m unittest scripts/openclaw/preflight/tests/test_analyze_memory.py
```

Expected: PASS。

- [ ] **Step 4: 生成候选清单**

```bash
python3 scripts/openclaw/preflight/analyze-memory.py /var/log/umoweb/openclaw-preflight/baseline.csv
ps -eo pid,user,rss,comm,args --sort=-rss | head -n 30
systemd-cgtop --order=memory --iterations=1 --batch
```

`docs/project/openclaw-memory-preflight.md` 只记录：

```text
明确未使用且可安全停用的服务
重复运行的进程
容量配置明显过大的 JVM/容器
必须保留的 MySQL、backend、frontend、SSH、systemd 和监控服务
每项候选的风险、收益、回滚命令和验证方式
```

- [ ] **Step 5: 提交**

```bash
git add scripts/openclaw/preflight docs/project/openclaw-memory-preflight.md
git commit -m "ops: analyze openclaw memory baseline"
```

### Task 4: 逐项执行可逆优化

**Files:**
- Modify: `docs/project/openclaw-memory-preflight.md`
- Create: `scripts/openclaw/preflight/add-openclaw-swap.sh`
- Create: `scripts/openclaw/preflight/tests/add-openclaw-swap-test.sh`

**Interfaces:**
- Consumes: Task 3 的候选清单。
- Produces: 每次只应用一项优化的前后对照记录。
- Produces: `/swapfile-openclaw`，大小为 2 GiB，不作为 Gate 0 的内存通过条件。

- [ ] **Step 1: 写 swap 安装测试**

测试必须拒绝：

```text
已有非本脚本管理的 /swapfile-openclaw
目标文件系统剩余空间不足 3 GiB
fstab 中已有同一路径
缺少 root 权限
```

Run:

```bash
bash scripts/openclaw/preflight/tests/add-openclaw-swap-test.sh
```

Expected: FAIL，因为脚本尚不存在。

- [ ] **Step 2: 实现并安装 2 GiB swap**

```bash
bash scripts/openclaw/preflight/add-openclaw-swap.sh
swapon --show
```

Expected: 出现 `/swapfile-openclaw`，现有 `/www/swap` 保持不变。

- [ ] **Step 3: 对候选优化逐项应用**

每个候选严格执行：

```text
记录当前 MemAvailable 和容器内存
记录回滚命令
只修改一个服务或配置
运行 UmoWeb 相关健康检查
至少连续观察 30 分钟
写入结果和回滚命令
不达标立即回滚后再做下一项
```

禁止把多个服务停用合并成一次“批量优化”。

- [ ] **Step 4: 运行 swap 测试和配置检查**

```bash
bash scripts/openclaw/preflight/tests/add-openclaw-swap-test.sh
findmnt --verify
sysctl vm.swappiness
```

Expected: swap 测试 PASS，fstab 可解析，记录当前 swappiness 值。

- [ ] **Step 5: 提交**

```bash
git add scripts/openclaw/preflight docs/project/openclaw-memory-preflight.md
git commit -m "ops: apply reversible memory optimizations"
```

### Task 5: 执行 72 小时 Gate 0

**Files:**
- Modify: `docs/project/openclaw-memory-preflight.md`

**Interfaces:**
- Consumes: 优化后的采样数据。
- Produces: Gate 0 通过或不通过结论。
- Blocks: Node、OpenClaw、QQBot 安装和阶段 1。

- [ ] **Step 1: 启动新的 72 小时采样文件**

```bash
systemctl stop umoweb-openclaw-preflight.timer
bash scripts/openclaw/preflight/install-memory-preflight-timer.sh gate0
```

Expected: 新文件为 `/var/log/umoweb/openclaw-preflight/gate0.csv`，旧 baseline 文件保留。

- [ ] **Step 2: 观察 72 小时**

每 5 分钟必须有样本。出现以下任一情况立即失败：

```text
MemAvailable <= 500 MiB
宿主 OOM kill
frontend/backend/mysql 任一容器 unhealthy 或重启
openclaw.service 已存在
采样器写入非 CSV 或缺失字段
```

- [ ] **Step 3: 运行分析器**

```bash
python3 scripts/openclaw/preflight/analyze-memory.py /var/log/umoweb/openclaw-preflight/gate0.csv
```

Expected: `sample_count >= 864`、`min_mem_available_kib > 512000`、`pass=true`。

- [ ] **Step 4: 运行 UmoWeb 冒烟**

```powershell
cd "Server Side\UmoWebBackend"
.\scripts\api-smoke.ps1 -BaseUrl "http://127.0.0.1" -Username "admin" -Password "<current-password>"
```

Expected: 31/31 通过。

- [ ] **Step 5: 写入 Gate 0 结论**

`docs/project/openclaw-memory-preflight.md` 必须包含：

```text
观察开始和结束时间
样本总数
MemAvailable 最小值、P05、中位数
最低样本时的进程和容器状态
OOM 和容器重启次数
已应用优化及回滚命令
swap 使用但不计入通过条件
UmoWeb 冒烟结果
通过或不通过
```

- [ ] **Step 6: 提交**

```bash
git add docs/project/openclaw-memory-preflight.md
git commit -m "docs: record openclaw gate zero memory result"
```

## Gate 0 后续

- 通过：允许进入阶段 1，但只允许安装 OpenClaw 最小配置并执行 72 小时空载测试；UmoWeb 工具保持未注册。
- 不通过：不安装 OpenClaw，保留原始数据和报告，停止后续阶段。
