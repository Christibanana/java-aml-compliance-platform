# 文档与实现证据映射

本表以真实源码和可追溯测试为准。历史验收日期为 2026-10-15；当前未提交的认证修复不计入历史通过结果。API 路径均以 `/api` 为前缀，迁移文件位于 `backend/src/main/resources/db/migration/`。

| 文档章节 | Java 源码 | API | 数据库表 | 测试与结论 |
|---|---|---|---|---|
| 项目说明书·总体架构 | `backend/src/main/java/com/example/aml/AmlApplication.java`、`backend/pom.xml` | `/actuator/health`、`/v3/api-docs` | Flyway V1–V6 | `AmlApplicationTest`；历史 Compose 与 HTTP 健康检查通过 |
| 用户权限手册·成功登录与鉴权 | `auth/AuthController.java`、`security/SecurityConfig.java`、`security/JwtAuthenticationFilter.java`、`user/User.java` | `POST /auth/login`、受保护业务 API | `sys_user`、`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission` | `JwtServiceTest`、`CoreWorkflowIntegrationTest`；历史成功登录及 401/403 已验证 |
| 用户权限手册·失败、锁定与审计 | `auth/AuthController.java`、`auth/LoginStateService.java`、`audit/AuditService.java`、`user/UserRepository.java` | `POST /auth/login`、`GET /audit-logs` | `sys_user`、`sys_audit_log` | 旧版本失败事务回滚已观察；`AuthPostgresIntegrationTest` 尚未执行成功，当前修复未验收 |
| 客户 KYC 手册·客户资料 | `customer/CustomerController.java`、`customer/CustomerService.java` | `/customers` | `aml_customer`、`aml_customer_kyc` | `CustomerServiceTest`、核心 E2E；客户维护已验证，KYC 工作流未实现 |
| 账户手册·账户关联 | `account/AccountController.java`、`account/AccountService.java` | `/accounts` | `aml_account`、`aml_account_customer` | `AccountServiceTest`、核心 E2E 已验证 |
| 交易手册·导入 | `transaction/TransactionController.java`、`transaction/TransactionImportService.java`、`transaction/AmlSimImportService.java` | `/transactions`、`/transactions/import`、`/transactions/import/amlsim` | `aml_transaction`、`aml_transaction_import_batch` | 两个导入单测、核心 E2E；模拟 CSV 导入已验证 |
| 规则手册·版本和执行 | `rule/RuleController.java`、`rule/RuleConfigService.java`、`rule/RuleExecutionService.java`、`rule/DroolsRuleEvaluator.java`、`rules/transaction-monitoring.drl` | `/rules`、`/rules/{code}/versions`、`/rules/{code}/status`、`/rules/execute`、`/rules/hits` | `aml_rule`、`aml_rule_version`、`aml_rule_execution`、`aml_rule_hit`、`aml_rule_hit_transaction` | `DroolsRuleEvaluatorTest`、`RuleParameterValidatorTest`、核心 E2E；R001–R003 已实现，R004–R008 为禁用规划项 |
| 预警手册·审核 | `alert/AlertController.java`、`alert/AlertService.java` | `/alerts`、`/alerts/{id}/transition`、`/alerts/{id}/escalate` | `aml_alert`、`aml_alert_transaction`、`aml_alert_history` | `AlertServiceTest`、核心 E2E 已验证 |
| 案件手册·调查 | `casework/CaseController.java`、`casework/CaseService.java`、`casework/CaseEvidenceService.java` | `/cases`、`/cases/{id}/evidence` | `aml_case`、`aml_case_alert`、`aml_case_note`、`aml_case_evidence`、`aml_case_history` | `CaseServiceTest`、`CaseEvidenceServiceTest`、核心 E2E 已验证 |
| 筛查手册·候选复核 | `screening/ScreeningController.java`、`screening/MockScreeningProvider.java`、`screening/YenteScreeningProvider.java` | `/screenings` | `aml_screening_request`、`aml_screening_match`、`aml_screening_review` | Mock、Yente stub 与核心 E2E；真实 Yente 未实连 |
| 风险手册·评分 | `risk/RiskScoringController.java`、`risk/RiskScoringService.java` | `/risk-scores/customers`、`/risk-scores/transactions` | `aml_customer_risk`、`aml_customer_risk_history`、`aml_transaction_risk`、`aml_transaction_risk_history` | 核心 E2E；固定教学模型，不是生产评分模型 |
| 报表审计手册·导出 | `dashboard/DashboardController.java`、`dashboard/ReportController.java`、`audit/AuditController.java` | `/dashboard`、`/reports/{type}/csv`、`/audit-logs` | 业务表、`sys_audit_log`；`aml_report*` 尚未接入 | 核心 E2E；即时 CSV 与审计查询已验证，报表任务生命周期未实现 |
| 部署手册·外部适配 | `integration/fineract/FineractApiAdapter.java`、`transaction/AmlSimImportService.java`、`screening/YenteScreeningProvider.java` | `/transactions/integrations`、`/screenings/providers` | 导入批次、交易、筛查表 | Fineract/Yente 本地 stub 契约已测试；真实上游未实连 |

源码路径均相对 `backend/src/main/java/com/example/aml/`，规则 DRL 路径相对 `backend/src/main/resources/`。2026-10-15 后端 32 个测试、Flyway 空库和 HTTP E2E 属于历史基线，详见 `docs/TEST_RESULTS.md`；它们不证明当前未提交认证修复已通过。

`latex/figures/screenshots/` 的 11 张截图来自历史实际运行页面，数据为合成或模拟数据。截图不是可重复的 UI 自动化测试；更新文档未重新采集页面。
