# 开源项目研究报告

核验日期：2026-10-13。以下信息来自指定项目官方 GitHub 仓库及官方文档；本机终端访问 GitHub 超时，因此未克隆源码，文件与说明通过只读在线访问核验。

## 核验摘要

| 项目 | 地址/README | 许可证 | 技术与启动 | Docker | 维护情况 | 集成判断 |
|---|---|---|---|---|---|---|
| Apache Fineract | 已核验 | Apache-2.0 | Java/Gradle/Spring，`./gradlew devRun`，PostgreSQL | 提供多套 Compose | 仓库约 11k 提交且持续更新 | 仅后续通过 REST 适配客户、账户、交易；不嵌入 |
| Apache KIE/Drools | 已核验；旧地址已重定向至 Apache KIE | Apache-2.0 | Java 规则引擎；10.x 发布线，10.2.0 发布说明包含 Java 21 支持工作 | 项目级示例为主 | 持续发布 | 作为受控规则执行依赖，不允许任意 DRL |
| IBM AMLSim | 已核验 | Apache-2.0 | Python/Java 组件与合成交易配置；面向 ML/图算法测试数据 | README 提供容器相关资产（本机未运行） | 有历史与开放 issue；活跃度低于其余项目 | 只适配已确认 CSV 输出，不引入生成器运行时 |
| OpenSanctions | 已核验 | 代码 MIT；生成数据 CC BY-NC 4.0；部分第三方数据无再许可 | Python/zavod，采集、清洗、去重、导出 | 官方建议 Docker | 约 19k 提交且持续更新 | 不复制数据；生产/商业使用须另行核验数据许可 |
| Yente | 已核验 | MIT（数据许可另计） | Python/FastAPI，依赖 Elasticsearch/OpenSearch，`uv sync`、`yente serve` | Dockerfile 与多套 Compose | 持续更新 | 通过 HTTP `ScreeningProvider` 适配；不可用时显式 Mock |

## 已由开源项目提供

- Fineract：核心银行客户、账户、交易、REST API、权限/审计和部署基础。
- Drools：事实模型匹配、DRL/KIE 执行、规则测试与版本化依赖基础。
- AMLSim：合成账户/交易及已知异常模式的数据生成思路。
- OpenSanctions/Yente：制裁、PEP 与相关实体数据加工，实体搜索/批量匹配 API。

## 本系统自行开发

- AML 领域数据模型、规则参数治理、执行幂等、命中到预警的聚合逻辑。
- 预警与案件状态机、分派、复核、证据、审计与内部报告草稿。
- 客户风险解释、看板、教学数据和完整中文操作/任务手册。
- 与外部服务隔离的端口、超时、错误映射与降级行为。

## 可通过 API 集成

- Fineract 的客户、账户与交易同步（第二阶段，可配置、默认关闭）。
- Yente 的实体检索和匹配（可选；匹配结果必须人工复核）。

## 暂不实现

- 部署完整 Fineract、OpenSanctions 数据流水线或 AMLSim 生成集群。
- 自动向监管机构提交报告。
- 机器学习风险模型、实时流计算和跨机构图谱。
- 任何受许可限制的真实名单数据分发。

## 已核验与待核验边界

- 已核验：仓库存在、README、页面展示的许可证、主要技术、官方启动说明、Docker 资产和当前页面维护活动。
- 待核验：各项目在本机的完整构建、具体 API 响应字段、AMLSim 各版本 CSV 列差异，以及所有传递依赖许可证。原因是本机终端网络超时且 Docker 引擎未运行。

## 官方来源

- https://github.com/apache/fineract
- https://github.com/apache/incubator-kie
- https://github.com/IBM/AMLSim
- https://github.com/opensanctions/opensanctions
- https://github.com/opensanctions/yente
