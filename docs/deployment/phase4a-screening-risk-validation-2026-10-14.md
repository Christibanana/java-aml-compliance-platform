# Phase 4A 名单筛查与风险评分验证记录（2026-10-14）

## 范围和数据声明

本次实现 `ScreeningProvider`、Yente API 适配器、显式模拟适配器、候选人工复核、客户/交易风险评分及评分历史。运行态业务验证只使用合成数据；未连接真实名单或客户数据。Yente 默认禁用，`REAL` 与 `SIMULATED` 不允许静默互换。

## 执行命令与结果

| 命令/场景 | 结果 |
|---|---|
| `mvn -B test`（项目 JDK 21） | 通过：25 tests，0 failures，0 errors，0 skipped |
| `npm run build` | 通过：1713 modules transformed；保留约 1.09 MB 主 chunk 警告 |
| `docker compose --profile app build backend frontend` | 通过；镜像内再次执行后端 25/25 测试和前端生产构建 |
| `docker compose --profile app up -d --force-recreate postgres backend frontend` | 通过；PostgreSQL、backend、frontend 均启动 |
| backend 日志与 `flyway_schema_history` | PostgreSQL 16.10 上 V5→V6 成功，6 个迁移全部 `success=true` |
| `GET /actuator/health` | `{"status":"UP"}` |
| 登录后 `GET /api/screenings/providers` | MOCK=`SIMULATED/available/default`；YENTE=`REAL/unavailable` |
| MOCK 筛查与人工复核 | 请求 `ce11ef2c-a1bf-470f-aa72-a1bd40201f32` 完成，1 个模拟候选，复核为 `CONFIRMED_MATCH` |
| 客户风险连续计算两次 | 59.00 / MEDIUM / `CUSTOMER_RULE_V1` / `SIMULATED`，历史 2 条 |
| 交易风险连续计算两次 | 92.00 / HIGH / `TRANSACTION_RULE_V1` / `SIMULATED`，历史 2 条 |
| 显式选择禁用的 YENTE | HTTP 400；未调用外部服务，也未切换 Mock |
| PostgreSQL 核对 | public 35 张表；筛查请求/候选均为 `SIMULATED`；复核、四次评分均有审计 |

运行端口由本次 Shell 中实际生效的 Compose 默认值确定：backend `8080`、frontend `5173`、PostgreSQL `5432`。此前文档使用的覆盖变量名是 `*_HOST_PORT`；本次最初设置了错误的变量名 `AML_BACKEND_PORT/AML_FRONTEND_PORT`，因此未改变宿主端口，功能验证仍在实际映射端口完成。

## 自动化覆盖

- `YenteScreeningProviderTest` 使用 JDK `HttpServer` 验证 `/match/{dataset}`、`ApiKey`、查询体、阈值响应解析、503 和禁用状态。
- `ScreeningServiceTest` 验证外部异常后请求从 `PENDING` 落为 `FAILED`、保存错误并写失败审计，API 异常可携带 requestId。
- `CoreWorkflowIntegrationTest` 覆盖客户→模拟筛查→人工复核→账户→交易→规则/预警→客户/交易评分及历史→案件关闭。
- Mock 测试确认合成数据标签、模拟数据集和确定性行为。

## 中间失败与修复

首次定向测试启动 Spring 上下文失败：`YenteScreeningProvider` 存在两个构造器，Spring 无法选择。为配置构造器增加 `@Autowired` 后，定向测试及全量 25 个测试全部通过。首次前端构建发现 `RiskScoresView` 表格回调参数存在隐式 `any`，补充显式类型后通过。

## 验收边界

Yente 契约依据当前官方 `/match/{dataset}` 接口实现，已由本地 stub 验证，但本机没有可用的真实 Yente 实例/API key，因此没有声称真实名单实连成功。外部失败不会变成“未命中”；真实服务上线前还应在目标部署环境执行一次受控契约/连通性测试。评分仅用于教学演示和调查优先级，不构成法律、制裁或监管结论。
