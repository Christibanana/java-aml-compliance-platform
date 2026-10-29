# API 接口开发手册

基础路径 `/api`；除登录和健康/OpenAPI 外均需 `Authorization: Bearer <token>`。

运行时契约以 `GET /v3/api-docs` 为准。登录示例：

```http
POST /api/auth/login
Content-Type: application/json

{"username":"admin","password":"ChangeMe123!"}
```

成功响应包含 `token/tokenType/displayName/roles/permissions`。连续失败 5 次锁定 15 分钟，到期自动放行、成功登录重置计数；认证失败统一返回 401。旧版失败事务回滚是历史缺陷，2026-10-30 独立事务修复已由 `AuthPostgresIntegrationTest` 4/4 验证。生产部署不得使用演示口令。

| 方法 | 路径 | 权限 | 状态 |
|---|---|---|---|
| POST | `/auth/login` | 匿名 | 已实现 |
| GET/POST | `/customers` | CUSTOMER_READ/WRITE | 已实现：分页、关键字查询、新增 |
| GET/PUT | `/customers/{id}` | CUSTOMER_READ/WRITE | 已实现：详情、编辑与停用 |
| GET | `/customers/{id}/kyc` | CUSTOMER_READ | 当前 KYC 与历史；到期状态按有效期派生 |
| POST | `/customers/{id}/kyc/submit` | CUSTOMER_WRITE | 提交不超过 500 字的合成/脱敏 `documentSummary`；返回 PENDING |
| POST | `/customers/{id}/kyc/review` | KYC_REVIEW | `decision` 为 VERIFIED/REJECTED，`reason` 必填；VERIFIED 时 `expiresAt` 必须为未来时间 |
| GET/POST | `/accounts` | CUSTOMER_READ/ACCOUNT_WRITE | 已实现：分页、按客户筛选、新增及客户关系 |
| GET/PUT | `/accounts/{id}`、`/accounts/{id}/status` | CUSTOMER_READ/ACCOUNT_WRITE | 已实现：详情、启用/冻结/关闭 |
| GET | `/transactions` | CUSTOMER_READ | 已实现：按客户或账户筛选 |
| POST | `/transactions/import` | TRANSACTION_IMPORT | 已实现：批次、校验、去重、逐行结果 |
| GET | `/transactions/template` | TRANSACTION_IMPORT | 已实现：下载 UTF-8 CSV 模板 |
| GET/POST | `/rules`, `/rules/execute` | CUSTOMER_READ/RULE_EXECUTE | 已实现 R001 大额、R002 高频、R003 累计；执行支持 `ruleCodes/asOf` |
| GET/POST | `/rules/{code}/versions` | CUSTOMER_READ/RULE_EXECUTE | 版本历史与受控参数新版本；禁止任意 DRL |
| PUT | `/rules/{code}/status` | RULE_EXECUTE | 启停已实现规则；PLANNED 规则不可启用 |
| GET | `/rules/compatibility`, `/rules/executions`, `/rules/hits` | CUSTOMER_READ/RULE_EXECUTE | 运行版本、执行与命中查询 |
| GET/POST | `/alerts/**` | ALERT_REVIEW/CASE_INVESTIGATE | 分页筛选、详情、分派、状态审核与升级案件 |
| GET | `/cases`、`/cases/{id}`、`/cases/investigators` | CASE_INVESTIGATE | 分页筛选、聚合详情、可用调查员 |
| POST | `/cases/{id}/assign`、`/cases/{id}/notes`、`/cases/{id}/transition` | CASE_INVESTIGATE | 分派、调查笔记、受控状态流转和关闭结论 |
| POST | `/cases/{id}/evidence` | CASE_INVESTIGATE | multipart 证据上传；白名单类型、10MB 上限、SHA-256 |
| GET/DELETE | `/cases/{caseId}/evidence/{evidenceId}/download` | CASE_INVESTIGATE | 下载并审计；DELETE 为需原因的逻辑移除，不物理删除 |
| GET/POST | `/screenings` | SCREENING_EXECUTE | 筛查历史与新筛查；每次请求明确返回 `REAL` 或 `SIMULATED` |
| GET | `/screenings/providers`、`/screenings/{id}` | SCREENING_EXECUTE | Provider 可用性和筛查详情 |
| POST | `/screenings/{requestId}/matches/{matchId}/review` | SCREENING_EXECUTE | 人工复核：`NEEDS_MORE_INFO`、`CONFIRMED_MATCH`、`FALSE_POSITIVE` |
| POST | `/risk-scores/customers/{id}/calculate` | RULE_EXECUTE | 计算客户风险并追加版本化历史 |
| GET | `/risk-scores/customers/{id}`、`/risk-scores/customers/{id}/history` | REPORT_READ | 客户当前评分与历史 |
| POST | `/risk-scores/transactions/{id}/calculate` | RULE_EXECUTE | 计算交易风险并追加版本化历史 |
| GET | `/risk-scores/transactions/{id}`、`/risk-scores/transactions/{id}/history` | REPORT_READ | 交易当前评分与历史 |
| GET | `/dashboard?from&to` | REPORT_READ | 摘要、客户风险分布、预警趋势、规则命中、案件状态/优先级；默认最近 30 个上海自然日 |
| GET | `/reports/{CUSTOMER_RISK|ALERTS|RULE_HITS|CASES}/csv?from&to` | REPORT_READ | UTF-8 BOM CSV；响应含 `X-Report-Rows`，导出写审计 |
| GET | `/audit-logs?actor&action&resourceType&result&from&to&page&size` | AUDIT_READ | 条件查询与分页，size 最大 100 |
| GET | `/audit-logs/export?...` | AUDIT_READ | 导出当前筛选范围，最多 10000 行，导出写审计 |

