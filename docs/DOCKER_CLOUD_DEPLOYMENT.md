# DataAgent 云服务器 Docker 部署完整指南

> 适用场景：单台 Linux 云服务器、Docker Compose 部署 DataAgent、Nginx 安装在宿主机而不是容器中。
>
> 本文命令以 Ubuntu/Debian 为例，默认项目域名为 `dataagent.example.com`，服务器项目目录为 `/opt/data-agent`。执行时请替换成你的真实域名和目录。
>
> 文档基于仓库当前的 Nuxt 4 前端、Spring Boot 后端、MySQL 管理数据库和 Python Docker 沙盒设计编写。

## 目录

- [第 1 章：先理解最终架构](#第-1-章先理解最终架构)
- [第 2 章：必须掌握的前置知识](#第-2-章必须掌握的前置知识)
- [第 3 章：部署前风险检查](#第-3-章部署前风险检查)
- [第 4 章：准备云服务器、域名和防火墙](#第-4-章准备云服务器域名和防火墙)
- [第 5 章：安装 Docker 和检查 Nginx](#第-5-章安装-docker-和检查-nginx)
- [第 6 章：上传代码并准备生产配置](#第-6-章上传代码并准备生产配置)
- [第 7 章：创建生产前端镜像](#第-7-章创建生产前端镜像)
- [第 8 章：创建生产 Docker Compose](#第-8-章创建生产-docker-compose)
- [第 9 章：配置生产环境变量](#第-9-章配置生产环境变量)
- [第 10 章：初始化管理数据库](#第-10-章初始化管理数据库)
- [第 11 章：构建并启动 DataAgent](#第-11-章构建并启动-dataagent)
- [第 12 章：配置宿主机 Nginx](#第-12-章配置宿主机-nginx)
- [第 13 章：配置访问保护和 HTTPS](#第-13-章配置访问保护和-https)
- [第 14 章：上线验收](#第-14-章上线验收)
- [第 15 章：日常运维、更新和回滚](#第-15-章日常运维更新和回滚)
- [第 16 章：备份与恢复](#第-16-章备份与恢复)
- [第 17 章：常见故障排查](#第-17-章常见故障排查)
- [第 18 章：生产安全与扩展建议](#第-18-章生产安全与扩展建议)
- [附录 A：完整部署检查清单](#附录-a完整部署检查清单)
- [附录 B：官方参考资料](#附录-b官方参考资料)

---

## 第 1 章：先理解最终架构

### 1.1 本文采用的架构

```text
                         Internet
                             │
                    HTTP 80 / HTTPS 443
                             │
                  ┌──────────▼──────────┐
                  │ 宿主机 Nginx        │
                  │ TLS、鉴权、反向代理  │
                  └───────┬───────┬─────┘
                          │       │
                     / 页面请求    └─ /api、/nl2sql、/uploads、/sse
                          │                    │
                  127.0.0.1:3000       127.0.0.1:8065
                          │                    │
                 ┌────────▼───────┐   ┌────────▼────────┐
                 │ Nuxt Node 容器 │   │ Spring Boot 容器 │
                 │ frontend       │   │ backend          │
                 └────────────────┘   └───────┬─────────┘
                                              │ Docker 网络
                                      ┌───────▼────────┐
                                      │ MySQL 容器      │
                                      │ 管理数据库      │
                                      └────────────────┘
```

三个应用容器由 Docker Compose 管理：

| 服务 | 用途 | 宿主机监听地址 | 是否直接开放公网 |
| --- | --- | --- | --- |
| `frontend` | 运行 Nuxt 生产服务 | `127.0.0.1:3000` | 否 |
| `backend` | 运行 Spring Boot API | `127.0.0.1:8065` | 否 |
| `mysql` | 保存智能体、会话、配置等管理数据 | 不映射端口 | 否 |

宿主机 Nginx 是唯一公网入口。

### 1.2 为什么这样设计

1. **不把 3000、8065、3306 暴露到公网。** 外部只能访问 Nginx，攻击面更小。
2. **前后端使用同一个域名。** 浏览器请求 `/api/...` 时不跨域，不需要额外配置 CORS。
3. **Nginx 适合处理 HTTPS 和长连接。** DataAgent 的问答过程包含流式响应，必须避免代理缓冲。
4. **MySQL 使用 Docker 命名卷。** 重建容器不会删除业务数据。
5. **Docker 内不再运行 Nginx。** 前端使用 Nuxt 自带的 Nitro Node Server，符合“宿主机 Nginx”的要求。

### 1.3 哪些内容不包含在默认架构中

- `docker-compose.yml` 中的 `mysql-data` 和 `postgres-data` 是模拟分析数据源，不是 DataAgent 自身必需组件。
- Milvus、PGVector 等可选向量数据库默认不启动；默认使用 Chroma。
- Prometheus、Grafana、集中日志等监控组件默认不启动。
- 负载均衡、多节点高可用和 Kubernetes 不属于本文单机部署范围。

---

## 第 2 章：必须掌握的前置知识

不要求你精通 Docker，但需要理解下面几个概念。

### 2.1 镜像和容器

- **镜像（Image）**：应用和运行环境的只读模板。
- **容器（Container）**：镜像启动后的运行实例。
- 修改代码后需要重新构建镜像，再重新创建容器；直接进入容器修改文件不是可靠的部署方式。

### 2.2 Dockerfile

Dockerfile 描述“如何构建镜像”。本项目需要两个应用镜像：

- 后端镜像：编译 Maven 项目，运行 Spring Boot JAR。
- 前端镜像：安装 pnpm 依赖，执行 Nuxt 构建，运行 `.output/server/index.mjs`。

### 2.3 Docker Compose

Compose 文件描述一组需要共同运行的容器，包括：

- 使用哪个镜像或 Dockerfile；
- 容器之间的依赖关系；
- 环境变量；
- 网络；
- 数据卷；
- 端口映射和重启策略。

它让三个服务可以通过一条命令启动，而不是分别维护多条 `docker run` 命令。

### 2.4 Docker 网络

Compose 网络内，容器使用**服务名**互相访问：

```text
backend → jdbc:mysql://mysql:3306/data_agent
```

后端容器中的 `localhost` 指的是后端容器自己，不是 MySQL 容器，也不是云服务器宿主机。因此数据库地址必须写 `mysql`，不能写 `127.0.0.1`。

### 2.5 数据卷

容器文件系统是可替换的。下面两类数据必须放在命名卷中：

- MySQL 数据目录 `/var/lib/mysql`；
- 后端本地上传目录 `/app/uploads`。

如果没有卷，执行镜像更新、容器重建或删除容器后，数据可能丢失。

### 2.6 反向代理

Nginx 接收公网请求，再根据 URL 将请求转发给本机不同端口：

| URL | 转发目标 | 原因 |
| --- | --- | --- |
| `/`、`/_nuxt/...` | 前端 `127.0.0.1:3000` | 页面和静态资源 |
| `/api/...` | 后端 `127.0.0.1:8065` | 管理 API 和问答 API |
| `/nl2sql/...` | 后端 `127.0.0.1:8065` | NL2SQL 接口 |
| `/uploads/...` | 后端 `127.0.0.1:8065` | 本地上传文件 |
| `/sse` | 后端 `127.0.0.1:8065` | 可选 MCP SSE 接口 |

### 2.7 SSE 与普通 HTTP 的差别

DataAgent 的问答响应可能持续较长时间，并不断推送中间状态。Nginx 如果缓存响应，浏览器可能长时间看不到进度。因此 API 代理需要：

```nginx
proxy_buffering off;
proxy_cache off;
proxy_read_timeout 24h;
```

### 2.8 管理数据库与分析数据源不是同一个概念

这是部署中最容易混淆的地方。

- **管理数据库**：保存智能体配置、模型配置、提示词、会话和数据源连接信息，由环境变量 `DATA_AGENT_DATASOURCE_*` 配置。
- **分析数据源**：创建智能体后在 Web 页面里添加，是 Agent 真正查询和分析的数据，例如订单库、游戏销量库。

生产环境中建议将两者分开，避免分析 SQL 误操作 DataAgent 自己的管理表。

---

## 第 3 章：部署前风险检查

### 3.1 当前仓库中的 Docker 配置不能直接用于本方案

当前 `docker-file/Dockerfile-frontend` 存在两个不适合当前仓库的点：

1. 它复制的是 `data-agent-frontend`，但当前实际目录是 `data-agent-frontend-nuxt`。
2. 它按 `dist` 静态目录处理构建结果，而 Nuxt 4 的 `nuxt build` 默认生成 `.output`，Node Server 入口是 `.output/server/index.mjs`。

当前 `docker-file/docker-compose.yml` 还会在前端容器中启动 Nginx，并挂载 `docker-file/config/nginx.conf`。本文要求 Nginx 位于宿主机，因此会单独创建生产前端 Dockerfile 和生产 Compose 文件，不改动开发配置。

### 3.2 当前内部 MySQL 没有真正启用持久卷

仓库 Compose 中 MySQL 的数据卷挂载被注释。生产 Compose 必须挂载：

```yaml
volumes:
  - mysql-data:/var/lib/mysql
```

### 3.3 删除源码中的真实数据库地址和密码

部署前检查：

```bash
grep -RniE 'password:|api[_-]?key|jdbc:mysql://' \
  data-agent-management/src/main/resources \
  --exclude='application-h2.yml'
```

`application.yml` 中不应保存真实公网数据库地址、真实密码或模型密钥。生产值统一放在 `.env.prod`，并且不能提交 Git。

如果真实密码曾经提交到 Git、发到聊天、截图或上传到公开仓库，仅仅从当前文件删除并不够，必须在数据库端**轮换密码**。

### 3.4 DataAgent 管理端默认没有完整的用户登录保护

当前 `WebFluxSecurityConfiguration` 只要求 `/api/stream/search` 使用 Agent API Key，其余请求为 `permitAll()`。这意味着直接公开管理页面后，知道域名的人可能访问配置和管理接口。

上线至少选择一种保护方式：

1. Nginx Basic Auth；
2. 云防火墙或 Nginx IP 白名单；
3. 公司 VPN、零信任网关；
4. 在应用中实现正式用户认证和权限系统。

个人或内部使用推荐先采用“HTTPS + Nginx Basic Auth + IP 限制”。对外提供产品服务时，应实现应用级身份认证，不能只依赖 Basic Auth。

### 3.5 默认向量库不会持久化

`application.yml` 默认配置：

```yaml
spring:
  ai:
    vectorstore:
      type: simple
```

`simple` 是内存向量库。正式环境默认使用 Chroma 持久化向量，并通过独立 Docker 卷保存 Lucene 关键词索引；请同时备份 Chroma 与 Lucene 数据卷。

### 3.6 Python 沙盒需要 Docker Socket

只有执行包含 Python 步骤的工作流才需要：

```yaml
- /var/run/docker.sock:/var/run/docker.sock
```

Docker Socket 权限非常高。拿到该 Socket 的进程通常能够控制宿主机上的 Docker，接近宿主机 root 权限。

- 只使用 SQL 分析：删除 Docker Socket 挂载。
- 需要 Python 分析：保留挂载，但必须限制后台访问，定期更新镜像，生产环境进一步考虑受限 Docker Socket Proxy 或独立沙盒主机。

---

## 第 4 章：准备云服务器、域名和防火墙

### 4.1 建议的服务器资源

用于单机体验或小规模内部使用：

| 资源 | 最低建议 | 更稳妥建议 | 说明 |
| --- | --- | --- | --- |
| CPU | 2 核 | 4 核以上 | Maven、Nuxt 构建和 Python 沙盒会占用 CPU |
| 内存 | 4 GB | 8 GB 以上 | 构建阶段和并发分析需要额外内存 |
| 磁盘 | 30 GB | 60 GB 以上 | Docker 镜像、MySQL、上传文件和日志 |
| 系统 | Ubuntu 22.04+ | Ubuntu 24.04 LTS | 本文命令按 Ubuntu 编写 |

如果服务器只有 2 GB 内存，Maven 或 Nuxt 构建容易被 OOM Killer 终止。可以在 CI 或本地构建镜像后推送到私有镜像仓库，但这属于另一种发布流程。

### 4.2 安全组和防火墙

云厂商安全组建议：

| 端口 | 来源 | 是否开放 | 用途 |
| --- | --- | --- | --- |
| `22/tcp` | 你的固定 IP | 是 | SSH 管理 |
| `80/tcp` | `0.0.0.0/0`、`::/0` | 是 | HTTP 和证书签发 |
| `443/tcp` | `0.0.0.0/0`、`::/0` | 是 | HTTPS |
| `3000/tcp` | 公网 | 否 | 前端内部端口 |
| `8065/tcp` | 公网 | 否 | 后端内部端口 |
| `3306/tcp` | 公网 | 否 | 管理数据库 |

为什么：即使 Docker 或应用配置存在疏漏，云安全组仍能形成第二层防护。

如果启用了 UFW：

```bash
sudo ufw allow OpenSSH
sudo ufw allow 'Nginx Full'
sudo ufw status
```

在远程服务器上启用 UFW 前，必须先允许 SSH，否则可能把自己锁在服务器外。

### 4.3 域名解析

在域名服务商添加：

```text
类型：A
主机记录：dataagent
值：云服务器公网 IPv4
```

验证：

```bash
dig +short dataagent.example.com
```

输出应是云服务器公网 IP。HTTPS 证书签发依赖正确的 DNS 解析。

---

## 第 5 章：安装 Docker 和检查 Nginx

### 5.1 安装 Docker Engine 与 Compose 插件

以下是 Ubuntu 使用 Docker 官方仓库的方式。不要再安装旧版独立命令 `docker-compose`；本文使用新版 `docker compose` 插件。

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl

sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

. /etc/os-release
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${UBUNTU_CODENAME:-$VERSION_CODENAME} stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt-get update
sudo apt-get install -y \
  docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

启动并设置开机启动：

```bash
sudo systemctl enable --now docker
```

验证：

```bash
sudo docker version
sudo docker compose version
sudo docker run --rm hello-world
```

为什么使用官方仓库：Ubuntu 自带仓库中的版本可能落后，官方仓库会同时提供 Docker Engine、Buildx 和 Compose 插件。

### 5.2 是否把当前用户加入 docker 组

可选操作：

```bash
sudo usermod -aG docker "$USER"
```

然后退出 SSH 并重新登录。

注意：`docker` 组拥有接近 root 的权限。共享服务器上，更安全的方式是保持使用 `sudo docker ...`。本文后续命令默认当前用户已经有 Docker 权限；否则在 Docker 命令前添加 `sudo`。

### 5.3 检查宿主机 Nginx

如果已经安装：

```bash
nginx -v
sudo nginx -t
sudo systemctl status nginx --no-pager
```

如果尚未安装：

```bash
sudo apt-get update
sudo apt-get install -y nginx
sudo systemctl enable --now nginx
```

访问 `http://服务器公网IP`，应看到 Nginx 默认页。此时只是确认网络和 Nginx 正常，DataAgent 尚未部署。

---

## 第 6 章：上传代码并准备生产配置

### 6.1 创建项目目录

```bash
sudo mkdir -p /opt/data-agent
sudo chown -R "$USER":"$USER" /opt/data-agent
```

为什么放在 `/opt`：它通常用于人工部署的第三方应用，和系统包、用户主目录分离，便于备份和权限管理。

### 6.2 获取代码

使用 Git：

```bash
git clone YOUR_REPOSITORY_URL /opt/data-agent
cd /opt/data-agent
```

如果代码没有远程仓库，可以从本地上传：

```bash
rsync -av --exclude='.git' --exclude='**/node_modules' --exclude='**/target' \
  ./DataAgent/ USER@SERVER_IP:/opt/data-agent/
```

不要上传本地 `node_modules`、`target`、`.output`，因为 Linux 容器会重新构建它们，本地 Windows 产物也不能可靠地在 Linux 容器中运行。

### 6.3 创建 `.dockerignore`

在项目根目录创建 `.dockerignore`：

```dockerignore
.git
.github
.idea
.vscode
**/node_modules
**/.nuxt
**/.output
**/target
uploads
vectorstore
.env
.env.*
*.log
```

为什么：Docker 构建会把整个 Context 发送给构建引擎。忽略无关文件可以加快构建，并防止密钥、Git 历史和本地上传文件进入镜像层。

### 6.4 确保生产环境文件不进入 Git

在 `.gitignore` 中加入：

```gitignore
.env.prod
backups/
```

验证：

```bash
git check-ignore .env.prod
```

如果输出 `.env.prod`，说明忽略规则生效。

---

## 第 7 章：创建生产前端镜像

创建 `docker-file/Dockerfile-frontend-prod`：

```dockerfile
FROM node:22-alpine AS build

WORKDIR /app

RUN corepack enable \
    && corepack prepare pnpm@11.9.0 --activate

COPY data-agent-frontend-nuxt/ ./

RUN pnpm install --frozen-lockfile
RUN pnpm build

FROM node:22-alpine

WORKDIR /app

COPY --from=build /app/.output ./.output

ENV NODE_ENV=production
ENV NITRO_HOST=0.0.0.0
ENV NITRO_PORT=3000

USER node

EXPOSE 3000

CMD ["node", ".output/server/index.mjs"]
```

### 7.1 为什么使用多阶段构建

第一阶段包含源码、pnpm 和编译依赖；第二阶段只复制 `.output`。这样运行镜像更小，也不会携带完整源码和开发依赖。

### 7.2 为什么不在前端容器里使用 Nginx

Nuxt 4 构建后可以通过 Node Server 启动：

```bash
NODE_ENV=production node .output/server/index.mjs
```

宿主机 Nginx 已负责公网入口和 HTTPS，因此容器只需监听内部端口 3000。官方文档也说明 Node Server 默认监听 3000，并支持通过 `NITRO_HOST`、`NITRO_PORT` 控制地址和端口。

### 7.3 为什么仍保留 Nuxt 的 `/api` routeRules

当前前端配置中的浏览器请求使用相对路径 `/api/...`。生产环境中，请求首先到宿主机 Nginx，并被 Nginx 直接转发到 8065，因此不会进入 Nuxt 的开发代理。

这实现了：

```text
浏览器 https://dataagent.example.com/api/...
  → 宿主机 Nginx
  → 127.0.0.1:8065
```

前后端同源，因此没有跨域问题。

---

## 第 8 章：创建生产 Docker Compose

创建 `docker-file/docker-compose.prod.yml`：

```yaml
name: data-agent

x-default-logging: &default-logging
  driver: json-file
  options:
    max-size: "20m"
    max-file: "5"

services:
  mysql:
    image: mysql:8.0
    container_name: data-agent-mysql
    restart: unless-stopped
    environment:
      MYSQL_DATABASE: ${MYSQL_DATABASE}
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
    volumes:
      - mysql-data:/var/lib/mysql
    networks:
      - data-agent-network
    healthcheck:
      test:
        - CMD-SHELL
        - mysqladmin ping -h 127.0.0.1 -uroot -p$${MYSQL_ROOT_PASSWORD} --silent
      interval: 10s
      timeout: 5s
      retries: 20
      start_period: 30s
    logging: *default-logging

  backend:
    image: data-agent-backend:${DATA_AGENT_VERSION:-latest}
    build:
      context: ..
      dockerfile: docker-file/Dockerfile-backend
    container_name: data-agent-backend
    restart: unless-stopped
    environment:
      DATA_AGENT_DATASOURCE_URL: >-
        jdbc:mysql://mysql:3306/${MYSQL_DATABASE}?useUnicode=true&characterEncoding=utf-8&zeroDateTimeBehavior=convertToNull&transformedBitIsBoolean=true&allowMultiQueries=true&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai
      DATA_AGENT_DATASOURCE_USERNAME: ${MYSQL_USER}
      DATA_AGENT_DATASOURCE_PASSWORD: ${MYSQL_PASSWORD}
      DATA_AGENT_DATASOURCE_SQL_INIT: ${DATA_AGENT_SQL_INIT:-never}
      AI_DASHSCOPE_API_KEY: ${AI_DASHSCOPE_API_KEY:-}
      DATAAGENT_SANDBOX_DOCKER_HOST: unix:///var/run/docker.sock
      DATAAGENT_SANDBOX_IMAGE: ${DATAAGENT_SANDBOX_IMAGE:-agentscope-registry.ap-southeast-1.cr.aliyuncs.com/agentscope/runtime-sandbox-base:latest}
      DATAAGENT_PYPI_INDEX_URL: ${DATAAGENT_PYPI_INDEX_URL:-https://pypi.org/simple}
    ports:
      - "127.0.0.1:8065:8065"
    volumes:
      - backend-uploads:/app/uploads
      # 仅在需要 Python 分析时保留下一行
      - /var/run/docker.sock:/var/run/docker.sock
    depends_on:
      mysql:
        condition: service_healthy
    networks:
      - data-agent-network
    logging: *default-logging

  frontend:
    image: data-agent-frontend:${DATA_AGENT_VERSION:-latest}
    build:
      context: ..
      dockerfile: docker-file/Dockerfile-frontend-prod
    container_name: data-agent-frontend
    restart: unless-stopped
    environment:
      NODE_ENV: production
      NITRO_HOST: 0.0.0.0
      NITRO_PORT: 3000
    ports:
      - "127.0.0.1:3000:3000"
    depends_on:
      - backend
    networks:
      - data-agent-network
    logging: *default-logging

volumes:
  mysql-data:
    name: data-agent-mysql-data
  backend-uploads:
    name: data-agent-backend-uploads

networks:
  data-agent-network:
    name: data-agent-network
    driver: bridge
```

### 8.1 关键配置说明

#### 端口只绑定到 127.0.0.1

```yaml
- "127.0.0.1:3000:3000"
- "127.0.0.1:8065:8065"
```

如果写成 `3000:3000`，Docker 默认可能监听所有网卡，公网可以绕过 Nginx 直接访问。绑定回环地址后，只有宿主机 Nginx 能访问。

#### MySQL 不使用 ports

MySQL 只需被 `backend` 访问，Compose 网络已经提供通信。没有端口映射意味着公网和宿主机其他网络接口都不能直接连接 3306。

#### 后端通过 mysql 服务名连接数据库

```text
jdbc:mysql://mysql:3306/...
```

`mysql` 会由 Docker 内置 DNS 解析到 MySQL 容器。

#### restart: unless-stopped

服务器重启或进程异常退出后，Docker 会自动恢复容器；如果管理员明确执行过停止操作，则不会自动重新启动。

#### 日志轮转

Docker 默认 JSON 日志可能无限增长。`max-size` 和 `max-file` 限制每个容器最多保留约 100 MB 日志，避免磁盘被填满。

#### 显式命名数据卷

显式名称让备份命令稳定，不受 Compose 项目目录名变化影响。

### 8.2 SQL-only 模式

如果确定不执行 Python 工作流，从 `backend` 删除：

```yaml
DATAAGENT_SANDBOX_DOCKER_HOST: unix:///var/run/docker.sock
DATAAGENT_SANDBOX_IMAGE: ...
DATAAGENT_PYPI_INDEX_URL: ...
```

以及：

```yaml
- /var/run/docker.sock:/var/run/docker.sock
```

删除高权限 Socket 是明显的安全提升。

---

## 第 9 章：配置生产环境变量

### 9.1 生成密码

分别生成两个不同密码：

```bash
openssl rand -hex 24
openssl rand -hex 24
```

为什么使用十六进制：它具有足够熵，并避免 `.env` 中的 `$`、`#`、空格等特殊字符解析问题。

### 9.2 创建 `.env.prod`

```dotenv
DATA_AGENT_VERSION=2026.09.09-1

MYSQL_DATABASE=data_agent
MYSQL_USER=dataagent
MYSQL_PASSWORD=替换为第一个随机密码
MYSQL_ROOT_PASSWORD=替换为第二个随机密码

# 生产推荐手动导入 schema，因此保持 never
DATA_AGENT_SQL_INIT=never

# 如果启动或默认模型需要 DashScope，再填写；也可启动后在模型配置页面添加模型
AI_DASHSCOPE_API_KEY=

# 需要 Python 分析时使用。正式生产建议固定到镜像 digest，而不是长期使用 latest
DATAAGENT_SANDBOX_IMAGE=agentscope-registry.ap-southeast-1.cr.aliyuncs.com/agentscope/runtime-sandbox-base:latest
DATAAGENT_PYPI_INDEX_URL=https://pypi.org/simple
```

保护文件：

```bash
chmod 600 .env.prod
```

### 9.3 检查 Compose 语法

```bash
docker compose \
  --env-file .env.prod \
  -f docker-file/docker-compose.prod.yml \
  config --quiet
```

没有输出且退出码为 0 表示语法和变量解析通过。

不要在共享终端或日志中执行不带 `--quiet` 的 `docker compose config`，因为它会输出插值后的完整配置，其中可能包含数据库密码。

### 9.4 MySQL 密码的一个重要特性

`MYSQL_PASSWORD`、`MYSQL_ROOT_PASSWORD` 只在 MySQL 数据目录**首次初始化**时生效。已有数据卷的情况下，只修改 `.env.prod` 不会自动修改数据库内部密码。

因此：

- 首次部署前把密码确定好；
- 后续轮换密码时，先在 MySQL 中执行 `ALTER USER`，再同步修改 `.env.prod`；
- 不要为了让新密码生效而删除生产数据卷。

---

## 第 10 章：初始化管理数据库

### 10.1 推荐方式：生产环境只导入表结构

先启动 MySQL：

```bash
docker compose \
  --env-file .env.prod \
  -f docker-file/docker-compose.prod.yml \
  up -d mysql
```

确认健康：

```bash
docker compose \
  --env-file .env.prod \
  -f docker-file/docker-compose.prod.yml \
  ps mysql
```

导入管理表结构：

```bash
docker compose \
  --env-file .env.prod \
  -f docker-file/docker-compose.prod.yml \
  exec -T mysql sh -lc \
  'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' \
  < data-agent-management/src/main/resources/sql/schema.sql
```

为什么推荐手动导入：

- 可以明确知道执行了哪个 SQL 文件；
- 避免每次后端重启都重复执行初始化；
- 不会自动写入演示智能体和演示知识数据；
- 发生错误时更容易定位。

### 10.2 可选：导入演示数据

只在体验环境执行：

```bash
docker compose \
  --env-file .env.prod \
  -f docker-file/docker-compose.prod.yml \
  exec -T mysql sh -lc \
  'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' \
  < data-agent-management/src/main/resources/sql/data.sql
```

`data.sql` 包含固定 ID 的示例记录，不建议在已有生产数据的数据库上重复导入。

### 10.3 可选：使用 Spring 自动初始化

快速体验时可以在 `.env.prod` 中临时设置：

```dotenv
DATA_AGENT_SQL_INIT=always
```

后端首次启动成功后立刻改回：

```dotenv
DATA_AGENT_SQL_INIT=never
```

然后重新创建后端容器：

```bash
docker compose \
  --env-file .env.prod \
  -f docker-file/docker-compose.prod.yml \
  up -d backend
```

生产环境不应长期使用 `always`，因为 `data.sql` 中存在固定 ID 的 `INSERT`，重复执行会产生错误日志或部分初始化。

### 10.4 检查表是否存在

```bash
docker compose \
  --env-file .env.prod \
  -f docker-file/docker-compose.prod.yml \
  exec mysql mysql -u dataagent -p data_agent
```

输入密码后执行：

```sql
SHOW TABLES;
EXIT;
```

应看到 `agent`、`datasource`、`chat_session`、`model_config` 等表。

---

## 第 11 章：构建并启动 DataAgent

### 11.1 拉取基础镜像并构建

```bash
cd /opt/data-agent

docker compose \
  --env-file .env.prod \
  -f docker-file/docker-compose.prod.yml \
  build --pull
```

为什么使用 `--pull`：在不改变 Dockerfile 的情况下尝试获取基础镜像的最新补丁版本。正式环境如果追求完全可复现，应进一步将所有基础镜像固定到 digest。

后端 Maven 构建和前端 pnpm 构建可能持续数分钟。服务器内存不足时可能出现退出码 137，通常表示进程被 OOM Killer 终止。

### 11.2 Python 模式预拉取沙盒镜像

如果保留了 Docker Socket 并需要 Python 分析：

```bash
docker pull agentscope-registry.ap-southeast-1.cr.aliyuncs.com/agentscope/runtime-sandbox-base:latest
```

为什么提前拉取：避免用户第一次执行 Python 工作流时才等待大型运行时镜像下载，也能提前发现服务器无法访问镜像仓库的问题。

### 11.3 启动全部服务

```bash
docker compose \
  --env-file .env.prod \
  -f docker-file/docker-compose.prod.yml \
  up -d
```

`-d` 表示后台运行。不要使用前台方式长期占用 SSH 会话。

### 11.4 查看状态

```bash
docker compose \
  --env-file .env.prod \
  -f docker-file/docker-compose.prod.yml \
  ps
```

预期：

- `data-agent-mysql` 为 `healthy`；
- `data-agent-backend` 为 `Up`；
- `data-agent-frontend` 为 `Up`。

### 11.5 查看日志

```bash
docker compose \
  --env-file .env.prod \
  -f docker-file/docker-compose.prod.yml \
  logs --tail=200 backend frontend mysql
```

持续跟踪：

```bash
docker compose \
  --env-file .env.prod \
  -f docker-file/docker-compose.prod.yml \
  logs -f backend frontend
```

按 `Ctrl+C` 只会退出日志查看，不会停止后台容器。

### 11.6 在宿主机本地验证端口

```bash
curl -I http://127.0.0.1:3000/
curl -fsS http://127.0.0.1:8065/v3/api-docs | head
```

第一条通常返回 200 或重定向；第二条应输出 OpenAPI JSON 的开头。

从另一台电脑直接访问 `http://服务器IP:3000` 或 `:8065` 应失败，因为端口只监听 `127.0.0.1`。这是预期安全行为。

---

## 第 12 章：配置宿主机 Nginx

### 12.1 创建站点配置

Ubuntu/Debian 创建：

```bash
sudo nano /etc/nginx/sites-available/data-agent.conf
```

写入：

```nginx
server {
    listen 80;
    listen [::]:80;

    server_name dataagent.example.com;

    client_max_body_size 10m;

    # DataAgent 后端 API、SSE、上传文件和可选 MCP SSE 端点。
    # proxy_pass 后不添加路径或结尾斜杠，因此原始 URI 会被完整保留。
    location ~ ^/(api|nl2sql|uploads|sse)(/|$) {
        proxy_pass http://127.0.0.1:8065;
        proxy_http_version 1.1;

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Connection "";

        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 24h;
        proxy_send_timeout 300s;
        proxy_connect_timeout 75s;
        gzip off;
    }

    # 其余路径交给 Nuxt 前端。
    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_http_version 1.1;

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_read_timeout 120s;
    }
}
```

如果你的系统使用 `/etc/nginx/conf.d/`，可以把相同 `server` 配置保存为：

```text
/etc/nginx/conf.d/data-agent.conf
```

不要同时在 `sites-enabled` 和 `conf.d` 加载同一配置，否则会出现重复 `server_name` 警告。

### 12.2 启用站点

使用 `sites-available` 的系统执行：

```bash
sudo ln -s /etc/nginx/sites-available/data-agent.conf \
  /etc/nginx/sites-enabled/data-agent.conf
```

如果链接已经存在，不需要重复创建。

### 12.3 测试并重载

```bash
sudo nginx -t
sudo systemctl reload nginx
```

必须先执行 `nginx -t`。如果配置错误，直接 reload 可能导致新配置不生效；严重时可能影响同一服务器上的其他站点。

### 12.4 验证 HTTP

```bash
curl -I http://dataagent.example.com/
curl -I http://dataagent.example.com/v3/api-docs
```

第二条默认可能被前端接收，因为示例 Nginx 只公开业务 API 路径。这并不影响应用。若确实要公开 Swagger，可额外代理 `/v3/api-docs` 和 `/swagger-ui`，但生产环境不建议向公网暴露调试文档。

---

## 第 13 章：配置访问保护和 HTTPS

### 13.1 HTTPS 和身份认证解决的是不同问题

HTTPS 保护传输过程，身份认证阻止未授权用户访问页面。由于当前管理接口大部分为 `permitAll()`，公开部署必须同时配置 HTTPS 和访问控制。

建议先完成证书签发，再启用全站 Basic Auth。这样不会让 HTTP Basic Auth 意外阻挡首次 ACME HTTP 验证。

### 13.2 先获取 HTTPS 证书

前置条件：

- 域名已经解析到该服务器；
- 公网能访问 80 端口；
- `sudo nginx -t` 通过；
- HTTP 站点可以正常打开。

按照 Certbot 官方推荐的 Snap 安装方式：

```bash
sudo snap install --classic certbot
sudo ln -s /snap/bin/certbot /usr/local/bin/certbot
```

签发并自动修改 Nginx：

```bash
sudo certbot --nginx -d dataagent.example.com
```

选择将 HTTP 重定向到 HTTPS。

验证自动续期：

```bash
sudo certbot renew --dry-run
```

为什么使用宿主机终止 TLS：证书续期、80/443 监听和多个站点管理都由已有 Nginx 统一负责，容器内只使用本机 HTTP 通信。

如果系统中已经通过 apt 安装 Certbot，不要同时混用 apt 与 Snap 版本，按 Certbot 官方说明选择一种安装方式。

### 13.3 方案一：Nginx Basic Auth

安装密码工具：

```bash
sudo apt-get install -y apache2-utils
```

创建用户：

```bash
sudo htpasswd -c /etc/nginx/data-agent.htpasswd dataagent-admin
```

输入一个与数据库密码不同的强密码。

在 Certbot 修改后的 DataAgent `server {}` 中加入：

```nginx
auth_basic "DataAgent Administration";
auth_basic_user_file /etc/nginx/data-agent.htpasswd;
```

然后：

```bash
sudo nginx -t
sudo systemctl reload nginx
```

为什么放在 `server` 层：页面和 API 使用同一组认证，避免只保护页面却遗漏可直接调用的 API。

如果今后使用 Webroot 方式签发或续期证书，需要保证 `/.well-known/acme-challenge/` 不被认证阻挡；使用 Certbot Nginx 插件时也应通过 `sudo certbot renew --dry-run` 验证续期流程。

### 13.4 方案二：IP 白名单

只允许办公室或 VPN 出口 IP：

```nginx
allow 203.0.113.10/32;
allow 198.51.100.0/24;
deny all;
```

可以和 Basic Auth 同时使用。注意家庭宽带公网 IP 可能变化，修改前应保留一个可靠的管理入口。

---

## 第 14 章：上线验收

### 14.1 容器层检查

```bash
docker compose \
  --env-file .env.prod \
  -f docker-file/docker-compose.prod.yml \
  ps

docker stats --no-stream
```

检查是否存在反复重启、CPU 异常或内存接近上限。

### 14.2 Nginx 层检查

```bash
sudo nginx -t
curl -I https://dataagent.example.com/
```

启用 Basic Auth 后可以这样验证：

```bash
curl -I -u dataagent-admin https://dataagent.example.com/
```

预期：

- HTTP 自动跳转 HTTPS；
- HTTPS 证书域名正确且浏览器无安全警告；
- 配置 Basic Auth 后会先要求用户名和密码。

### 14.3 页面功能检查

按顺序进行：

1. 打开首页，确认前端资源没有 404。
2. 创建或检查模型配置。
3. 添加一个独立的分析数据源并执行连接测试。
4. 创建智能体并绑定数据源。
5. 发起简单 SQL 问答。
6. 检查回答过程是否持续更新，而不是长时间空白后一次性出现。
7. 上传头像或知识文件，重启后端后检查文件仍能访问。

### 14.4 检查浏览器网络请求

浏览器开发者工具的 Network 中确认：

- API 地址是 `https://dataagent.example.com/api/...`；
- 没有请求浏览器用户本机的 `localhost:8065`；
- 没有 CORS 错误；
- 流式请求没有被 Nginx 缓冲；
- `/uploads/...` 返回文件而不是 Nuxt HTML 页面。

### 14.5 Python 沙盒检查

仅 Python 模式执行：

```bash
docker info
docker ps --format '{{.Names}}' | grep '^dataagent-sandbox-' || true
```

提交一个包含 Python 步骤的请求。执行期间应短暂出现 `dataagent-sandbox-...` 容器，完成后不应长期残留。

如果只执行 SQL，不出现沙盒容器是正常的。

---

## 第 15 章：日常运维、更新和回滚

以下命令均在 `/opt/data-agent` 执行。

为了减少重复，可以先定义：

```bash
export DATA_AGENT_COMPOSE='docker compose --env-file .env.prod -f docker-file/docker-compose.prod.yml'
```

这是当前 Shell 会话变量，退出 SSH 后需要重新定义。

### 15.1 常用命令

```bash
# 状态
$DATA_AGENT_COMPOSE ps

# 最近日志
$DATA_AGENT_COMPOSE logs --tail=200 backend frontend mysql

# 跟踪后端日志
$DATA_AGENT_COMPOSE logs -f backend

# 重启单个服务
$DATA_AGENT_COMPOSE restart backend

# 停止全部服务但保留数据卷
$DATA_AGENT_COMPOSE down

# 重新启动
$DATA_AGENT_COMPOSE up -d
```

绝对不要在没有备份和明确确认的情况下执行：

```bash
docker compose down -v
```

`-v` 会删除 Compose 数据卷，包括 MySQL 数据和上传文件。

### 15.2 更新应用

更新前先备份数据库和上传文件，然后：

```bash
cd /opt/data-agent
git fetch --all --tags
git status
git pull --ff-only
```

将 `.env.prod` 中版本改为新的唯一值，例如：

```dotenv
DATA_AGENT_VERSION=2026.09.20-1
```

构建并部署：

```bash
$DATA_AGENT_COMPOSE config --quiet
$DATA_AGENT_COMPOSE build --pull
$DATA_AGENT_COMPOSE up -d
$DATA_AGENT_COMPOSE ps
$DATA_AGENT_COMPOSE logs --tail=200 backend frontend
```

Docker 官方建议生产环境使用独立 Compose 配置、设置重启策略，并在代码变化后重新构建和创建服务。

### 15.3 为什么使用版本标签

`DATA_AGENT_VERSION` 会生成：

```text
data-agent-backend:2026.09.20-1
data-agent-frontend:2026.09.20-1
```

如果新版本失败，而旧镜像仍在服务器上，可以把 `.env.prod` 的版本改回旧值并执行：

```bash
$DATA_AGENT_COMPOSE up -d --no-build backend frontend
```

注意：镜像回滚不能自动回滚数据库结构。如果新版本包含数据库迁移，应在升级前备份，并确认迁移的向前、向后兼容性。

### 15.4 只更新一个服务

只修改前端：

```bash
$DATA_AGENT_COMPOSE build frontend
$DATA_AGENT_COMPOSE up -d --no-deps frontend
```

只修改后端：

```bash
$DATA_AGENT_COMPOSE build backend
$DATA_AGENT_COMPOSE up -d --no-deps backend
```

`--no-deps` 避免无意义地重建 MySQL 或其他依赖服务。

---

## 第 16 章：备份与恢复

### 16.1 为什么必须同时备份两部分

1. MySQL 中保存智能体、会话、模型配置、API Key 和数据源连接配置。
2. `data-agent-backend-uploads` 保存本地上传的头像或知识文件。

只备份其中一个不能完整恢复系统。数据库备份本身可能包含敏感的模型和数据源凭据，必须加密保存并限制访问。

### 16.2 创建备份目录

```bash
mkdir -p /opt/data-agent/backups
chmod 700 /opt/data-agent/backups
```

### 16.3 备份 MySQL

```bash
cd /opt/data-agent

docker compose \
  --env-file .env.prod \
  -f docker-file/docker-compose.prod.yml \
  exec -T mysql sh -lc \
  'exec mysqldump -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" --single-transaction --routines --triggers "$MYSQL_DATABASE"' \
  > "backups/data_agent_$(date +%F_%H%M%S).sql"
```

检查文件不是空文件：

```bash
ls -lh backups/
head -n 5 backups/data_agent_*.sql
```

### 16.4 备份上传卷

```bash
docker run --rm \
  -v data-agent-backend-uploads:/source:ro \
  -v /opt/data-agent/backups:/backup \
  alpine:3.21 \
  tar -czf "/backup/uploads_$(date +%F_%H%M%S).tar.gz" -C /source .
```

该命令以只读方式挂载源卷，降低备份过程中误修改上传文件的风险。

### 16.5 恢复 MySQL

恢复会覆盖或叠加当前数据库内容。操作前必须：

1. 确认备份文件正确；
2. 再备份一次当前数据库；
3. 停止前后端写入；
4. 确认恢复目标数据库名称。

停止应用但保留 MySQL：

```bash
$DATA_AGENT_COMPOSE stop frontend backend
```

在空数据库或已确认的恢复目标中导入：

```bash
$DATA_AGENT_COMPOSE exec -T mysql sh -lc \
  'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' \
  < backups/你确认过的备份文件.sql
```

重新启动：

```bash
$DATA_AGENT_COMPOSE start backend frontend
```

### 16.6 恢复上传卷

先停止后端并额外保存当前卷，再执行恢复。不要直接把一个未经确认的压缩包展开到生产卷。

```bash
$DATA_AGENT_COMPOSE stop backend

docker run --rm \
  -v data-agent-backend-uploads:/target \
  -v /opt/data-agent/backups:/backup:ro \
  alpine:3.21 \
  tar -xzf /backup/你确认过的上传备份.tar.gz -C /target

$DATA_AGENT_COMPOSE start backend
```

如果目标卷中已经存在文件，解压可能覆盖同名文件。正式恢复前应在测试卷演练。

---

## 第 17 章：常见故障排查

### 17.1 Nginx 返回 502 Bad Gateway

检查：

```bash
curl -I http://127.0.0.1:3000/
curl -fsS http://127.0.0.1:8065/v3/api-docs | head
$DATA_AGENT_COMPOSE ps
$DATA_AGENT_COMPOSE logs --tail=200 frontend backend
sudo tail -n 100 /var/log/nginx/error.log
```

常见原因：

- 容器未启动或正在反复重启；
- Nginx 代理端口写错；
- 后端仍在等待 MySQL；
- 服务器内存不足导致进程退出。

### 17.2 前端打开空白或 `/_nuxt/` 资源 404

检查：

```bash
$DATA_AGENT_COMPOSE logs --tail=200 frontend
curl -I http://127.0.0.1:3000/_nuxt/
```

浏览器检查具体失败的资源地址。常见原因：

- 使用了旧 `Dockerfile-frontend`；
- 没有复制完整 `.output`；
- 应用部署在子路径但没有配置 `NUXT_APP_BASE_URL`；
- Nginx 对 `/_nuxt/` 做了错误重写。

本文默认部署在域名根路径 `/`，不需要额外配置 Base URL。

### 17.3 API 返回前端 HTML

如果 `/api/...` 返回 `<!DOCTYPE html>`，说明请求被转发到了前端。

检查 Nginx 中后端正则 Location 是否存在：

```nginx
location ~ ^/(api|nl2sql|uploads|sse)(/|$) {
```

然后运行：

```bash
sudo nginx -t
sudo systemctl reload nginx
```

### 17.4 流式问答长时间没有进度

检查 API Location：

```nginx
proxy_http_version 1.1;
proxy_buffering off;
proxy_cache off;
proxy_read_timeout 24h;
gzip off;
```

如果前面还有 CDN、WAF 或其他网关，也需要检查它们是否缓存 SSE 或设置了较短超时。

### 17.5 后端无法连接 MySQL

查看：

```bash
$DATA_AGENT_COMPOSE logs --tail=200 mysql backend
$DATA_AGENT_COMPOSE exec backend getent hosts mysql
```

确认 JDBC 地址使用：

```text
jdbc:mysql://mysql:3306/数据库名
```

而不是 `localhost`。

如果修改过已有 MySQL 数据卷的 `.env.prod` 密码，数据库内部密码不会自动变化，参见第 9.4 节。

### 17.6 MySQL 容器一直 unhealthy

```bash
$DATA_AGENT_COMPOSE logs --tail=200 mysql
docker inspect data-agent-mysql --format '{{json .State.Health}}'
df -h
```

检查磁盘空间、密码、数据目录权限和 MySQL 初始化错误。

### 17.7 后端启动时提示表不存在

检查：

```bash
$DATA_AGENT_COMPOSE exec mysql mysql -u dataagent -p data_agent
```

在 MySQL 中执行：

```sql
SHOW TABLES;
```

如果为空，重新执行第 10.1 节的 `schema.sql` 导入。不要通过删除数据卷解决表缺失问题。

### 17.8 Python 沙盒无法连接 Docker

检查：

```bash
docker info
docker inspect data-agent-backend \
  --format '{{range .Mounts}}{{println .Source "->" .Destination}}{{end}}'
$DATA_AGENT_COMPOSE logs --tail=300 backend
```

应看到：

```text
/var/run/docker.sock -> /var/run/docker.sock
```

如果服务器使用 Rootless Docker，Socket 路径可能不同，不能直接套用默认路径。需要根据 `docker context inspect` 的 Endpoint 调整，并评估权限模型。

### 17.9 Python 安装依赖失败

检查服务器是否能访问：

- 沙盒镜像仓库；
- `DATAAGENT_PYPI_INDEX_URL`；
- DNS 和 HTTPS 出站网络。

国内网络环境可以把 PyPI 地址指向可信的企业代理。不要把未知公共镜像源直接用于生产依赖供应链。

### 17.10 重启后知识检索结果消失

`simple` 向量库存在内存中。这不是 Docker 卷故障。正式环境默认使用 Chroma；如需切换到 Milvus，可启用项目的 `application-milvus.yml` Profile。

### 17.11 上传文件重启后丢失

检查挂载：

```bash
docker inspect data-agent-backend \
  --format '{{range .Mounts}}{{println .Name .Destination}}{{end}}'
```

应包含：

```text
data-agent-backend-uploads /app/uploads
```

### 17.12 HTTPS 证书申请失败

检查：

```bash
dig +short dataagent.example.com
curl -I http://dataagent.example.com/
sudo ss -lntp | grep -E ':80|:443'
sudo nginx -t
```

Certbot 使用 HTTP 校验时，公网必须能访问 80 端口。云安全组和服务器防火墙都需要允许。

---

## 第 18 章：生产安全与扩展建议

### 18.1 必做安全项

- 删除源码中的真实数据库地址和密码，并轮换已经暴露的密码。
- `.env.prod` 权限设为 `600`，不提交 Git，不放入 Docker 镜像。
- 公网只开放 80/443；SSH 只允许可信 IP。
- 使用 HTTPS。
- 使用 Basic Auth、IP 白名单、VPN 或完整身份系统保护管理端。
- MySQL 不映射宿主机端口。
- 前后端端口只绑定 `127.0.0.1`。
- 定期备份 MySQL 与上传卷，并进行恢复演练。
- 定期更新基础镜像和系统安全补丁。

### 18.2 数据源权限最小化

Agent 分析数据库使用单独账号，并尽量只授予：

```sql
SELECT
```

不要直接填写生产数据库 root、DBA 或拥有写权限的账号。即使系统目标是生成查询 SQL，模型输出、配置错误或后续功能变化都可能扩大风险。

### 18.3 模型和数据源凭据保护

模型 API Key、代理密码和数据源密码可能保存在管理数据库中。因此：

- 管理数据库备份属于敏感文件；
- 限制服务器、备份目录和对象存储访问权限；
- 不把数据库备份发到公共聊天或公开网盘；
- 定期轮换模型 Key；
- 日志中出现完整凭据时应立即清理并轮换。

### 18.4 Docker Socket 加固

保留 Docker Socket 时：

- 后台管理端绝不能无认证暴露；
- 不运行来源不明的后端镜像；
- 沙盒基础镜像固定到 digest；
- 限制沙盒出站网络；
- 优先使用可信的内部 PyPI 代理；
- 定期检查残留 `dataagent-sandbox-` 容器。

### 18.5 向量库持久化

正式使用知识库时，选择一种受支持的持久化向量库。需要同时考虑：

- 数据卷或外部托管服务；
- 维度和索引配置；
- 备份与恢复；
- 后端 Profile 和连接凭据；
- 从 `simple` 切换后的重新嵌入流程。

### 18.6 监控建议

最少监控：

```bash
docker stats --no-stream
docker system df
df -h
free -h
sudo journalctl -u docker --since today
sudo tail -n 100 /var/log/nginx/error.log
```

重点告警项：

- 磁盘超过 80%；
- MySQL unhealthy；
- 后端反复重启；
- Nginx 5xx 激增；
- 沙盒容器残留；
- TLS 证书即将过期；
- 备份任务失败。

### 18.7 不要随意使用的清理命令

下面命令可能删除仍需要的镜像、缓存或数据，生产环境执行前必须确认范围：

```bash
docker system prune -a
docker volume prune
docker compose down -v
```

尤其是 `down -v`，会直接删除本指南创建的数据库和上传卷。

---

## 附录 A：完整部署检查清单

### A.1 上线前

- [ ] 云服务器 CPU、内存、磁盘满足需求。
- [ ] 域名 A 记录指向服务器公网 IP。
- [ ] 安全组只开放 22、80、443。
- [ ] 22 端口限制为可信来源。
- [ ] Docker Engine、Buildx、Compose 插件可用。
- [ ] 宿主机 Nginx 正常运行。
- [ ] 已创建 `.dockerignore`。
- [ ] 已创建 `Dockerfile-frontend-prod`。
- [ ] 已创建 `docker-compose.prod.yml`。
- [ ] `.env.prod` 已加入 `.gitignore`。
- [ ] `.env.prod` 权限为 `600`。
- [ ] 源码中没有真实密码和 API Key。
- [ ] 已轮换曾经暴露的数据库密码。
- [ ] 已决定是否需要 Python Docker Socket。
- [ ] 已决定是否需要持久化向量数据库。

### A.2 启动阶段

- [ ] `docker compose config --quiet` 通过。
- [ ] MySQL 为 healthy。
- [ ] `schema.sql` 已导入。
- [ ] 前后端镜像构建成功。
- [ ] 前端本地 3000 可访问。
- [ ] 后端本地 8065 可访问。
- [ ] 3000、8065、3306 未直接暴露公网。
- [ ] Nginx 配置测试通过。
- [ ] API 和 SSE 路径转发到后端。
- [ ] 页面和 `/_nuxt` 资源转发到前端。

### A.3 公网上线

- [ ] 已启用 Basic Auth、IP 白名单、VPN 或应用级身份认证。
- [ ] HTTPS 证书签发成功。
- [ ] HTTP 自动跳转 HTTPS。
- [ ] Certbot 自动续期测试通过。
- [ ] 浏览器无 CORS 错误。
- [ ] 流式问答不会被缓冲。
- [ ] 分析数据源使用只读账号。
- [ ] 数据库和上传文件完成首次备份。
- [ ] 已测试备份文件可读取。
- [ ] 已记录当前 `DATA_AGENT_VERSION` 和 Git 提交号。

---

## 附录 B：官方参考资料

- [Docker Engine 在 Ubuntu 上的官方安装说明](https://docs.docker.com/engine/install/ubuntu/)
- [Docker Compose Linux 插件安装说明](https://docs.docker.com/compose/install/linux/)
- [Docker Compose 生产环境建议](https://docs.docker.com/compose/how-tos/production/)
- [Nuxt 4 部署说明](https://nuxt.com/docs/4.x/getting-started/deployment)
- [Nginx HTTP Proxy 模块](https://nginx.org/en/docs/http/ngx_http_proxy_module.html)
- [Certbot 与 Nginx 官方安装指引](https://certbot.eff.org/instructions?ws=nginx&os=snap)
- 项目内文档：`docs/QUICK_START.md`
- 项目内文档：`docs/ADVANCED_FEATURES.md`
- 项目配置：`data-agent-management/src/main/resources/application.yml`
- 项目前端配置：`data-agent-frontend-nuxt/nuxt.config.ts`
