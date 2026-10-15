# 测试结果

## 最终交付复核（2026-10-15）

| 范围 | 命令/方式 | 结果 | 真实结论 |
|---|---|---|---|
| 后端完整回归 | 便携 Microsoft OpenJDK 21 下 `mvn -B clean verify` | 通过 | 32 tests，0 failures/errors/skipped；BUILD SUCCESS，43.052 秒 |
| 前端生产构建 | `npm run build` | 通过 | 2300 modules transformed，12.98 秒；ECharts 555.67 KB、Element Plus 917.22 KB 大块告警仍存在 |
| 前端生产依赖审计 | `npm audit --omit=dev` | 通过 | 0 vulnerabilities |
| LaTeX/PDF | MiKTeX 26.5 XeLaTeX 双遍编译、文本解析与页面渲染抽检 | 通过 | 项目说明书 26 页、操作手册 24 页、部署手册 18 页；PDF 1.7、中文字体嵌入、替换字符 0、无 overfull/致命编译错误 |
| Docker Compose 配置 | `docker compose config --quiet` | 通过 | 配置可解析 |
| Docker 恢复 | Docker Desktop 重启失败后执行 `wsl --shutdown` 再启动 | 通过 | 初次日志为 `WSL_E_USER_VHD_ALREADY_ATTACHED`；未重置数据、未删除卷，恢复后 Server 29.8.2 |
| 运行状态 | `docker compose --profile app up -d` 与 `ps` | 通过 | PostgreSQL 16.10 healthy，backend/frontend running |
| 健康/OpenAPI/前端 | `GET :8080/actuator/health`、`:8080/v3/api-docs`、`:5173` | 通过 | 后端 `UP`，OpenAPI 200，前端 200 |
| PostgreSQL HTTP E2E | `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\e2e-smoke.ps1` | 通过 | `result=PASS`；案件 `CLOSED`；首次规则命中 1、重复命中 0；CSV 1507 bytes |
| 页面截图 | Edge + 临时 Puppeteer 控制本地实际页面 | 通过（人工抽检） | 11 张真实运行截图；不是仓库内可重复执行的 UI 自动化测试 |
| 真实 Yente/Fineract | 未执行 | 未验证 | 目标服务未配置；仅适配器契约与失败路径已测试 |

最终 E2E 使用 `SYNTHETIC_AND_SIMULATED` 数据：customer `c217769a-9abb-4efb-8f0f-75ed1c53c644`、transaction `e8eefa23-8dfd-4229-82b9-e61d93cc56eb`、alert `d717a75c-5a81-4464-8b88-0a40b271be7a`、case `1d1ea811-11e6-45c9-980c-dcadd5b8d7c5`。这些标识只用于本次验证，不是固定产品数据。

LaTeX 复核补充：三份文档均以正式项目文档口径重写，清除了面向特定评审场景的措辞；抽查封面、目录、正文、表格、代码块、系统截图和末页均可正常渲染。Edge 无头模式不会加载内置 PDF 查看器，得到空白截图，不能用于判断文件有效性；实际显示入口改为 VS Code LaTeX Workshop/PDF 扩展，结构验证与渲染验证由 PyMuPDF 完成。

版式复核补充：封面仅保留日期，正文页脚已移除版本文字；章号和章标题在同一行，均为 26 pt，并作为整体水平居中。章节小标题设置页底空间保护，自动检测未发现孤立标题；短表采用“标题与表格整体预留空间”策略，交付版中的表格均未跨页拆分。

## 全项目最终回归（2026-10-14）

| 范围 | 结果 | 真实结论 |
|---|---|---|
| 后端 JDK 21 | 通过 | 32 tests，0 failures/errors/skipped，JAR/镜像成功 |
| 前端生产构建 | 通过 | vue-tsc + Vite，2300 modules transformed |
| 生产依赖审计 | 通过 | npm 官方 registry：0 vulnerabilities |
| Flyway 空库 | 通过 | V1–V6；35 张基础表、49 外键、81 索引；测试库已删除 |
| Compose/健康/OpenAPI | 通过 | backend/proxy `UP`，前端 200，OpenAPI 200/39136 bytes |
| PostgreSQL HTTP E2E | 通过 | `result=PASS`，案件 `CLOSED`，规则二次命中 0，CSV 1175 bytes |
| 浏览器点击回归 | 阻塞 | 会话无页面浏览器自动化接口，不冒充通过 |
| Yente/Fineract 目标实连 | 未执行 | 目标服务未配置；只通过适配器契约和失败路径测试 |

完整命令、失败重试、修复和 E2E 标识见 `docs/deployment/full-validation-2026-10-14.md`。

## Phase 4C 外部适配增量结果（2026-10-14）

