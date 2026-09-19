# DataAgent 自动部署失败排查与恢复指南

> 更新时间：2026-09-12  
> 仓库：`Pinkgoo0631/DataAnalysis-Agent`  
> 分支：`main`  
> 当前远程提交：`83956ad`

## 1. 当前结论

本次登录与环境隔离代码已经推送到 GitHub，但尚未成功部署到云服务器。

目前最明确的故障是：云服务器上的 GitHub Actions 自托管 Runner 无法稳定地从 GitHub 获取代码。现有证据更偏向服务器出站网络、DNS、TLS、代理或 Runner 工作目录问题，而不是 Java 登录代码编译失败。

本地已经成功执行与后端 Dockerfile 一致的构建命令：

```text
mvn install -DskipTests
BUILD SUCCESS
```

因此，认证代码和测试代码可以正常编译并生成后端 JAR。

## 2. 相关提交

| 提交 | 说明 |
| --- | --- |
| `9dc34a7` | 修复浏览器 Basic Auth 循环弹窗以及 SPA CSRF 登录失败 |
| `ee32b6c` | 为 GitHub 代码获取增加 HTTP/1.1、超时和三次重试 |
| `6d9ecca` | 为 Docker Compose 构建与启动增加一次重试 |
| `83956ad` | 改为复用用自托管 Runner 工作目录并执行增量更新 |

## 3. GitHub Actions 运行证据

### Run #7

- 对应提交：`9dc34a7`
- 失败步骤：`Clone latest main (shallow)`
- 退出码：`128`
- Docker、数据库迁移和健康检查均未执行。
- 地址：<https://github.com/Pinkgoo0631/DataAnalysis-Agent/actions/runs/34695275376>

### Run #8

- 对应提交：`ee32b6c`
- GitHub 代码获取成功。
- 失败步骤：`Build and restart backend + frontend`
- 说明除了 GitHub clone 外，Docker Hub、APT、Maven 或 PNPM 网络也可能不稳定。

### Run #9

- 对应提交：`6d9ecca`
- 失败步骤：`Fetch triggering commit (shallow, with retry)`
- 三次全量浅拉取均未成功。

### Run #10

- 对应提交：`83956ad`
- 失败步骤：`Update persistent checkout (shallow, with retry)`
- 退出码：`1`
- 地址：<https://github.com/Pinkgoo0631/DataAnalysis-Agent/actions/runs/34697014838>

## 4. 最可能的故障点

### 4.1 云服务器访问 GitHub 不稳定

这是当前概率最高的原因。

Runner 能接收 GitHub 下发的任务，但连续无法完成 Git 数据传输。这种情况通常由以下因素造成：

- DNS 解析异常或返回不可达地址；
- 云服务器到 GitHub 的国际网络链路丢包；
- 防火墙、安全组或代理限制出站 443；
- IPv6 地址可解析但实际不可达；
- TLS 证书链、系统时间或中间代理异常；
- GitHub 域名或其 CNAME 没有完整加入允许列表。

在云服务器执行：

```bash
getent ahosts github.com
curl -4Iv --connect-timeout 15 https://github.com
git ls-remote https://github.com/Pinkgoo0631/DataAnalysis-Agent.git
```

如果 `curl -4` 成功而默认 `curl` 失败，应重点检查 IPv6 路由和 DNS。

GitHub 自托管 Runner 至少需要访问：

```text
github.com
api.github.com
*.actions.githubusercontent.com
codeload.github.com
results-receiver.actions.githubusercontent.com
*.blob.core.windows.net
objects.githubusercontent.com
objects-origin.githubusercontent.com
```

参考：<https://docs.github.com/en/actions/reference/runners/self-hosted-runners#communication>

### 4.2 Runner 工作目录权限或 Git 仓库损坏

可能出现的日志：

```text
Permission denied
detected dubious ownership
not a git repository
index.lock already exists
cannot create directory
```

检查命令：

