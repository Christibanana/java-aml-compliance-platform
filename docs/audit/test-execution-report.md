# 测试执行报告

审查日期：2026-10-14；最终归档复核：2026-10-15。所有结果来自真实命令输出；最终阶段截图来自实际运行页面。

2026-10-23 文档修订说明：以下 32 个测试和运行数字仅属于 2026-10-15 历史基线。当前工作区新增的认证 PostgreSQL 测试尚未执行完成；首次 Maven 运行选错根目录，后续定向运行在 Testcontainers 依赖下载阶段被中断，没有可报告的通过数量。文档更新不改变既有验收结果。

## 执行结果

| 范围 | 命令/方式 | 结果 |
|---|---|---|
| 宿主 Java | `java -version`、`javac -version`、`mvn -version` | Java 8u391；无 javac；Maven 绑定 Java 8，不能用于 Java 21 项目 |
| 后端 | JDK 21 Maven 容器执行 `mvn -B clean verify` | BUILD SUCCESS；91 主源码、18 测试源码；32 tests，0 failure/error/skipped；3 分 20 秒 |
| 前端 | `npm run build` | 成功；vue-tsc + Vite；2300 modules；15.70 秒 |
| 前端依赖 | `npm audit --omit=dev --registry=https://registry.npmjs.org` | `found 0 vulnerabilities` |
| Compose | `docker compose config --quiet` | 通过 |
| 运行状态 | `docker compose --profile app ps` | PostgreSQL healthy；backend/frontend running |
| 健康与静态页 | 后端/代理 health、前端首页、OpenAPI | `UP/UP/200/200`；OpenAPI 39136 bytes |
| 空库 Flyway | 独立 `aml_audit_verify_20261014_2230` 启动应用 | V1–V6 全成功；34 应用表、49 外键、81 索引；测试库和容器已删除 |
| 核心 E2E | `scripts/e2e-smoke.ps1` | PASS；客户→账户→交易→筛查→规则→预警→案件→关闭→统计/审计/CSV |
| AMLSim 运行端点 | 上传现有官方列示例 | HTTP 成功；total=1、imported=0、duplicates=1，证明端点、解析和去重可运行 |
| 外部状态 | integrations/providers API | Local/AMLSim/Mock 可用；Fineract/Yente 不可用 |
| 浏览器 UI | Edge + 临时 Puppeteer 控制 localhost | 采集 11 张实际页面截图并人工抽检；仓库无可重复执行的 UI 测试套件 |
| LaTeX | 官方便携 Tectonic 0.17 | 三套 PDF 编译成功并完成文本/页面渲染抽检；宿主仍无 xelatex/latexmk |

## 2026-10-15 最终运行复核

- Docker Desktop 初次因 `WSL_E_USER_VHD_ALREADY_ATTACHED` 无法启动；执行 `wsl --shutdown` 后恢复，未重置或删除数据卷。
- `docker compose --profile app up -d` 成功；PostgreSQL healthy，后端 health 为 `UP`，OpenAPI 200，前端 200。
- `scripts/e2e-smoke.ps1` 再次 PASS：case `1d1ea811-11e6-45c9-980c-dcadd5b8d7c5` 最终 `CLOSED`，重复规则命中 0，CSV 1507 bytes。

## E2E 实际输出摘要

- `result=PASS`，数据性质 `SYNTHETIC_AND_SIMULATED`。
- customer `4c7e9038-5454-4870-9778-382326340fbd`。
- transaction `97a8231b-966a-432f-8a87-0e7b48871eba`。
- alert `04b7e5e3-8e9f-46ff-9bc9-3f00f3599825`。
- case `96b18d1e-c373-47b8-bc94-e14e928cbb3f`，最终状态 `CLOSED`。
- R001 首次新增命中 1，第二次新增命中 0；客户风险 MEDIUM，交易风险 HIGH；CSV 1341 bytes。

E2E 会保留合成业务和审计数据。执行前运行库为 4 客户、4 账户、15 交易、7 预警、3 案件、115 审计；不要在论文中把数量写成固定产品指标。

## 自动化覆盖说明

32 个用例覆盖 JWT、客户、账户、CSV、AMLSim、Fineract 契约、Drools、规则参数、预警非法流转、案件状态机、证据、Mock/Yente、筛查失败、CSV 安全、应用健康和核心 MockMvc 闭环。核心集成使用 H2 PostgreSQL 模式并手工建立部分表，因此不能替代真实 PostgreSQL；本次另以独立 PostgreSQL 空库迁移和运行 E2E 补充。

## 真实失败/告警

- 编译测试源码报告一处 deprecated API 和一处 unchecked 操作。
- Spring 报告直接序列化 `PageImpl` 的 JSON 结构未来不稳定。
- Vite 报告 ECharts 555.67 KB、Element Plus 917.22 KB 块超过 500 KB。
- 错误密码专项测试返回 HTTP 400；`failed_attempts` 前后均为 0，失败登录审计前后均为 0，确认锁定与失败审计缺陷。
