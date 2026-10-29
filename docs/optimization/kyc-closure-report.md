# KYC 与规则闭环验收报告

日期：2026-10-30。范围限于默认 R001–R003 执行可靠性、既有认证修复验收和 KYC 小流程；未扩展高级规则或外部系统。

## 实施内容

- Flyway V7 将 `aml_rule_execution.scope` 从 `varchar(30)` 扩大为 `varchar(160)`，不改 R001–R003 判断语义；KYC 表增加摘要、提交/审核人及时间、理由，新增只追加的历史表与 `KYC_REVIEW` 权限。
- `KycService` 在客户行锁保护下提交与审核：`PENDING → VERIFIED/REJECTED`；已通过且到期后才能新建一轮 `PENDING`，旧轮追加 `EXPIRED` 事件。读取时派生到期状态，未启动后台定时任务；KYC 操作与审计在同一事务。
- 客户页增加真实 API 驱动的 KYC 弹窗。页面警示仅使用合成/脱敏摘要。KYC 审核不改变客户风险等级。
- 浏览器第 2 条用例通过页面提交和审核；第 3 条用例把同一虚构客户的 KYC、账户、CSV、默认三规则、R001 预警及升级案件贯通。浏览器未执行案件关闭；其已有后端测试仍覆盖该动作。

## 实际命令与结果

本机使用项目内 JDK 21、Maven 3.9、Docker Desktop 29.8.2、已缓存的 PostgreSQL 16.10-alpine、Microsoft Edge。后端命令在 `backend/` 运行，进程环境设定 `JAVA_HOME`、`DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine`、`TESTCONTAINERS_RYUK_DISABLED=true`：

```powershell
mvn -B '-Dtest=KycAndRulePostgresIntegrationTest,AuthPostgresIntegrationTest' test
mvn -B test
```

定向 PostgreSQL **6/6**；完整后端 **42/42，0 failures，0 errors，0 skipped，BUILD SUCCESS**。认证 4 项覆盖错误密码提交、失败审计、第五次锁定、到期解锁、成功重置、未知账号统一响应和四路并发计数。新增 2 项在 Flyway V1–V7 初始化的 PostgreSQL 上验证默认三规则联合执行、幂等、KYC 状态、403、历史、审计和风险等级不变。测试方法未包裹外层事务，断言从数据库重新读取。

另在宿主机执行 `mvn -B '-Dmaven.test.skip=true' package`，JAR 打包成功。首次未给 PowerShell 中的 `-D` 参数加引号，Maven 报 `Unknown lifecycle phase ".test.skip=true"`；修正命令后成功。该打包结果不等同于容器镜像验收。

```powershell
cd frontend
npm run build
cd ..
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-frontend-e2e.ps1 -JavaHome (Resolve-Path '.tools/jdk21/jdk-21.0.12.1+1').Path
```

前端类型检查和生产构建成功，仍有大 chunk 非阻断告警。隔离 PostgreSQL + Edge/Playwright 最终 **3/3**；截图见 `docs/optimization/screenshots/frontend-e2e-kyc.png`、`frontend-e2e-r001-alert.png`、`frontend-e2e-case.png`。测试库使用独立 Compose 项目和临时内存存储，结束时关闭，不接触生产数据。

## 失败与修复记录

1. 首轮 Testcontainers 1.21.3 对 Docker 29 的 API 协商失败，6 个 PostgreSQL 用例均未进入业务断言；测试资源 `docker-java.properties` 固定 API 1.44 后解决。该配置仅进入测试 classpath。
2. 本机无法拉取 `testcontainers/ryuk:0.12.0`。使用已缓存 `postgres:16.10-alpine` 并仅在本轮测试进程中关闭 Ryuk；不宣称镜像仓库故障已修复。容器在 JVM 退出后关闭，后续 CI 应恢复正常清理机制或验证同等清理措施。
3. 扩展后的浏览器用例首轮 2/3：Element Plus 提示框按钮实际名称为英文 `OK`，测试误写“确定”，请求未提交。修正定位器后重新执行全部三条，3/3 通过。

## 边界

本轮未重建完整生产 Compose 镜像，也未运行真实 Yente/Fineract、跨浏览器兼容性、性能基准或生产安全验收。KYC 不采集原件、无自动到期通知、无强制提交人与审核人分离；输入仅用于虚构数据演示，生产隐私保护和适用法规仍需单独设计与审查。R004–R008 仍是规划项。

新增真实 PostgreSQL 测试后，后端 Dockerfile 改为在镜像内跳过测试编译与执行，以免构建容器无法访问 Docker 守护进程；完整测试必须先在宿主机执行。本轮运行 `docker compose --profile app build backend`，构建在 Maven Central 下载 `org.testcontainers:testcontainers:1.21.3` 时长时间无进展，人工中止；镜像构建仍为**待验证**，不能记作通过。构建输出保留于本地 `docs/optimization/docker-build-final.log`（忽略提交）。
