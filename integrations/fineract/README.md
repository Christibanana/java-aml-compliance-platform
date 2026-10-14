# Apache Fineract 适配状态

- 官方仓库：https://github.com/apache/fineract
- 核验分支/提交：`develop` / `d4ef4069547fba99400871877ceefadb62ad585b`
- 许可证：Apache License 2.0。

## 已实现

实现了只读储蓄账户交易适配器：

- `GET {baseUrl}/savingsaccounts/{id}?associations=transactions`
- Basic Authentication
- `fineract-platform-tenantid` 请求头
- 连接和请求超时
- 储蓄账户号、币种、交易 ID、日期、金额、存入/取出、冲正状态映射
- `POST /api/transactions/import/fineract/{savingsAccountId}` 导入到已存在且账号、币种一致的本地账户
- 幂等键为 `source=FINERACT + externalId=SAV-{accountId}-TX-{transactionId}`

Fineract 该接口只提供交易日期时，平台明确转换为该日期 UTC 00:00:00。冲正交易和方向不明确的交易会被拒绝并出现在批次错误中。

默认 `AML_FINERACT_ENABLED=false`。远端未启用、连接失败、超时、非 2xx 或响应缺字段时返回明确错误，不生成替代数据；本地 CSV 和 AMLSim 导入不依赖 Fineract。

## 未实现

- 当前环境没有运行 Fineract，未完成真实 Fineract 实例端到端验证。
- 未同步 Fineract 客户、贷款账户、转账对手方、增量游标或 Webhook/外部事件。
- 未处理 OAuth2、多租户批量轮询、分页流水或生产证书部署。
- 适配器不是 Fineract 的写回接口，不会修改远端核心账务数据。
