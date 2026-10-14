# Phase 3B 规则引擎与风险预警验证记录

验证日期：2026-10-14（Asia/Shanghai）

## 实现范围

- Drools 10.1.0 可执行模型，Java 21；规则由代码仓库内固定 DRL 提供，不接受用户提交任意 DRL。
- AML-R001 单笔大额、AML-R002 短时高频、AML-R003 时间窗口累计金额。
- 规则参数白名单校验、启停、不可变版本历史、执行记录、命中记录及命中交易关联。
- 基于稳定 `hit_key` 与数据库唯一约束的幂等命中/预警生成。
- 预警分页筛选、详情、关联交易、审核历史、状态转换及案件升级入口。
- AML-R004 至 AML-R008 标记为 `PLANNED`；复杂图/循环转账规则留待后续实现。

## 命令与结果

| 命令或检查 | 结果 |
|---|---|
| `mvn -B dependency:tree '-Dincludes=org.drools:*,org.kie:*'` | 成功；Drools/KIE 均为 10.1.0。首次未给 PowerShell 参数加引号导致通配符参数错误，修正后通过。 |
| `mvn -B -DskipTests compile` | 系统 Java 8/JRE 首次失败；显式设置项目 JDK 21 后成功，68 个源文件完成编译。 |
| `mvn -B '-Dtest=DroolsRuleEvaluatorTest,RuleParameterValidatorTest' test` | 4/4 通过。首次发现 `KieHelper` 默认经典构建路径缺少约束实现，改用 `ExecutableModelProject` 后通过。 |
| `mvn -B test`（项目 JDK 21） | 18/18 通过，0 failures，0 errors，0 skipped。 |
| `npm run build` | 成功；1708 modules transformed。首次发现两个模板回调隐式 `any`，修复后通过；保留主 chunk 警告。 |
| `docker compose --profile app build backend frontend` | 成功；容器内 Maven 18/18 测试和前端生产构建通过。 |
| `docker compose --profile app up -d`（宿主 18080/15173） | 三服务启动，PostgreSQL healthy。 |
| 后端日志 / `flyway_schema_history` | V3/V4 成功，schema version 4；V4 确保所有 `PLANNED` 规则处于停用状态。 |
| `GET :18080/actuator/health` / `GET :15173/` | 后端 `UP`；前端 HTTP 200。 |
| `GET /api/rules/compatibility` | Drools 10.1.0、Java 21.0.12.1、`COMPATIBLE`。 |
| 导入 `rule-window-demo.csv` | total=10，imported=10，duplicates=0，rejected=0。 |
| 首次执行 R002/R003（asOf `2026-10-14T04:30:00Z`） | processed=11，hits=2，alerts=2；R002 关联 10 笔，R003 累计 400000 CNY。 |
| 相同窗口重复执行 R002/R003 | hits=0，alerts=0，幂等通过。 |
| 创建 R001 v2 | 成功，`amountThreshold=110000`，v1 保留；携带额外 `drl` 字段返回 HTTP 400。 |
| 尝试启用 AML-R007 | HTTP 400；R004～R008 的 `PLANNED` 规则启用数为 0。 |
| 导入 `rule-large-demo.csv` 并执行 R001 v2 | 首次 hits=1、alerts=1；重复执行 hits=0、alerts=0。 |
| 预警查询与审核 | 客户筛选返回 3 条；R002 详情含 10 笔交易；完成 `NEW → IN_REVIEW → CLOSED`，历史由 1 条增至 3 条。 |
| 最终镜像复核 | `asOf` 移至 04:31 后，窗口纳入新增大额交易，R002/R003 合理产生 2 个新窗口命中；完全相同的 04:31 请求再次执行为 hits=0、alerts=0。当前测试客户共 5 条预警。 |

## 验证中发现并修复的问题

1. Drools 10 的 `drools-engine` 应明确使用可执行模型构建；默认经典构建路径会抛出 `DummyConstraintBuilder` 异常。
2. PostgreSQL JDBC 不能从未带 SQL 类型的 `Instant` 推断 `timestamptz`；JDBC 边界改用 `Timestamp`。
3. 预警关联交易的业务编号字段是 `external_id`，查询以 `transaction_no` 别名返回。
4. browser 技能所需的 JavaScript 浏览器执行接口在本会话中不可用，故无浏览器截图；以生产构建、容器 HTTP 200 和真实 API 闭环替代。

## 结论

Phase 3B 的规则配置、规则版本、三条交易监控规则、时间窗口统计、命中/预警幂等、预警查询和审核流程均已在自动化测试与真实 PostgreSQL/Docker 环境通过。复杂图规则未实现，数据库中保持 `PLANNED`，不会被启用或执行。
