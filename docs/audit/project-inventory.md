# 项目资产盘点

审查时间：2026-10-14（Asia/Shanghai）。审查根目录：`C:\Users\86152\Desktop\Cpp\java-aml-platform`。

## 仓库状态

- 这是 Git 仓库，当前分支 `master`，但尚无任何提交。
- `git ls-files` 为 0；`.env.example`、源码、文档、Compose 等全部显示为未跟踪。
- `.gitignore` 已排除 `.env`、`.tools/`、`backend/target/`、`frontend/node_modules/`、`frontend/dist/`、日志、证据目录和 TypeScript 增量文件。
- 工作区存在被忽略的 `.tools/`、构建产物和若干日志；没有发现 LICENSE/NOTICE 文件。

因此当前项目可以运行，但还不能称为已归档版本；首次提交前必须逐项确认纳入文件并形成基线标签。

## 规模与目录

| 资产 | 实际数量/说明 |
|---|---|
| Java 主源码 | 91 个文件 |
| Java 测试 | 18 个测试类，32 个测试用例 |
| Controller | 12 个 |
| Service | 21 个（含查询、导入、安全服务） |
| Spring Data Repository | 7 个 |
| JPA 实体 | 8 个业务实体；其他领域大量采用 `JdbcTemplate` |
| Vue 页面 | 11 个 View |
| OpenAPI | 48 个 path、53 个 operation |
| Flyway | V1–V6，34 张应用表 |
| 主要源码规模 | 后端、测试、前端、迁移合计约 133 文件/5275 行 |

```text
backend/       Spring Boot、Flyway、测试、Dockerfile
frontend/      Vue 3、Vite、Nginx、Dockerfile
integrations/  AMLSim CSV、Fineract/Yente 说明、证据样例
scripts/       可执行 HTTP E2E 脚本
docs/          设计、手册、历史验证记录和本次 audit
compose.yaml   PostgreSQL、后端、前端编排
```

## 后端构成

- 启动类：`backend/src/main/java/com/example/aml/AmlApplication.java`。
- 领域包：`auth`、`security`、`user`、`customer`、`account`、`transaction`、`rule`、`alert`、`casework`、`screening`、`risk`、`dashboard`、`audit`、`integration`、`common`。
- JPA 主要用于用户、客户、账户、交易和导入批次；规则、预警、案件、筛查、评分、统计、审计主要使用参数化 `JdbcTemplate`，并非缺少持久化实现。
- 统一配置：`application.yml`；dev profile 创建演示管理员；test profile 使用 H2 PostgreSQL 兼容模式。

## 前端构成

页面包括登录、仪表盘、客户、账户、交易、规则、预警、案件、名单筛查、风险评分和审计。路由使用 Vue Router，状态使用 Pinia，HTTP 使用 Axios，组件库为 Element Plus，图表为 ECharts。

没有独立的用户/角色、KYC 或报表任务页面；CSV 报表入口集成在仪表盘中。

## 数据与集成资产

- AMLSim：5 个 CSV/说明文件，可导入官方默认交易列；数据固定标记 `SIMULATED`。
- Fineract：只读储蓄账户流水适配器和说明；没有客户、贷款或增量同步。
- Yente：名单匹配适配器和说明；当前环境未启用真实服务。
- 证据：一个纯文本合成样例。
- E2E：`scripts/e2e-smoke.ps1` 会向开发库写入 `E2E-*` 合成记录并保留审计。

## 运行状态

审查时 PostgreSQL、backend、frontend 三容器均在运行；端口为 5432、8080、5173。后端和前端代理健康均为 `UP`，前端首页和 OpenAPI 均为 HTTP 200。
