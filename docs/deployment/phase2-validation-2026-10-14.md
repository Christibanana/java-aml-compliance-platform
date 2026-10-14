# Phase 2 初始化与验证记录（2026-10-14）

工作目录：`C:\Users\86152\Desktop\Cpp\java-aml-platform`
时区：Asia/Shanghai
范围：项目目录、Spring Boot、Vue 3、PostgreSQL、Flyway、Docker Compose 初始化及验证。未开展高级 AML 功能。

## 1. 项目规则与工作区

```powershell
Get-Content AGENTS.md -Encoding utf8
Get-Content docs/PROJECT_PLAN.md -Encoding utf8
Get-Content docs/PROGRESS.md -Encoding utf8
git status --short
rg --files
```

结果：三个指定文件均成功读取。仓库是尚无初始提交的工作树，现有项目文件均显示为 untracked；工作目录未重复嵌套创建。确认 Flyway 是唯一 schema 变更机制，并按要求更新测试、进度和问题记录。

## 2. 环境检查

```powershell
git --version
java -version
javac -version
mvn -version
node --version
npm --version
docker --version
docker compose version
docker info
```

结果：Git 2.53.0.windows.1；系统 Java 为 Oracle JRE 1.8.0_391、没有 javac；Maven 3.9.16 默认运行于 Java 8；Node v24.15.0；npm 11.12.1；Docker Client/Server 29.8.2；Compose v5.6.0；Docker Engine 可连接。

首次便携 JDK 探测误写为父目录，命令返回路径不存在；纠正为项目目录后通过：

```powershell
Test-Path ..\.tools\jdk21\jdk-21.0.12.1+1\bin\java.exe
Test-Path .\.tools\jdk21\jdk-21.0.12.1+1\bin\java.exe
& .\.tools\jdk21\jdk-21.0.12.1+1\bin\java.exe -version
& .\.tools\jdk21\jdk-21.0.12.1+1\bin\javac.exe -version
```

