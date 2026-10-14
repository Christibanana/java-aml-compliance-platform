# 项目开发计划

| 阶段 | 目标 | 验收证据 | 状态 |
|---|---|---|---|
| Phase 0 | 环境检查 | `deployment/environment-check.md` | 已完成；系统 Java 8 由项目便携 JDK 21 绕过 |
| Phase 1 | 研究与设计 | 研究、需求、架构、数据库、API 文档 | 已完成基线 |
| Phase 2 | 工程初始化 | 后端、前端、PostgreSQL、Flyway、Compose、健康检查、真实构建 | **已完成并验证通过（2026-10-14）** |
| Phase 3A | 客户→账户→模拟交易 | Flyway、JPA、Service、Controller、DTO、Vue 页面、测试、真实 PG HTTP 闭环 | **已完成并验证通过（2026-10-14）** |
| Phase 3B | 规则→预警→案件 | Drools 兼容、规则配置/版本、三类规则、窗口统计、幂等预警、查询审核 | **规则与预警范围已完成并验证通过（2026-10-14）**；复杂图规则后续实现 |
| Phase 3C | 案件调查与关闭 | 预警升级、分派、笔记、证据、状态机、审计、前后端与真实闭环 | **已完成并验证通过（2026-10-14）**；多预警手工并案和病毒扫描后续实现 |
| Phase 4 | 扩展模块 | KYC、筛查、评分、报表、审计 | **筛查、人工复核、客户/交易评分、AML 仪表盘、CSV 内部报表和审计查询均已完成并验证（2026-10-14）**；KYC 编辑与正式监管报送待实现 |
| Phase 5 | 外部集成 | AMLSim、Yente、Fineract 适配器 | **AMLSim 默认交易 CSV、Fineract 储蓄流水 API、Yente match 适配器及本地契约测试已完成（2026-10-14）**；当前环境没有运行三个上游系统，不标记真实服务端到端成功；账户/客户批量同步等扩展待实施 |
| Phase 6 | 测试修复 | 单元、接口、数据库、权限、状态、E2E | **已完成当前范围并验证（2026-10-15）**；已知缺陷见 `docs/ISSUES.md` |
| Phase 7 | 文档交付 | 任务手册、部署和验收材料 | **已完成本地交付（2026-10-15）**；GitHub 远程创建与推送等待用户确认 |

## Phase 2 验收结论

后端与前端本机构建、Docker 镜像构建、PostgreSQL 健康检查、Flyway 迁移、Hibernate schema validation、Spring Boot 健康端点、Nginx 代理和前端静态页面均已用真实命令验证。详细证据见 `docs/TEST_RESULTS.md` 与 `docs/deployment/phase2-validation-2026-10-14.md`。

## 后续阶段约束

基础项目已验证通过。在明确进入下一阶段前，不继续实现高级 AML 功能。