| 范围 | 命令/场景 | 结果 | 说明 |
|---|---|---|---|
| 官方仓库 | 三次 `git ls-remote` | 通过 | 记录 AMLSim master、Fineract develop、yente main 的实际提交 |
| 适配器定向测试 | `mvn -B -Dtest=AmlSimImportServiceTest,FineractApiAdapterTest,FineractImportServiceTest,YenteScreeningProviderTest test` | 通过 | 8 tests，0 failures/errors/skipped；验证官方字段、认证头、503/禁用失败和导入映射 |
| 后端全量回归 | `mvn -B test` | 通过 | 30 tests，0 failures/errors/skipped |
| 核心 HTTP 集成复核 | `mvn -B -Dtest=CoreWorkflowIntegrationTest test` | 通过 | 新增状态接口、Fineract 502、AMLSim HTTP 导入/幂等断言后通过 |
| 前端构建 | `npm run build` | 通过 | 2307 modules；保留既有大 chunk 警告 |
| Docker 镜像 | `docker compose --profile app build backend frontend` | 通过 | 镜像内后端 30/30，前端构建通过 |
| PostgreSQL AMLSim | 首次与重复导入官方列示例 | 通过 | 首次写入 1 条 `AMLSIM` 合成交易；重复导入 imported=0/duplicates=1 |
| Fineract 默认关闭 | 状态查询与导入调用 | 通过 | available=false；调用返回 HTTP 502，不生成假交易 |
| 本机 Yente | `curl ... localhost:8000/healthz` | 未运行 | exit 7 / HTTP 000，不标记真实集成成功 |
| 本机 Fineract | `curl -k ... localhost:8443/.../health` | 未运行 | exit 7 / HTTP 000，不标记真实集成成功 |

详情见 `docs/deployment/phase4c-external-integration-validation-2026-10-14.md`。

## Phase 4B 仪表盘、CSV 与审计增量结果（2026-10-14）

| 范围 | 命令/场景 | 结果 | 说明 |
|---|---|---|---|
| 核心 HTTP 集成 | `mvn -B -Dtest=CoreWorkflowIntegrationTest test` | 通过 | 聚合、风险分布、规则/案件统计、业务/审计 CSV、分页筛选、401/403 权限 |
| 后端全量测试 | JDK 21 下 `mvn -B test` | 通过 | 25 tests，0 failures/errors/skipped；显式 Byte Buddy agent 消除 Mockito 偶发自附加失败 |
| 前端生产构建 | `npm run build` | 通过 | 2307 modules transformed；类型检查通过，保留 ECharts 与主 chunk 警告 |
| Docker 镜像 | `docker compose --profile app build backend frontend` | 通过 | 镜像内后端 25/25 和前端构建再次通过 |
| PostgreSQL 实际查询 | `/api/dashboard?from=2026-09-15&to=2026-10-14` | 通过 | 2 客户、12 交易、5 预警、1 案件；R001/2/3 命中 1/2/2 |
| 业务 CSV | `/api/reports/CUSTOMER_RISK/csv` | 通过 | HTTP 200，`X-Report-Rows=2`，UTF-8 CSV |
| 审计筛选/CSV | `action=CALCULATE` | 通过 | 分页 total=4；CSV HTTP 200，`X-Report-Rows=4` |
| 页面/代理 | `/dashboard`、Nginx 代理健康 | 通过 | 前端路由 HTTP 200，代理后端健康 `UP` |

详情见 `docs/deployment/phase4b-reporting-validation-2026-10-14.md`。

## Phase 4A 名单筛查与风险评分增量结果（2026-10-14）

| 范围 | 命令/场景 | 结果 | 说明 |
|---|---|---|---|
| 后端全量测试 | JDK 21 下 `mvn -B test` | 通过 | 25 tests，0 failures/errors/skipped |
| Yente 契约 | `YenteScreeningProviderTest` | 通过 | 成功请求、ApiKey、REAL 标记、503、禁用状态 |
| 外部失败持久化 | `ScreeningServiceTest` | 通过 | PENDING→FAILED、错误信息、失败审计、requestId |
| 核心 HTTP 集成 | `CoreWorkflowIntegrationTest` | 通过 | 筛查复核、两类评分与历史均纳入 AML 闭环 |
| 前端生产构建 | `npm run build` | 通过 | 1713 modules transformed；保留大 chunk 警告 |
| Docker 镜像 | `docker compose --profile app build backend frontend` | 通过 | 镜像内后端 25/25 与前端构建再次通过 |
| Flyway V6 | PostgreSQL 16.10 | 通过 | `screening and risk scoring`，schema version 6，35 张表 |
| 运行健康 | `GET :8080/actuator/health` | 通过 | `UP` |
| Provider 区分 | `/api/screenings/providers` | 通过 | MOCK=SIMULATED 可用默认；YENTE=REAL 未配置不可用 |
| 筛查与复核 | MOCK 请求→`CONFIRMED_MATCH` | 通过 | 请求/候选均为 SIMULATED，1 条人工复核和审计 |
| 客户评分历史 | 同一客户计算两次 | 通过 | 59/MEDIUM，CUSTOMER_RULE_V1，SIMULATED，历史 2 条 |
| 交易评分历史 | 同一交易计算两次 | 通过 | 92/HIGH，TRANSACTION_RULE_V1，SIMULATED，历史 2 条 |
| 禁用真实服务 | 显式选择 YENTE | 通过 | HTTP 400，不发外部请求、不切换 Mock |

