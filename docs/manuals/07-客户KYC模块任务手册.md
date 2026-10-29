# 客户 KYC 模块任务手册

## 1. 模块目标与边界
在虚构客户资料上演示“提交脱敏摘要 → 人工审核 → 通过或退回 → 到期后重新提交”。不采集真实证件、不上传原件；审核结论不自动改变客户风险等级，也不代表满足任何特定司法辖区的监管要求。

## 2. 业务流程
具有 `CUSTOMER_WRITE` 权限的人员在客户页打开 KYC，填写不超过 500 字的合成/脱敏资料摘要并提交。记录进入 `PENDING`。具有 `KYC_REVIEW` 权限的人员填写理由，选择 `VERIFIED` 并指定未来的到期时间，或选择 `REJECTED` 退回。待审核状态不得重复提交或重复审核；已通过且尚未到期不得重新提交。到期后查询显示 `EXPIRED`，下一次提交时把旧记录置为过期、追加历史，再建立新一轮 `PENDING` 记录。退回后可重新提交。

## 3. 数据与权限
Flyway V7 扩展 `aml_customer_kyc` 的摘要、提交人/时间、审核人/时间、审核理由等字段，并新增只追加的 `aml_customer_kyc_history`。在客户行加锁的事务中校验状态，KYC 操作及安全审计同事务提交。读取要求 `CUSTOMER_READ`，提交要求 `CUSTOMER_WRITE`，审核要求新权限 `KYC_REVIEW`；V7 将审核权授予 `AML_MANAGER` 和 `SYSTEM_ADMIN`。客户端隐藏按钮不代替后端权限检查。

## 4. 接口与页面
- `GET /api/customers/{id}/kyc`：当前记录及历史，已过期的当前状态按有效期计算。
- `POST /api/customers/{id}/kyc/submit`：`documentSummary` 必填。
- `POST /api/customers/{id}/kyc/review`：`decision` 为 `VERIFIED` 或 `REJECTED`，`reason` 必填；通过时 `expiresAt` 必须是未来时间。
- 客户列表每行的“KYC”打开详情弹窗，可提交、审核、查看到期时间及操作历史；所有按钮调用真实 API，错误由全局 API 处理器反馈。

## 5. 验证与限制
`KycAndRulePostgresIntegrationTest` 在 Flyway 初始化的 PostgreSQL 上覆盖提交、重复提交拒绝、分析员审核 403、通过、重复审核拒绝、到期查询、复审退回、历史与审计持久化、客户风险等级不变。三条浏览器测试中的第二条通过客户页提交/审核，第三条把 KYC 与账户、交易、默认三规则、预警、案件贯通。本轮后端全量 42/42、浏览器 3/3 通过，见 `docs/optimization/kyc-closure-report.md`。

尚无自动到期提醒、证件原件采集、双人强制分离审核或正式法规判定。`EXPIRED` 在读取时是派生状态，只有再次提交才写入过期历史；不可将其描述为后台定时任务。
