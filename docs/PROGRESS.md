# 开发进度

2026-10-23 v1.1.3：CSV 逻辑记录解析、100 条分块提交、R001–R003 查询边界和规则 FAILED 执行记录已完成代码与定向验证。36 个非认证测试通过；完整 40 测试中的 4 个认证 Testcontainers 环境错误仍待处理。认证修复保留在工作区，未纳入本次提交。

## Phase 4C 外部项目核验与适配（2026-10-14）

- 已核验 IBM AMLSim、Apache Fineract、OpenSanctions/yente 官方仓库及许可证：AMLSim/Fineract 为 Apache-2.0，yente 软件为 MIT；OpenSanctions 数据许可与 yente 软件许可分开处理。
- 已实现 AMLSim 官方默认交易 CSV 导入，严格区分 `SIMULATED`，保留 SAR/alert 合成标签但不直接生成平台预警或监管报告。
- 已实现 Fineract 储蓄账户交易只读 API 适配器、Basic Auth、tenant、超时、字段映射和幂等导入；默认关闭，外部失败显式返回，不影响本地 CSV/AMLSim 流程。
- 已重新按当前官方 OpenAPI 核验 Yente `/match/{dataset}` 和 `ApiKey` 认证；真实 Yente 与 Mock 数据模式继续严格分离。
- 本机 `localhost:8000` 与 `localhost:8443` 均无上游服务，真实 Yente/Fineract 端到端状态明确记为“未验证”，未虚构成功。

## Phase 4B 统计仪表盘、报表与审计（2026-10-14）

- 已完成数据库实时摘要、客户风险分布、预警逐日趋势、规则命中、案件状态/优先级统计；支持上海自然日范围并在后端转换为 UTC 边界，空白日期补零但不伪造业务记录。
- 已完成客户风险、预警、规则命中和案件四类 UTF-8 BOM CSV；完成审计日志按操作者、动作、资源、结果、日期分页查询及筛选后 CSV，导出动作自身写审计。
- 权限严格分离：仪表盘/业务 CSV 要求 `REPORT_READ`，审计查询/审计 CSV 要求 `AUDIT_READ`；集成测试验证匿名 401、仅审计用户访问仪表盘 403 且可访问审计接口。
- Vue 仪表盘使用 ECharts 展示四类统计，审计页支持筛选、分页、导出；两页均实现加载、空数据、错误、重试和无权限状态，导航按登录权限过滤。
- 本地后端全量 25/25 测试通过；核心 HTTP 集成测试通过；前端生产构建 2307 modules transformed；Docker 镜像内 25/25 与前端构建再次通过。
- 在现有 PostgreSQL 16.10 实际数据上验证：2 客户、12 交易、5 预警、1 案件；30 日预警趋势合计 5，规则命中 R001=1/R002=2/R003=2，客户风险 CSV 2 行，筛选审计 CSV 4 行，健康检查 `UP`。
- 完整命令和结果见 `docs/deployment/phase4b-reporting-validation-2026-10-14.md`。

## Phase 4A 名单筛查与风险评分（2026-10-14）

- 已实现 `ScreeningProvider` 端口、Yente `/match/{dataset}` HTTP 适配器及显式 `SIMULATED` 的 Mock 适配器；Yente 支持 API key、阈值、超时、非 2xx/网络/畸形响应处理。
- 已实现筛查请求/候选查询、Provider 状态、人工复核状态约束、失败落库和操作审计。真实 Provider 失败不会静默降级为模拟或伪造“未命中”。
- 已实现客户与交易确定性风险评分、因素解释、等级、模型版本、当前快照、只追加历史以及 `NONE/REAL/SIMULATED/MIXED` 来源传播。
- 已完成 Vue 筛查复核页和风险评分页，页面明确显示真实/模拟来源和用途边界提示。
- Flyway V6 已在 PostgreSQL 16.10 成功执行，public schema 35 张表；后端全量测试 25/25、前端 1713 modules 构建、前后端 Docker 镜像构建均通过。
- 真实 HTTP 验证完成 MOCK 筛查→人工复核→客户/交易风险评分→历史查询；客户 59/MEDIUM、交易 92/HIGH，来源均为 `SIMULATED`，各有 2 条历史和完整审计。
- Yente 真实外部实例未配置，未声称实连；适配器契约和 503 路径由本地 HTTP stub 与服务集成测试验证。完整记录见 `docs/deployment/phase4a-screening-risk-validation-2026-10-14.md`。