完整命令、运行标识、失败重试及验收边界见 `docs/deployment/phase4a-screening-risk-validation-2026-10-14.md`。

## Phase 3C 案件调查增量结果（2026-10-14）

| 范围 | 命令/场景 | 结果 | 说明 |
|---|---|---|---|
| 案件定向测试 | `mvn -B '-Dtest=CaseServiceTest,CaseEvidenceServiceTest,CoreWorkflowIntegrationTest' test` | 通过 | 5 tests，状态约束、证据和完整闭环通过 |
| 后端全量测试 | JDK 21 下 `mvn -B test` | 通过 | 21 tests，0 failures，0 errors，0 skipped |
| 前端生产构建 | `npm run build` | 通过 | Vue 类型检查通过；1709 modules transformed |
| Docker 镜像 | `docker compose --profile app build backend frontend` | 通过 | 容器内后端 21/21 测试；两镜像构建成功 |
| Flyway V5 | 真实 PostgreSQL 16.10 | 通过 | `case investigation` success=true，schema version 5 |
| 健康检查 | backend 18080 / frontend 15173 | 通过 | 后端及代理健康 `UP`，前端首页和 `/cases` HTTP 200 |
| 完整 AML 闭环 | `TX-RULE-LARGE-001 → AML-R001 → ALT-6559532D-04F6 → CASE-1791956341822-9FB95427 → CLOSED` | 通过 | 大额 150000 CNY；预警 `ESCALATED`；案件正常关闭 |
| 案件状态机 | 分派、调查、复核、关闭及关闭后写入 | 通过 | 4 条状态历史；关闭后添加笔记返回 HTTP 400 |
| 证据管理 | 上传、下载、SHA-256、逻辑移除、再次上传 | 通过 | 下载哈希一致；被移除证据保留移除元数据且不再出现在活动清单 |
| 审计日志 | SQL/API 查询案件资源 | 通过 | CREATE、ASSIGN、ADD_NOTE、3 次 TRANSITION、2 次 UPLOAD、DOWNLOAD、REMOVE 共 10 条 |

完整命令、中间失败和重试结果见 `docs/deployment/phase3c-case-validation-2026-10-14.md`。

## Phase 3B 增量结果（2026-10-14）

| 范围 | 命令/场景 | 结果 | 说明 |
|---|---|---|---|
| Drools 依赖 | `mvn dependency:tree`（限定 org.drools/org.kie） | 通过 | 全部为 10.1.0；Java 21 满足 JDK 17+ 要求 |
| Drools/参数测试 | `DroolsRuleEvaluatorTest`、`RuleParameterValidatorTest` | 通过 | 三条规则边界命中、低于阈值不命中、未知字段/规则拒绝 |
| 后端全量测试 | `mvn -B test` | 通过 | 18 tests，0 failures，0 errors，0 skipped |
| 前端生产构建 | `npm run build` | 通过 | 1708 modules transformed；保留主 chunk 警告 |
| Flyway V3/V4 | 真实 PostgreSQL 16.10 | 通过 | 新增规则实现状态、窗口字段和命中交易关联表；PLANNED 规则统一停用 |
| R002/R003 | 固定 asOf 窗口执行两次 | 通过 | 首次 2 hits/2 alerts，重复 0/0 |
| R001 v2 | 大额交易执行两次 | 通过 | 首次 1 hit/1 alert，重复 0/0 |
| 规则版本校验 | 新建 v2 / 提交额外 `drl` 字段 | 通过 | v2 激活且 v1 保留；非法字段 HTTP 400 |
| 预警审核 | 查询详情并转换状态 | 通过 | 10 笔关联交易；`NEW → IN_REVIEW → CLOSED`；3 条历史 |
| Docker/健康 | Compose 构建与启动 | 通过 | 后端健康 `UP`，前端首页 HTTP 200 |

完整命令及中间失败修复记录见 `docs/deployment/phase3b-rule-alert-validation-2026-10-14.md`。

更新时间：2026-10-14（Asia/Shanghai）

