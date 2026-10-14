# Phase 4B 统计仪表盘、CSV 与审计验证记录（2026-10-14）

## 实现范围

本阶段实现 AML 摘要、客户风险分布、预警趋势、规则命中、案件状态/优先级、四类业务 CSV、审计条件查询/分页/CSV，以及前端加载、空数据、错误重试和权限状态。所有数值由后端 SQL 查询当前 PostgreSQL/H2 测试库产生，前端没有硬编码统计值。

## 命令和结果

| 命令/场景 | 结果 |
|---|---|
| `mvn -B -DskipTests compile` | 首次因 JdbcTemplate lambda 重载歧义失败；改为无返回值回调后通过 |
| `npm run build` | 通过，2307 modules transformed；保留 ECharts/主 chunk 大小警告 |
| `mvn -B -Dtest=CoreWorkflowIntegrationTest test` | 首次断言错误（资料 HIGH、当前评分 MEDIUM）；修正后通过 |
| `mvn -B test` | 首次遭遇 Windows Mockito 动态自附加失败；显式配置 Byte Buddy agent 后 25/25 通过 |
| `docker compose --profile app build backend frontend` | 通过；镜像内后端 25/25，前端 2307 modules 构建通过 |
| `docker compose --profile app up -d --force-recreate backend frontend` | 通过；backend 8080、frontend 5173、PostgreSQL 5432 |
| `GET /actuator/health` | `UP` |
| `GET /api/dashboard?from=2026-09-15&to=2026-10-14` | HTTP 200；2 客户、12 交易、5 预警、1 案件，预警趋势合计 5 |
| 规则命中统计 | R001=1、R002=2、R003=2，其余计划规则为 0 |
| 案件统计 | CLOSED=1、HIGH 优先级=1 |
| 客户风险 CSV | HTTP 200，`X-Report-Rows=2`，326 bytes |
| 审计查询/CSV（`action=CALCULATE`） | 分页 total=4；CSV HTTP 200，`X-Report-Rows=4` |
| `GET http://localhost:5173/dashboard` | HTTP 200 |

## 权限与边界

- 仪表盘与业务 CSV 后端要求 `REPORT_READ`；审计列表与审计 CSV 要求 `AUDIT_READ`。
- 集成测试验证匿名仪表盘请求返回 401；只有 `AUDIT_READ` 的测试用户访问仪表盘返回 403，但可读取审计列表。
- 默认周期为最近 30 个上海自然日，最大 366 天；转换为 UTC 半开区间查询。预警趋势缺失日期只补 0 以保持时间轴连续。
- 业务 CSV 和审计 CSV 均使用 UTF-8 BOM、RFC Content-Disposition 文件名，并返回实际数据行数。审计导出限制 10000 行；更大数据量应改为异步任务。
- 当前 CSV 是内部教学报表，不是监管报送文件，不会自动外发。

## 最终运行复核

| 命令/检查 | 结果 |
|---|---|
| `docker compose ps --format json` | backend、frontend、postgres 均为 running；PostgreSQL 为 healthy |
| `docker compose logs --tail 40 backend` | Spring Boot 正常启动，Flyway 成功校验 6 个迁移，schema 版本为 6，无启动或查询异常 |
| `GET /actuator/health` | `UP` |
| 无效令牌访问仪表盘/审计接口 | 均返回 HTTP 401 |
| 浏览器自动化能力检查 | 当前 Codex 会话未暴露 browser skill 所需 JavaScript REPL；因此采用生产构建、Nginx 路由 HTTP 200、真实 API 和集成测试作为验收证据，未伪造浏览器截图 |
| `JAVA_HOME=..\.tools\microsoft-jdk-21.0.12.1; mvn -B test` | 失败：该历史路径不存在，未进入 Maven；改用仓库实际 JDK 路径后重试 |
| `JAVA_HOME=..\.tools\jdk21\jdk-21.0.12.1+1; mvn -B test` | 最终全量复核通过：25 tests，0 failures，0 errors，0 skipped，`BUILD SUCCESS` |