当前 Flyway schema version 为 6，backend/frontend/postgres 容器运行于本次实际端口 8080/5173/5432，健康检查为 `UP`。后续可继续 KYC 编辑、监管报表、目标环境 Yente 实连和高级模型参数管理。

更新时间：2026-10-14（Asia/Shanghai）

## 已完成

- 已读取并遵循 `AGENTS.md`、`docs/PROJECT_PLAN.md`、`docs/PROGRESS.md`。
- 已核验工作区、Git、系统 Java/Maven、项目便携 JDK、Node.js/npm、Docker/Compose 与端口占用情况。
- 已完成 Spring Boot 3.5.7 / Java 21、Vue 3 / Vite、PostgreSQL 16、Flyway 和 Docker Compose 基础工程初始化与校验。
- 使用项目内 Microsoft OpenJDK 21.0.12.1 执行 `mvn -B clean package`：9/9 测试通过并生成可执行 JAR。
- 执行 `npm ci` 与 `npm run build`：前端生产构建通过（1705 modules transformed）。
- 启动真实 PostgreSQL 16.10，Flyway 从空库执行 V1 成功；`flyway_schema_history` 为 `1|1|initial schema|true`，public schema 共 32 张表。
- 修复 PostgreSQL `CHAR` 与 Hibernate `VARCHAR` 类型校验不一致；应用已在真实 PostgreSQL 上启动，健康端点为 `UP`，OpenAPI 为 JSON 200。
- 构建后端、前端 Docker 镜像，并使用 Compose 启动三服务完成端到端健康检查。
- 宿主端口支持环境变量覆盖；因本机 8080 已占用，本次验证使用后端 18080、前端 15173。
- 完整命令、结果和故障重试记录见 `docs/deployment/phase2-validation-2026-10-14.md`。
- 完成客户管理模块：独立 DTO/Service/Controller、分页与关键字查询、新增、编辑、停用、参数校验和审计。
- 完成账户管理模块：JPA 复合主键客户关系、按客户查询、新增、状态流转、所有权比例校验和审计。
- 完成模拟交易导入模块：导入批次、CSV 引号解析、文件/字段/金额/时间/币种/账户状态校验、幂等去重、逐行错误和模板下载。
- 完成 Vue 客户→账户→交易导航、筛选、表单校验、导入结果及错误展示。
- Flyway V2 已在 PostgreSQL 16.10 实际执行；真实 HTTP 关联闭环和重复导入验证通过。
- 后端自动化测试扩展为 14 个，全部通过；前端生产构建通过（1706 modules transformed）。
- 完整 Phase 3A 验证命令与结果见 `docs/deployment/phase3a-validation-2026-10-14.md`。
- 完成案件调查模块：预警事务升级案件、案件分页/详情与调查员筛选、案件分派、调查笔记、证据上传/下载/逻辑移除、SHA-256、状态机、关闭结论和审计日志。
- Flyway V5 已在 PostgreSQL 16.10 实际执行；证据目录使用独立 Docker volume，文件逻辑移除但物理内容按审计要求保留。
- 完成 Vue 案件调查工作台：筛选、详情、分派、状态操作、笔记、证据及历史时间线。
- 后端全量测试为 21/21 通过；前端生产构建为 1709 modules transformed；前后端 Docker 镜像构建通过。
- 真实 HTTP 与 SQL 验证 `TX-RULE-LARGE-001 → AML-R001 → ALT-6559532D-04F6 → CASE-1791956341822-9FB95427 → CLOSED`，证据下载哈希一致且 10 条案件审计动作完整。
- 完整 Phase 3C 命令、结果和中间重试见 `docs/deployment/phase3c-case-validation-2026-10-14.md`。

## 当前状态
- Phase 3B 规则引擎与风险预警已完成：Drools 10.1.0 可执行模型、R001/R002/R003、规则版本和参数白名单、时间窗口、幂等命中、预警分页/详情/审核页面。
- Flyway V3/V4 已在 PostgreSQL 16.10 应用；真实 HTTP 验证三类规则均可命中，完全相同窗口重复执行新增命中和预警均为 0；窗口纳入新交易时会生成新的确定性命中。
- Phase 3B 完成时后端自动化测试为 18/18 通过；加入案件模块后当前为 21/21 通过，前端生产构建与前后端 Docker 镜像构建通过。
- AML-R004～R008 和复杂图/循环转账算法保持 `PLANNED`，不会被规则执行器加载。
- Phase 3C 案件调查已完成；状态流转限定为 `OPEN → INVESTIGATING → PENDING_REVIEW → CLOSED`，复核可退回调查，关闭后禁止分派、笔记、证据变更和重新打开。
- Flyway 当前 schema version 为 6；本次重建后的 backend/frontend/postgres 三容器运行于 8080/5173/5432，健康检查为 `UP`。

