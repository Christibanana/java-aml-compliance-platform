# Phase 3A 客户、账户、模拟交易验证记录

日期：2026-10-14（Asia/Shanghai）
范围：客户管理、账户管理、模拟交易导入及客户→账户→交易关联流程。

## 构建与自动化测试

```powershell
$env:JAVA_HOME=(Resolve-Path '..\.tools\jdk21\jdk-21.0.12.1+1').Path
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -B test
mvn -B clean package
npm run build
docker compose --profile app build backend frontend
```

结果：后端 14 tests、0 failures、0 errors、0 skipped；可执行 JAR 生成成功。前端 1706 modules transformed，生产构建通过（保留大 chunk 警告）。后端容器内再次执行 14/14 测试，前后端镜像均构建成功。

## PostgreSQL 与 Flyway

```powershell
docker compose exec -T postgres pg_isready -U aml -d aml
java -jar backend/target/aml-platform-0.1.0-SNAPSHOT.jar
docker compose exec -T postgres psql -U aml -d aml -Atc "select installed_rank || '|' || version || '|' || description || '|' || success from flyway_schema_history order by installed_rank;"
```

结果：PostgreSQL 16.10 healthy；Flyway 验证 2 个迁移并将 schema 从 v1 升至 v2；Hibernate schema validation 通过。迁移记录：

```text
1|1|initial schema|true
2|2|customer account transaction modules|true
```

## 真实 HTTP 关联闭环

应用使用 18080（宿主 8080 已被其他进程占用）连接真实 PostgreSQL，依次执行：

1. 登录本地演示管理员。
2. 创建 `CUS-MODULE-001`，小写 `cn` 被标准化为 `CN`。
3. 为该客户创建 `ACC-MODULE-001/CNY/OWNER/100%`，响应包含客户关系。
4. 上传 `integrations/amlsim/customer-account-transaction-demo.csv`。
5. 按 `customerId` 查询账户、按 `accountId` 查询交易。
6. 再次上传同一 CSV 验证幂等。

结果：首次导入 `total=1, imported=1, duplicates=0, rejected=0`；批次表为 `COMPLETED|1|1|0`。关联查询均为 1 条，交易响应的账户为 `ACC-MODULE-001`。重复导入为 `imported=0, duplicates=1, rejected=0`。

非法客户请求（空名称、非法编号、三位国家代码）返回 HTTP 400、`REQUEST_VALIDATION_FAILED`，并包含 `customerNo/countryCode/fullName` 三个字段错误。

验证期间尝试使用 PowerShell 7 的 `Invoke-WebRequest -SkipHttpErrorCheck`，当前 PowerShell 5 不支持该参数；随后通过 `Invoke-RestMethod` 异常响应流完成相同验证，已记录在 `docs/ISSUES.md`。

## Compose 验证与收尾

```powershell
$env:AML_BACKEND_HOST_PORT='18080'
$env:AML_FRONTEND_HOST_PORT='15173'
docker compose --profile app up -d --no-build
Invoke-RestMethod http://127.0.0.1:18080/actuator/health
Invoke-RestMethod http://127.0.0.1:15173/actuator/health
Invoke-WebRequest http://127.0.0.1:15173/ -UseBasicParsing
docker compose --profile app stop frontend backend
```

结果：容器后端直连和 Nginx 代理健康均为 `UP`，前端首页 HTTP 200。验证结束后 backend/frontend 已停止；PostgreSQL 保持 healthy 供复查。