## 通用约定

- JSON 时间均为 ISO-8601；数据库按 UTC 保存。仪表盘 `from/to` 是 Asia/Shanghai 自然日，最长 366 天。
- JPA 分页接口当前返回 Spring `Page` 结构，核心字段为 `content/totalElements/totalPages/number/size`；预警、案件和审计使用同义的显式分页 DTO。
- 常见状态：200 成功，400 参数或状态机错误，401 未认证/令牌无效，403 权限不足，404 资源不存在，409 并发或唯一键冲突，502 外部适配器失败。
- 统一错误 JSON 包含 `timestamp/status/code/message/path`，字段校验错误另含 `fields`。
- 所有写接口会写 `sys_audit_log`；规则、预警、案件还写各自执行/历史表。
- CSV 为 UTF-8 BOM、CRLF、字段双引号转义；来自业务数据且以公式危险字符开头的值会被中和。

## 核心请求示例

```jsonc
// POST /api/customers
{"customerNo":"DEMO-C001","customerType":"INDIVIDUAL","fullName":"Synthetic Customer","idType":"DEMO","idNumberMasked":"MASKED","countryCode":"CN","riskLevel":"LOW"}

// POST /api/accounts
{"accountNo":"DEMO-A001","currency":"CNY","customerId":"<uuid>","relationType":"OWNER","ownershipPercent":100}

// POST /api/rules/execute；不传 body 表示执行全部已启用且已实现规则
{"ruleCodes":["AML-R001"],"asOf":"2026-10-14T14:00:00Z"}

// POST /api/alerts/{id}/transition
{"status":"IN_REVIEW","comment":"开始人工复核"}

// POST /api/cases/{id}/transition
{"status":"INVESTIGATING","comment":"开始调查"}
```

CSV 表头固定为：`externalId,accountNo,counterpartyAccount,direction,amount,currency,transactionTime,transactionType,description`。时间需 ISO-8601 UTC，币种三位大写，方向为 `IN`/`OUT`。

## 客户、账户、交易关联流程

