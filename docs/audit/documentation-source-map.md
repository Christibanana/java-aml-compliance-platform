# 文档与实现证据映射

本表以 2026-10-30 源码和可追溯测试为准；2026-10-15、10-23 报告是带日期的历史结果。API 路径均以 `/api` 为前缀，迁移文件位于 `backend/src/main/resources/db/migration/`。

| 文档章节 | Java 源码 | API | 数据库表 | 测试与结论 |
|---|---|---|---|---|
| 项目说明书·总体架构 | `backend/src/main/java/com/example/aml/AmlApplication.java`、`backend/pom.xml` | `/actuator/health`、`/v3/api-docs` | Flyway V1–V7 | `AmlApplicationTest`、完整 `mvn -B test` 42/42；历史 Compose 健康检查另见旧报告 |
| 用户权限手册·成功登录与鉴权 | `auth/AuthController.java`、`security/SecurityConfig.java`、`security/JwtAuthenticationFilter.java`、`user/User.java` | `POST /auth/login`、受保护业务 API | `sys_user`、`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission` | `JwtServiceTest`、`CoreWorkflowIntegrationTest`；历史成功登录及 401/403 已验证 |
| 用户权限手册·失败、锁定与审计 | `auth/AuthController.java`、`auth/LoginStateService.java`、`audit/AuditService.java`、`user/UserRepository.java` | `POST /auth/login`、`GET /audit-logs` | `sys_user`、`sys_audit_log` | `AuthPostgresIntegrationTest` 4/4 通过；失败计数、锁定、统一响应、并发与失败审计已在 PostgreSQL 验证 |
| 客户 KYC 手册·客户资料与审核 | `customer/CustomerController.java`、`customer/CustomerService.java`、`customer/KycController.java`、`customer/KycService.java` | `/customers`、`/customers/{id}/kyc`、`/submit`、`/review` | `aml_customer`、`aml_customer_kyc`、`aml_customer_kyc_history` | `CustomerServiceTest`、`KycAndRulePostgresIntegrationTest`、浏览器第 2/3 条；提交、审核、过期复审与审计已验证 |
| 账户手册·账户关联 | `account/AccountController.java`、`account/AccountService.java` | `/accounts` | `aml_account`、`aml_account_customer` | `AccountServiceTest`、核心 E2E 已验证 |
| 交易手册·导入 | `transaction/TransactionController.java`、`transaction/TransactionImportService.java`、`transaction/AmlSimImportService.java` | `/transactions`、`/transactions/import`、`/transactions/import/amlsim` | `aml_transaction`、`aml_transaction_import_batch` | 两个导入单测、核心 E2E；模拟 CSV 导入已验证 |
| 规则手册·版本和执行 | `rule/RuleController.java`、`rule/RuleConfigService.java`、`rule/RuleExecutionService.java`、`rule/DroolsRuleEvaluator.java`、`rules/transaction-monitoring.drl` | `/rules`、`/rules/{code}/versions`、`/rules/{code}/status`、`/rules/execute`、`/rules/hits` | `aml_rule`、`aml_rule_version`、`aml_rule_execution`、`aml_rule_hit`、`aml_rule_hit_transaction`；V7 扩大 scope | `DroolsRuleEvaluatorTest`、`KycAndRulePostgresIntegrationTest`、浏览器第 3 条；默认 R001–R003 联合执行已通过，R004–R008 仍为规划项 |
| 预警手册·审核 | `alert/AlertController.java`、`alert/AlertService.java` | `/alerts`、`/alerts/{id}/transition`、`/alerts/{id}/escalate` | `aml_alert`、`aml_alert_transaction`、`aml_alert_history` | `AlertServiceTest`、核心 E2E 已验证 |
| 案件手册·调查 | `casework/CaseController.java`、`casework/CaseService.java`、`casework/CaseEvidenceService.java` | `/cases`、`/cases/{id}/evidence` | `aml_case`、`aml_case_alert`、`aml_case_note`、`aml_case_evidence`、`aml_case_history` | `CaseServiceTest`、`CaseEvidenceServiceTest`、核心 E2E 已验证 |
| 筛查手册·候选复核 | `screening/ScreeningController.java`、`screening/MockScreeningProvider.java`、`screening/YenteScreeningProvider.java` | `/screenings` | `aml_screening_request`、`aml_screening_match`、`aml_screening_review` | Mock、Yente stub 与核心 E2E；真实 Yente 未实连 |
| 风险手册·评分 | `risk/RiskScoringController.java`、`risk/RiskScoringService.java` | `/risk-scores/customers`、`/risk-scores/transactions` | `aml_customer_risk`、`aml_customer_risk_history`、`aml_transaction_risk`、`aml_transaction_risk_history` | 核心 E2E；固定教学模型，不是生产评分模型 |
| 报表审计手册·导出 | `dashboard/DashboardController.java`、`dashboard/ReportController.java`、`audit/AuditController.java` | `/dashboard`、`/reports/{type}/csv`、`/audit-logs` | 业务表、`sys_audit_log`；`aml_report*` 尚未接入 | 核心 E2E；即时 CSV 与审计查询已验证，报表任务生命周期未实现 |
| 部署手册·外部适配 | `integration/fineract/FineractApiAdapter.java`、`transaction/AmlSimImportService.java`、`screening/YenteScreeningProvider.java` | `/transactions/integrations`、`/screenings/providers` | 导入批次、交易、筛查表 | Fineract/Yente 本地 stub 契约已测试；真实上游未实连 |

源码路径均相对 `backend/src/main/java/com/example/aml/`，规则 DRL 路径相对 `backend/src/main/resources/`。当前验收为 2026-10-30 的后端 42/42 与浏览器 3/3；旧测试结果仅作为历史基线。

`latex/figures/screenshots/` 是历史页面截图；`docs/optimization/screenshots/` 新增 Edge/Playwright 的 KYC、预警和案件截图，均为合成数据。截图与用例代码、隔离测试库共同构成浏览器验证证据。
