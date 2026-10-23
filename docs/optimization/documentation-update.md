# v1.1.2 文档与 PDF 纠偏记录

日期：2026-10-23。范围仅含 Markdown 文档、LaTeX 源码和由其生成的三份 PDF；未修改业务代码。当前分支 `fix/auth-login-audit` 中既有的认证代码与测试改动属于上一轮未提交工作，本次提交不纳入。

## 修复内容

- 规则手册旧文误写“仅 R001 使用 Java 执行器、R002–R008 未实现、版本/启停 API 和 Drools 未实现”。实际 `RuleExecutionService` 构造三种事实，`DroolsRuleEvaluator` 加载 `transaction-monitoring.drl` 并执行 KIE session，`RuleController` 提供版本、启停、执行和查询 API；R004–R008 为禁用的 `PLANNED` 元数据。
- 用户权限手册旧文把失败锁定、失败审计列入已实现能力，也称登录集成和越权测试待补。历史 `CoreWorkflowIntegrationTest` 已覆盖成功登录和越权；旧版错误口令的 PostgreSQL 专项检查证实计数与失败审计回滚。当前 `LoginStateService` 与 `AuthPostgresIntegrationTest` 尚未验收，文档仅描述修复设计。
- 证据映射中错误的 `com/aml/platform` 路径已统一改为真实 `com/example/aml`，按文档章节逐项关联 Java 源码、API、数据库表和测试。同步更正 README、API 说明、功能矩阵、已知问题、需求与测试手册，以及三份 LaTeX 正文中的认证状态和历史测试日期。系统操作手册还去除了不存在的账号管理页面与账户类型输入说明。
- 截图说明明确为历史实际运行界面和合成数据，不代表本轮认证修复测试通过。

## PDF 编译与检查

在原 `latex/` 工程内以 MiKTeX XeLaTeX 对三份 `main.tex` 分别执行两遍，输出至原 `latex/output/`。首次试用非本发行版参数 `-jobname` 失败，改为 `-job-name` 后六次正式编译均返回 0。`pdfinfo` 检查 A4 和页数：项目说明书 26 页、系统操作手册 24 页、系统部署手册 18 页；`pdftotext` 能提取中文、目录和更新后的认证说明。编译日志未检出 `Overfull`、`LaTeX Error` 或 `undefined`。11 张现有截图全部可读取，宽度均为 1440 像素；抽查规则页截图清晰显示实现与规划状态。未重新运行应用或采集截图。

## 尚未核验

- 认证 PostgreSQL Testcontainers 测试尚未完成；历史 32/32 测试不包含当前修复，不可标记为 v1.1.1 已验收。
- 本轮仅编译与结构/文本检查 PDF，并抽查现有规则截图；未逐页人工视觉校对全部 68 页。
- 真实 Yente/Fineract 仍未实连；R004–R008、完整 KYC、用户管理和正式监管报送仍未实现。