```bash
id
pwd
ls -ld /你的Runner目录
ls -ld /你的Runner目录/_work
find /你的Runner目录/_work -maxdepth 4 -name .git -type d -print
```

找到 DataAgent 工作目录后执行：

```bash
git -C /实际工作目录 status
git -C /实际工作目录 remote -v
git -C /实际工作目录 fsck --no-reflogs
```

不要在尚未确认路径时运行 `rm -rf` 或 `git clean`。

### 4.3 TLS、CA 证书或服务器时间异常

检查命令：

```bash
date -Is
timedatectl status
curl -Iv https://github.com
```

重点观察：

- `certificate verify failed`；
- 证书尚未生效或已经过期；
- 系统时间偏差过大；
- 企业代理或网络设备替换了 GitHub 证书。

不要通过关闭 TLS 校验作为正式修复。应更新系统 CA 证书、修正系统时间或正确配置代理证书。

### 4.4 磁盘、inode 或内存不足

检查命令：

```bash
df -h
df -i
free -h
docker system df
```

可能出现的日志：

```text
No space left on device
cannot allocate memory
killed
exit code 137
```

如果怀疑 OOM：

```bash
dmesg -T | grep -i -E 'oom|killed process' | tail -50
```

### 4.5 Docker 服务或 Runner 用户权限异常

检查命令：

```bash
systemctl is-active docker
docker info
docker compose version
id
ls -l /var/run/docker.sock
```

常见错误：

```text
Cannot connect to the Docker daemon
permission denied while trying to connect to the Docker daemon socket
docker: command not found
```

### 4.6 Docker Hub、APT、Maven 或 PNPM 网络不稳定

Run #8 已经进入 Docker 构建后失败，因此这些外部依赖端点也需要验证。

```bash
docker pull eclipse-temurin:17-jdk-jammy
docker pull node:22-alpine
docker pull nginx:alpine
curl -I --connect-timeout 15 https://repo.maven.apache.org/maven2/
curl -I --connect-timeout 15 https://registry.npmjs.org/
```

可能出现的日志：

```text
TLS handshake timeout
i/o timeout
connection reset by peer
temporary failure resolving
failed to fetch anonymous token
could not transfer artifact
ERR_PNPM_FETCH
```

### 4.7 Compose 服务、容器名或网络冲突

代码和镜像获取恢复后，下一阶段可能失败在 Compose。

```bash
docker compose -f /实际源码目录/docker-file/docker-compose.yml config
docker network inspect data-agent-network
docker ps -a --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
```

需要确认：

- `mysql0631` 容器存在；
- `mysql0631` 已加入 `data-agent-network`；
- 端口 `3000` 和 `8065` 没有被其他容器占用；
- Runner 用户能够执行 Docker Compose；
- Compose 使用的源码目录确实对应触发部署的提交。

### 4.8 数据库连接或用户隔离迁移失败

只有代码获取、镜像构建和容器启动都成功后，才会进入这一阶段。

```bash
docker logs --tail 300 data-agent-backend
docker inspect data-agent-backend --format '{{.State.Status}} {{.State.ExitCode}}'
```

重点检查：

- MySQL 地址 `mysql0631:3306` 是否可访问；
- 数据库 `data_agent` 是否存在；
- 用户名和密码是否正确；
- Runner/Compose 是否设置 `DATA_AGENT_DATASOURCE_SQL_INIT=never`；
- `app_user` 和 `data_agent_schema_migration` 是否能创建；
- 业务表是否允许增加 `user_id` 列和索引；
- 数据库账号是否具有 `CREATE TABLE`、`ALTER TABLE`、`CREATE INDEX` 和 `UPDATE` 权限。

### 4.9 后端健康检查失败

当前工作流检查：

```text
http://127.0.0.1:8065/v3/api-docs
```

检查命令：

```bash
curl -i --max-time 10 http://127.0.0.1:8065/v3/api-docs
docker logs --tail 300 data-agent-backend
```

