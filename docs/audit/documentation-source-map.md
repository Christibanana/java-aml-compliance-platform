# 文档内容与实现证据映射

审查日期：2026-10-14

本文件用于撰写实验报告、系统操作手册和教师验收材料。所有“可写入内容”均限定在实际源码、数据库对象或本次测试证据能够支持的范围内；边界栏中的内容不得扩写为已完成能力。

## 证据映射表

| 论文或手册中的功能描述 | 实际源码 | 数据库对象 | 测试或运行证据 | 可写结论与边界 |
|---|---|---|---|---|
| 系统采用前后端分离的 Java AML 教学平台架构 | `backend/pom.xml`、`backend/src/main/java/com/aml/platform/AmlPlatformApplication.java`、`frontend/package.json`、`frontend/src/`、`compose.yaml` | PostgreSQL 业务库；Flyway `flyway_schema_history` | 后端 `mvn clean verify` 成功；前端 `npm run build` 成功；Compose 服务实际运行 | 可写为 Spring Boot + Vue 3 + PostgreSQL 的模块化单体系统；不可写成微服务、消息队列或分布式架构 |
| 用户通过 JWT 登录并按角色和权限访问接口 | `backend/src/main/java/com/aml/platform/auth/`、`security/`、`user/`，`frontend/src/views/LoginView.vue`、`frontend/src/stores/auth.ts` | `sys_user`、`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission` | 自动化测试覆盖认证与鉴权；E2E 使用登录令牌访问核心接口 | 登录成功与接口鉴权已实现；失败登录计数、锁定和失败审计因事务回滚未生效，不能写为验证通过 |
| 客户资料可新增、查询、修改并作为 AML 业务主体 | `backend/src/main/java/com/aml/platform/customer/`、`frontend/src/views/CustomersView.vue` | `aml_customer`、`aml_customer_kyc`、`aml_customer_risk`、`aml_customer_risk_history` | Maven 客户相关测试；E2E 新建客户并参与后续完整链路 | 客户 CRUD 与风险结果关联可写为已实现；独立 KYC 工作流仅有表结构，不能写为已实现 |
| 客户可以关联一个或多个账户 | `backend/src/main/java/com/aml/platform/account/`、`frontend/src/views/AccountsView.vue` | `aml_account`，外键关联 `aml_customer` | Maven 测试；E2E 在新客户下创建账户 | 客户—账户关系已实现并运行验证 |
| 系统支持交易录入、查询和批量 CSV 导入 | `backend/src/main/java/com/aml/platform/transaction/`、`frontend/src/views/TransactionsView.vue` | `aml_transaction`、`aml_transaction_import_batch`、`aml_transaction_risk`、`aml_transaction_risk_history` | Maven 测试；E2E 创建交易；本地 CSV/AMLSim 样例导入返回重复识别结果 | 手工/接口交易和 CSV 导入可写为已实现；示例与 E2E 数据必须标注为模拟数据 |
| 系统可导入 IBM AMLSim 格式的模拟交易数据 | `backend/src/main/java/com/aml/platform/integration/`、`integrations/amlsim/` | 导入后进入 `aml_transaction` 与批次表 | 官方格式样例文件调用导入端点成功，结果为 1 条重复、0 条新增 | 可写为兼容 AMLSim CSV 的离线适配；不可写为已连接或运行 IBM AMLSim 服务 |
| 系统预留 Apache Fineract 账户/交易集成 | `backend/src/main/java/com/aml/platform/integration/` 中 Fineract 适配代码 | 最终映射目标为客户、账户、交易表 | 运行时集成状态为 `EXTERNAL` 且 `available=false` | 只能写为适配器/接口预留和不可用时本地运行；不能宣称真实 Fineract 联调成功 |
| Drools 规则引擎执行大额、高频和累计交易监控 | `backend/src/main/java/com/aml/platform/rule/`、`backend/src/main/resources/rules/`、`frontend/src/views/RulesView.vue` | `aml_rule`、`aml_rule_version`、`aml_rule_execution`、`aml_rule_hit` | Drools 10.1.0 在 JDK 21 下完成 Maven 测试；E2E 的 R001 首次命中 1 次 | R001—R003 可写为已实现；R004—R008 为禁用的 `PLANNED` 记录，不得写成已实现 |
| 规则版本和时间窗口统计可追溯 | 规则模块中的版本、执行和统计服务 | `aml_rule_version`、`aml_rule_execution`、`aml_rule_hit`、交易表时间索引 | 规则相关测试及 E2E 规则执行记录 | 版本、执行、命中和窗口统计已实现；规则执行异常记录可能随事务回滚丢失，需列为缺陷 |
| 同一交易重复执行规则不会重复产生命中和预警 | 规则执行服务、预警生成服务 | 规则命中与预警表中的业务唯一约束/关联字段 | E2E 首次规则命中 1，重复执行命中 0 | 可写为本次 E2E 已验证幂等；不要扩展为所有并发场景均已证明 |
| 规则命中后自动生成风险预警，分析员可查询和审核 | `backend/src/main/java/com/aml/platform/alert/`、`frontend/src/views/AlertsView.vue` | `aml_alert`、`aml_alert_transaction`、`aml_alert_history` | Maven 预警测试；E2E 获得预警并继续升级案件 | 预警生成、查询和审核相关能力已实现并验证核心路径 |
| 预警可升级为案件并完成调查闭环 | `backend/src/main/java/com/aml/platform/casework/`、`frontend/src/views/CasesView.vue` | `aml_case`、`aml_case_alert`、`aml_case_note`、`aml_case_evidence`、`aml_case_history` | Maven 案件测试；E2E 创建案件并最终达到 `CLOSED` | 预警升级、分派字段维护、笔记、证据、状态流转和关闭可写为已实现；需区分本地文件卷与外部对象存储能力 |
| 系统记录关键操作审计日志并支持查询 | `backend/src/main/java/com/aml/platform/audit/`、`frontend/src/views/AuditView.vue` | `sys_audit_log` | E2E 查询到 17 条规则执行相关审计记录；页面和接口可用 | 成功操作审计和查询已实现；登录失败审计缺陷必须同时披露 |
| 客户可通过名单筛查服务进行制裁/负面名单匹配与人工复核 | `backend/src/main/java/com/aml/platform/screening/`、`frontend/src/views/ScreeningView.vue` | `aml_screening_request`、`aml_screening_match`、`aml_screening_review` | Maven Mock 适配器与 Yente 适配测试；运行状态 Mock 可用、Yente 不可用 | Mock 筛查和复核流程可写为已实现；Yente 仅完成适配器与失败处理，不能写成真实名单查询成功 |
| OpenSanctions/Yente 外部服务不可用时系统仍可本地运行 | Screening Provider 抽象、Mock/Yente 适配器及配置 | 筛查请求、匹配与复核表 | 运行时 `YENTE REAL available=false`、`MOCK SIMULATED available=true`；其余核心 E2E 仍通过 | 可写为真实与模拟提供者明确区分且具备本地降级能力 |
| 系统计算并保存客户风险评分和交易风险评分历史 | `backend/src/main/java/com/aml/platform/risk/`、`frontend/src/views/RiskScoresView.vue` | `aml_customer_risk`、`aml_customer_risk_history`、`aml_transaction_risk`、`aml_transaction_risk_history` | Maven 风险测试；E2E 客户为 `MEDIUM`、交易为 `HIGH` | 风险评分及历史持久化已实现；评分属于教学规则结果，不应描述为生产监管模型 |
| 仪表盘展示客户风险分布、预警趋势、规则命中和案件统计 | `backend/src/main/java/com/aml/platform/dashboard/`、`frontend/src/views/DashboardView.vue` | 通过 JdbcTemplate 聚合客户、预警、命中、案件等真实表 | Maven Dashboard 查询测试；E2E 仪表盘客户数为 5 | 可写为后端实时数据库聚合，不是前端写死数据；跨时区日期边界存在潜在偏差，应列为已知问题 |
| 系统可导出客户、预警、规则命中和案件 CSV 报表 | Dashboard/Report 控制器与服务、前端报表入口 | 实时查询相关业务表；`aml_report*` 表当前未形成完整生命周期 | Maven 报表相关测试；E2E 下载 CSV 1341 字节 | 可写为实时 CSV 导出已验证；不可写为异步报表生成、归档或调度系统 |
| 数据库由 Flyway 管理并保持外键和索引约束 | `backend/src/main/resources/db/migration/V1__...sql` 至 `V6__...sql` | 34 个应用表、49 个外键、81 个索引，另有 Flyway 历史表 | 在独立临时 PostgreSQL 库上从零执行 V1—V6 成功，应用完成 schema validation | 可写为迁移可重复从零部署；数字应注明为本次审查环境实测 |
| Docker Compose 可提供数据库、后端和前端本地运行环境 | `compose.yaml`、`backend/Dockerfile`、`frontend/Dockerfile`、Nginx 配置 | PostgreSQL 持久卷 | `docker compose config --quiet` 通过；PostgreSQL 健康；后端、前端和 OpenAPI 均 HTTP 200 | 可写为容器化本地部署验证通过；Compose 尚未给后端和前端配置原生 healthcheck |
| 系统完成客户→账户→交易→规则→预警→案件→关闭闭环 | 各业务模块以及 `scripts/e2e-smoke.ps1` | 上述核心表及审计表 | 真实执行 E2E 全链路通过，生成的记录明确标记为 `SYNTHETIC_AND_SIMULATED` | 这是实验报告最强的验收证据；必须注明测试数据为合成/模拟数据，而非真实金融数据 |
| 系统提供用户与角色后台管理 | 当前仅有认证、安全和用户持久化内部代码，无对应管理 Controller/View | RBAC 五张表 | 未发现用户/角色管理 API、前端页面或端到端证据 | 只能描述为底层 RBAC 数据模型已存在；管理功能属于未实现 |
| 系统提供完整自动化前端测试 | 无前端测试目录或测试脚本；`frontend/package.json` 仅有 dev/build/preview | 不适用 | 仅完成 TypeScript/Vite 生产构建；无组件/E2E 浏览器测试 | 不得写为前端自动化测试已完成；浏览器自动化工具在本次环境中也不可用 |

## 推荐的实验报告证据引用顺序

1. 先引用 `technology-stack.md` 说明实际技术选型和运行环境。
2. 用 `database-inventory.md` 说明 Flyway 迁移、实体关系、外键和索引。
3. 用 `feature-verification-matrix.md` 区分“实现”“验证”“部分实现”和“未实现”。
4. 用 `api-inventory.md` 列出可操作接口，但不把接口存在等同于功能完整。
5. 用 `test-execution-report.md` 提供构建、迁移、HTTP 和端到端测试的实际命令与结果。
6. 在结论中同时引用 `known-issues.md`，主动披露登录失败事务、外部集成、前端测试和健康检查等边界。

## 截图与演示材料边界

- 最终归档阶段已使用本机 Edge 打开实际运行系统，生成 11 张页面截图，路径为 `latex/figures/screenshots/`；不存在伪造界面或真实金融数据。
- 截图覆盖登录、仪表盘、客户、账户、交易、规则、预警、案件、筛查、风险评分和审计，可作为论文与操作手册的视觉证据。
- 这些截图是一次性的人工验收证据，不等同于仓库内的前端自动化测试；AMLSim、Mock Screening 和 E2E 数据均应标注为“模拟/合成数据”。