结果：第一条为 `False`；纠正后为 `True`，Java 与 javac 均为 Microsoft OpenJDK 21.0.12.1。

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen
```

结果：PID 9836 已占用 8080。未终止该进程；后续验证使用 18080。

## 3. Compose 与 PostgreSQL/Flyway

```powershell
docker compose config --quiet
docker image inspect postgres:16.10-alpine
docker compose up -d postgres
docker compose ps
docker compose exec -T postgres pg_isready -U aml -d aml
docker compose exec -T postgres psql -U aml -d aml -Atc "select version();"
```

结果：Compose 配置通过；镜像起初不存在，`up` 成功拉取 `postgres:16.10-alpine` 并启动。容器状态为 healthy，`pg_isready` 返回 accepting connections，数据库版本为 PostgreSQL 16.10。

首次以真实 PostgreSQL 启动应用时，Flyway 已从空 schema 成功执行 V1，但 Hibernate 随后发现 `aml_account.currency` 的数据库类型 `bpchar/CHAR` 与实体预期 `VARCHAR` 不一致并终止启动。仅增加 `columnDefinition="char(3)"` 后复测仍失败。最终为三个定长字符字段增加 Hibernate JDBC CHAR 类型标注：

- `Account.currency`：`@JdbcTypeCode(SqlTypes.CHAR)` / `char(3)`
- `Customer.countryCode`：`@JdbcTypeCode(SqlTypes.CHAR)` / `char(2)`
- `AmlTransaction.currency`：`@JdbcTypeCode(SqlTypes.CHAR)` / `char(3)`

```powershell
docker compose exec -T postgres psql -U aml -d aml -Atc "select installed_rank || '|' || version || '|' || description || '|' || success from flyway_schema_history order by installed_rank;"
docker compose exec -T postgres psql -U aml -d aml -Atc "select count(*) from pg_tables where schemaname='public';"
```

结果：`1|1|initial schema|true`；public schema 为 32 张表。

## 4. 本机构建

后端构建时只在当前 PowerShell 进程内设置 Java 21：

```powershell
$env:JAVA_HOME=(Resolve-Path '.\.tools\jdk21\jdk-21.0.12.1+1').Path
$env:Path="$env:JAVA_HOME\bin;$env:Path"
Set-Location backend
mvn -B clean package
```

结果：`BUILD SUCCESS`；9 tests，0 failures，0 errors，0 skipped；生成 `backend/target/aml-platform-0.1.0-SNAPSHOT.jar`。修复 CHAR 映射后再次执行同一完整命令，仍为 9/9 通过，总耗时约 37.8 秒。

```powershell
Set-Location frontend
npm ci
npm run build
```

结果：均通过；Vite 转换 1705 个模块并生成 `dist`。主 JavaScript chunk 约 1,088 KB，Vite 给出拆包建议但未导致构建失败。

清理 TypeScript/Vite 生成的工作区中间文件，并防止其再次进入版本控制：

```powershell
Remove-Item frontend/tsconfig.app.tsbuildinfo,frontend/tsconfig.node.tsbuildinfo,frontend/vite.config.js,frontend/vite.config.d.ts
```

结果：四个生成文件已删除；`.gitignore` 增加 `*.tsbuildinfo`。

## 5. 本地应用连接真实 PostgreSQL

```powershell
$env:AML_DB_URL='jdbc:postgresql://localhost:5432/aml'
$env:AML_DB_USER='aml'
$env:AML_DB_PASSWORD='aml_local_only'
& .\.tools\jdk21\jdk-21.0.12.1+1\bin\java.exe -jar backend\target\aml-platform-0.1.0-SNAPSHOT.jar
```

结果：修复 CHAR 映射后，Flyway 与 Hibernate schema validation 通过；默认 8080 启动仅因已有进程占用而失败。

```powershell
$env:AML_BACKEND_PORT='18080'
& .\.tools\jdk21\jdk-21.0.12.1+1\bin\java.exe -jar backend\target\aml-platform-0.1.0-SNAPSHOT.jar
Invoke-RestMethod http://127.0.0.1:18080/actuator/health
Invoke-WebRequest http://127.0.0.1:18080/v3/api-docs -UseBasicParsing
```

结果：应用启动成功；健康响应 `{"status":"UP"}`；OpenAPI HTTP 200、`application/json`、14092 bytes。验证完成后以 Ctrl+C 正常停止应用（Shell 记录退出码 1，源于手工中断）。

## 6. Docker 镜像构建

```powershell
docker compose --profile app build backend frontend
```

首次结果：Docker Hub auth token 的 IPv6 网络连接失败。随后逐个拉取基础镜像：

```powershell
docker pull node:24-alpine
docker pull maven:3.9.11-eclipse-temurin-21
docker pull eclipse-temurin:21-jre
docker pull nginx:1.29-alpine
```

结果：四个基础镜像全部拉取成功。

第二次组合构建：前端镜像成功；后端在 `mvn dependency:go-offline` 下载非必要的大型插件依赖图时连接中断。处理如下：

- 后端 Dockerfile 改为 BuildKit Maven 缓存挂载后直接执行 `mvn -B -U package`。
- 前端 Dockerfile 使用可复现的 `npm ci`。
- 新增两端 `.dockerignore`；前端构建上下文从约 190 MB 降至 1.52 KB，后端约 5.45 KB。

```powershell
docker compose --profile app build backend
```

首次结果：容器内 9/9 测试已通过，但 Maven jar 插件依赖 `zstd-jni` 下载提前结束（期望 6,776,400 bytes，收到 2,289,664 bytes），构建失败；缓存保留。

```powershell
docker compose --profile app build backend
```

重试结果：`zstd-jni` 完整下载，容器内再次 9/9 测试通过，`BUILD SUCCESS`，镜像 `java-aml-platform-backend:latest` 生成成功。最终镜像：backend 613 MB、frontend 94.5 MB、postgres 394 MB（Docker Desktop 显示口径）。

## 7. Compose 整栈健康检查

为保留默认端口兼容性并避开占用，将宿主端口改为环境变量可覆盖。本次执行：

```powershell
$env:AML_BACKEND_HOST_PORT='18080'
$env:AML_FRONTEND_HOST_PORT='15173'
$env:AML_POSTGRES_HOST_PORT='5432'
docker compose --profile app config --quiet
docker compose --profile app up -d --no-build
docker compose --profile app ps
```

结果：PostgreSQL healthy，backend 与 frontend 均为 Up；端口分别为 5432、18080、15173。

```powershell
Invoke-RestMethod http://127.0.0.1:18080/actuator/health
Invoke-RestMethod http://127.0.0.1:15173/actuator/health
Invoke-WebRequest http://127.0.0.1:15173/ -UseBasicParsing
Invoke-WebRequest http://127.0.0.1:18080/v3/api-docs -UseBasicParsing
docker compose exec -T postgres psql -U aml -d aml -Atc "select installed_rank || '|' || version || '|' || description || '|' || success from flyway_schema_history order by installed_rank;"
docker compose exec -T postgres psql -U aml -d aml -Atc "select count(*) from pg_tables where schemaname='public';"
docker compose --profile app logs --tail 80 backend
```

结果：后端直连健康为 `UP`；经前端 Nginx 代理健康也为 `UP`；前端首页 HTTP 200；后端 OpenAPI HTTP 200、JSON、14092 bytes；Flyway 为 `1|1|initial schema|true`；32 张表。日志确认 PostgreSQL 16.10、Flyway migration 已验证且 schema version 1、Hibernate 初始化成功、Tomcat 8080 启动成功。

曾用前端地址请求 `/v3/api-docs`，由于 Nginx 只代理 `/api/` 与 `/actuator/`，SPA fallback 返回 HTML 200；这不是后端 OpenAPI 检查。随后按上面的后端直连地址复测，得到正确 JSON 200。

```powershell
docker compose --profile app stop frontend backend
docker compose --profile app ps
docker compose exec -T postgres pg_isready -U aml -d aml
```

结果：应用容器已停止；PostgreSQL 容器仍为 healthy 且 accepting connections，保留供复查。

## 8. 验收

```powershell
docker compose config --quiet
$env:AML_BACKEND_HOST_PORT='18080'
$env:AML_FRONTEND_HOST_PORT='15173'
docker compose --profile app config --quiet
git diff --check
rg --files frontend -g '*.tsbuildinfo' -g 'vite.config.js' -g 'vite.config.d.ts'
docker compose ps
docker images --format '{{.Repository}}:{{.Tag}}|{{.ID}}|{{.Size}}'
git status --short
```

结果：默认与 app profile 的 Compose 配置均通过；`git diff --check` 无输出；未发现不应保留的 TypeScript/Vite 生成文件；仅 PostgreSQL 容器保持 healthy；三个目标镜像均存在。仓库尚无初始提交，所以 `git status --short` 将整个项目显示为 untracked，这是既有仓库状态，本次未擅自提交。

Phase 2 基础项目验证通过。高级 AML 功能未在本次工作中实现或修改。