1. `POST /api/customers` 创建客户；客户编号只允许字母、数字、下划线和连字符。
2. `POST /api/accounts` 以 `customerId`、`relationType` 和 `ownershipPercent` 建立账户及客户关系。停用客户不能开户。
3. `POST /api/transactions/import` 上传 CSV。账号必须存在且为 `ACTIVE`，交易币种必须与账户币种一致。
4. `GET /api/accounts?customerId={customerId}` 查询客户名下账户。
5. `GET /api/transactions?accountId={accountId}` 或 `?customerId={customerId}` 查询关联交易；两种筛选不能同时提交。

交易导入响应包含 `batchId/batchNo/total/imported/duplicates/rejected/errors`。同一 `source + externalId` 的重复流水不会再次写入；最多返回前 100 条逐行错误。

参数校验失败返回含 `timestamp/status/code/message/path/fields` 的 JSON；无令牌和无效令牌返回 401，权限不足返回 403。错误密码在 2026-10-15 历史基线中返回 400；当前工作区拟改为统一 `401 / INVALID_CREDENTIALS`，待 PostgreSQL 集成测试通过后确认。

## 名单筛查与风险评分

- `MOCK` Provider 的请求、候选和后续评分都标记为 `SIMULATED`，页面显示醒目免责声明；`YENTE` 标记为 `REAL`。
- Yente 使用 `POST /match/{dataset}` 查询契约，可配置 API key、阈值和请求超时。未启用时拒绝选择；网络、超时、非 2xx 或畸形响应返回 `SCREENING_PROVIDER_FAILED`（HTTP 502），并把请求记为 `FAILED`，不会伪造空结果或自动切换 Mock。
- 人工复核最终结论不可再次修改。候选相似并不等于身份一致或违法，系统不会自动定案。
- 客户模型版本为 `CUSTOMER_RULE_V1`，交易模型版本为 `TRANSACTION_RULE_V1`。当前/历史记录包含分数、等级、解释、模型版本、计算人和 `NONE/REAL/SIMULATED/MIXED` 来源。

## 案件调查流程

1. 预警必须先到 `IN_REVIEW`，再调用 `POST /api/alerts/{id}/escalate` 原子创建案件、关联预警并将预警置为 `ESCALATED`。
2. 调用 `/cases/{id}/assign` 分派给启用且拥有 `CASE_INVESTIGATE` 权限的用户；未分派案件不能开始调查。
3. 案件状态机为 `OPEN → INVESTIGATING → PENDING_REVIEW → CLOSED`；`PENDING_REVIEW` 可退回 `INVESTIGATING`，提交复核前至少需要一条调查笔记。
4. 关闭时 `comment` 写入案件结论；关闭案件不可再次分派、添加笔记、变更证据或流转状态。
5. 分派、笔记、状态流转、证据上传/下载/移除均写入 `sys_audit_log`；状态变化另写 `aml_case_history`。

# 外部模拟与核心系统适配

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/transactions/integrations` | `TRANSACTION_IMPORT` | 返回 LOCAL_CSV、AMLSIM_CSV、FINERACT_API 的配置可用状态和数据模式 |
| POST | `/api/transactions/import/amlsim?currency=USD` | `TRANSACTION_IMPORT` | 导入 IBM AMLSim 默认交易 CSV；数据模式固定为模拟 |
| GET | `/api/transactions/template/amlsim` | `TRANSACTION_IMPORT` | 下载 AMLSim 官方默认列的本地示例模板 |
| POST | `/api/transactions/import/fineract/{savingsAccountId}` | `TRANSACTION_IMPORT` | 从显式启用的 Fineract 读取储蓄账户交易并映射至同账号本地账户 |

Fineract 未启用、超时、连接失败、非 2xx 或坏响应返回 `502 / EXTERNAL_INTEGRATION_FAILED`，不会伪造空数据成功。Yente 接口及 REAL/SIMULATED 边界见筛查章节。

## 可执行接口验收

仓库提供 `scripts/e2e-smoke.ps1`。它调用真实 HTTP API，覆盖登录、客户、账户、交易、重复导入、Mock 筛查与复核、R001 与命中幂等、评分、预警、案件、证据、仪表盘、审计和 CSV。所有写入均带 `E2E-` 前缀并标记为合成/模拟数据。