期望结果是 HTTP 200。该路径不应被登录保护。

### 4.10 前端烟雾测试失败

检查命令：

```bash
curl -i --max-time 10 http://127.0.0.1:3000/
docker logs --tail 200 data-agent-frontend
```

返回 HTML 应包含 `/_nuxt/` 资源引用。

## 5. 获取 Runner 的准确日志

GitHub 官方说明，自托管 Runner 的详细日志位于安装目录的 `_diag` 中：

```bash
cd /你的/actions-runner/安装目录
ls -lt _diag | head -20
tail -n 300 "$(ls -t _diag/Worker_*.log | head -1)"
```

Runner 服务日志：

```bash
cat .service
systemctl --type=service | grep actions.runner
journalctl -u actions.runner.实际服务名.service --since '30 minutes ago' --no-pager
```

参考：<https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/monitor-and-troubleshoot>

分享日志前应删除或遮挡：PAT、API Key、Cookie、数据库密码、代理认证信息和私钥内容。

## 6. SSH 主机指纹异常

当前本机连接 `8.138.211.23` 时收到：

```text
WARNING: REMOTE HOST IDENTIFICATION HAS CHANGED!
ED25519 SHA256:Cbg6o86PJtD2wuKwlPrUBo00wF7NHcgZ5RbY/abz8Ns
```

这可能是服务器重装、SSH Host Key 轮换，也可能是连接到了错误主机或遭遇中间人攻击。不能直接绕过。

请先通过云厂商控制台登录服务器并执行：

```bash
ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub
```

只有控制台显示的指纹与下面内容完全一致时，才能更新本机记录：

```text
SHA256:Cbg6o86PJtD2wuKwlPrUBo00wF7NHcgZ5RbY/abz8Ns
```

确认一致后，才可以在本机执行：

```powershell
ssh-keygen -R 8.138.211.23
ssh 8.138.211.23
```

另一个 SSH 配置目标 `43.136.85.231:22` 当前连接超时，尚不能确认哪一台是 DataAgent Runner 主机。

## 7. 推荐执行顺序

1. 在云厂商控制台核对 `8.138.211.23` 的 SSH ED25519 指纹。
2. 安全恢复 SSH 后，读取最新 `_diag/Worker_*.log`。
3. 在服务器执行 GitHub、DNS、IPv4 和 TLS 检查。
4. 检查磁盘、inode、内存和 Docker 状态。
5. 单独测试 GitHub 仓库 `git ls-remote`。
6. 单独测试 Docker Hub、Maven Central 和 NPM Registry。
7. 网络恢复后，在 GitHub Actions 手动重新运行 `Deploy to Server`。
8. 部署成功后验证登录、注册及数据隔离。

## 8. 部署成功验收标准

### 基础服务

```bash
curl -f http://127.0.0.1:8065/v3/api-docs
curl -f http://127.0.0.1:3000/
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
```

### 登录功能

1. 未登录访问应用时显示 DataAgent 登录页，不出现浏览器原生 Basic Auth 弹窗。
2. 使用 `admin / 1234567` 可以登录。
3. 登录后 `/api/auth/me` 返回 `admin` 和 `ADMIN`。
4. 登录后可以看到迁移前已有数据。

### 新用户隔离

1. 注册一个全新用户。
2. 使用新用户登录。
3. 智能体列表为空。
4. 模型配置列表为空。
5. 业务知识不可见。
6. 数据源不可见。
7. 新用户不能通过修改 URL 或 ID 访问 admin 的资源。

## 9. 当前安全状态

- 工作流失败时没有删除生产数据库。
- Run #9 和 Run #10 均在源码更新阶段终止，没有执行新的数据库迁移。
- 旧版应用容器应继续运行，但登录修复提交 `9dc34a7` 尚未确认部署成功。
- 不应在没有核对 SSH 指纹的情况下使用 `StrictHostKeyChecking=no` 或删除 `known_hosts` 记录。

