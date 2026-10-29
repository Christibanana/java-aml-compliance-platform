# Java AML Compliance Platform

面向学习、演示和二次开发的 Java 反洗钱合规管理平台。系统以虚构客户和脱敏资料演示“客户 KYC → 账户 → 交易 → 规则 → 预警 → 案件”闭环；它不是生产监管系统，教学阈值不构成法律建议。

## 已实现与已验证（2026-10-30）

- **业务数据：** 客户与账户维护、KYC 资料摘要提交/人工审核/退回/到期复审及历史、CSV/AMLSim 交易导入和去重。演示时仅应填写虚构或脱敏摘要，不上传证件。
- **监测预警：** Drools 执行 R001 单笔大额、R002 高频和 R003 窗口累计规则，支持版本、窗口、幂等命中、预警审核及升级案件。默认三规则联合执行已由 PostgreSQL 与浏览器测试通过。
- **调查处置：** 案件分派、笔记、证据、复核和状态历史；模拟名单筛查及人工复核；客户与交易的可解释教学风险评分。筛查候选和分数不是违法认定。
- **统计审计：** 数据库实时仪表盘、四类内部 CSV 报表、审计查询与导出；JWT、BCrypt、角色权限、失败登录计数和锁定。外部 Yente/Fineract 仅有适配器与本地契约测试，未实连。

本轮使用 Flyway V1--V7 的真实 PostgreSQL 执行完整后端测试 42/42 通过；三条 Edge/Playwright 浏览器测试 3/3 通过，包含同一虚构客户的 KYC、交易、默认三规则、预警与案件。详细结果见 `docs/optimization/kyc-closure-report.md`。

## 当前限制

- AML-R004～R008 仅完成模型和参数，复杂图/循环转账算法未实现。
- Yente 与 Fineract 适配器已由本地 HTTP stub 验证契约，AMLSim 默认 schema 已完成真实 PostgreSQL 导入；本机未运行 Yente/Fineract，因此不能宣称这两个外部服务实连成功。Fineract 客户/贷款/增量同步和 AMLSim 账户/图文件导入尚未实现。
- 用户/角色维护后台和正式监管报送仍未实现；KYC 过期状态在读取时计算，在下一次提交时写入历史，不含自动提醒或双人复核；风险评分仍为固定教学模型。
- 2026-10-23 的默认规则字段故障与认证 Testcontainers 阻塞已在本轮修复/排除并复验；旧报告只代表其记录日期，不是当前结论。未执行真实 Yente/Fineract 联调、病毒扫描或生产安全验收。

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
# 在可访问 Docker 的 JDK 21 宿主机运行完整后端测试
cd backend
mvn -B test
cd ..

# 镜像构建仅打包，不代替上述测试
docker compose --progress plain build backend

cd frontend
npm run build
npm audit --omit=dev --registry=https://registry.npmjs.org
cd ..

docker compose --profile app up -d --build
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\e2e-smoke.ps1
```

样例 CSV：平台模板见 `integrations/amlsim/sample-transactions.csv`，AMLSim 默认列样例见 `integrations/amlsim/official-transactions-sample.csv`。当前验收见 `docs/optimization/kyc-closure-report.md`；历史全项目验收见 `docs/deployment/full-validation-2026-10-14.md`。

## 项目文档与验证材料

- 全面源码审查：`docs/audit/`，其中 `documentation-source-map.md` 对应文档与源码证据
- 最终交付清单：`docs/final-delivery-checklist.md`
- 项目说明书：`latex/output/01-项目说明书.pdf`
- 系统操作手册：`latex/output/02-系统操作手册.pdf`
- 系统部署手册：`latex/output/03-系统部署手册.pdf`
- LaTeX 源文件及真实运行截图：`latex/`、`docs/optimization/screenshots/`

仓库已配置 GitHub `origin`；当前没有 `LICENSE`/`NOTICE`。公开发布或复用前仍需由项目所有者确认可见性和许可方案。
