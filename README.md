# Java AML Compliance Platform

面向学习、演示和二次开发的 Java 反洗钱合规管理平台。当前版本已完成基础工程、核心 AML 闭环、名单筛查与风险评分、统计报表、审计查询和外部适配器的本地验证；它不是生产监管系统，教学阈值不构成法律建议。

## 已实现

- JWT 登录、BCrypt、本地演示管理员、五类角色与后端方法级权限。
- 客户分页/查询/编辑/停用、账户及客户关系/状态管理、CSV 交易批次导入/校验/去重。
- 八条规则的版本化元数据；AML-R001 大额、R002 高频、R003 累计交易规则，窗口统计、命中和预警幂等。
- 预警状态机、审核和升级案件；案件分页/详情、调查员分派、笔记、证据、复核、关闭状态机和完整审计。
- 数据库实时 AML 仪表盘、客户风险/预警趋势/规则命中/案件统计、四类业务 CSV、可筛选分页审计与审计 CSV。
- `ScreeningProvider`、Yente/模拟名单适配器、人工复核、客户与交易风险评分和评分历史；真实与模拟来源全链路标记。
- IBM AMLSim 官方默认交易 CSV 导入、Apache Fineract 储蓄账户交易只读 API 适配器；外部系统默认关闭且不影响本地运行。
- Vue 3 后台页面、OpenAPI/Swagger、Flyway 全量初始结构、Compose。

以上为 2026-10-15 历史验收基线。当前 `fix/auth-login-audit` 工作区存在未提交的认证修复代码和 PostgreSQL 集成测试，尚未完成测试，不将失败计数、锁定或失败审计列为已验证。

## 当前限制

- AML-R004～R008 仅完成模型和参数，复杂图/循环转账算法未实现。
- Yente 与 Fineract 适配器已由本地 HTTP stub 验证契约，AMLSim 默认 schema 已完成真实 PostgreSQL 导入；本机未运行 Yente/Fineract，因此不能宣称这两个外部服务实连成功。Fineract 客户/贷款/增量同步和 AMLSim 账户/图文件导入尚未实现。
- 用户/角色维护、KYC 编辑、监管报表尚未完成；风险评分当前为固定版本的可解释教学规则模型；案件多预警手工并案、证据病毒扫描和对象存储留作后续扩展。
- 历史版本错误密码导致失败计数与审计回滚；修复设计和待验收状态见 `docs/optimization/auth-issue-analysis.md`、`docs/manuals/06-用户权限模块任务手册.md`。
- 基础工程、客户→账户→交易、三类规则→预警和案件调查均已完成真实 PostgreSQL/Flyway 与完整 Compose 验证。

## 本地启动

### 1. 数据库

复制 `.env.example` 为 `.env` 并更换本地密码/密钥，然后：

```powershell
docker compose up -d postgres
```

### 2. 后端

需要 Java 21 与 Maven 3.9+：

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE='dev'
mvn spring-boot:run
```

本次验证使用项目内临时 Microsoft OpenJDK 21（`.tools/` 已忽略）；无需依赖该目录提交。Swagger：`http://localhost:8080/swagger-ui.html`，健康检查：`http://localhost:8080/actuator/health`。

### 3. 前端

```powershell
cd frontend
npm ci
npm run dev
```

访问 `http://localhost:5173`。dev 配置默认演示账户为 `admin / ChangeMe123!`，仅限本地；生产环境不得启用 dev 配置或默认密码。

### 4. 完整 Compose

```powershell
docker compose --profile app up --build
```

## 测试

```powershell
# 本机必须为 JDK 21；或直接使用容器构建（会执行后端测试）
docker compose --progress plain build backend

cd frontend
npm run build
npm audit --omit=dev --registry=https://registry.npmjs.org
cd ..

docker compose --profile app up -d --build
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\e2e-smoke.ps1
```

样例 CSV：平台模板见 `integrations/amlsim/sample-transactions.csv`，AMLSim 官方默认列样例见 `integrations/amlsim/official-transactions-sample.csv`；客户→账户→交易验收样例为 `integrations/amlsim/customer-account-transaction-demo.csv`。完整测试索引见 `docs/TEST_RESULTS.md`，本轮全项目验收见 `docs/deployment/full-validation-2026-10-14.md`，外部适配核验见 `docs/deployment/phase4c-external-integration-validation-2026-10-14.md`。

## 项目文档与验证材料

- 全面源码审查：`docs/audit/`，其中 `documentation-source-map.md` 对应文档与源码证据
- 最终交付清单：`docs/final-delivery-checklist.md`
- 项目说明书：`latex/output/01-项目说明书.pdf`
- 系统操作手册：`latex/output/02-系统操作手册.pdf`
- 系统部署手册：`latex/output/03-系统部署手册.pdf`
- LaTeX 源文件和 11 张真实运行截图：`latex/`

仓库当前未配置 GitHub remote，也没有 `LICENSE`/`NOTICE`。公开归档前需由项目所有者确认账号、仓库名、可见性和许可方案。