- Phase 2 基础项目验证通过；Phase 3A 客户→账户→模拟交易模块验证通过。
- 复杂图规则、案件多预警手工并案、证据病毒扫描和对象存储未在本次范围内实现；Yente Provider 已实现但目标真实服务尚未配置实连。

## 下一步

- 后续可进入 KYC 编辑、监管报表、目标环境 Yente 实连和评分模型参数管理阶段。
- 可补充 PostgreSQL Testcontainers、证据病毒扫描/对象存储、案件多预警手工并案、前端 chunk 拆分及 CI 构建缓存优化；这些不影响 Phase 3C 验收。
## 全项目验收更新（2026-10-14）

- 代码检查和修复完成：CSV 公式注入防护、401 全量清理认证缓存、ECharts 按需加载/分包、可执行 PowerShell E2E。
- 容器 JDK 21 后端 32/32 测试通过；前端生产构建通过；官方 npm registry 生产依赖审计为 0 漏洞。
- 独立 PostgreSQL 空库从 Flyway V1 迁移到 V6，35 张基础表、49 外键、81 索引；应用 schema validate 启动成功，测试库已清理。
- 真实 Compose/HTTP 流程通过，最终案件 CLOSED、规则重复执行新增命中 0；backend/proxy 健康 UP，OpenAPI 200。
- 五类中文文档已按实际源码和本次结果重建。完整记录见 `docs/deployment/full-validation-2026-10-14.md`。
- 未完成和阻塞：R004–R008 等高级能力仍为规划项；Yente/Fineract 未实连；当前会话无浏览器自动化接口，未声称 UI 点击回归成功；宿主默认 Java 8，项目测试使用容器 JDK 21。

## 最终整理与归档准备（2026-10-15）

- 已基于实际源码生成项目说明书、系统操作手册、系统部署手册三套 LaTeX 源文件，并使用官方便携 Tectonic 0.17 编译为三份 PDF；中文、目录、截图和页面边界已渲染抽检。
- 已通过真实 Edge 浏览器与本地运行系统采集 11 张页面截图，覆盖登录、仪表盘、客户、账户、交易、规则、预警、案件、名单筛查、风险评分和审计。截图为模拟/合成数据，不包含真实金融数据；本项目仍没有可重复执行的前端自动化测试套件。
- 2026-10-15 最终回归时 Docker Desktop 一度因 WSL 数据盘仍附加而无法启动；仅执行进程重启与 `wsl --shutdown`，未重置或删除数据卷。恢复后 PostgreSQL healthy、后端健康 `UP`、OpenAPI 200、前端 200。
- 恢复后再次执行 `scripts/e2e-smoke.ps1`，结果 PASS：案件最终 `CLOSED`、首次规则命中 1、重复命中 0、客户风险 MEDIUM、交易风险 HIGH、CSV 1507 bytes。
- GitHub 远程归档尚未执行：当前没有 remote，且 `gh` 未安装；需用户确认 GitHub 账号、仓库名与公开性后再创建并推送。

## LaTeX 文档正式化与显示环境（2026-10-15）

- 三份 LaTeX 文档已改为统一的正式项目文档版式，系统性补充需求、架构、业务流程、数据、接口、安全、部署、运维、故障处理和能力边界，删除面向特定评审场景的措辞。
- 已安装并更新至 MiKTeX 26.5 和 LaTeX Workshop 10.19.0；VS Code 用户配置固定使用 XeLaTeX 双遍编译，MiKTeX 可执行目录已加入用户 PATH。
- 使用 XeLaTeX 真实重编译：项目说明书 26 页、系统操作手册 24 页、系统部署手册 18 页。三份均为 PDF 1.7，嵌入中文字体，文本替换字符为 0，编译日志无 overfull、未定义控制序列或致命错误。
- 已渲染抽检封面、目录、正文、表格、截图、代码块和末页，并修复部署手册的孤立段落与不合理分页。业务源码和数据库结构未改动。
- 已按最终版式要求将封面信息精简为日期，移除正文页脚版本号，将章号与章标题设置为同一行、同字号并整体居中；小标题具备页底空间保护，所有短表均保持在单页内。
