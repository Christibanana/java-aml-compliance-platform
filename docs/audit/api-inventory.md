# API 清单

2026-10-15 历史 OpenAPI 实测为 48 个路径、53 个操作；V7 新增 KYC 三条操作，本轮未重新统计当前总数。基础路径 `/api`；登录外的业务接口需要 Bearer JWT，并由后端 `@PreAuthorize` 校验。

| 领域 | 方法与路径 | 权限 | 状态/说明 |
|---|---|---|---|
| 登录 | `POST /auth/login` | 匿名 | 当前统一 401、失败计数、锁定与审计已由 PostgreSQL 集成测试验证 |
| 客户 | `GET,POST /customers`；`GET,PUT /customers/{id}` | READ/WRITE | 已实现并验证 |
| 客户 KYC | `GET /customers/{id}/kyc`；`POST /customers/{id}/kyc/submit`、`/review` | CUSTOMER_READ/WRITE、KYC_REVIEW | 2026-10-30 PostgreSQL 与浏览器验证 |
| 账户 | `GET,POST /accounts`；`GET /accounts/{id}`；`PUT /accounts/{id}/status` | CUSTOMER_READ/ACCOUNT_WRITE | 已实现并验证 |
| 交易 | `GET /transactions` | CUSTOMER_READ | 已实现并验证 |
| 本地导入 | `POST /transactions/import`；`GET /transactions/template` | TRANSACTION_IMPORT | 已实现并验证 |
| AMLSim | `POST /transactions/import/amlsim`；`GET /transactions/template/amlsim` | TRANSACTION_IMPORT | 已实现；本次运行验证重复识别 |
| Fineract | `POST /transactions/import/fineract/{savingsAccountId}` | TRANSACTION_IMPORT | 适配器完成，真实服务未验证 |
| 集成状态 | `GET /transactions/integrations` | TRANSACTION_IMPORT | 本次返回 Local/AMLSim 可用、Fineract 不可用 |
| 规则 | `GET /rules`；`GET,POST /rules/{code}/versions`；`PUT /rules/{code}/status` | CUSTOMER_READ/RULE_EXECUTE | 已实现并验证 |
| 规则执行 | `POST /rules/execute`；`GET /rules/executions`；`GET /rules/hits`；`GET /rules/compatibility` | CUSTOMER_READ/RULE_EXECUTE | R001–R003 已实现；R004–R008 拒绝启用 |
| 预警 | `GET /alerts`；`GET /alerts/{id}`；`POST /alerts/{id}/assign`；`POST /alerts/{id}/transition` | ALERT_REVIEW | 已实现并验证 |
| 升级案件 | `POST /alerts/{id}/escalate` | CASE_INVESTIGATE | 已实现并验证 |
| 案件 | `GET /cases`、`/cases/{id}`、`/cases/investigators` | CASE_INVESTIGATE | 已实现并验证 |
| 调查 | `POST /cases/{id}/assign`、`notes`、`transition` | CASE_INVESTIGATE | 已实现并验证 |
| 证据 | `POST /cases/{id}/evidence`；`GET/DELETE /cases/{caseId}/evidence/{evidenceId}[/download]` | CASE_INVESTIGATE | 上传、下载、逻辑移除已实现 |
| 筛查 | `GET,POST /screenings`；`GET /screenings/{id}`、`providers` | SCREENING_EXECUTE | Mock 已运行验证；Yente 未实连 |
| 复核 | `POST /screenings/{requestId}/matches/{matchId}/review` | SCREENING_EXECUTE | 已实现并验证 |
| 客户评分 | `POST .../customers/{id}/calculate`；`GET .../customers/{id}[/history]` | RULE_EXECUTE/REPORT_READ | 已实现并验证 |
| 交易评分 | `POST .../transactions/{id}/calculate`；`GET .../transactions/{id}[/history]` | RULE_EXECUTE/REPORT_READ | 已实现并验证 |
| 仪表盘 | `GET /dashboard?from&to` | REPORT_READ | 数据库实时统计，API 已验证 |
| CSV 报表 | `GET /reports/{CUSTOMER_RISK|ALERTS|RULE_HITS|CASES}/csv` | REPORT_READ | 已实现并验证 |
| 审计 | `GET /audit-logs`；`GET /audit-logs/export` | AUDIT_READ | 查询和导出已实现并验证 |

## 非 `/api` 端点

- `GET /actuator/health`：匿名，审查时 `UP`。
- `GET /v3/api-docs`、`/swagger-ui/**`：匿名，OpenAPI 实测 HTTP 200、39136 bytes。

## 响应与状态问题

- 参数/业务异常统一由 `ApiExceptionHandler` 输出 JSON；错误登录当前返回 400，而不是通常期望的 401。
- 客户、账户和交易直接序列化 Spring `PageImpl`，测试日志明确提示其 JSON 结构未来不保证稳定。
- 外部服务禁用、超时、非 2xx 或坏响应会明确失败，不会自动伪造成功；实际 Fineract/Yente 服务未连接。
- CSV 已包含 UTF-8 BOM、引号转义和公式注入中和。
