# 功能核验矩阵

状态严格使用：已实现且验证通过、已实现但未验证、部分实现、仅有设计或占位、未实现。“运行验证”以源码、Flyway V1–V7、2026-10-30 的真实 PostgreSQL 与浏览器测试为准；旧测试数字保留在带日期的历史报告，不代表当前结果。

| 功能/描述 | 后端源码 | 前端源码 | 数据库表 | API | 测试代码 | 状态 | 运行验证 | 问题 |
|---|---|---|---|---|---|---|---|---|
| JWT 登录与 RBAC：BCrypt、JWT、方法权限 | `auth/`、`security/`、`user/` | `LoginView.vue`、`stores/auth.ts`、`AppLayout.vue` | `sys_user/role/permission` 及连接表 | `POST /auth/login` | `JwtServiceTest`、`CoreWorkflowIntegrationTest`、`AuthPostgresIntegrationTest` | 已实现且验证通过 | PostgreSQL 4 项验证失败计数、锁定、审计、未知用户统一响应及并发；浏览器登录退出通过 | 无用户/角色维护 UI/API；不等于生产身份治理完成 |
| 客户管理：查询、新增、编辑、停用 | `customer/*` | `CustomersView.vue` | `aml_customer` | `/customers/**` | `CustomerServiceTest`、核心集成/E2E | 已实现且验证通过 | 单测、MockMvc、真实 HTTP | 无物理删除是设计；页面固定取最多 100 条，未做完整分页 UI |
| KYC 管理 | `customer/KycController`、`KycService` | `CustomersView.vue` KYC 弹窗 | `aml_customer_kyc`、`aml_customer_kyc_history` | `/customers/{id}/kyc`、`submit`、`review` | `KycAndRulePostgresIntegrationTest`、浏览器第 2/3 条 | 已实现且验证通过 | PostgreSQL 审核权限、状态、到期复审、历史和审计；页面提交与审核通过 | 无自动提醒或强制双人分离；只保存合成/脱敏摘要，不采集原件 |
| 账户及客户关系：开户、筛选、冻结/关闭 | `account/*` | `AccountsView.vue` | `aml_account`、`aml_account_customer` | `/accounts/**` | `AccountServiceTest`、核心集成/E2E | 已实现且验证通过 | 单测、MockMvc、真实 HTTP | 页面固定取最多 100 条 |
| 平台 CSV 交易导入：批次、校验、去重 | `transaction/TransactionImportService` 等 | `TransactionsView.vue` | `aml_transaction_import_batch`、`aml_transaction` | `/transactions/import`、查询、模板 | `TransactionImportServiceTest`、核心集成/E2E | 已实现且验证通过 | Commons CSV 支持字段内换行与引号；100 条有效记录分块；重复导入测试通过 | 10 MB 上限、无断点续传，跨块非整批原子性 |
| IBM AMLSim 交易 CSV | `AmlSimImportService` | `TransactionsView.vue` | 同交易表，source=`AMLSIM` | `/transactions/import/amlsim`、模板 | `AmlSimImportServiceTest`、核心集成 | 已实现且验证通过 | 本次真实端点返回 duplicates=1 | 只支持默认交易列；不导入图、账户和 SAR 结构 |
| Apache Fineract 储蓄流水 | `integration/fineract/*` | `TransactionsView.vue` | 同交易表，source=`FINERACT` | `/transactions/import/fineract/{id}`、状态 | 两个 Fineract 测试类 | 已实现但未验证 | 本地 stub 契约通过；运行状态 `available=false` | 没有真实 Fineract；无客户/贷款/增量同步；远程调用处在数据库事务中 |
| 规则配置与版本 | `rule/RuleConfigService`、参数校验 | `RulesView.vue` | `aml_rule`、`aml_rule_version` | `/rules`、版本、状态 | `RuleParameterValidatorTest`、核心集成 | 已实现且验证通过 | 版本创建、非法参数、启停约束通过 | 仅 R001–R003 参数白名单；无审批发布流程 |
| R001 单笔大额 | `RuleExecutionService`、`DroolsRuleEvaluator` | `RulesView.vue` | 执行、命中及交易关系表 | `/rules/execute`、hits/executions | Drools、`KycAndRulePostgresIntegrationTest`、浏览器 E2E | 已实现且验证通过 | 默认三规则联合执行通过；R001 命中与预警、重复执行幂等 | 阈值为教学配置 |
| R002 短时高频 | 同上 | `RulesView.vue` | 同上 | 同上 | Drools/核心集成/PostgreSQL | 已实现且验证通过 | 默认联合执行 API 与浏览器路径通过；窗口边界由既有测试验证 | 当前以账户+客户窗口聚合，无流式调度器 |
| R003 窗口累计金额 | 同上 | `RulesView.vue` | 同上 | 同上 | Drools/核心集成/PostgreSQL | 已实现且验证通过 | 默认联合执行 API 与浏览器路径通过；窗口边界由既有测试验证 | 当前为批量手工执行 |
| R004–R008 高风险/新户/行为/图规则 | 仅迁移元数据；执行器明确拒绝 | 规则页可展示规划项 | `aml_rule/version` | 查询可见，不能启用/执行 | 参数拒绝有间接覆盖 | 仅有设计或占位 | 运行库确认 `PLANNED/disabled` | 算法未实现，尤其循环转账/图关系 |
| 预警查询、分派和状态审核 | `alert/*` | `AlertsView.vue` | `aml_alert`、交易关系、历史 | `/alerts/**` | `AlertServiceTest`、核心集成/E2E | 已实现且验证通过 | API 状态流和历史通过 | 无 SLA、批量处置或通知 |
| 案件升级、分派、笔记、状态机、关闭 | `casework/CaseService/QueryService` | `CasesView.vue` | `aml_case`、alert/note/history | `/cases/**`、alert escalate | `CaseServiceTest`、核心集成/E2E | 已实现且验证通过 | 本轮浏览器用例升级并打开案件；历史 HTTP E2E 验证关闭 | 一条预警最多一个案件；无手工多预警并案 |
| 证据文件：上传、下载、哈希、逻辑移除 | `CaseEvidenceService` | `CasesView.vue` | `aml_case_evidence` + 证据卷 | case evidence API | `CaseEvidenceServiceTest`、核心集成/E2E | 已实现且验证通过 | 类型/哈希/下载/移除和真实上传通过 | 无病毒扫描、对象存储、内容预览 |
| Mock 名单筛查与人工复核 | `screening/Mock*`、`ScreeningService` | `ScreeningView.vue` | screening 三表 | `/screenings/**` | Mock/Service/核心集成/E2E | 已实现且验证通过 | 本次 E2E 标记 `SIMULATED` 并复核 | 仅合成候选，不是制裁名单 |
| Yente/OpenSanctions 筛查 | `YenteScreeningProvider` | `ScreeningView.vue` | screening 三表 | 同筛查 API | `YenteScreeningProviderTest` | 已实现但未验证 | 本地 HTTP stub 契约通过；运行状态不可用 | 未连接真实服务或数据集，不能写“实连成功” |
| 客户风险评分与历史 | `risk/RiskScoringService` | `RiskScoresView.vue` | customer risk/current/history | `/risk-scores/customers/**` | 核心集成/E2E | 已实现且验证通过 | 本次评分 MEDIUM | 固定教学模型 V1；缺独立单元边界测试和模型管理 |
| 交易风险评分与历史 | 同上 | `RiskScoresView.vue` | transaction risk/current/history | `/risk-scores/transactions/**` | 核心集成/E2E | 已实现且验证通过 | 本次评分 HIGH | 页面要求手填 UUID；固定模型 V1 |
| AML 统计仪表盘 | `dashboard/DashboardService` | `DashboardView.vue` | 客户、交易、预警、案件、命中等 | `GET /dashboard` | 核心集成/E2E | 已实现且验证通过 | API、前端构建及浏览器登录进入仪表盘通过 | 按日趋势可能受 DB 会话时区影响；未做跨浏览器视觉验证 |
| 即时 CSV 报表 | `ReportExportService`、`CsvCell` | 仪表盘导出 | 实时查询业务表 | `/reports/{type}/csv` | 核心集成、`CsvCellTest`、E2E | 已实现且验证通过 | 历史 HTTP E2E 曾验证 CSV 1341 bytes，本轮后端全量测试通过 | 仅四类内部 CSV，不是监管报文 |
| 报表任务/产物生命周期 | 无业务 Service/Controller | 无独立报表页 | `aml_report`、`aml_report_generation` | 无 | 无 | 仅有设计或占位 | 未运行 | 两表没有实际调用 |
| 审计查询与导出 | `audit/*` | `AuditView.vue` | `sys_audit_log` | `/audit-logs/**` | 核心集成/E2E、`AuthPostgresIntegrationTest` | 已实现且验证通过 | 失败登录审计真实 PostgreSQL 持久化；查询、权限、CSV 既有测试通过 | 无防篡改存储/签名 |
| 前端 SPA 与权限导航 | 后端权限为最终边界 | router/store/layout、业务页面及 KYC 弹窗 | 无 | 调用上述 API | `frontend/e2e/aml-workflows.spec.ts` | 已实现且验证通过 | vue-tsc/Vite 构建、Edge/Playwright 三条隔离浏览器用例通过 | 路由守卫只看 token，细粒度仍依赖后端权限；未测其他浏览器 |
| Docker Compose 部署 | Dockerfile、配置 | Nginx | PostgreSQL + 两命名卷 | 健康/OpenAPI | Compose、迁移、E2E | 已实现且验证通过 | 三容器运行、健康 UP | backend/frontend 无 Compose healthcheck；配置是开发演示级 |

## 总体判定

2026-10-30 核心教学闭环由真实 PostgreSQL 与浏览器复验：虚构客户 KYC 审核→账户→交易→默认 R001–R003 联合执行→R001 预警→升级案件。案件关闭由既有 HTTP 集成测试覆盖；本轮浏览器只验证案件打开，不冒称其关闭。真实名单/核心银行实连、用户管理、高级规则及正式监管报表仍未完成。
