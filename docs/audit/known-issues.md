# 已知问题与归档前建议

> 2026-10-30 状态更正：下文 10-15/10-23 的认证事务回滚、无浏览器自动化、CSV 换行解析、规则 FAILED 记录与默认三规则 scope 故障均为历史问题。本轮 Flyway V7、认证 PostgreSQL 4 项、新增 KYC/规则 PostgreSQL 2 项及浏览器 3 条已复验通过。当前限制和失败重试见 `docs/optimization/kyc-closure-report.md`；其余条目需逐项按当前源码重新核验，不能整体视为现存缺陷。

## P0：归档阻断

1. **GitHub 远程归档尚未执行。** 最终整理阶段会完成敏感信息检查与本地基线提交；远程仓库创建、可见性选择和推送必须等待用户确认。项目还没有 `LICENSE`/`NOTICE`。
2. **历史登录失败锁定无效，工作区修复待验收。** 旧版 `AuthController.login` 在同一 `@Transactional` 方法中增加失败次数、写失败审计后抛异常，导致事务回滚；实测错误口令后 `failed_attempts=0`、FAILED LOGIN 审计=0，HTTP 400。当前 `fix/auth-login-audit` 工作区已有独立事务和 PostgreSQL 测试代码，但尚无通过结果，不将锁定/审计标为已修复。

## P1：验收与安全问题

3. **默认凭据出现在演示配置和登录表单。** 虽标明 dev 用途，但共享验收环境应使用 `.env` 覆盖，并在报告中注明生产禁用 dev profile。
4. **没有可重复执行的前端 UI 自动化测试套件。** 最终阶段已使用 Edge 对真实页面采集 11 张截图并人工抽检，但仓库中仍无 Playwright/Cypress 测试。
5. **宿主 LaTeX 工具链缺失。** 已用官方便携 Tectonic 0.17 生成并抽检 PDF；若要在另一台机器复现，应安装 Tectonic 或 XeLaTeX/latexmk。
6. **没有项目 LICENSE/NOTICE。** 源码归档前应明确自有代码许可，并保留依赖与外部样例的许可证归属说明。
7. **真实 Yente/Fineract 未联调。** 只能陈述适配器和 stub 契约测试通过；不得在论文写“外部系统集成成功”。

## P2：功能完整性

8. KYC 只有表；用户/角色管理没有业务 API/UI；`aml_report*` 两表没有业务代码。
9. R004–R008 只有定义和参数，处于 `PLANNED/disabled`；没有图规则、行为基线和实时调度。
10. 案件不支持多预警手工并案；证据没有病毒扫描、对象存储和预览。
11. Fineract 只读取单个储蓄账户流水，且外部 HTTP 调用位于数据库事务范围内；没有客户、贷款或增量同步。
12. 风险评分是固定教学模型；没有模型参数管理、审批、回测或校准。

## P2：工程质量

13. 客户/账户/交易直接返回 `PageImpl`，构建日志警告 JSON 结构不稳定；建议转换为显式分页 DTO。
14. 前端没有单元测试、组件测试、ESLint 脚本和端到端浏览器测试；只有 TypeScript/Vite 构建。
15. 后端测试主要使用 H2 且核心集成手工建表；建议增加 PostgreSQL Testcontainers 和真正由 Flyway 初始化的集成测试。
16. `aml_rule_execution.error_message/status=FAILED` 有数据库字段，但执行服务未捕获失败并持久化失败记录；事务失败时执行行可能整体回滚。
17. 仪表盘日期边界按上海时区计算，但趋势 SQL 用数据库会话时区 `cast(timestamptz as date)` 分组；UTC 会话下午夜附近可能归错日，应使用显式 `AT TIME ZONE 'Asia/Shanghai'` 并加边界测试。
18. Compose 只有 PostgreSQL healthcheck；backend/frontend 没有 healthcheck，frontend 也不等待后端健康。
19. Element Plus 与 ECharts vendor chunk 仍超过 Vite 500 KB 告警阈值。
20. 两个测试类存在 deprecated/unchecked 编译告警；应开启更严格编译选项后处理。

## 文档一致性

21. 项目早期验证记录包含不同阶段的测试数与数据量；引用时应优先采用 `docs/TEST_RESULTS.md` 顶部的最终交付复核，不能把历史阶段数字混为当前值。
22. 页面截图中的数据是模拟/合成数据，其中一条早期客户名称在界面显示为问号；不影响 API/E2E，但正式演示前可另建 UTF-8 中文样例。

本阶段按用户限制只记录问题，没有修改业务逻辑、数据库迁移或前端功能。
