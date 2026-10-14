# 数据库清单

## 迁移实测

Flyway 共 6 个版本：V1 初始结构，V2 客户/账户/交易约束，V3 规则/预警扩展，V4 禁用规划规则，V5 案件证据扩展，V6 筛查与评分。独立空库实测 V1–V6 全部 `success=true`；共 34 张应用表、49 个外键、81 个索引。临时数据库和容器已在验证后删除。

## 表清单

| 领域 | 表 | 关系/用途 |
|---|---|---|
| RBAC | `sys_user`、`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission` | 用户、角色、权限及多对多关系 |
| 审计 | `sys_audit_log` | 操作者、动作、资源、结果、IP、详情、时间 |
| 客户/KYC | `aml_customer`、`aml_customer_kyc` | 客户主数据；KYC 表当前无业务代码 |
| 客户评分 | `aml_customer_risk`、`aml_customer_risk_history` | 当前快照和只追加历史 |
| 账户 | `aml_account`、`aml_account_customer` | 账户及客户多对多关系 |
| 交易 | `aml_transaction_import_batch`、`aml_transaction` | 批次与交易；`source+external_id` 去重 |
| 交易评分 | `aml_transaction_risk`、`aml_transaction_risk_history` | 当前快照和只追加历史 |
| 筛查 | `aml_screening_request`、`aml_screening_match`、`aml_screening_review` | 请求、候选、人工复核及数据模式 |
| 规则 | `aml_rule`、`aml_rule_version`、`aml_rule_execution` | 定义、版本、执行 |
| 命中 | `aml_rule_hit`、`aml_rule_hit_transaction` | 稳定命中键及多交易关系 |
| 预警 | `aml_alert`、`aml_alert_transaction`、`aml_alert_history` | 预警、关联交易、状态历史 |
| 案件 | `aml_case`、`aml_case_alert`、`aml_case_note`、`aml_case_evidence`、`aml_case_history` | 案件闭环及证据元数据 |
| 报表占位 | `aml_report`、`aml_report_generation` | 当前没有 Service/API 使用；即时 CSV 不写这两表 |

## 主键、外键和索引原则

- 主键统一 UUID，连接表使用复合主键；外部业务号另有唯一约束。
- 金额 `numeric(19,4)`，交易金额大于 0；风险分数限制 0–100。
- 时间为 `timestamptz`；Hibernate JDBC 按 UTC。
- `aml_rule_hit.hit_key` 和 `aml_alert.rule_hit_id` 唯一，实现规则/预警幂等。
- 主要索引覆盖客户名称/风险、账户状态、交易账户+时间、规则命中、预警/案件状态、调查员、筛查复核、评分历史和审计资源。
- 证据逻辑移除要求活动标记与移除时间/人员/原因一致；文件本体位于独立卷。

## 初始化数据

- V1 初始化 5 个角色、11 个权限及角色权限关系。
- V1 初始化 AML-R001～R008 及 v1 参数；V3 仅标记 R001–R003 为 `IMPLEMENTED`；V4 禁用所有 `PLANNED` 规则。
- dev profile 在没有同名用户时创建 BCrypt 演示管理员；该用户不是 Flyway 初始数据。

## 示例与当前运行数据

示例均位于 `integrations/`，包括平台 CSV、AMLSim 官方列 CSV、窗口/大额规则数据和文本证据。审查执行 E2E 前，运行库有 4 客户、4 账户、15 交易、7 预警、3 案件、115 条审计；E2E 又写入一组合成闭环数据。不得将这些记录描述为真实客户数据。

当前规则运行库状态：R001–R003 已实现且启用；R001 活跃版本为 v2；R004–R008 为 `PLANNED` 且禁用。
