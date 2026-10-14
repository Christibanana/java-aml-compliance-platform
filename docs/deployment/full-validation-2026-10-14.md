# 全项目代码检查与验收记录（2026-10-14）

## 环境

工作区 `C:\Users\86152\Desktop\Cpp\java-aml-platform`；Windows 11 / PowerShell；Docker Engine 29.8.2；Node.js 24.15.0；npm 11.12.1。宿主默认 Java 是 Oracle JRE 8 且没有 `javac`，容器构建/运行使用 Eclipse Temurin 21.0.12.1。

## 执行命令与真实结果

| 命令/检查 | 结果 |
|---|---|
| `git status --short` | 仓库内容当前均为未跟踪文件；未覆盖或删除用户文件 |
| `java -version; javac -version; mvn -version` | 宿主 Java 8；`javac` 不存在；Maven 3.9.16 绑定 Java 8 |
| 宿主 `mvn -B verify` | 失败；Surefire 在运行测试前报 class file 61 与 Java 8 不兼容，Tests run=0；判定为环境问题 |
| `docker compose build --progress plain backend` | 通过；容器 JDK 21 编译 91 个主源码、18 个测试源码；32 tests，0 failures/errors/skipped；可执行 JAR 与镜像成功 |
| `npm run build` | 通过；Vue TypeScript 检查和 Vite 构建成功，2300 modules transformed |
| `npm audit --omit=dev` | 首次失败；npmmirror 审计端点返回 404/NOT_IMPLEMENTED，不据此判断安全性 |
| `npm audit --omit=dev --registry=https://registry.npmjs.org` | 通过；`found 0 vulnerabilities` |
| `maven-dependency-plugin:3.8.1:analyze`（JDK 21） | BUILD SUCCESS；报告的“未声明/未使用”主要来自 Spring Boot/Drools starter 与其传递依赖，不能机械删除；保留 starter 管理 |
| `docker compose config --quiet` | 通过 |
| 独立空库应用启动 | 通过；Flyway V1–V6 全部 success，Hibernate validate 后应用启动 |
| 空库结构统计 | 35 张基础表（含 Flyway）、49 外键、81 索引 |
| 空库清理 | 已删除精确容器 `java-aml-migration-verify-20261014` 和数据库 `aml_migration_verify_20261014_2200`，现有开发库未受影响 |
| `docker compose --profile app up -d --build` | 通过；PostgreSQL healthy，backend/frontend 正常运行 |
| `GET :8080/actuator/health` | 200，`UP` |
| `GET :5173/actuator/health` | 200，`UP`（Nginx 代理） |
| `GET :8080/v3/api-docs` | 200，39136 bytes |
| `GET :5173/` | 200，383 bytes |
| `scripts/e2e-smoke.ps1` | 通过；完整结果见下节 |
| `rg` 扫描 TODO/FIXME/System.out/printStackTrace/ddl create-update | 主源码与前端源码无匹配 |

后端编译仍报告测试源码中的一处 deprecated API 和一处 unchecked 操作；Spring 集成测试提示 `PageImpl` 直接序列化将来可能不稳定。它们不影响本次通过，但已列入技术债务。

依赖分析第一次使用未指定插件坐标的新容器，Maven 为解析 `dependency` 前缀枚举并下载大量插件元数据，人工中止；随后挂载现有 Maven 缓存并使用完整插件坐标重跑，在 1 分 34 秒后成功。报告把 starter 声明为“未使用”、把其内部模块列为“已使用但未声明”，属于该分析器对聚合 starter 的常见结果，因此没有将 Spring/Drools 管理的传递依赖全部扁平化到 POM。

## 修复内容

1. CSV 导出原先只处理引号，业务字段或审计详情以 `= + - @` 开头时可能被电子表格当作公式。新增 `CsvCell` 统一转义与公式中和，并新增 2 个安全测试；业务报表和审计导出均使用它。
2. Axios 收到 401 原先只清除 token，残留姓名、角色和权限。现同时清理四项登录缓存。
3. 仪表盘原先全量导入 ECharts，页面块约 1.13 MB。改为 core/charts/components/renderers 按需注册，并拆分 Vue、Axios、图表、Element Plus vendor；Dashboard 页面 JS 降到约 7.4 KB，图表块约 555.7 KB。
4. 增加可重复执行的真实 HTTP E2E 脚本；修复 Windows PowerShell 5.1 对 UTF-8 无 BOM 和 `Invoke-WebRequest` 下载的兼容问题，改用 ASCII 脚本字符串和 curl 下载。
5. 重建中文任务、数据库、接口、部署、源码学习文档，删除“迁移尚未验证”等过期描述。

## E2E 结果

最终一次运行输出：

```json
{
  "result": "PASS",
  "dataMode": "SYNTHETIC_AND_SIMULATED",
  "customerId": "315b6aec-b89a-40dc-b0fb-af57673c8036",
  "accountId": "816f3a8f-d315-4168-8eba-11c1dcf50cff",
  "transactionId": "c21afa56-f4f5-4735-89c6-27d1c732d2a9",
  "alertId": "b313189e-f580-4c3f-b6c4-d6adda079930",
  "caseId": "8448c1ef-cc50-4da8-ac7a-1aba0b3660c1",
  "caseStatus": "CLOSED",
  "firstRuleHits": 1,
  "duplicateRuleHits": 0,
  "customerRisk": "MEDIUM",
  "transactionRisk": "HIGH",
  "dashboardCustomers": 4,
  "auditExecuteRecords": 15,
  "csvReportBytes": 1175
}
```

该脚本真实调用运行中的 PostgreSQL/后端，覆盖：登录→客户→账户→交易首次/重复导入→Mock 筛查/复核→R001 首次/重复执行→客户/交易评分→预警复核→案件升级/分派/调查笔记/证据/待复核/关闭→仪表盘→审计→CSV。写入均为 `E2E-*` 合成记录。第一次调试运行已完成业务关闭但在 CSV 下载处遇到 PowerShell 客户端空引用，修复脚本后完整重跑通过；该失败和残留合成审计数据未被隐瞒。

## 已完成、未完成与阻塞

已完成：本轮代码检查、JDK 21 后端全量测试、前端生产构建、官方 npm registry 生产依赖审计、独立空库 Flyway、Compose 重建、真实 HTTP E2E、健康/OpenAPI 探测、问题修复和五类中文文档。

未完成：R004–R008/图规则、KYC 完整工作台、用户角色管理 UI、多预警并案、证据病毒扫描/对象存储、监管报送、前端单元测试、PostgreSQL Testcontainers、CI/CD 和生产高可用；详见项目任务手册。

环境阻塞：当前会话没有页面浏览器自动化接口，不能声称已完成浏览器点击/视觉回归；以 Vue 生产构建、Nginx HTTP 探测和真实 API E2E 替代。真实 Yente/Fineract 服务未配置，未声称外部实连成功。本机默认 Java 8 不能直接运行项目测试，但容器 JDK 21 已完成全量测试。
