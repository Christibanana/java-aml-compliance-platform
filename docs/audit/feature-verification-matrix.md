# 功能核验矩阵

状态严格使用：已实现且验证通过、已实现但未验证、部分实现、仅有设计或占位、未实现。“运行验证”指本次或可追溯的自动化/真实 HTTP 证据，不因存在 Controller 或静态页面自动判定完成。

| 功能/描述 | 后端源码 | 前端源码 | 数据库表 | API | 测试代码 | 状态 | 运行验证 | 问题 |
|---|---|---|---|---|---|---|---|---|
| JWT 登录与 RBAC：BCrypt、JWT、方法权限 | `auth/`、`security/`、`user/` | `LoginView.vue`、`stores/auth.ts`、`AppLayout.vue` | `sys_user/role/permission` 及连接表 | `POST /auth/login` | `JwtServiceTest`、`CoreWorkflowIntegrationTest`；新增 `AuthPostgresIntegrationTest` 待运行 | 部分实现 | 历史基线的正确登录、401/403 已验证；认证修复未验收 | 旧版错误口令导致失败次数和审计回滚；当前工作区已有独立事务修复代码，PostgreSQL 测试尚未通过；无用户管理 UI/API |
| 客户管理：查询、新增、编辑、停用 | `customer/*` | `CustomersView.vue` | `aml_customer` | `/customers/**` | `CustomerServiceTest`、核心集成/E2E | 已实现且验证通过 | 单测、MockMvc、真实 HTTP | 无物理删除是设计；页面固定取最多 100 条，未做完整分页 UI |
| KYC 管理 | 无 Service/Controller/DTO | 无 | `aml_customer_kyc` | 无 | 无 | 仅有设计或占位 | 未运行 | 只有表，无采集、审核、到期提醒或页面 |
| 账户及客户关系：开户、筛选、冻结/关闭 | `account/*` | `AccountsView.vue` | `aml_account`、`aml_account_customer` | `/accounts/**` | `AccountServiceTest`、核心集成/E2E | 已实现且验证通过 | 单测、MockMvc、真实 HTTP | 页面固定取最多 100 条 |
| 平台 CSV 交易导入：批次、校验、去重 | `transaction/TransactionImportService` 等 | `TransactionsView.vue` | `aml_transaction_import_batch`、`aml_transaction` | `/transactions/import`、查询、模板 | `TransactionImportServiceTest`、核心集成/E2E | 已实现且验证通过 | 首次导入和重复导入均验证 | 自写解析器不支持字段内换行；大文件整事务逐行处理 |
| IBM AMLSim 交易 CSV | `AmlSimImportService` | `TransactionsView.vue` | 同交易表，source=`AMLSIM` | `/transactions/import/amlsim`、模板 | `AmlSimImportServiceTest`、核心集成 | 已实现且验证通过 | 本次真实端点返回 duplicates=1 | 只支持默认交易列；不导入图、账户和 SAR 结构 |
| Apache Fineract 储蓄流水 | `integration/fineract/*` | `TransactionsView.vue` | 同交易表，source=`FINERACT` | `/transactions/import/fineract/{id}`、状态 | 两个 Fineract 测试类 | 已实现但未验证 | 本地 stub 契约通过；运行状态 `available=false` | 没有真实 Fineract；无客户/贷款/增量同步；远程调用处在数据库事务中 |
| 规则配置与版本 | `rule/RuleConfigService`、参数校验 | `RulesView.vue` | `aml_rule`、`aml_rule_version` | `/rules`、版本、状态 | `RuleParameterValidatorTest`、核心集成 | 已实现且验证通过 | 版本创建、非法参数、启停约束通过 | 仅 R001–R003 参数白名单；无审批发布流程 |
| R001 单笔大额 | `RuleExecutionService`、`DroolsRuleEvaluator` | `RulesView.vue` | 执行、命中及交易关系表 | `/rules/execute`、hits/executions | Drools/核心集成/E2E | 已实现且验证通过 | 本次 E2E 首次命中 1、重复 0 | 阈值为教学配置 |
| R002 短时高频 | 同上 | `RulesView.vue` | 同上 | 同上 | Drools/核心集成 | 已实现且验证通过 | 自动化与既有真实 PG 记录 | 当前以账户+客户窗口聚合，无流式调度器 |
| R003 窗口累计金额 | 同上 | `RulesView.vue` | 同上 | 同上 | Drools/核心集成 | 已实现且验证通过 | 自动化与既有真实 PG 记录 | 当前为批量手工执行 |
| R004–R008 高风险/新户/行为/图规则 | 仅迁移元数据；执行器明确拒绝 | 规则页可展示规划项 | `aml_rule/version` | 查询可见，不能启用/执行 | 参数拒绝有间接覆盖 | 仅有设计或占位 | 运行库确认 `PLANNED/disabled` | 算法未实现，尤其循环转账/图关系 |
| 预警查询、分派和状态审核 | `alert/*` | `AlertsView.vue` | `aml_alert`、交易关系、历史 | `/alerts/**` | `AlertServiceTest`、核心集成/E2E | 已实现且验证通过 | API 状态流和历史通过 | 无 SLA、批量处置或通知 |
| 案件升级、分派、笔记、状态机、关闭 | `casework/CaseService/QueryService` | `CasesView.vue` | `aml_case`、alert/note/history | `/cases/**`、alert escalate | `CaseServiceTest`、核心集成/E2E | 已实现且验证通过 | 本次 E2E 最终 `CLOSED` | 一条预警最多一个案件；无手工多预警并案 |
| 证据文件：上传、下载、哈希、逻辑移除 | `CaseEvidenceService` | `CasesView.vue` | `aml_case_evidence` + 证据卷 | case evidence API | `CaseEvidenceServiceTest`、核心集成/E2E | 已实现且验证通过 | 类型/哈希/下载/移除和真实上传通过 | 无病毒扫描、对象存储、内容预览 |
| Mock 名单筛查与人工复核 | `screening/Mock*`、`ScreeningService` | `ScreeningView.vue` | screening 三表 | `/screenings/**` | Mock/Service/核心集成/E2E | 已实现且验证通过 | 本次 E2E 标记 `SIMULATED` 并复核 | 仅合成候选，不是制裁名单 |
| Yente/OpenSanctions 筛查 | `YenteScreeningProvider` | `ScreeningView.vue` | screening 三表 | 同筛查 API | `YenteScreeningProviderTest` | 已实现但未验证 | 本地 HTTP stub 契约通过；运行状态不可用 | 未连接真实服务或数据集，不能写“实连成功” |
| 客户风险评分与历史 | `risk/RiskScoringService` | `RiskScoresView.vue` | customer risk/current/history | `/risk-scores/customers/**` | 核心集成/E2E | 已实现且验证通过 | 本次评分 MEDIUM | 固定教学模型 V1；缺独立单元边界测试和模型管理 |
| 交易风险评分与历史 | 同上 | `RiskScoresView.vue` | transaction risk/current/history | `/risk-scores/transactions/**` | 核心集成/E2E | 已实现且验证通过 | 本次评分 HIGH | 页面要求手填 UUID；固定模型 V1 |
| AML 统计仪表盘 | `dashboard/DashboardService` | `DashboardView.vue` | 客户、交易、预警、案件、命中等 | `GET /dashboard` | 核心集成/E2E | 已实现且验证通过 | API、健康与前端构建通过 | 未完成浏览器视觉验证；按日趋势可能受 DB 会话时区影响 |
| 即时 CSV 报表 | `ReportExportService`、`CsvCell` | 仪表盘导出 | 实时查询业务表 | `/reports/{type}/csv` | 核心集成、`CsvCellTest`、E2E | 已实现且验证通过 | 本次 CSV 1341 bytes | 仅四类内部 CSV，不是监管报文 |
| 报表任务/产物生命周期 | 无业务 Service/Controller | 无独立报表页 | `aml_report`、`aml_report_generation` | 无 | 无 | 仅有设计或占位 | 未运行 | 两表没有实际调用 |
| 审计查询与导出 | `audit/*` | `AuditView.vue` | `sys_audit_log` | `/audit-logs/**` | 核心集成/E2E；失败登录专项待运行 | 部分实现 | 历史基线的查询、权限、CSV 已验证 | 旧版错误登录审计回滚；工作区修复待 PostgreSQL 验证；无防篡改存储/签名 |
| 前端 SPA 与权限导航 | 后端权限为最终边界 | router/store/layout 和 11 页 | 无 | 调用上述 API | 只有构建；无仓库内前端测试 | 已实现但未自动化验证 | vue-tsc/Vite、首页 HTTP 200；历史 Edge 实际页面截图 11 张 | 有一次性人工截图证据，无可重复浏览器点击测试；路由守卫只看 token，细粒度主要靠菜单和后端 |
| Docker Compose 部署 | Dockerfile、配置 | Nginx | PostgreSQL + 两命名卷 | 健康/OpenAPI | Compose、迁移、E2E | 已实现且验证通过 | 三容器运行、健康 UP | backend/frontend 无 Compose healthcheck；配置是开发演示级 |

## 总体判定

核心教学闭环确实可运行，不是只有静态页面：客户→账户→交易→R001→预警→案件→关闭已由真实 PostgreSQL HTTP E2E 通过。外部真实名单和核心银行系统、KYC、用户管理、高级规则、正式监管报表则不能列为完成。