| 范围 | 命令 | 结果 | 说明 |
|---|---|---|---|
| 环境 | `git --version` | 通过 | 2.53.0.windows.1 |
| 环境 | `java -version` / `javac -version` | 环境限制 | 系统仅 Oracle JRE 8 且无 javac；未修改系统环境 |
| Java 21 | `.tools/.../java -version`、`javac -version` | 通过 | Microsoft OpenJDK 21.0.12.1 |
| 环境 | `mvn -version` | 通过 | Maven 3.9.16；实际构建显式使用便携 JDK 21 |
| 环境 | `node --version` / `npm --version` | 通过 | v24.15.0 / 11.12.1 |
| Docker | `docker version` / `docker compose version` | 通过 | Client/Server 29.8.2；Compose v5.6.0 |
| Compose 配置 | `docker compose --profile app config --quiet` | 通过 | PostgreSQL、backend、frontend 配置可解析 |
| PostgreSQL | `docker compose up -d postgres`、`pg_isready` | 通过 | PostgreSQL 16.10，容器 healthy，接受连接 |
| Flyway | 查询 `flyway_schema_history` | 通过 | `1|1|initial schema|true` |
| 数据库结构 | 查询 `pg_tables` | 通过 | public schema 32 张表 |
| 后端本机构建 | `mvn -B clean package`（JDK 21） | 通过 | 9 tests，0 failures，0 errors，0 skipped；JAR 生成成功 |
| 前端依赖 | `npm ci` | 通过 | lockfile 安装完成 |
| 前端生产构建 | `npm run build` | 通过 | 1705 modules transformed；存在约 1.09 MB 主 chunk 警告 |
| 真实 PG 启动 | `java -jar ...` + PostgreSQL | 失败后修复 | 修复 3 个定长字符字段的 Hibernate JDBC 类型映射后通过 |
| Spring 健康 | `GET :18080/actuator/health` | 通过 | `{"status":"UP"}` |
| OpenAPI | `GET :18080/v3/api-docs` | 通过 | HTTP 200，`application/json`，14092 bytes |
| 前端镜像 | `docker compose --profile app build frontend` | 通过 | `java-aml-platform-frontend:latest` |
| 后端镜像 | `docker compose --profile app build backend` | 通过（重试后） | 容器构建内 9/9 测试通过；首次依赖下载中断，缓存重试成功 |
| Compose 整栈 | `docker compose --profile app up -d --no-build` | 通过 | 三服务启动；后端 18080、前端 15173 |
| 容器后端健康 | `GET :18080/actuator/health` | 通过 | `UP` |
| Nginx 代理健康 | `GET :15173/actuator/health` | 通过 | `UP` |
| 前端页面 | `GET :15173/` | 通过 | HTTP 200 |
| Compose 内 Flyway | backend 日志与 SQL 查询 | 通过 | V1、V2 均 success=true，当前 schema version 2 |
| 客户 Service | `CustomerServiceTest` | 通过 | 重复编号、字段标准化与审计调用 |
| 账户 Service | `AccountServiceTest` | 通过 | 停用客户禁止开户、关闭账户禁止重新启用 |
| CSV 导入 | `TransactionImportServiceTest` | 通过 | 非法表头、带逗号及转义引号解析 |
| 后端完整测试 | `mvn -B clean package` | 通过 | 14 tests，0 failures，0 errors，0 skipped；可执行 JAR 已生成 |
| 前端模块构建 | `npm run build` | 通过 | 1706 modules transformed；客户/账户/交易页面类型检查通过 |
| Flyway V2 | 真实 PostgreSQL 启动 | 通过 | schema 从 v1 升至 v2，`customer account transaction modules` success=true |
| Hibernate 校验 | 真实 PostgreSQL 启动 | 通过 | 7 个 JPA Repository 初始化，schema validation 通过 |
| 关联 HTTP 闭环 | 客户→账户→CSV 交易 | 通过 | 创建客户、开户、导入 1 笔、按客户查账户、按账户查交易均通过 |
| 导入幂等 | 同一 CSV 再次上传 | 通过 | imported=0，duplicates=1，rejected=0 |
| 请求参数校验 | 非法客户 JSON | 通过 | HTTP 400，返回 customerNo/countryCode/fullName 字段错误 |
| Docker 镜像构建 | `docker compose --profile app build backend frontend` | 通过 | 容器内后端 14/14 测试通过，前后端镜像已更新 |
| Compose 模块版本 | `docker compose --profile app up -d --no-build` | 通过 | 直连/代理健康为 UP，前端首页 HTTP 200 |

## 验收结论

Phase 2 基础工程和 Phase 3A 客户→账户→模拟交易模块均已通过真实构建、自动化测试、PostgreSQL/Flyway 和 HTTP 闭环验证。系统 Java 8、前端大 chunk 警告和偶发外部依赖下载中断均已记录，不影响当前验收。详细命令见 `docs/deployment/phase2-validation-2026-10-14.md` 与 `docs/deployment/phase3a-validation-2026-10-14.md`。
